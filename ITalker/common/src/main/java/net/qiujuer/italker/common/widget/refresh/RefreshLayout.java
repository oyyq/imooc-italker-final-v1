package net.qiujuer.italker.common.widget.refresh;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.Scroller;

import net.qiujuer.genius.ui.Ui;
import net.qiujuer.italker.common.R;


/**
 * Created by AItsuki on 2016/6/13.
 * RefreshLayout的context在这里是GroupMemberActivity
 */
public class RefreshLayout extends ViewGroup {

    private static final String TAG = "RefreshLayout";
    private static final float DRAG_RATE = 0.5f;
    private static final int INVALID_POINTER = -1;

    // scroller duration
    private static final int SCROLL_TO_TOP_DURATION = 800;           //滚动缩回的时间
    private static final int SCROLL_TO_REFRESH_DURATION = 250;      //?
    private static final long SHOW_COMPLETED_TIME = 500;            //Header拉出来, 底部波浪刷新完的停住时间

    private View refreshHeader;
    private View target;

    private int currentTargetOffsetTop;             // ?
    private int totalDragDistance;      //需要下拉这个距离才进入松手刷新状态，默认和header高度一致, 但往往 > header高度才能触发refresh
    private int lastTargetOffsetTop;

    private boolean hasMeasureHeader;   // 是否已经计算头部高度
    private int touchSlop;
    private int headerHeight;           // header高度
    private int maxDragDistance;

    private int activePointerId;
    private boolean isTouch;
    private boolean hasSendCancelEvent;             //下拉过程中松手了, Header直接缩回
    private float lastMotionX;
    private float lastMotionY;
    private float initDownY;
    private float initDownX;

    private static final int START_POSITION = 0;    //Header顶部 的Y 偏移量

    private MotionEvent lastEvent;          //todo 哪个点按事件 ?
    private boolean mIsBeginDragged;         //是否开始拉动 ??


    private AutoScroll autoScroll;
    private State state = State.RESET;
    private OnRefreshListener refreshListener;          //底部波浪回调
    private boolean isAutoRefresh;


    // 刷新成功，显示500ms成功状态再滚动回顶部, todo 耗时800ms
    private Runnable delayToScrollTopRunnable = new Runnable() {
        @Override
        public void run() {
            autoScroll.scrollTo(START_POSITION, SCROLL_TO_TOP_DURATION);            //800ms
        }
    };


