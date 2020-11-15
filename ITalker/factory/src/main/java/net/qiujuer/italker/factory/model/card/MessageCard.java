package net.qiujuer.italker.factory.model.card;

import net.qiujuer.italker.factory.model.db.Group;
import net.qiujuer.italker.factory.model.db.Message;
import net.qiujuer.italker.factory.model.db.User;

import java.util.Date;


/**
 * 消息的卡片，用于接收服务器返回信息
 *
 * @author qiujuer Email:qiujuer@live.cn
 * @version 1.0.0
 */
public class MessageCard extends Card<Message> {
    private String id;
    private String content;
    private String attach;                  //attach: eg: 音频时长
    private Date createAt;
    private int type;
    private String groupId;
    private String senderId;
    private String receiverId;

    // 3个额外的本地字段, 只在客户端创建消息时需要
    // transient 不会被Gson序列化和反序列化
    private transient int status = Message.STATUS_DONE;     //当前消息状态
    private transient boolean uploaded = false;             // 上传是否完成（对应的是文件）
    private transient boolean isRepushed = false;


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

    public String getAttach() {
        return attach;
    }

    public void setAttach(String attach) {
        this.attach = attach;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
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

    public String getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(String receiverId) {
        this.receiverId = receiverId;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }


    public boolean isRepushed() {
        return isRepushed;
    }

    public void setRepushed(boolean repushed) {
        isRepushed = repushed;
    }

    public Date getCreateAt() {
        return createAt;
    }

    public void setCreateAt(Date createAt) {
        this.createAt = createAt;
    }

    /**
     * 要构建一个消息，必须准备好3个外键对应的Model
     *
     * @param sender   发送者
     * @param receiver 接收者
     * @param group    接收者-群
     * @return 一个消息
     */
    public Message build(User sender, User receiver, Group group) {
        Message message = new Message();
        message.setId(id);
        message.setContent(content);
        message.setAttach(attach);
        message.setType(type);
        //接收到Server推送来的消息
        message.setCreateAt(createAt);
        message.setGroup(group);
        message.setSender(sender);
        message.setReceiver(receiver);
        //按照MessageCard.STATUS设置message.STATUS
        message.setStatus(status);
        message.setRepushed(isRepushed);
        return message;
    }
}
