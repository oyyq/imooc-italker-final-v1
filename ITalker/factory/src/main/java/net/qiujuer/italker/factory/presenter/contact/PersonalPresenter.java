package net.qiujuer.italker.factory.presenter.contact;

import net.qiujuer.genius.kit.handler.Run;
import net.qiujuer.genius.kit.handler.runable.Action;
import net.qiujuer.italker.factory.Factory;
import net.qiujuer.italker.factory.data.DataSource;
import net.qiujuer.italker.factory.data.helper.UserHelper;
import net.qiujuer.italker.factory.model.card.UserCard;
import net.qiujuer.italker.factory.model.db.User;
import net.qiujuer.italker.factory.persistence.Account;
import net.qiujuer.italker.factory.presenter.BasePresenter;

import retrofit2.Call;

/**
 * @author qiujuer Email:qiujuer@live.cn
 * @version 1.0.0
 */
public class PersonalPresenter
        extends BasePresenter<PersonalContract.View>
        implements PersonalContract.Presenter,
        DataSource.Callback<UserCard> {

    private User user;
    private Call changeCall;

    public PersonalPresenter(PersonalContract.View view) {
        super(view);
    }


    @Override
    public void start() {
        super.start();

        // 个人界面用户数据优先从网络拉取
        Factory.runOnAsync(new Runnable() {
            @Override
            public void run() {
                PersonalContract.View view = getView();
                if (view != null) {
                    String id = view.getUserId();
                    //不一定是"我"关注的
                    User user = UserHelper.searchFirstOfNet(id);
                    onLoaded(user);
                }
            }
        });
    }



    /**
     * 进行界面的设置
     * @param user 用户信息
     */
    private void onLoaded(final User user) {
        this.user = user;
        // 是否就是我自己
        final boolean isSelf = user.getId().equals(Account.getUserId());
        // 是否已经关注
        final boolean isFollow = isSelf || user.isFollow();
        // 已经关注同时不是自己才能聊天
        final boolean allowSayHello = isFollow && !isSelf;

        // 切换到Ui线程
        Run.onUiAsync(new Action() {
            @Override
            public void call() {
                final PersonalContract.View view = getView();
                if (view == null)
                    return;
                view.onLoadDone(user);
                view.setFollowStatus(isFollow);
                view.allowSayHello(allowSayHello);
            }
        });
    }


    @Override
    public User getUserPersonal() {
        return user;
    }


    @Override
    public void changeFollowStatus(boolean mIsFollowUser, String userId) {
        Call changeCall = this.changeCall;

        if ( changeCall != null && !changeCall.isCanceled()) {
            // 如果有上一次的请求，并且没有取消，
            // 则调用取消请求操作
            changeCall.cancel();
            return;
        }

        if(mIsFollowUser == false){
            this.changeCall = follow(userId);
        }else{
            this.changeCall = unfollow();
        }

    }

    @Override
    public Call follow(String userId) {
        return UserHelper.follow(userId, this);
    }

    @Override
    public Call unfollow() {
        //解关注
        if(user != null)
            return UserHelper.unfollow(user, this);

        return null;
    }


    @Override
    public void onDataLoaded(UserCard userCard) {
        User user = userCard.build();
        onLoaded(user);
    }

    @Override
    public void onDataNotAvailable(int strRes) {
        //不做任何处理
    }


}
