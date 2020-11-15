package net.qiujuer.italker.factory.model.db;

import android.text.TextUtils;

import com.raizlabs.android.dbflow.annotation.Column;
import com.raizlabs.android.dbflow.annotation.ForeignKey;
import com.raizlabs.android.dbflow.annotation.PrimaryKey;
import com.raizlabs.android.dbflow.annotation.Table;
import com.raizlabs.android.dbflow.config.DatabaseDefinition;
import com.raizlabs.android.dbflow.config.FlowManager;
import com.raizlabs.android.dbflow.structure.ModelAdapter;
import com.raizlabs.android.dbflow.structure.database.DatabaseWrapper;
import com.raizlabs.android.dbflow.structure.database.transaction.ITransaction;

import net.qiujuer.italker.common.Common;
import net.qiujuer.italker.factory.data.helper.DbHelper;
import net.qiujuer.italker.factory.data.helper.GroupHelper;
import net.qiujuer.italker.factory.data.helper.MessageHelper;
import net.qiujuer.italker.factory.data.helper.NotifyHelper;
import net.qiujuer.italker.factory.data.helper.UserHelper;
import net.qiujuer.italker.factory.model.api.PushModel;
import net.qiujuer.italker.factory.model.api.SysNotify.NonStateModel;
import net.qiujuer.italker.factory.persistence.Account;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


/**
 * 本地的会话表
 *
 * @author qiujuer Email:qiujuer@live.cn
 * @version 1.0.0
 */
@Table(database = AppDatabase.class)
public class Session extends BaseDbModel<Session> {
    @PrimaryKey
    private String id;          // Id, 是Message /SysNotify中的接收者User的Id或者接收群的Id, 或: SysNotify的id, 代表显示在界面的无状态通知
    @Column
    private String picture;                                     // 图片，接收者用户的头像，或者群的图片
    @Column
    private String title;                                           // 标题，用户的名称，或者群的名称
    @Column
    private String content;                                         // 显示在界面上的简单内容，是Message的一个描述
    @Column
    private int receiverType = Common.RECEIVER_TYPE_NONE;            // 类型，"对方"是一个人，或者一个群 或者无状态推送
    @Column
    private int unReadCount = 0;            // 未读数量，当没有在当前界面时，应当增加未读数量
    @Column
    private Date modifyAt = null;                  // 最后更改时间, 对应消息或通知的时间

    @ForeignKey(tableClass = Message.class)
    private Message message;                    // 对应的消息，外键为Message的Id

    @ForeignKey(tableClass = SysNotify.class)
    private SysNotify notify;                   // 对应的消息，外键为SysNotify的Id

    @Column
    private boolean  needUpdateUnReadCount = true;             //是否需要更新unReadCount(++)?

    //按照数据库事务来讲, increUnread没问题, DbHelper.SessionTransaction事务总是先于updateSessionOuter事务
    public void increUnread(){
        unReadCount++;
    }

    public Session() {
    }

    public Session(Identify identify) {
        this.id = identify.id;
        this.receiverType = identify.type;
    }


    public Session(Message message) {
        if (message.getGroup() == null) {
            receiverType = Common.RECEIVER_TYPE_NONE;
            User other = message.getOther();
            id = other.getId();
            picture = other.getPortrait();
            title = other.getName();
        } else {
            receiverType = Common.RECEIVER_TYPE_GROUP;
            id = message.getGroup().getId();
            picture = message.getGroup().getPicture();
            title = message.getGroup().getName();
        }
        this.message = message;
        this.content = message.getSampleContent();
        this.modifyAt = message.getCreateAt();
    }


    public boolean isNeedUpdateUnReadCount() {
        return needUpdateUnReadCount;
    }

    public void setNeedUpdateUnReadCount(boolean needUpdateUnReadCount) {
        this.needUpdateUnReadCount = needUpdateUnReadCount;
    }

    public SysNotify getNotify() {
        return notify;
    }

    public void setNotify(SysNotify notify) {
        this.notify = notify;
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getReceiverType() {
        return receiverType;
    }

    public void setReceiverType(int receiverType) {
        this.receiverType = receiverType;
    }

    public String getPicture() {
        return picture;
    }

    public void setPicture(String picture) {
        this.picture = picture;
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    public int getUnReadCount() {
        return unReadCount;
    }

    public void setUnReadCount(int unReadCount) {
        this.unReadCount = unReadCount;
    }

    public Date getModifyAt() {
        return modifyAt;
    }

    public void setModifyAt(Date modifyAt) {
        this.modifyAt = modifyAt;
    }


    //从BaseDbRepository.dataList中移除的时候用到 equals
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Session session = (Session) o;

        return receiverType == session.receiverType
                && Objects.equals(id, session.id)
                && Objects.equals(picture, session.picture)
                && Objects.equals(title, session.title);
    }



    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + receiverType;
        return result;
    }

