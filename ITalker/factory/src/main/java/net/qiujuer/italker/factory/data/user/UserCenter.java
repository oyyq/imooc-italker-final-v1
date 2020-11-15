package net.qiujuer.italker.factory.data.user;

import net.qiujuer.italker.factory.model.card.UserCard;
import net.qiujuer.italker.factory.model.db.User;

/**
 * 用户中心的基本定义
 *
 * @author qiujuer Email:qiujuer@live.cn
 * @version 1.0.0
 */
public interface UserCenter {
    // 分发处理一堆用户卡片的信息，并更新到数据库
    void dispatch(int follow_type, UserCard... cards);

    //void unfollow(User... user);

    int USER_FOLLOW = 1;            //关注用户 -> 增
    int USRE_UNFOLLOW = 2;          //解关注用户 -> 改
    int USER_QUERY = 3;             //查询用户 -> 查
    int USER_MODIFY = 4;           //更给本地数据库字段 -> 改


}
