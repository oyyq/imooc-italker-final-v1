package net.qiujuer.italker.factory.presenter.message;

import android.support.v4.app.Fragment;
import android.support.v7.util.DiffUtil;
import android.text.TextUtils;

import net.qiujuer.italker.common.app.PresenterFragment;
import net.qiujuer.italker.factory.data.BaseDbRepository;
import net.qiujuer.italker.factory.data.Pushed.PushedDataSource;
import net.qiujuer.italker.factory.data.helper.MessageHelper;
import net.qiujuer.italker.factory.model.api.message.MsgCreateModel;
import net.qiujuer.italker.factory.model.db.GetPushedImpl;
import net.qiujuer.italker.factory.model.db.Message;
import net.qiujuer.italker.factory.persistence.Account;
import net.qiujuer.italker.factory.presenter.BaseSourcePresenter;
import net.qiujuer.italker.factory.utils.DiffUiDataCallback;

import java.util.Date;
import java.util.List;

/**
 * 聊天Presenter的基础类
 *
 * @author qiujuer Email:qiujuer@live.cn
 * @version 1.0.0
 */
@SuppressWarnings("WeakerAccess")
public class ChatPresenter<View extends ChatContract.View>
        extends BaseSourcePresenter<GetPushedImpl, GetPushedImpl, PushedDataSource, View>
        implements ChatContract.Presenter, LoaderListener {

    // 接收者Id，可能是群，或者人的ID
    protected String mReceiverId;
    // 区分是人还是群Id
    protected int mReceiverType;

    private boolean AppBarIsExpand;           //mAppBarLayout是否是展开状态

    private int initialUnread = -1;          //初次进入MessageActivity需要向上滚动的数量



    //被View调用
    public int getInitialUnread() {
        return initialUnread;
    }

    //被mSource调用
    public void setInitialUnread(int initialUnread) {
        this.initialUnread = initialUnread;
    }


    public void setAppBarIsExpand(boolean expand){
        AppBarIsExpand = expand;
    }


    public ChatPresenter(PushedDataSource source, View view,
                         String receiverId, int receiverType) {
        super(source, view);
        this.mReceiverId = receiverId;
        this.mReceiverType = receiverType;
    }

    @Override
    public void pushText(String content) {
        // 构建一个新的消息
        MsgCreateModel model = new MsgCreateModel.Builder()
                .receiver(mReceiverId, mReceiverType)
                .content(content, Message.TYPE_STR)
                .build();

        // 进行网络发送
        MessageHelper.push(model);
    }


    //path: 本地路径
    @Override
    public void pushAudio(String path, long time) {
        if(TextUtils.isEmpty(path)){
            return;
        }

        // 构建一个新的消息
        MsgCreateModel model = new MsgCreateModel.Builder()
                .receiver(mReceiverId, mReceiverType)
                .content(path, Message.TYPE_AUDIO)
                //attach
                .attach(String.valueOf(time))
                .build();

        // 进行网络发送
        MessageHelper.push(model);
    }


    @Override
    public void pushImages(String[] paths) {
        if (paths == null || paths.length == 0)
            return;
        // 此时路径是本地的手机上的路径
        for (String path : paths) {
            // 构建一个新的消息
            MsgCreateModel model = new MsgCreateModel.Builder()
                    .receiver(mReceiverId, mReceiverType)
                    .content(path, Message.TYPE_PIC)
                    .build();

            // 进行网络发送
            MessageHelper.push(model);
        }
    }



    @Override
    public boolean rePush(GetPushedImpl pushed) {

        final Message message = (Message) pushed;
        // 确定消息是可重复发送的
        if (Account.getUserId().equalsIgnoreCase(message.getSender().getId())
                && message.getStatus() == Message.STATUS_FAILED) {

            // 更改信息状态为"创建"
            message.setStatus(Message.STATUS_CREATED);
            // 构建发送Model
            MsgCreateModel model = MsgCreateModel.buildWithMessage(message);
            model.setRepushed(true);

            MessageHelper.push(model);
            return true;
        }

        return false;
    }



    public int getUnread(){
        BaseDbRepository repoSource = (BaseDbRepository)mSource;
        return repoSource.getUnread();
    }


    public void setUnread(int unread){
        BaseDbRepository repoSource = (BaseDbRepository)mSource;
        repoSource.setUnread(unread);
    }


    @Override
    public void reload() {
        //不需要做任何事
    }


    //todo 只适合非下拉刷新, 下拉刷新待拓展RecyclerView滚动
    @Override
    public void onDataLoaded(List<GetPushedImpl> getPusheds) {

        final int unread = getUnread();
        ChatContract.View view = getView();
        if (view == null)
            return;

        @SuppressWarnings("unchecked")
        List<GetPushedImpl> old = view.getRecyclerAdapter().getItems();         //旧数据

        DiffUiDataCallback<GetPushedImpl> callback = new DiffUiDataCallback<>(old, getPusheds);
        final DiffUtil.DiffResult result = DiffUtil.calculateDiff(callback);
        refreshData(result, getPusheds);

        //"我"停留在聊天界面时, 需要滚动到新到达消息
        boolean visible = ((Fragment)view).isVisible();
        if (visible) {
            // (1). 自己正在观看ChatFragment, 新消息到达 unRead = 1
            // (2). 自己由 MessageActivity.show 进入到ChatFragment, 但是visible = false
            if (unread > 0) {
                if(AppBarIsExpand){
                    ((PresenterFragment)view).CollapseAppBar(); }

                int position = getPusheds.size() - unread;          //得到消息在adapter中的位置
                setUnread(0);
                smoothMoveToPosition(position);                    //RecyclerView滑到position
            }
        }
    }



    //复写BaseSourcePresenter.destroy()方法, 使得Presenter被销毁时 todo 不要将
    // 数据监听器 Repo 仓库层也随之销毁, Repo还要保留在DbHelper的注册
    @Override
    public void destroy() {
        //Presenter层和View层解耦
        if (mView != null) {
            mView.setPresenter(null);
        }
        mView = null;

        BaseDbRepository repoSource = (BaseDbRepository)mSource;
        //mSource.unread清零
        repoSource.setUnread(0);
        //Presenter与mSource解绑, 保留mSource在DbHelper的注册
        repoSource.removeCallback(this);
        mSource = null;
    }


    //拿到Adapter中最早一条数据, 并从本地查询更早的数据
    @Override
    public void loadBefore(Date date) {
        //((BaseDbRepository)mSource).loadBefore(date);
    }

    //拿到Adapter中最晚的一条数据, 并从本地查询更晚的数据
    @Override
    public void loadAfter(Date date) {

    }
}
