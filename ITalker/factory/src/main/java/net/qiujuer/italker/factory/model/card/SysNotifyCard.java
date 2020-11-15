package net.qiujuer.italker.factory.model.card;

import net.qiujuer.italker.common.Common;
import net.qiujuer.italker.factory.Factory;
import net.qiujuer.italker.factory.model.api.SysNotify.NonStateModel;
import net.qiujuer.italker.factory.model.db.Group;
import net.qiujuer.italker.factory.model.db.SysNotify;
import net.qiujuer.italker.factory.model.db.User;

import java.util.Date;

public class SysNotifyCard extends Card<SysNotify> {

    private String id;
    private String content;
    private Date createAt;
    private String groupId;
    private String senderId;
    //SysNotifyCard  需要  receiverId字段
    private String receiverId;
    private int PushType = Common.NON_PushType;
    private String nonStateModel;       //Json解析成NonStateModel对象, 作为字段存储

    //不被Gson解析的对象, 不序列化, 本地, 不传输
    private transient SysNotify notify = null;

    public String getNonStateModel() {
        return nonStateModel;
    }

    public void setNonStateModel(String nonStateModel) {
        this.nonStateModel = nonStateModel;
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Date getCreateAt() {
        return createAt;
    }

    public void setCreateAt(Date createAt) {
        this.createAt = createAt;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getSenderId() {
        return senderId;
    }
    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    @Override
    public int getPushType() {
        return PushType;
    }

    public void setPushType(int pushType) {
        PushType = pushType;
    }

    public String getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(String receiverId) {
        this.receiverId = receiverId;
    }


    public SysNotify build(User sender, Group group) {
        if(notify == null) {
            SysNotify notify = new SysNotify();
            notify.setId(id);
            notify.setContent(content);
            notify.setCreateAt(createAt);
            notify.setPushType(PushType);
            //notify  不需要  receiver字段, receiver就是"我"
            notify.setSender(sender);
            notify.setGroup(group);
            if(nonStateModel != null)
                notify.setNonStateModel(Factory.getGson().fromJson(nonStateModel, NonStateModel.class));
            this.notify = notify;
        }
        return notify;
    }
}