    @Override
    public boolean isSame(Session oldT) {
        return Objects.equals(id, oldT.id)
                && Objects.equals(receiverType, oldT.receiverType);
//                && (isNonstate && oldT.isNonstate);
    }

    @Override
    public boolean isUiContentSame(Session oldT) {
        return this.content.equals(oldT.content)
                && Objects.equals(this.modifyAt, oldT.modifyAt)
                && this.unReadCount == oldT.unReadCount;
    }


    /**
     * 对于一条消息，我们提取主要部分，用于和Session进行对应
     *
     * @param pushed 消息Model, Message or SysNotify
     * @return 返回一个Session.Identify
     */
    public static Identify createSessionIdentify(GetPushed pushed) {

        if(pushed == null) return null;

        Identify identify = new Identify();
        if (pushed.getGroup() == null
                && pushed.getSender() != null
                && pushed.getReceiver() != null) {

            //"我"发给人的消息 或者 人发给"我"的消息, 或者 单聊发给 "我"的通知
            identify.type = Common.RECEIVER_TYPE_NONE;
            User other = pushed instanceof Message?
                    ((Message) pushed).getOther(): pushed.getSender();

            identify.id = other.getId();
        } else if( pushed.getGroup() != null ) {                                         //sender != null
            //"我"发给群的消息, 群发给"我"的消息(别人发到群的消息), 或者 群聊发给"我"的系统通知
            identify.type = Common.RECEIVER_TYPE_GROUP;
            identify.id = pushed.getGroup().getId();

        } else if (pushed.getGroup() == null && pushed.getSender() == null){               //receiver !=null

            if(pushed.getReceiver() == null) return identify;        //错误情形
            // 无状态系统推送
            identify.type = Common.NON_STATE_PUSH;
            // 服务端推送过来的id, 新建一个Session, 并且Session.id 就是SysNotify.id(Server生成)
            identify.id = pushed.getId();

        }

        //todo 其他情况identify没初始化, identify.id == null, 若另外有需要identify初始化的情况则以后拓展
        return identify;
    }




    //IdPusheds是按照时间排序的, 某些很特殊情况下可能有错误, 但是不管了
    public void refreshToNow(final Class<? extends GetPushed> tClass, List<GetPushed> IdPusheds) {

            if (tClass.equals(Message.class)) {
                for (int i = 0; i < IdPusheds.size(); i++) {
                    GetPushed pushed = IdPusheds.get(i);
                    if (pushed == null) continue;

                    Message newMes = (Message) pushed;
                    if (newMes.isRepushed()) continue;

                    if (message != null) {
                        if (message.isSame(newMes)) {
                            continue;
                        } else if (message.getCreateAt().compareTo(newMes.getCreateAt()) <= 0) {
                            if(needUpdateUnReadCount) unReadCount++;
                        } else {
                            continue;
                        }
                    } else if (notify != null) {
                        if (notify.getCreateAt().compareTo(newMes.getCreateAt()) <= 0) {
                           if(needUpdateUnReadCount) unReadCount++;
                        }  else {
                            continue;
                        }
                    }
                    else {
                        if(needUpdateUnReadCount) unReadCount++;
                    }

                    notify = null;
                    message = newMes;
                }

                SessionSetting(message != null ? message: notify );

            } else if (tClass.equals(SysNotify.class)) {

                for (int i = 0; i < IdPusheds.size(); i++) {
                    GetPushed pushed = IdPusheds.get(i);
                    if (pushed == null) continue;

                    SysNotify newSys = (SysNotify) pushed;
                    if (message != null) {
                        if (message.getCreateAt().compareTo(newSys.getCreateAt()) <= 0) {
                            if(needUpdateUnReadCount) unReadCount++;
                        } else {
                            continue;
                        }
                    } else if (notify != null) {
                        if (notify.isSame(newSys)) {
                            continue;
                        } else if (notify.getCreateAt().compareTo(newSys.getCreateAt()) <= 0) {
                            if(needUpdateUnReadCount) unReadCount++;
                        } else {
                            continue;
                        }
                    } else {
                        if(needUpdateUnReadCount) unReadCount++;
                    }

                    message = null;
                    notify = newSys;
                }

                SessionSetting(message != null ? message: notify );
            }



//        if (Message.class.equals(tClass)) {
//            //Session拿到最后一条单聊消息 or 群聊消息
//            Message message1;
//            if (receiverType == Common.RECEIVER_TYPE_GROUP) {
//                message1 = MessageHelper.findLastWithGroup(id);
//            } else {
//                message1 = MessageHelper.findLastWithUser(id);
//            }
//            SessionSetting(message1);
//        }
//        else if (SysNotify.class.equals(tClass)) {
//            //Session刷新系统通知
//            SysNotify notify1 = null;
//            if (receiverType == Common.RECEIVER_TYPE_GROUP) {
//                notify1 = NotifyHelper.findLastWithGroup(id);            //Session.id是群id, 人id, 或者是一个自己创建的
//            } else if ( receiverType == Common.RECEIVER_TYPE_NONE ) {
//                notify1 = NotifyHelper.findLastWithUser(id);
//
//            } else if(receiverType == Common.NON_STATE_PUSH) {
//                notify1 = NotifyHelper.findLastFromLocal(id);         //id对应的最后一条无状态推送从数据查出
//            }
//
//            SessionSetting(notify1);
//        }


    }