    private Runnable autoRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            // 标记当前是自动刷新状态，finishScroll调用时需要判断
            // 在actionDown事件中重新标记为false
            isAutoRefresh = true;
            changeState(State.PULL);
            // 缩回拉出的白色部分以及部分Header
            autoScroll.scrollTo(totalDragDistance, SCROLL_TO_REFRESH_DURATION);         //250ms
        }
    };



    public RefreshLayout(Context context) {
        this(context, null);
    }

    public RefreshLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        autoScroll = new AutoScroll();

        // 添加默认的头部，先简单的用一个ImageView代替头部
        ImageView imageView = new ImageView(context);
        imageView.setImageResource(R.drawable.default_face);
        imageView.setBackgroundColor(Color.BLACK);
        setRefreshHeader(imageView);
    }



    /**
     * 设置自定义header
     */
    public void setRefreshHeader(View view) {
        if (view != null && view != refreshHeader) {
            removeView(refreshHeader);

            // 为header添加layoutParams, 但是在xml文件已经有layoutParams
            LayoutParams layoutParams = view.getLayoutParams();
            if (layoutParams == null) {
                //设置Header的宽高
                layoutParams = new LayoutParams(LayoutParams.MATCH_PARENT,
                        (int) (Ui.dipToPx( getContext().getResources(), 120)+0.5f));
                view.setLayoutParams(layoutParams);
            }
            refreshHeader = view;
            addView(refreshHeader);                 //看源码: addView(refreshHeader, -1) 添加到 ViewGroup的头部
        }
    }




    public void setRefreshListener(OnRefreshListener refreshListener) {
        this.refreshListener = refreshListener;
    }


    //刷新结束
    public void refreshComplete() {
        changeState(State.COMPLETE);
        // if refresh completed and the target at top, change state to reset.
        if (currentTargetOffsetTop == START_POSITION) {
            changeState(State.RESET);
        } else {
            // waiting for a time to show refreshView completed state.
            // at next touch event, remove this runnable, 没有监听到点按
            if (!isTouch) {
                postDelayed(delayToScrollTopRunnable, SHOW_COMPLETED_TIME);
            }
        }
    }



    public void autoRefresh() {
        autoRefresh(500);
    }


    /**
     * 在onCreate中调用autoRefresh，此时View可能还没有初始化好，需要延长一段时间执行。
     *
     * @param duration 延时执行的毫秒值
     */
    public void autoRefresh(long duration) {
        if (state != State.RESET) {
            return;
        }
        postDelayed(autoRefreshRunnable, duration);
    }




    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (target == null) {
            ensureTarget();
        }

        if (target == null) {
            return;
        }

        // ----- measure target -----
        // target占满整屏, target: CoordinatorLayout, todo 不可去掉
        target.measure(MeasureSpec.makeMeasureSpec(
                getMeasuredWidth() - getPaddingLeft() - getPaddingRight(),
                MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(
                getMeasuredHeight() - getPaddingTop() - getPaddingBottom(), MeasureSpec.EXACTLY));

        // ----- measure refreshView-----
        measureChild(refreshHeader, widthMeasureSpec, heightMeasureSpec);
        if (!hasMeasureHeader) { // 防止header重复测量
            hasMeasureHeader = true;
            headerHeight = refreshHeader.getMeasuredHeight(); // header高度
            totalDragDistance = headerHeight - 30;   // 需要pull这个距离才进入松手刷新状态, headerHeight > 60
            if (maxDragDistance == 0) {  // 默认最大下拉距离为控件高度的五分之四
                maxDragDistance = totalDragDistance*4/5;
            }
        }
    }




    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int width = getMeasuredWidth();
        final int height = getMeasuredHeight();
        if (getChildCount() == 0) {
            return;
        }

        if (target == null) {
            ensureTarget();
        }
        if (target == null) {
            return;
        }


        // target铺满屏幕 todo 不可去掉
        final View child = target;
        final int childLeft = getPaddingLeft();
        final int childTop = getPaddingTop() + currentTargetOffsetTop;
        final int childWidth = width - getPaddingLeft() - getPaddingRight();
        final int childHeight = height - getPaddingTop() - getPaddingBottom();
        child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);


        // header放到target的上方，水平居中
        int refreshViewWidth = refreshHeader.getMeasuredWidth();
        refreshHeader.layout((width / 2 - refreshViewWidth / 2),
                -headerHeight + currentTargetOffsetTop,
                (width / 2 + refreshViewWidth / 2),
                currentTargetOffsetTop);
    }


    /**
     * 将第一个不是refreshHeader的Child作为target, 按照activity_scrolling.xmlCoordinatorLayout
     */
    private void ensureTarget() {
        // Don't bother getting the parent height if the parent hasn't been laid
        // out yet.
        if (target == null) {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (!child.equals(refreshHeader)) {
                    target = child;
                    break;
                }
            }
        }
    }




    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (!isEnabled() || target == null) {
            return super.dispatchTouchEvent(ev);
        }

        final int actionMasked = ev.getActionMasked(); // support Multi-touch
        switch (actionMasked) {
            //第一个 手指 初次接触到屏幕 时触发。
            case MotionEvent.ACTION_DOWN:
                //点按发生时按下去的point的pointerIndex == 0, 找到它的id
                activePointerId = ev.getPointerId(0);


                isAutoRefresh = false;
                isTouch = true;
                hasSendCancelEvent = false;
                mIsBeginDragged = false;


                lastTargetOffsetTop = currentTargetOffsetTop;
                currentTargetOffsetTop = target.getTop();



                initDownX = lastMotionX = ev.getX(0);
                initDownY = lastMotionY = ev.getY(0);


                autoScroll.stop();          //如: 在header缩回的过程中, 点按住了, 就会停止滚动


                removeCallbacks(delayToScrollTopRunnable);
                removeCallbacks(autoRefreshRunnable);
                super.dispatchTouchEvent(ev);
                return true;    // return true，否则可能接受不到move和up事件

            //手指 在屏幕上滑动 时触发，会多次触发。
            case MotionEvent.ACTION_MOVE:
                if (activePointerId == INVALID_POINTER) {
                    Log.e(TAG, "Got ACTION_MOVE event but don't have an active pointer id.");
                    return super.dispatchTouchEvent(ev);
                }
                lastEvent = ev;
                float x = ev.getX(MotionEventCompat.findPointerIndex(ev, activePointerId));
                float y = ev.getY(MotionEventCompat.findPointerIndex(ev, activePointerId));
                float yDiff = y - lastMotionY;
                float offsetY = yDiff * DRAG_RATE;

                lastMotionX = x;
                lastMotionY = y;


                if (!mIsBeginDragged && Math.abs(y - initDownY) > touchSlop) {
                    mIsBeginDragged = true;
                }

                if (mIsBeginDragged) {
                    boolean moveDown = offsetY > 0; // ↓
                    //boolean canMoveDown = canChildScrollUp();
                    boolean moveUp = !moveDown;     // ↑
                    boolean canMoveUp = currentTargetOffsetTop > START_POSITION;

                    // 判断是否拦截事件,
                   // if ((moveDown && !canMoveDown) || (moveUp && canMoveUp)) {
                    if ( moveDown || (moveUp && canMoveUp)) {
                        moveSpinner(offsetY);           // moveSpinner中会修改currentTargetOffsetTop, 使其不断变化
                        return true;
                    }
                }
                break;


            //与ACTION_UP一样的处理
            case MotionEvent.ACTION_CANCEL:
                //最后一个 手指 离开屏幕 时触发。
            case MotionEvent.ACTION_UP:
                isTouch = false;
                if (currentTargetOffsetTop > START_POSITION) {
                    finishSpinner();
                }
                activePointerId = INVALID_POINTER;      //屏幕上已经没有点触事件后将activePointerId设置为无效
                break;



            //有非主要的手指按下(即按下之前已经有手指在屏幕上)。
            case MotionEvent.ACTION_POINTER_DOWN:
                int pointerIndex = MotionEventCompat.getActionIndex(ev);
                if (pointerIndex < 0) {
                    Log.e(TAG, "Got ACTION_POINTER_DOWN event but have an invalid action index.");
                    return super.dispatchTouchEvent(ev);
                }
                lastMotionX = ev.getX(pointerIndex);
                lastMotionY = ev.getY(pointerIndex);
                lastEvent = ev;
                activePointerId = MotionEventCompat.getPointerId(ev, pointerIndex);
                break;
            //有非主要的手指抬起(即抬起之后仍然有手指在屏幕上)
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                lastMotionY = ev.getY(ev.findPointerIndex(activePointerId));
                lastMotionX = ev.getX(ev.findPointerIndex(activePointerId));
                break;
        }

        return super.dispatchTouchEvent(ev);
    }





    private void moveSpinner(float diff) {
        int offset = Math.round(diff);
        if (offset == 0) {
            return;
        }


        int targetY = Math.max(0, currentTargetOffsetTop + offset); // target不能移动到小于0的位置…… -> targetY >= 0
        // y = x - (x/2)^2
        float extraOS = targetY - totalDragDistance;
        float slingshotDist = totalDragDistance;
        float tensionSlingshotPercent = Math.max(0, Math.min(extraOS, slingshotDist * 2) / slingshotDist);
        float tensionPercent = (float) (tensionSlingshotPercent - Math.pow(tensionSlingshotPercent / 2, 3));

        if (offset > 0) { // 下拉的时候才添加阻力, tensionPercent >= 0, 在白色部分没拉出时 = 0
            offset = (int) (offset * (1f - tensionPercent));
            targetY = Math.max(0, currentTargetOffsetTop + offset);
        }


        // 1. 在RESET状态时，第一次下拉出现header的时候，设置状态变成PULL
        if (state == State.RESET && currentTargetOffsetTop == START_POSITION && targetY > 0) {
            changeState(State.PULL);
        }

        // 2. 在PULL或者COMPLETE状态时，header回到顶部的时候，状态变回RESET
        if (currentTargetOffsetTop > START_POSITION && targetY <= START_POSITION) {
            if (state == State.PULL || state == State.COMPLETE) {
                changeState(State.RESET);
            }
        }



        // 3. 如果是从底部回到顶部的过程(往上滚动)，并且手指是松开状态, 并且当前是PULL状态，状态变成LOADING，这时候我们需要强制停止autoScroll, todo 怎么触发的 ?
        if (state == State.PULL && !isTouch && currentTargetOffsetTop > totalDragDistance && targetY <= totalDragDistance) {
            //这个时候滚动回触到顶, 灰色部分刚好消失, 让scroller停止滚动
            autoScroll.stop();
            changeState(State.LOADING);
            if (refreshListener != null) {
                refreshListener.onRefresh();
            }
            // 因为判断条件targetY <= totalDragDistance，会导致不能回到正确的刷新高度（有那么一丁点偏差），调整change
            int adjustOffset = totalDragDistance - targetY;
            offset += adjustOffset;
        }


        setTargetOffsetTopAndBottom(offset);

        // 别忘了回调header的位置改变方法。
        if (refreshHeader instanceof RefreshHeader) {
            ((RefreshHeader) refreshHeader)
                    .onPositionChange(currentTargetOffsetTop, lastTargetOffsetTop, totalDragDistance, isTouch, state);

        }

    }



    private void finishSpinner() {
        if (state == State.LOADING) {
            if (currentTargetOffsetTop > totalDragDistance) {
                autoScroll.scrollTo(totalDragDistance, SCROLL_TO_REFRESH_DURATION);
            }
        } else {
            autoScroll.scrollTo(START_POSITION, SCROLL_TO_TOP_DURATION);
        }
    }


    private void changeState(State state) {
        this.state = state;


        RefreshHeader refreshHeader = this.refreshHeader instanceof RefreshHeader ?
                ((RefreshHeader) this.refreshHeader) : null;

        if (refreshHeader != null) {
            switch (state) {
                case RESET:
                    refreshHeader.reset();
                    break;
                case PULL:
                    refreshHeader.pull();
                    break;
                case LOADING:
                    refreshHeader.refreshing();
                    break;
                case COMPLETE:
                    refreshHeader.complete();
                    break;
            }
        }

    }




    //add offset
    private void setTargetOffsetTopAndBottom(int offset) {
        if (offset == 0) {
            return;
        }
        target.offsetTopAndBottom(offset);
        refreshHeader.offsetTopAndBottom(offset);
        lastTargetOffsetTop = currentTargetOffsetTop;
        currentTargetOffsetTop = target.getTop();
//        Log.e(TAG, "moveSpinner: currentTargetOffsetTop = "+ currentTargetOffsetTop);
        invalidate();
    }




    private void sendCancelEvent() {
        if (lastEvent == null) {
            return;
        }
        MotionEvent ev = MotionEvent.obtain(lastEvent);
        ev.setAction(MotionEvent.ACTION_CANCEL);
        super.dispatchTouchEvent(ev);
    }



    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = MotionEventCompat.getActionIndex(ev);
        final int pointerId = MotionEventCompat.getPointerId(ev, pointerIndex);
        if (pointerId == activePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            lastMotionY = ev.getY(newPointerIndex);
            lastMotionX = ev.getX(newPointerIndex);
            activePointerId = MotionEventCompat.getPointerId(ev, newPointerIndex);
        }
    }



