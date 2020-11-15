package net.qiujuer.italker.factory.model.api.SysNotify;


import java.util.Objects;

/**
 * 专门用来处理无状态推送的额外的necessary信息
 * 如userId的User申请加入groupId的群
 */
public class NonStateModel {

   private String userId;          //相关用户

   private String groupId;         //相关群


    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    //todo 拓展其他属性


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NonStateModel model = (NonStateModel) o;
        return Objects.equals(userId, model.userId) &&
                Objects.equals(groupId, model.groupId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, groupId);
    }
}
