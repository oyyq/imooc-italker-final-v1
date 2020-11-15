package net.qiujuer.italker.factory.data.helper;

import android.os.Build;
import android.support.annotation.RequiresApi;
import com.raizlabs.android.dbflow.sql.language.OperatorGroup;
import com.raizlabs.android.dbflow.sql.language.SQLite;
import com.raizlabs.android.dbflow.structure.BaseModel;

import net.qiujuer.genius.kit.handler.Run;
import net.qiujuer.italker.common.Common;
import net.qiujuer.italker.factory.Factory;
import net.qiujuer.italker.factory.model.api.PushModel;
import net.qiujuer.italker.factory.model.api.SysNotify.NonStateModel;
import net.qiujuer.italker.factory.model.api.SysNotify.NotifyCreateModel;
import net.qiujuer.italker.factory.model.card.ApplyCard;
import net.qiujuer.italker.factory.model.card.Card;
import net.qiujuer.italker.factory.model.card.GroupMemberCard;
import net.qiujuer.italker.factory.model.card.SysNotifyCard;
import net.qiujuer.italker.factory.model.db.BaseDbModel;
import net.qiujuer.italker.factory.model.db.Group;
import net.qiujuer.italker.factory.model.db.GroupMember;
import net.qiujuer.italker.factory.model.db.Message;
import net.qiujuer.italker.factory.model.db.Message_Table;
import net.qiujuer.italker.factory.model.db.SysNotify;
import net.qiujuer.italker.factory.model.db.SysNotify_Table;
import net.qiujuer.italker.factory.model.db.User;
import net.qiujuer.italker.factory.persistence.Account;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;



public class NotifyHelper {

    public static SysNotify findLastWithGroup(String groupId) {
        return SQLite.select()
                .from(SysNotify.class)
                .where(SysNotify_Table.group_id.eq(groupId))
                .orderBy(SysNotify_Table.createAt, false)       //找出最近一条
                .querySingle();
    }


    public static SysNotify findLastWithUser(String senderId){
        return SQLite.select()
                .from(SysNotify.class)
                .where(SysNotify_Table.group_id.isNull())
                .and(SysNotify_Table.sender_id.eq(senderId))
                .orderBy(SysNotify_Table.createAt, false)
                .querySingle();
    }



    //从本地查找NON_STATE_PUSH SysNotify
    public static SysNotify findLastFromLocal(String id){
        return SQLite.select()
                .from(SysNotify.class)
                .where(SysNotify_Table.id.eq(id))
                .andAll(SysNotify_Table.sender_id.isNull(), SysNotify_Table.group_id.isNull())
                .orderBy(SysNotify_Table.createAt, false)          // 时间降序, 反向排列最近一条
                .querySingle();

    }



    public static SysNotify findFromLocal(String id) {

        return SQLite.select()
                .from(SysNotify.class)
                .where(SysNotify_Table.id.eq(id))
                .querySingle();

    }




    /**
     * 在自己的客户端给自己推送系统通知(不是服务器端过来的)的统一入口
     * todo 10-28 21:49 明天需要check所有需要推送SysNotify的情况, 在Server端
     * @param
     */
    public static  <Model extends BaseModel>
    List<NotifyCreateModel> createNotifyModel(final Class<Model> tClass, int pushType, final Card<Model>... cards){   //SysInfo: 系统消息
        int PushType = pushType;
        List<NotifyCreateModel> modelList = new ArrayList<>();

        if(BaseDbModel.class.isAssignableFrom(tClass)){
            //"我"建群给自己的推送, 后面可以拓展修改群名,  ....等等
            if( Group.class.equals(tClass) ) {
                for (Card card : cards) {
                    String extra = card.getEXTRA();

                    NotifyCreateModel notifyModel = new NotifyCreateModel.Builder()
                            //3. 推送内容
                            .content(extra)
                            //群聊推送, 1. 推送源: 群, 2. 推送类型
                            .groupReceiver( card.getGroupId() )
                            //Card是GroupCard, getId == getGroupId
                            .PushType( PushType )
                            .build();

                    modelList.add(notifyModel);

                }
            }
            //添加, 移除群成员, 修改群成员 -> 给"我"的群推送
            else if (GroupMember.class.equals(tClass)){

                StringBuilder builder = new StringBuilder();
                String groupId = ((GroupMemberCard)cards[0]).getGroupId();

                for (Card card: cards){
                    builder.append(  card != null ? ((GroupMemberCard)card).getAlias() +", " :"" );          //群员别名 == user名称
                }
                builder.delete(builder.lastIndexOf(", "), builder.length());        //去掉末尾的", "

                switch (PushType){
                    case PushModel.ENTITY_TYPE_NOTI_ADD_GROUP_MEMBERS:
                        builder.insert(0,"You append ");
                        builder.append(" to group!");
                        break;
                    case PushModel.ENTITY_TYPE_NOTI_DEL_GROUP_MEMBERS:
                        builder.insert(0, "You delete ");
                        builder.append(" from group!");
                        break;
                    case PushModel.ENTITY_TYPE_MODIFY_GROUP_MEMBERS_PERMISSION:
                        builder.insert(0, "You move ");
                        builder.append(" to new Admins!");
                        break;
                    default:                //拓展更多情形
                        break;
                }

                NotifyCreateModel notifyModel = new NotifyCreateModel.Builder()
                        .content(builder.toString())
                        .groupReceiver( groupId )
                        .PushType( PushType )
                        .build();

                modelList.add(notifyModel);

            }
            else if(User.class.equals(tClass) ){

                for (Card card : cards) {
                    String extra = card.getEXTRA();

                    NotifyCreateModel notifyModel = new NotifyCreateModel.Builder()
                            .content(extra)
                            //单聊推送 "对方", card是UserCard
                            .sender( card.getId() )
                            .PushType( card.getPushType() )
                            .build();

                    modelList.add(notifyModel);

                }
            }

        }

        return modelList;
    }


