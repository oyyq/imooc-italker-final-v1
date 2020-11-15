package net.qiujuer.italker.factory.model.api;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import net.qiujuer.italker.factory.Factory;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 推送消息的基础Model
 *
 * @author qiujuer Email:qiujuer@live.cn
 * @version 1.0.0
 */
@SuppressWarnings("WeakerAccess")
public class PushModel {

    // 退出登录
    public static final int ENTITY_TYPE_LOGOUT = -1;
    // 普通消息送达
    public static final int ENTITY_TYPE_MESSAGE = 200;
    //我被关注
    public static final int ENTITY_TYPE_ADD_FRIEND = 1001;
    //我被解关注
    public static final int ENTITY_TYPE_DEL_FRIEND = 1011;
    //我被加入*群
    public static final int ENTITY_TYPE_ADD_GROUP = 1002;
    //系统通知我被加入*群
    public static final int ENTITY_TYPE_NOTI_ADD_GROUP = 1102;
    //我被移除群
    public static final int ENTITY_TYPE_OUT_GROUP = 1012;
    //系统通知我被移除群
    public static final int ENTITY_TYPE_NOTI_OUT_GROUP = 1112;
    // 新的群成员添加: 刷新群员列表
    public static final int ENTITY_TYPE_ADD_GROUP_MEMBERS = 1003;
    // 新的群成员添加: 接到系统通知
    public static final int ENTITY_TYPE_NOTI_ADD_GROUP_MEMBERS = 1030;

    //移除群成员: 刷新群员列表
    public static final int ENTITY_TYPE_DEL_GROUP_MEMBERS = 1004;
    //移除群成员: 接到系统通知
    public static final int ENTITY_TYPE_NOTI_DEL_GROUP_MEMBERS = 1040;
    // 群成员权限修改: 接到系统通知, **成为新群管理...
    public static final int ENTITY_TYPE_MODIFY_GROUP_MEMBERS_PERMISSION = 2001;
    // 群成员退出, 我退群通知给别人
    public static final int ENTITY_TYPE_EXIT_GROUP_MEMBERS = 3001;
    public static final int ENTITY_TYPE_NOTI_EXIT_GROUP_MEMBERS = 3010;
    //别人向"我"申请加群, 或"我"申请加别人的群
    public static final int ENTITY_TYPE_APPLY_JOIN_GROUP = 4000;
    //收到的推送实体是SysNotify, 并且来源是单聊或者群聊
    public static final int ENTITY_TYPE_SYSTEM_NOTIFICATION = 10240;


    private List<Entity> entities = new ArrayList<>();

    private PushModel(List<Entity> entities) {
        this.entities = entities;
    }

    public List<Entity> getEntities() {
        return entities;
    }

    public void setEntities(List<Entity> entities) {
        this.entities = entities;
    }


    /**
     * 把一个Json字符串，转化为一个实体数组
     * 并把数组封装到PushModel中，方便后面的数据流处理
     *
     * @param json Json数据
     * @return
     */
    public static PushModel decode(String json) {
        Gson gson = Factory.getGson();
        Type type = new TypeToken<List<Entity>>() {
        }.getType();

        try {
            List<Entity> entities = gson.fromJson(json, type);
            if (entities.size() > 0)
                return new PushModel(entities);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }





    public static class Entity {
        public Entity() {
        }

        // 消息类型
        public int type;
        // 消息实体
        public String content;
        // 消息生成时间
        public Date createAt;

        @Override
        public String toString() {
            return "Entity{" +
                    "type=" + type +
                    ", content='" + content + '\'' +
                    ", createAt=" + createAt +
                    '}';
        }
    }
}
