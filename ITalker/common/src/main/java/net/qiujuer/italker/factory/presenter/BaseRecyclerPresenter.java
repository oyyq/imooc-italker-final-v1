package net.qiujuer.italker.factory.presenter;

import android.support.v7.util.DiffUtil;
import android.support.v7.widget.RecyclerView;

import net.qiujuer.genius.kit.handler.Run;
import net.qiujuer.genius.kit.handler.runable.Action;
import net.qiujuer.italker.common.widget.recycler.RecyclerAdapter;

import java.util.List;

/**
 * 对RecyclerView进行的一个简单的Presenter封装
 *
 * @author qiujuer Email:qiujuer@live.cn
 * @version 1.0.0
 */
public abstract class BaseRecyclerPresenter<ViewMode, View extends BaseContract.RecyclerView>
        extends BasePresenter<View> {

    public BaseRecyclerPresenter(View view) {
        super(view);
    }

    /**
     * 刷新一堆新数据到界面中
     *
     * @param dataList 新数据
     */
    protected void refreshData(final List<ViewMode> dataList) {
        Run.onUiAsync(new Action() {
            @Override
            public void call() {
                View view = getView();
                if (view == null)
                    return;

                // 基本的更新数据并刷新界面
                RecyclerAdapter<ViewMode> adapter = view.getRecyclerAdapter();
                adapter.replace(dataList);
                view.onAdapterDataChanged();
            }
        });
    }

    /**
     * 刷新界面操作，该操作可以保证执行方法在主线程进行
     * 差异刷新和刷出初始数据均可
     * @param diffResult 一个差异的结果集
     * @param dataList   具体的新数据
     */
    protected void refreshData(final DiffUtil.DiffResult diffResult, final List<ViewMode> dataList) {
        Run.onUiAsync(new Action() {
            @Override
            public void call() {
                // 这里是主线程运行时
                refreshDataOnUiThread(diffResult, dataList);
            }
        });
    }


    private void refreshDataOnUiThread(final DiffUtil.DiffResult diffResult, final List<ViewMode> dataList) {
        View view = getView();
        if (view == null)
            return;

        // 基本的更新数据并刷新界面
        RecyclerAdapter<ViewMode> adapter = view.getRecyclerAdapter();
        // 改变数据集合并不通知界面刷新
        adapter.getItems().clear();
        adapter.getItems().addAll(dataList);
        // 通知界面刷新占位布局
        view.onAdapterDataChanged();

        // 进行增量更新
        diffResult.dispatchUpdatesTo(adapter);
    }


    /**
     * 使得RecyclerView滑动到指定位置
     *
     * 1. 指定项在可见范围前, 向前滚动
     * 2.       在可见范围内, 调整到第一条
     * 3.       在可见范围下面, 向下滚动
     * 参考 https://stackoverflow.com/questions/24989218/get-visible-items-in-recyclerview
     *     https://juejin.im/post/6844903908502945805
     *     https://www.lagou.com/lgeduarticle/7454.html
     * @param position 指定的adapter position
     */
    public void smoothMoveToPosition(final int position){

        View view = getView();
        final RecyclerView recyclerView = view.getRecyclerView();

        recyclerView.post(new Runnable() {
            @Override
            public void run() {

                //能看到的第一个item定位
                int firstItem = recyclerView.getChildLayoutPosition(recyclerView.getChildAt(0));
                //能看到的最后一个item定位
                int lastItem = recyclerView.getChildLayoutPosition(
                        recyclerView.getChildAt(recyclerView.getChildCount()-1));

                if(position < firstItem){
                    //1. 跳转位置在第一个可见位置之前,
                    recyclerView.smoothScrollToPosition(position);

                }else if (position <= lastItem){
                    //2. 跳转位置在第一个可见位置之后, 最后一个可见项之前
                    int movePosition = position-firstItem;
                    if(movePosition >= 0 && movePosition < recyclerView.getChildCount() ){
                        int top = recyclerView.getChildAt(movePosition).getTop();
                        //smoothScrollToPostion 不会有效果, 此时调用smoothScrollby来滑动到指定位置
                        recyclerView.smoothScrollBy(0, top);            //往上滚动top距离
                    }
                }else {
                    //3. 未读消息在可见范围后
                    recyclerView.smoothScrollToPosition(position);
                }

            }
        });


    }




}
