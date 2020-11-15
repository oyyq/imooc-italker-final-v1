package net.qiujuer.italker.push.frags.message;

import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.annotation.Nullable;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewStub;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import com.bumptech.glide.Glide;
import net.qiujuer.genius.kit.handler.Run;
import net.qiujuer.genius.kit.handler.runable.Action;
import net.qiujuer.genius.ui.Ui;
import net.qiujuer.genius.ui.compat.UiCompat;
import net.qiujuer.genius.ui.widget.Loading;
import net.qiujuer.italker.common.app.Application;
import net.qiujuer.italker.common.app.PresenterFragment;
import net.qiujuer.italker.common.tools.AudioPlayHelper;
import net.qiujuer.italker.common.widget.LinearLayoutManagerWithSmoothScroller;
import net.qiujuer.italker.common.widget.PortraitView;
import net.qiujuer.italker.common.widget.adapter.TextWatcherAdapter;
import net.qiujuer.italker.common.widget.recycler.RecyclerAdapter;
import net.qiujuer.italker.face.Face;
import net.qiujuer.italker.factory.data.helper.SessionHelper;
import net.qiujuer.italker.factory.model.db.GetPushedImpl;
import net.qiujuer.italker.factory.model.db.Message;
import net.qiujuer.italker.factory.model.db.Session;
import net.qiujuer.italker.factory.model.db.SysNotify;
import net.qiujuer.italker.factory.model.db.User;
import net.qiujuer.italker.factory.persistence.Account;
import net.qiujuer.italker.factory.presenter.message.ChatContract;
import net.qiujuer.italker.factory.presenter.message.ChatPresenter;
import net.qiujuer.italker.factory.presenter.message.LoaderListener;
import net.qiujuer.italker.factory.utils.FileCache;
import net.qiujuer.italker.push.R;
import net.qiujuer.italker.push.activities.MessageActivity;
import net.qiujuer.italker.push.frags.panel.PanelFragment;
import net.qiujuer.widget.airpanel.AirPanel;
import net.qiujuer.widget.airpanel.Util;
import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import butterknife.BindView;
import butterknife.OnClick;



/**
 * 进入时自动滚动到未读消息的地方
 * 撤回, 删除消息操作不做了
 * todo 当应用在后台待机却被杀死, 回到前台时, 需要恢复adapter中的数据, 这部分琢磨下 11-14 20:23
 * @author qiujuer Email:qiujuer@live.cn
 * @version 1.0.0
 */
