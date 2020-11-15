package net.qiujuer.italker.factory.model.db;


import com.raizlabs.android.dbflow.annotation.Column;
import com.raizlabs.android.dbflow.annotation.ForeignKey;
import com.raizlabs.android.dbflow.annotation.PrimaryKey;
import com.raizlabs.android.dbflow.annotation.Table;
import net.qiujuer.italker.factory.data.helper.UserHelper;
import net.qiujuer.italker.factory.model.api.SysNotify.NonStateModel;
import net.qiujuer.italker.factory.model.db.typeConverter.NSModelConverter;
import net.qiujuer.italker.factory.persistence.Account;

import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Date;
import java.util.Objects;


/**
 * 系统通知, 接受者一定是"我"
 */
@Table(database = AppDatabase.class)
public class SysNotify
        extends GetPushedImpl
        implements Serializable {

    @PrimaryKey
    private String id;              //主键, 独立

    @Column
    private String content;          //通知内容

    @Column
    private int PushType;           // 推送类型, PushModel.ENTITY****

    @Column
    private Date createAt;          // 创建时间

    // 当sender和group同时为null时, 是无状态通知, 显示在ActiveFragment, 而非ChatFragment
    @ForeignKey(tableClass = User.class, stubbedRelationship = true)
    private User sender;          // 和sender聊天, 推送给"我"的通知

    @ForeignKey(tableClass = Group.class, stubbedRelationship = true)
    private Group group;          // 群聊天, 推送给"我"通知,

    //列具体的类型转换器, 但是避开私有属性, dbflow
    @Column(typeConverter = NSModelConverter.class)
    private NonStateModel nonStateModel;


    public SysNotify(){
    }

    public NonStateModel getNonStateModel() {
        return nonStateModel;
    }

    public void setNonStateModel(NonStateModel nonStateModel) {
        this.nonStateModel = nonStateModel;
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

    public Date getCreateAt() {
        return createAt;
    }

    public void setCreateAt(Date createAt) {
        this.createAt = createAt;
    }

    public User getSender() {
        return sender;
    }

    @Override
    public User getReceiver() {
        return UserHelper.findFromLocal(Account.getUserId());    //返回就是"我"自己
    }

    public void setPushType(int pushType) {
        PushType = pushType;
    }

    public void setSender(User sender) {
        this.sender = sender;
    }

    public Group getGroup() {
        return group;
    }


    @Override
    public String getSampleContent() {
        return getContent();
    }

    @Override
    public int getPushType() {
        return PushType;
    }

    public void setGroup(Group group) {
        this.group = group;
    }



    @Override
    public boolean isSame(GetPushedImpl old) {
        return super.isSame(old) && Objects.equals(id, old.getId()) ;
    }

    @Override
    public boolean isUiContentSame(GetPushedImpl old) {

        return Objects.equals(content, old.getContent())
                && PushType == old.getPushType()
                && Objects.equals(sender == null? null: sender.getId(), old.getSender() ==null ? null: old.getSender().getId())
                && Objects.equals(group == null? null:group.getId(), old.getGroup()== null? null:old.getGroup().getId())
                //&& Objects.equals(old.getCreateAt(), createAt)
                && Objects.equals(((SysNotify)old).getNonStateModel(), nonStateModel);
    }


}