    @RequiresApi(api = Build.VERSION_CODES.N)
    public static void pushNotify(List<NotifyCreateModel> notifyModels) {
        Factory.runOnAsync(new Runnable() {
            @Override
            public void run() {

                final SysNotifyCard[] notifyCards
                        = notifyModels.stream()
                        .map(model -> model.buildCard())
                        .toArray(SysNotifyCard[]::new);

                Factory.getNotifyCenter().dispatch(notifyCards);

            }
        });
    }


    //目前只转换SysNotifyCard & ApplyCard
    public static SysNotifyCard toCard(Card card){
        if(card instanceof SysNotifyCard) return (SysNotifyCard) card;

        SysNotifyCard sysCard = new SysNotifyCard();

        if (ApplyCard.class.equals(card.getClass())) {
            ApplyCard applyCard = (ApplyCard) card;
            sysCard.setId(applyCard.getId());                   //SysNotifyCard, SysNotify, Session, applyCard四者id相同
            sysCard.setContent(applyCard.getDesc());
            sysCard.setCreateAt(applyCard.getCreateAt());
            sysCard.setReceiverId(Account.getUserId());
            sysCard.setPushType(applyCard.getPushType());       //PushModel.ENTITY_TYPE_APPLY_JOIN_GROUP

            NonStateModel model = new NonStateModel();
            model.setUserId(applyCard.getApplicantId());
            model.setGroupId(applyCard.getTargetId());
            sysCard.setNonStateModel(Factory.getGson().toJson(model));
            return sysCard;

        }

        return null;
    }






/*
    public static long findUnreadIncrement(Date earlier, Date later, int receiverType, String sessionId) {

        if(receiverType == Common.RECEIVER_TYPE_GROUP) {
            if(earlier == null) return SQLite.selectCountOf().from(SysNotify.class)
                    .where(OperatorGroup.clause( SysNotify_Table.group_id.eq(sessionId),
                            SysNotify_Table.sender_id.isNull() )
                            ).count();

            return SQLite.selectCountOf()
                    .from(SysNotify.class)
                    .where(SysNotify_Table.createAt.greaterThan(earlier).lessThanOrEq(later))
                    .and( OperatorGroup.clause( SysNotify_Table.group_id.eq(sessionId), SysNotify_Table.sender_id.isNull() ))
                    .count();

        } else if ( receiverType == Common.RECEIVER_TYPE_NONE ){
            if(earlier == null) return SQLite.selectCountOf().from(SysNotify.class)
                    .where(OperatorGroup.clause(
                            SysNotify_Table.sender_id.eq(sessionId),
                            SysNotify_Table.group_id.isNull() ))
                            .count();


            return SQLite.selectCountOf()
                    .from(SysNotify.class)
                    .where(SysNotify_Table.createAt.greaterThan(earlier).lessThanOrEq(later))
                    .and(  OperatorGroup.clause(  SysNotify_Table.sender_id.eq(sessionId), SysNotify_Table.group_id.isNull() ))
                    .count();
        }

        return -1024;
    }

    */



}