    //更新Session属性: picture, title, message, notify, content, modifyAt
    //计算unReadCount增量, 有些很特殊的情况导致SessionUnreadIncre计算错误就不考虑了
    private void SessionSetting(GetPushed pushed){

        if (receiverType == Common.RECEIVER_TYPE_GROUP) {
            // 本地有最后一条聊天记录
            if (TextUtils.isEmpty(picture)
                    || TextUtils.isEmpty(this.title)) {
                    //新建一个Session
                    Group group = pushed.getGroup();
                    group.load();               //message对group的懒加载
                    this.picture = group.getPicture();
                    this.title = group.getName();
            }

//            if(pushed instanceof Message){
//                message = (Message) pushed;
//                notify = null;
//
//            }else{
//                notify = (SysNotify) pushed;
//                message = null;
//            }

            this.content = pushed.getSampleContent();
            this.modifyAt = pushed.getCreateAt();         //this.modifyAt的赋值不要在this.message / this.notify的赋值之前

        } else if(receiverType == Common.RECEIVER_TYPE_NONE){

            if (TextUtils.isEmpty(picture)
                    || TextUtils.isEmpty(this.title)) {
                    // 查询人
                User other = pushed instanceof Message ? ((Message) pushed).getOther()
                        : pushed.getReceiver() ;

                other.load();                               // Message表关联User表, 懒加载, 重新加载一次
                this.picture = other.getPortrait();         //对方头像
                this.title = other.getName();               //对方名字
            }

//            if(pushed instanceof Message){
//                message = (Message) pushed;
//                notify = null;
//            }else{
//                notify = (SysNotify) pushed;
//                message = null;
//            }

            this.content = pushed.getSampleContent();
            this.modifyAt = pushed.getCreateAt();

        }

        else if(receiverType == Common.NON_STATE_PUSH){

            if (pushed instanceof SysNotify) {

                SysNotify notify = this.notify = (SysNotify) pushed;
                this.message = null;
                this.content = pushed.getSampleContent();
                this.modifyAt = pushed.getCreateAt();

                User user = pushed.getSender();            //和推送有关的"对方" 人
                Group group = pushed.getGroup();            //和推送 有关的"对方" 群

                if ( !(user == null && group == null) ) return;

                NonStateModel model = notify.getNonStateModel();
                String userObjId = model.getUserId();
                user = UserHelper.search(userObjId);
                String groupObjId = model.getGroupId();
                //限制在本地群
                group = GroupHelper.findFromLocal(groupObjId);
                if (user == null && group == null) return;          //不能都为空


                switch (notify.getPushType()) {
                    case PushModel.ENTITY_TYPE_APPLY_JOIN_GROUP:
                        if(group.getJoinAt() != null) {         //"我"在群里
                            if (user != null) {
                                this.picture = user.getPortrait();
                                this.title = user.getName();                            //user.getName();
                            } else {
                                this.picture = group.getPicture();
                                this.title = group.getName();                            // group.getName();
                            }
                        }
                        break;
                    default:
                        break;
                }

            }
            //todo 后续也许有pushed 不是SysNotify || Message的情况
        }
    }




    public interface SessionUpdate {
        void update(Session session);
    }


    //DbHelper外部更改Session, 利用数据库事务
    public static void updateSessionOuter(final Session session,  SessionUpdate... queries){

        DatabaseDefinition definition = FlowManager.getDatabase(AppDatabase.class);
        definition.beginTransactionAsync(new ITransaction() {
            @Override
            public void execute(DatabaseWrapper databaseWrapper) {
                for(SessionUpdate query : queries){
                    query.update(session); }
                ModelAdapter<Session> adapter = FlowManager.getModelAdapter(Session.class);
                adapter.update(session);
                DbHelper.getInstance().notifySave(Session.class, session);
            }
        }).build().execute();

    }




    /**
     * 对于会话信息，最重要的部分进行提取
     * 其中我们主要关注两个点：
     * 一个会话最重要的是标示是和人聊天还是在群聊天；所以对于这点：Id存储的是人或者群的Id
     * 紧跟着Type：存储的是具体的类型（人、群）
     * equals 和 hashCode 也是对两个字段进行判断
     */
    public static class Identify {
        public String id;
        public int type;


        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Identify identify = (Identify) o;

            return type == identify.type &&
                    (id != null ? id.equals(identify.id) : identify.id == null);

        }

        @Override
        public int hashCode() {
            int result = id != null ? id.hashCode() : 0;
            result = 31 * result + type;
            return result;
        }
    }
}