public abstract class ChatFragment<InitModel>
        extends PresenterFragment<ChatContract.Presenter>
        implements AppBarLayout.OnOffsetChangedListener,
        ChatContract.View<InitModel>,
        PanelFragment.PanelCallback {

    //群或人Id, "对方"
    protected String mReceiverId;
    protected Adapter mAdapter;

    @BindView(R.id.toolbar)
    Toolbar mToolbar;

    @BindView(R.id.recycler)
    RecyclerView mRecyclerView;

    @BindView(R.id.appbar)
    AppBarLayout mAppBarLayout;


    @BindView(R.id.collapsingToolbarLayout)
    CollapsingToolbarLayout mCollapsingLayout;

    @BindView(R.id.edit_content)
    EditText mContent;

    @BindView(R.id.btn_submit)
    View mSubmit;


    // 控制底部面板与软键盘过度的Boss控件
    private AirPanel.Boss mPanelBoss;
    private PanelFragment mPanelFragment;

    //  软键盘 或 底部面板是否弹出
    private boolean isShow = false;
    RecyclerView.LayoutManager layoutManager;

    // 语音的基础
    private FileCache<AudioHolder> mAudioFileCache;
    private AudioPlayHelper<AudioHolder> mAudioPlayer;

    private LoaderListener mLoaderListener;

    public LoaderListener getLoaderListener() {
        return mLoaderListener;
    }

    public void setLoaderListener(LoaderListener mLoaderListener) {
        this.mLoaderListener = mLoaderListener;
    }



    @Override
    protected void initArgs(Bundle bundle) {
        super.initArgs(bundle);
        mReceiverId = bundle.getString(MessageActivity.KEY_RECEIVER_ID);
    }

    @Override
    protected final int getContentLayoutId() {
        return R.layout.fragment_chat_common;
    }

    // 得到顶部布局的资源Id
    @LayoutRes
    protected abstract int getHeaderLayoutId();


    @Override
    protected void initWidget(View root) {
        // 拿到占位布局
        // 替换顶部布局一定需要发生在super之前
        // 防止控件绑定异常
        ViewStub stub = (ViewStub) root.findViewById(R.id.view_stub_header);
        stub.setLayoutResource(getHeaderLayoutId());
        stub.inflate();

        // 在这里进行了控件绑定
        super.initWidget(root);

        // 初始化面板操作
        mPanelBoss = (AirPanel.Boss) root.findViewById(R.id.lay_content);
        mPanelBoss.setup(new AirPanel.PanelListener() {
            @Override
            public void requestHideSoftKeyboard() {
                // 请求隐藏软键盘
                Util.hideKeyboard(mContent);
            }
        });

        mPanelBoss.setOnStateChangedListener(new AirPanel.OnStateChangedListener() {
            @Override
            public void onPanelStateChanged(boolean isOpen) {
                // 面板改变
                if (isOpen)
                    onBottomPanelOpened();
            }

            @Override
            public void onSoftKeyboardStateChanged(boolean isOpen) {
                // 软键盘改变
                if (isOpen)
                    onBottomPanelOpened();
            }
        });

        mPanelFragment = (PanelFragment) getChildFragmentManager().findFragmentById(R.id.frag_panel);
        mPanelFragment.setup(this);

        initToolbar();
        initAppbar();
        initEditContent();

        getActivity().getWindow().getDecorView().getViewTreeObserver()
               // 监听软键盘弹出
               .addOnGlobalLayoutListener(new KeyboardOnGlobalChangeListener());

        //layoutManager = new LinearLayoutManager(getContext());            //原
        layoutManager = new LinearLayoutManagerWithSmoothScroller(getContext());
        ((LinearLayoutManager)layoutManager).setOrientation(LinearLayoutManager.VERTICAL);
        ((LinearLayoutManager)layoutManager).setStackFromEnd(false);
        mRecyclerView.setLayoutManager(layoutManager);


        mAdapter = new Adapter();
        mRecyclerView.setAdapter(mAdapter);
        // 添加适配器监听器，进行点击的实现
        mAdapter.setListener(new RecyclerAdapter.AdapterListenerImpl<GetPushedImpl>() {
            @Override
            public void onItemClick(RecyclerAdapter.ViewHolder holder, GetPushedImpl pushed) {
                if( pushed instanceof Message){
                    Message message = (Message) pushed;
                    if( message.getType() == Message.TYPE_AUDIO &&
                            holder instanceof ChatFragment.AudioHolder ) {
                        //对于 语音, 要先进行下载之后播放, 若已经下载好了, 就不需要下载, 直接播放
                        mAudioFileCache.download((AudioHolder) holder, message.getContent());          //云服地址, oss路径 || 本地存储路径
                        //todo  ! 在下载之后要将pushed.content改成本地存储地址 !
                    }
                }
            }
        });


        //recyclerView的滑动监听, todo 还需要解决recyclerView与底部输入框适配的问题
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                //recyclerView已经不能向上滑动, 找出mSource.dataList中最早的数据, dataList[0]
                if(!mRecyclerView.canScrollVertically(-1)){
                    loadOlderData();
                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
            }
        });


