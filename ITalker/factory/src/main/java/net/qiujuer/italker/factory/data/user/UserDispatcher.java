package net.qiujuer.italker.factory.data.user;

import android.text.TextUtils;

import net.qiujuer.italker.factory.data.helper.DbHelper;
import net.qiujuer.italker.factory.data.helper.UserHelper;
import net.qiujuer.italker.factory.model.card.UserCard;
import net.qiujuer.italker.factory.model.db.User;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * @author qiujuer Email:qiujuer@live.cn
 * @version 1.0.0
 */
public class UserDispatcher implements UserCenter {
    private static UserCenter instance;
    // 单线程池；处理卡片一个个的消息进行处理
    private final Executor executor = Executors.newSingleThreadExecutor();


    public static UserCenter instance() {
        if (instance == null) {
            synchronized (UserDispatcher.class) {
                if (instance == null)
                    instance = new UserDispatcher();
            }
        }
        return instance;
    }

    @Override
    public void dispatch(int follow_type, UserCard... cards) {
        if (cards == null || cards.length == 0)
            return;

        executor.execute(new UserCardHandler(follow_type, cards));
    }



    /**
     * 线程调度的时候会触发run方法
     */
    private class UserCardHandler implements Runnable {
        private final UserCard[] cards;
        private final int follow_type;
        UserCardHandler(int follow_type, UserCard[] cards) {
            this.cards = cards;
            this.follow_type = follow_type;
        }

        @Override
        public void run() {
            // 单被线程调度的时候触发
            List<User> users = new ArrayList<>();
            for (UserCard card : cards) {
                // 进行过滤操作
                if (card == null || TextUtils.isEmpty(card.getId()))
                    continue;
                switch (follow_type) {
                    case USER_MODIFY:
                        //更新用户信息
                    case USER_QUERY:
                        //查询也用card.build() : 拿到服务器端最新的数据
                    case USER_FOLLOW:
                        // 添加操作
                    case USRE_UNFOLLOW:
                        //解关注也拿到Server端最新的数据
                        users.add(card.build());
                        break;
                    default:
                        break;
                }
            }


            switch (follow_type) {
                case USER_MODIFY:
                case USER_QUERY:
                case USER_FOLLOW:
                    // 新建数据记录
                    DbHelper.save(User.class, users.toArray(new User[0]));
                    break;
                case USRE_UNFOLLOW:
                    //更新数据记录, isFollow = false, 对方User记录还在"我"本地
                    DbHelper.update(User.class, users.toArray(new User[0]));
                    break;
                default:
                    break;
            }

        }

    }



}
