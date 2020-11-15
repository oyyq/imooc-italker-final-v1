package net.qiujuer.italker.factory.presenter.message;

import net.qiujuer.italker.common.Common;
import net.qiujuer.italker.factory.data.Pushed.PushedDataSource;
import net.qiujuer.italker.factory.data.Pushed.PushedUserRepository;
import net.qiujuer.italker.factory.data.helper.UserHelper;
import net.qiujuer.italker.factory.model.db.Message;
import net.qiujuer.italker.factory.model.db.User;

/**
 * @author qiujuer Email:qiujuer@live.cn
 * @version 1.0.0
 */
public class ChatUserPresenter
        extends ChatPresenter<ChatContract.UserView>
        implements ChatContract.Presenter {

    public ChatUserPresenter(PushedDataSource repo, ChatContract.UserView view, String receiverId ) {
        //能够确定是什么BaseDbRepository, 以及"对方"是人
        //super(new MessageRepository(receiverId),  view, receiverId, Common.RECEIVER_TYPE_NONE);
        super( repo == null? new PushedUserRepository(receiverId): repo,  view, receiverId, Common.RECEIVER_TYPE_NONE);
    }

    @Override
    public void start() {
        super.start();
        // 从本地拿这个人的信息
        User receiver = UserHelper.findFromLocal(mReceiverId);
        getView().onInit(receiver);
    }


}
