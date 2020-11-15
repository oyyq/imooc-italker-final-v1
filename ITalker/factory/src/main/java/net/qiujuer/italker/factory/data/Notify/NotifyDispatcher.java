package net.qiujuer.italker.factory.data.Notify;

import android.text.TextUtils;
import net.qiujuer.italker.common.Common;
import net.qiujuer.italker.factory.data.helper.DbHelper;
import net.qiujuer.italker.factory.data.helper.GroupHelper;
import net.qiujuer.italker.factory.data.helper.NotifyHelper;
import net.qiujuer.italker.factory.data.helper.UserHelper;
import net.qiujuer.italker.factory.model.card.SysNotifyCard;
import net.qiujuer.italker.factory.model.db.Group;
import net.qiujuer.italker.factory.model.db.SysNotify;
import net.qiujuer.italker.factory.model.db.User;
import net.qiujuer.italker.factory.persistence.Account;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;


public class NotifyDispatcher implements NotifyCenter {

    private static NotifyDispatcher instance;
    private final Executor executor = Executors.newSingleThreadExecutor();

    public static NotifyDispatcher instance() {
        if (instance == null) {
            synchronized (NotifyDispatcher.class) {
                if (instance == null)
                    instance = new NotifyDispatcher();
            }
        }
        return instance;
    }


    @Override
    public void dispatch(SysNotifyCard... cards) {
        if (cards == null || cards.length == 0)
            return;
        // 丢到单线程池中
        executor.execute(new NotifyDispatcher.NotifyCardHandler(cards));
    }



    private class NotifyCardHandler implements Runnable {

        private final SysNotifyCard[] cards;
        NotifyCardHandler(SysNotifyCard[] cards) {
            this.cards = cards;
        }

        @Override
        public void run() {

                List<SysNotify> notifies = new ArrayList<>();

                for (SysNotifyCard card : cards) {
                    // 卡片基础信息过滤，错误卡片直接过滤
                    if ( card == null || card.getPushType() == Common.NON_PushType
                            || TextUtils.isEmpty(card.getId())
                            //检查是否推送给"我"
                            || !card.getReceiverId().equalsIgnoreCase(Account.getUserId())
                            //检查推送类型
                            || (!TextUtils.isEmpty(card.getSenderId()) && !TextUtils.isEmpty(card.getGroupId()) ) )
                        continue;

                    SysNotify notify = NotifyHelper.findFromLocal(card.getId());

                    if (notify != null) {
                        // 被分发的SysNotify不可能已经存储过, 都是 待存储在本地的, 包括"我"自己创建的Sys
                       continue;
                    } else {
                        User sender = null;
                        Group group = null;

                        if (!TextUtils.isEmpty(card.getSenderId())) {
                            sender = UserHelper.search(card.getSenderId());
                        }
                        if (!TextUtils.isEmpty(card.getGroupId())) {
                            group = GroupHelper.findFromLocal(card.getGroupId());
                        }

                        // 接收者至少有一个是null, 1.个人聊天推送, 2.群聊天推送, 3.无状态推送
                        if ( sender != null && group != null )
                            continue;

                        notify = card.build(sender, group);
                    }
                    notifies.add( notify );
                }

                if (notifies.size() > 0)
                    DbHelper.save(SysNotify.class, notifies.toArray(new SysNotify[0]));

        }

    }


}
