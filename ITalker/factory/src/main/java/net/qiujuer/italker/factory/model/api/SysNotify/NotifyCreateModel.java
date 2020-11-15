package net.qiujuer.italker.factory.model.api.SysNotify;

import net.qiujuer.italker.common.Common;
import net.qiujuer.italker.factory.model.card.SysNotifyCard;
import net.qiujuer.italker.factory.model.db.SysNotify;
import net.qiujuer.italker.factory.persistence.Account;

import java.util.Date;
import java.util.UUID;


public class NotifyCreateModel {

    // ID从客户端生产，一个UUID, --> 一个SysNotify的Id
    private String id;
    private String content = null;
    // 从和谁的单聊过来的SysNotify ?
    private String senderId = null;
    // 从哪个群聊过来的SysNotify ?
    private String groupId = null;

    private int PushType = Common.NON_PushType;

    private NotifyCreateModel() {
        // 随机生产一个UUID, 赋给SysNotify
        this.id = UUID.randomUUID().toString();
    }

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

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public int getPushType() {
        return PushType;
    }

    public void setPushType(int pushType) {
        PushType = pushType;
    }

    public SysNotifyCard getCard() {
        return card;
    }

    public void setCard(SysNotifyCard card) {
        this.card = card;
    }

    private SysNotifyCard card;

    // 返回一个Card
    public SysNotifyCard buildCard() {

        if (card == null) {
            SysNotifyCard card = new SysNotifyCard();
            card.setId(id);                              //SysNotify在表中记录的Id
            card.setContent(content);
            card.setPushType(PushType);
            card.setCreateAt(new Date());               //自己在客户端产生推送给自己的SysNotifyCard的时间
            card.setGroupId(groupId);
            card.setSenderId(senderId);
            //收推者: 自己
            card.setReceiverId(Account.getUserId());
            this.card = card;
        }
        return this.card;
    }



    public static class Builder {
        private NotifyCreateModel model;

        public Builder() {
            this.model = new NotifyCreateModel();
        }
        // 设置接收者
        public NotifyCreateModel.Builder sender(String senderId) {
            this.model.senderId = senderId;
            return this;
        }
        // 设置接收者
        public NotifyCreateModel.Builder groupReceiver(String groupId) {
            this.model.groupId = groupId;
            return this;
        }
        // 设置内容
        public NotifyCreateModel.Builder content(String content) {
            this.model.content = content;
            return this;
        }
        //设置推送类型
        public NotifyCreateModel.Builder PushType(int PushType) {
            this.model.PushType = PushType;
            return this;
        }

        public NotifyCreateModel build() {
            return this.model;
        }

    }



    public static NotifyCreateModel buildWithNotify(SysNotify notify) {
        NotifyCreateModel model = new NotifyCreateModel();
        model.id = notify.getId();
        model.content = notify.getContent();
        model.PushType = notify.getPushType();

        /**
         * 推给人还是推给群, getReceiver or getGroup至少一个 null
         */
        if (notify.getSender() != null) {
            model.senderId = notify.getSender().getId();
        } else {
            model.groupId = notify.getGroup().getId();
        }

        return model;
    }




}