/*

    public boolean canChildScrollUp() {
        if (android.os.Build.VERSION.SDK_INT < 14) {
            if (target instanceof AbsListView) {
                final AbsListView absListView = (AbsListView) target;
                return absListView.getChildCount() > 0
                        && (absListView.getFirstVisiblePosition() > 0 || absListView.getChildAt(0)
                        .getTop() < absListView.getPaddingTop());
            } else {
                return ViewCompat.canScrollVertically(target, -1) || target.getScrollY() > 0;
            }
        } else {
            return ViewCompat.canScrollVertically(target, -1);
        }
    }

*/



    private class AutoScroll implements Runnable {
        private Scroller scroller;
        private int lastY;

        public AutoScroll() {
            //全体布局滚动
            scroller = new Scroller(getContext());
        }

        @Override
        public void run() {
            //滚动动画是否结束了 ?
            boolean finished = !scroller.computeScrollOffset() || scroller.isFinished();

            if (!finished) {                //若滚动还未结束
                int currY = scroller.getCurrY();
                int offset = currY - lastY;
                lastY = currY;

                //滚动, 更新currentTargetOffsetTop
                moveSpinner(offset);

                //继续放到UI线程去
                post(this);
                onScrollFinish(false);

            } else {
                stop();
                onScrollFinish(true);
            }

        }


        public void scrollTo(int to, int duration) {
            int from = currentTargetOffsetTop;
            int distance = to - from;

            stop();
            if (distance == 0) {
                return;
            }
            scroller.startScroll(0, 0, 0, distance, duration);

            post(this);         //放到UI线程执行
        }



        private void stop() {
            removeCallbacks(this);
            if (!scroller.isFinished()) {
                scroller.forceFinished(true);
            }
            lastY = 0;
        }


    }




    /**
     * 在scroll结束的时候会回调这个方法
     *
     * @param isForceFinish 是否是强制结束的
     */
    private void onScrollFinish(boolean isForceFinish) {
        if (isAutoRefresh && !isForceFinish) {
            isAutoRefresh = false;
            changeState(State.LOADING);
            if (refreshListener != null) {
                refreshListener.onRefresh();
            }
            finishSpinner();
        }
    }



    //浸入式状态栏statusBar
    @Override
    protected boolean fitSystemWindows(Rect insets){
        //todo statusBar的背景Rect(黑色) -> 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            insets.left = 0;
            insets.top = 0;
            insets.right = 0;
        }
        return super.fitSystemWindows(insets);
    }




    public interface OnRefreshListener {
        void onRefresh();
    }

    public enum State {
        //缩回, 重置
        RESET,
        //被拉出(部分或全部)
        PULL,
        //底部波浪
        LOADING,
        //底部波浪结束, 停滞0.5s
        COMPLETE
    }

}
