package net.qiujuer.italker.factory.data.message;

import android.text.TextUtils;

import net.qiujuer.italker.factory.data.helper.DbHelper;
import net.qiujuer.italker.factory.data.helper.GroupHelper;
import net.qiujuer.italker.factory.data.helper.MessageHelper;
import net.qiujuer.italker.factory.data.helper.UserHelper;
import net.qiujuer.italker.factory.data.user.UserDispatcher;
import net.qiujuer.italker.factory.model.card.MessageCard;
import net.qiujuer.italker.factory.model.db.Group;
import net.qiujuer.italker.factory.model.db.Message;
import net.qiujuer.italker.factory.model.db.User;
import net.qiujuer.italker.factory.persistence.Account;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 消息中心的实现类
 *
 * @author qiujuer Email:qiujuer@live.cn
 * @version 1.0.0
 */
public class MessageDispatcher implements MessageCenter {

    private static MessageCenter instance;
    private final Executor executor = Executors.newSingleThreadExecutor();

    public static MessageCenter instance() {
        if (instance == null) {
            synchronized (MessageDispatcher.class) {
                if (instance == null)
                    instance = new MessageDispatcher();
            }
        }
        return instance;
    }


    @Override
    public void dispatch(MessageCard... cards) {
        if (cards == null || cards.length == 0)
            return;
        executor.execute(new MessageCardHandler(cards));
    }



    /**
     * 消息的卡片的线程调度的处理会触发run方法
     */
    private class MessageCardHandler implements Runnable {
        private final MessageCard[] cards;

        MessageCardHandler(MessageCard[] cards) {
            this.cards = cards;
        }

        @Override
        public void run() {
            List<Message> messages = new ArrayList<>();
            // 遍历
            for (MessageCard card : cards) {
                // 卡片基础信息过滤，错误卡片直接过滤
                if (card == null || TextUtils.isEmpty(card.getSenderId())
                        || TextUtils.isEmpty(card.getId())
                        || (TextUtils.isEmpty(card.getReceiverId())
                        && TextUtils.isEmpty(card.getGroupId())))
                    continue;

                // 消息卡片有可能是推送过来的，也有可能是直接造的
                // 推送来的代表服务器一定有，我们可以查询到（本地有可能有，有可能没有）
                // 如果是直接造的，那么先存储本地，后发送网络
                // 发送消息流程：写消息->存储本地->发送网络->网络返回->刷新本地状态
                Message message = MessageHelper.findFromLocal(card.getId());
                if (message != null) {
                    // 消息本身字段从发送后就不变化了，如果收到了消息，
                    // 本地有，同时本地显示消息状态为完成状态，则不必处理，
                    // 因为此时回来的消息和本地一定一摸一样
                    // 如果本地消息显示已经完成则不做处理
                    if (message.getStatus() == Message.STATUS_DONE)
                        continue;

/*
                    // todo 10-26 11:35认为不需要更新时间
                    if (card.getStatus() == Message.STATUS_DONE) {
                        // 代表网络发送成功，此时需要修改时间为服务器的时间
                        message.setCreateAt(card.getCreateAt());
                        // 如果没有进入判断，则代表这个消息是发送失败了
                    }
*/

                    // 更新一些会变化的内容, 但不改变message的时间
                    message.setContent(card.getContent());
                    message.setAttach(card.getAttach());
                    // 更新状态
                    message.setStatus(card.getStatus());
                    //消息是否重发 false -> true
                    message.setRepushed(card.isRepushed());

                } else {
                    // 没找到本地消息--> 一定是新消息, 初次在数据库存储
                    User sender = UserHelper.search(card.getSenderId());
                    if(sender == null) continue;

                    User receiver = null;
                    Group group = null;

                    if (!TextUtils.isEmpty(card.getReceiverId())) {
                        receiver = UserHelper.findFromLocal(card.getReceiverId());
                    } else if (!TextUtils.isEmpty(card.getGroupId())) {
                        group = GroupHelper.findFromLocal(card.getGroupId());
                    }

                    //没有接收者
                    if (receiver == null && group == null )
                        continue;

                    // 我发出或接到的消息 过滤
                    //发信人是"我"
                    if(card.getSenderId().equals(Account.getUserId())){
                        //收信人不是"我"的好友
                        if( (group == null && receiver!= null) && !receiver.isFollow() ) continue;
                        //"我"不在这个群
                        if( group != null && group.getJoinAt() == null) continue;
                    }
                    //发信人不是"我"
                    else {
                        //不是发给"我"
                        if( (group == null && receiver!= null) && !receiver.getId().equals(Account.getUserId()) ) continue;
                        //"我"不在这个群
                        if( group != null && group.getJoinAt() == null) continue;

                    }

                    message = card.build(sender, receiver, group);
                }

                messages.add(message);
            }

            if (messages.size() > 0)
                DbHelper.save(Message.class, messages.toArray(new Message[0]));

        }
    }
}