/*
        //测试recyclerView的滑动状态改变
        String TAG = "oyyq";
        int TAG_CHECK_SCROLL_UP = -1;
        int TAG_CHECK_SCROLL_DOWN = 1;
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            //只有在滑动状态改变时才调用的方法, 不是滑动过程中多次调用, 例如 newState = 0 -> 1 -> 2 -> 0
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                Log.e(TAG, "-----------onScrollStateChanged-----------");
                //静止: newState == 0; 手松开惯性滑动: newState == 2; 手拖着滑动: newState = 1
                Log.e(TAG, "newState: " + newState);
            }

            //滑动过程中多次触发的方法,
            // 经过测试发现RecyclerView触发canScrollVertically(TAG_CHECK_SCROLL_UP) == -1的时候是APPBarLayout刚刚拉出的时候
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                Log.e(TAG, "-----------onScrolled-----------");
                Log.e(TAG, "dx: " + dx);
                Log.e(TAG, "dy: " + dy);
                Log.e(TAG, "CHECK_SCROLL_UP: " + recyclerView.canScrollVertically(TAG_CHECK_SCROLL_UP));
                Log.e(TAG, "CHECK_SCROLL_DOWN: " + recyclerView.canScrollVertically(TAG_CHECK_SCROLL_DOWN));
            }
        });

        */


    }


    private void loadOlderData(){
        List<GetPushedImpl> histories = mAdapter.getItems();
        if(histories != null && histories.size() > 0) {
            Date createAt = histories.get(0).getCreateAt();
            this.mLoaderListener.loadBefore(createAt);
        }
    }

    private void loadNewerData(){
        //不做
    }


    /**
     *  复写Fragment.onStart方法
     *     一旦进入界面就初始化
     */
    @Override
    public void onStart() {
        super.onStart();

        if(mReloadLocalData){
            // 进入界面的时候就进行初始化
            mAudioPlayer = new AudioPlayHelper<>(new AudioPlayHelper.RecordPlayListener<AudioHolder>() {
                @Override
                public void onPlayStart(AudioHolder audioHolder) {
                    // 范型作用就在于此
                    audioHolder.onPlayStart();
                }

                @Override
                public void onPlayStop(AudioHolder audioHolder) {
                    // 直接停止
                    audioHolder.onPlayStop();
                }

                @Override
                public void onPlayError(AudioHolder audioHolder) {
                    // 提示失败
                    Application.showToast(R.string.toast_audio_play_error);
                }
            });

            // 下载工具类
            mAudioFileCache = new FileCache<>("audio/cache", "mp3", new FileCache.CacheListener<AudioHolder>() {
                @Override
                public void onDownloadSucceed(final AudioHolder holder, final File file) {
                    Run.onUiAsync(new Action() {
                        @Override
                        public void call() {
                            // 主线程播放, 传入trigger的是当前 想要播放 或 正在播放的holder
                            // 若正在播放 -> trigger停止, 想要播放 -> 停止当前正在播放, 播放holder
                            mAudioPlayer.trigger(holder, file.getAbsolutePath());
                        }
                    });
                }

                @Override
                public void onDownloadFailed(AudioHolder holder) {
                    Application.showToast(R.string.toast_download_error);
                }
            });

            //收起AppBar
            CollapseAppBar();
            //滚动到未读位置,
            ScrollToUnRead();

            mReloadLocalData = false;
        }

    }



    void ScrollToUnRead(){
        ChatPresenter chatPresenter = (ChatPresenter)mPresenter;
        int Initunread = chatPresenter.getInitialUnread();

        if (Initunread > 0) {
            int position = mAdapter.getItemCount() - Initunread;            //消息在adapter中的位置
            mRecyclerView.scrollToPosition(position);
        }
        //不需要chatPresenter.setunReadMessage(0), 放到PushedRepo.load中去了
    }



    private void onBottomPanelOpened() {
        // 当底部面板或者软键盘打开时触发
       CollapseAppBar();
    }

    @Override
    public boolean onBackPressed() {
        if (mPanelBoss.isOpen()) {
            // 关闭面板并且返回true代表自己已经处理了 "返回", 也就是结束MessageActivity之前要先关闭面板
            mPanelBoss.closePanel();
            return true;
        }
        return super.onBackPressed();
    }


    @Override
    protected void initData() {
        super.initData();
        // 开始进行初始化操作
        mPresenter.start();
    }

    // 初始化Toolbar
    protected void initToolbar() {
        Toolbar toolbar = mToolbar;
        toolbar.setNavigationIcon(R.drawable.ic_back);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getActivity().finish();
            }
        });
    }


    //  给界面的Appbar设置一个监听，得到关闭与打开的时候的进度
    private void initAppbar() {
        mAppBarLayout.addOnOffsetChangedListener(this);
    }


    // 初始化输入框监听
    private void initEditContent() {
        mContent.addTextChangedListener(new TextWatcherAdapter(mContent) {
            @Override
            public void afterTextChanged(Editable editable) {
                super.afterTextChanged(editable);
                String content = editable.toString().trim();
                boolean needSendMsg = !TextUtils.isEmpty(content);
                // 设置状态，改变对应的Icon
                mSubmit.setActivated(needSendMsg);
            }
        });
    }



    // 子类复写AppBarLayout.OnOffsetChangedListener需要复写的方法, 监听AppBarLayout的滑动
    @Override
    public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
        if (verticalOffset == 0) {
            if (mCurrentState != State.EXPANDED) {
                onStateChanged(appBarLayout, State.EXPANDED);
            }
            mCurrentState = State.EXPANDED;
        } else if (Math.abs(verticalOffset) >= appBarLayout.getTotalScrollRange()) {
            if (mCurrentState != State.COLLAPSED) {
                onStateChanged(appBarLayout, State.COLLAPSED);
            }
            mCurrentState = State.COLLAPSED;
        } else {
            if (mCurrentState != State.IDLE) {
                onStateChanged(appBarLayout, State.IDLE);
            }
            mCurrentState = State.IDLE;
        }

    }


    private State mCurrentState = State.IDLE;
    public enum State {
        EXPANDED,
        COLLAPSED,
        IDLE
    }

    private void onStateChanged(AppBarLayout appBarLayout, State state){
        Log.d("STATE", state.name());

        if( state == State.EXPANDED ) {
            //展开
            ((ChatPresenter)mPresenter).setAppBarIsExpand(true);
        }else if(state == State.COLLAPSED){
            //折叠状态
            ((ChatPresenter)mPresenter).setAppBarIsExpand(false);

        }else {
            //中间状态
        }
    }



    @Override
    public void CollapseAppBar() {
        //Ui thread execute
        Run.onUiAsync(new Action() {
            @Override
            public void call() {
                //收起
                if (mAppBarLayout != null)
                    mAppBarLayout.setExpanded(false, true);
            }
        });
    }



    @OnClick(R.id.btn_face)
    void onFaceClick() {
        // 仅仅只需请求打开即可
        mPanelBoss.openPanel();
        mPanelFragment.showFace();
    }

    @OnClick(R.id.btn_record)
    void onRecordClick() {
        mPanelBoss.openPanel();
        mPanelFragment.showRecord();
    }

    private void onMoreClick() {
        mPanelBoss.openPanel();
        mPanelFragment.showGallery();
    }


    @OnClick(R.id.btn_submit)
    void onSubmitClick() {
        if (mSubmit.isActivated()) {
            // 发送
            String content = mContent.getText().toString();
            mContent.setText("");
            mPresenter.pushText(content);
        } else {
            onMoreClick();
        }
    }



    @Override
    public void onDestroy() {
        super.onDestroy();
        mAudioPlayer.destroy();

        Session session = SessionHelper.findFromLocal(mReceiverId);
        if(session != null){
            //现在开始需要更新unReadCount
            Session.updateSessionOuter(session, session1 -> session1.setNeedUpdateUnReadCount(true));
        }
    }



    @Override
    public RecyclerAdapter<GetPushedImpl> getRecyclerAdapter() {
        return mAdapter;
    }


    @Override
    public RecyclerView getRecyclerView() {
        return mRecyclerView;
    }

    //当mAdapter.mDataList数据发生改变时的回调, 如mDataList所有数据换新
    @Override
    public void onAdapterDataChanged() {
        // 界面没有占位布局，Recycler是一直显示的，所有不需要做任何事情
    }

    @Override
    public EditText getInputEditText() {
        // 返回输入框
        return mContent;
    }

    @Override
    public void onSendGallery(String[] paths) {
        // 图片回调回来
        mPresenter.pushImages(paths);
    }

    @Override
    public void onRecordDone(File file, long time) {
        // 语音回调回来
        mPresenter.pushAudio(file.getAbsolutePath(), time);
    }

    // 内容的适配器
    private class Adapter extends RecyclerAdapter<GetPushedImpl> {

        // 返回Cell的xml布局id, 6种布局, R.layout.***
        @Override
        protected int getItemViewType(int position, GetPushedImpl pushed) {

            if ( pushed instanceof Message) {
                Message message = (Message) pushed;
                //判断消息"左右"
                boolean isRight = Objects.equals(message.getSender().getId(), Account.getUserId());
                switch ( message.getType()) {
                    //文字
                    case Message.TYPE_STR:
                        return isRight ? R.layout.cell_chat_text_right : R.layout.cell_chat_text_left;
                    //语音
                    case Message.TYPE_AUDIO:
                        return isRight ? R.layout.cell_chat_audio_right : R.layout.cell_chat_audio_left;
                    //图片
                    case Message.TYPE_PIC:
                        return isRight ? R.layout.cell_chat_pic_right : R.layout.cell_chat_pic_left;
                    //文件或者其他
                    default:
                        return isRight ? R.layout.cell_chat_text_right : R.layout.cell_chat_text_left;

                }
            }
            else if( pushed instanceof SysNotify ) {
                //SysNotify的cell布局
                return R.layout.sysnotify;
            }else {
                //其他情况, 以后扩展
                return 0;
            }

        }

        @Override
        protected ViewHolder<GetPushedImpl> onCreateViewHolder(View root, int viewType) {
            switch (viewType){
                //左右都是同样的TextHolder, 在cell_chat..xml文件的最外层FrameLayout,
                case R.layout.cell_chat_text_right:
                case R.layout.cell_chat_text_left:
                    return new TextHolder(root);

                case R.layout.cell_chat_audio_right:
                case R.layout.cell_chat_audio_left:
                    return new AudioHolder(root);

                case R.layout.cell_chat_pic_right:
                case R.layout.cell_chat_pic_left:
                    return new PicHolder(root);

                case R.layout.sysnotify:
                    return new NotifyHolder(root);

                default:
                    return new TextHolder(root);

            }

        }
    }




    //SysNotify的ViewHolder
    class NotifyHolder extends RecyclerAdapter.ViewHolder<GetPushedImpl>{

        @BindView(R.id.txt_notify)
        TextView mNotify;

        public NotifyHolder(View itemView) {
            super(itemView);
        }

        @Override
        protected void onBind(GetPushedImpl pushed) {
            //文字绑定, 将系统消息绑定到对话框
            mNotify.setText(pushed.getContent());
        }

    }



    // Holder的基类, 不设置成private --> private无法进行Butterknife注入
    class BaseHolder extends RecyclerAdapter.ViewHolder<GetPushedImpl> {
        @BindView(R.id.im_portrait)
        PortraitView mPortrait;

        // 允许为空，左边没有，右边有
        @Nullable
        @BindView(R.id.loading)
        Loading mLoading;

        public BaseHolder(View itemView) {
            super(itemView);
        }


        /**
         * onBind方法: Adapter中的item变化一次就onBind重新绑定状态一次
         * @param pushed Message / SysNotify
         */
        @Override
        protected void onBind(GetPushedImpl pushed) {
            User sender = pushed.getSender();
            //BaseModel.load() 从数据表中拿出sender, 并进行初始化加载
            sender.load();

            mPortrait.setup(Glide.with(ChatFragment.this), sender);

            // 当前布局在右边
            if (mLoading != null) {
                //根据message的状态来决定是否应该显示Loading
                int status = pushed.getStatus();
                if (status == Message.STATUS_DONE) {
                    // 正常状态, 隐藏Loading
                    mLoading.stop();
                    mLoading.setVisibility(View.GONE);
                } else if (status == Message.STATUS_CREATED) {
                    // 正在发送中的状态
                    mLoading.setVisibility(View.VISIBLE);
                    mLoading.setProgress(0);
                    mLoading.setForegroundColor(UiCompat.getColor(getResources(), R.color.colorAccent));
                    mLoading.start();
                } else if (status == Message.STATUS_FAILED) {
                    // 发送失败状态, 允许重新发送
                    mLoading.setVisibility(View.VISIBLE);
                    mLoading.stop();
                    mLoading.setProgress(1);
                    mLoading.setForegroundColor(UiCompat.getColor(getResources(), R.color.alertImportant));
                }

                // 当状态是错误状态时才允许点击
                mPortrait.setEnabled(status == Message.STATUS_FAILED);
            }

        }



        @OnClick(R.id.im_portrait)
        void onRePushClick() {
            // 重新发送 此时mData.STATUS == Message.STATUS_FAILED
            if (mLoading != null && mPresenter.rePush(mData)) {
                // 必须是右边的才有可能需要重新发送
                // 状态改变需要重新刷新界面当前的信息
                updateData(mData);
            }

        }
    }


    // 文字的Holder
    class TextHolder extends BaseHolder {
        @BindView(R.id.txt_content)
        TextView mContent;
        public TextHolder(View itemView) {
            super(itemView);
        }

        @Override
        protected void onBind(GetPushedImpl pushed) {
            super.onBind(pushed);

            Spannable spannable = new SpannableString(pushed.getContent());
            // 解析表情
            Face.decode(mContent, spannable, (int) Ui.dipToPx(getResources(), 20));
            // 把内容设置到布局上
            mContent.setText(spannable);
        }
    }



    // 语音的Holder
    class AudioHolder extends BaseHolder {
        @BindView(R.id.txt_content)
        TextView mContent;
        @BindView(R.id.im_audio_track)
        ImageView mAudioTrack;

        public AudioHolder(View itemView) {
            super(itemView);
        }

        @Override
        protected void onBind(GetPushedImpl pushed) {
            super.onBind(pushed);
            //拿到播放时长 long 30000
            String attach = TextUtils.isEmpty(pushed.getAttach()) ? "0" :
                    pushed.getAttach();
            mContent.setText(formatTime(attach));
        }

        // 当播放开始
        void onPlayStart() {
            // 显示
            mAudioTrack.setVisibility(View.VISIBLE);
        }

        // 当播放停止
        void onPlayStop() {
            // 占位并隐藏
            mAudioTrack.setVisibility(View.INVISIBLE);
        }

        private String formatTime(String attach) {
            float time;
            try {
                // 毫秒转换为秒
                time = Float.parseFloat(attach) / 1000f;
            } catch (Exception e) {
                time = 0;
            }
            // 12000/1000f = 12.0000000
            // 取整一位小数点 1.234 -> 1.2 1.02 -> 1.0
            String shortTime = String.valueOf(Math.round(time * 10f) / 10f);
            // 1.0 -> 1     1.2000 -> 1.2
            shortTime = shortTime.replaceAll("[.]0+?$|0+?$", "");
            return String.format("%s″", shortTime);
        }
    }




    // 图片的Holder
    class PicHolder extends BaseHolder {
        @BindView(R.id.im_image)
        ImageView mContent;


        public PicHolder(View itemView) {
            super(itemView);
        }

        @Override
        protected void onBind(GetPushedImpl pushed) {
            super.onBind(pushed);
            // 当是图片类型的时候，Content中就是具体的地址
            String content = pushed.getContent();

            Glide.with(ChatFragment.this)
                    .load(content)
                    .fitCenter()
                    .into(mContent);

        }
    }




    /**
     * 软键盘视图监听以及自动滑到最底部
     */
    private class KeyboardOnGlobalChangeListener implements ViewTreeObserver.OnGlobalLayoutListener {

        private int getScreenHeight() {
            return ((WindowManager) getActivity().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getHeight();
        }

        @Override
        public void onGlobalLayout() {
            Rect rect = new Rect();
            getActivity().getWindow().getDecorView().getWindowVisibleDisplayFrame(rect);
            int screenHeight = getScreenHeight();
            int keyboardHeight = screenHeight - rect.bottom;                //软键盘高度

            if (Math.abs(keyboardHeight) > screenHeight / 5 && !isShow) {
                setScrollBottom();
                isShow = true;
            } else {
                isShow = false;

            }
        }
    }


    //RecyclerView滑到底部
    private void setScrollBottom(){
       mRecyclerView.post(new Runnable() {
           @Override
           public void run() {
                if(mAdapter.getItemCount() > 0 ) {
                    mRecyclerView.scrollToPosition(mAdapter.getItemCount() - 1);

//                    View target = layoutManager.findViewByPosition(mAdapter.getItemCount() - 1);
//                    if (target != null) {
//                        // int offset=  recyclerView.getMeasuredHeight() - target.getMeasuredHeight();
//                        ((LinearLayoutManager) layoutManager).scrollToPositionWithOffset(mAdapter.getItemCount() - 1, Integer.MAX_VALUE);//滚动偏移到底部
//                    }

                }
           }
       });

    }






}
