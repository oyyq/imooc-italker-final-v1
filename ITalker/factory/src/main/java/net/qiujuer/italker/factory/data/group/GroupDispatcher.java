package net.qiujuer.italker.factory.data.group;

import android.annotation.SuppressLint;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.text.TextUtils;

import net.qiujuer.italker.common.Common;
import net.qiujuer.italker.factory.data.helper.DbHelper;
import net.qiujuer.italker.factory.data.helper.GroupHelper;
import net.qiujuer.italker.factory.data.helper.NotifyHelper;
import net.qiujuer.italker.factory.data.helper.UserHelper;
import net.qiujuer.italker.factory.model.api.PushModel;
import net.qiujuer.italker.factory.model.api.SysNotify.NotifyCreateModel;
import net.qiujuer.italker.factory.model.card.GroupCard;
import net.qiujuer.italker.factory.model.card.GroupMemberCard;
import net.qiujuer.italker.factory.model.db.Group;
import net.qiujuer.italker.factory.model.db.GroupMember;
import net.qiujuer.italker.factory.model.db.User;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;


/**
 * 群／群成员卡片中心的实现类
 * @author qiujuer Email:qiujuer@live.cn
 * @version 1.0.0
 */
public class GroupDispatcher implements GroupCenter {
    private static GroupCenter instance;
    private Executor executor = Executors.newSingleThreadExecutor();

    public static GroupCenter instance() {
        if (instance == null) {
            synchronized (GroupDispatcher.class) {
                if (instance == null)
                    instance = new GroupDispatcher();
            }
        }
        return instance;
    }


    //我的群列表+1/-1
    @Override
    public void dispatch(int pushType, GroupCard... cards) {
        if (cards == null || cards.length == 0)
            return;
        executor.execute(new GroupHandler(cards, pushType));
    }


    //我接到其他人添加/删除群员的通知
    @Override
    public void dispatch(List<GroupMemberCard> cards, int pushType) {
        if (cards == null || cards.size() == 0)
            return;
        executor.execute(new MemberRspHandler(cards, pushType));
    }


    //收到群员被添加 / 被移除, 退群的通知
    private class MemberRspHandler implements Runnable {
        private final List<GroupMemberCard> cards;
        private final int pushType;

        MemberRspHandler(List<GroupMemberCard> cards, int pushType) {
            this.cards = cards;
            this.pushType = pushType;
        }

        @RequiresApi(api = Build.VERSION_CODES.N)
        @Override
        public void run() {
            //过滤掉非法卡片
            final GroupMember[] filteredMembers
                    =  cards.stream()
                    .map(memberCard -> {
                        User user =  UserHelper.search(memberCard.getUserId());
                        // 本地群, "我"加入时间不为空 : "我"目前在群里
                        Group group = GroupHelper.findFromLocal(memberCard.getGroupId());
                        if( user != null && group != null && group.getJoinAt() != null) {
                            //本地不存在的GroupMember,新建, 否则查出
                            GroupMember member ;
                            switch (pushType){
                                case PushModel.ENTITY_TYPE_DEL_GROUP_MEMBERS:
                                case PushModel.ENTITY_TYPE_EXIT_GROUP_MEMBERS:
                                    //本地必须有历史记录
                                    member = GroupHelper.findMemberFromLocal(user.getId(), group.getId());
                                    break;
                                default:
                                    //拿到服务器端最新数据: 增改查
                                    member = memberCard.build(group, user);
                                    break;

                            }
                            return  member;
                        }
                        return null; })
                    .filter(member -> member != null)
                    .toArray(GroupMember[]::new);

            if (filteredMembers.length > 0) {
                switch (pushType) {
                    case PushModel.ENTITY_TYPE_ADD_GROUP_MEMBERS:
                        DbHelper.save(GroupMember.class, filteredMembers);
                        DbHelper.updateGroup(true,filteredMembers);
                        break;
                    case PushModel.ENTITY_TYPE_DEL_GROUP_MEMBERS:
                    case PushModel.ENTITY_TYPE_EXIT_GROUP_MEMBERS:
                        DbHelper.delete(GroupMember.class, filteredMembers);
                        DbHelper.updateGroup(true, filteredMembers);
                        break;
                    //修改群员权限 Dbflow update -> 改,
                    // 而且如果"我" 被添加为新Admin, 若"我"当时在群聊中, 也需要先退出再进入才能看到权限
                    case PushModel.ENTITY_TYPE_MODIFY_GROUP_MEMBERS_PERMISSION:
                        for(GroupMember member: filteredMembers) {
                            member.setAdmin(true);
                            member.setModifyAt(new Date());
                        }
                        DbHelper.update(GroupMember.class, filteredMembers);
                        DbHelper.updateGroup(true, filteredMembers);
                        break;
                    default:
                        //Non_PushType: 拉取群员, -> 查
                        DbHelper.save(GroupMember.class, filteredMembers);
                        //只update group.holder
                        DbHelper.updateGroup(false, filteredMembers);
                        break;
                }
            }
        }
    }





    /**
     * 把群Card处理为群DB类
     */
    private class GroupHandler implements Runnable {
        private final List<GroupCard> cards;
        private int PushType = Common.NON_PushType;

        GroupHandler(GroupCard[] cards, int pushType) {
            this.cards = Arrays.asList(cards);
            this.PushType = pushType;
        }

        @SuppressLint("NewApi")
        @Override
        public void run() {
            List<Group> groups = new ArrayList<>();
            //过滤掉非法卡片
            List<Group> finalGroups = groups;
            List<GroupCard> newCards = cards.stream().filter(card -> {
                //owner未必在本地有存储, 所以用search
                User owner = UserHelper.search(card.getOwnerId());
                if (owner != null) {
                    //拿到群的Server端最新数据
                    finalGroups.add(card.build(owner));
                    return true;
                }
                return false;
            }).collect(Collectors.toList());


            // 过滤掉extra = null 那些是不需要系统推送的, groups.size == newCards.size
            if (groups.size() > 0) {
                //查/改情况extra == null
                if( newCards.size() > 0 ) {
                    GroupCard[] extraCards = newCards.stream()
                            .filter(groupCard -> !TextUtils.isEmpty(groupCard.getEXTRA()))
                            .toArray(GroupCard[]::new);

                    if(extraCards.length > 0) {
                        //过滤来有额外信息 GroupCard.extra, GroupCard.pushType 可以 != this.PushType
                        List<NotifyCreateModel> notifyModels
                                = NotifyHelper.createNotifyModel(Group.class, extraCards[0].getPushType(), extraCards);

                        NotifyHelper.pushNotify(notifyModels);
                    }
                }


                //"我"所在的Group的增删改查
                switch (this.PushType ){
                    //群列表多1
                    case PushModel.ENTITY_TYPE_ADD_GROUP:
                        DbHelper.save(Group.class, groups.toArray(new Group[0]));
                        break;
                    //少1 -->  改成不删群记录, 将joinAt 置null
                    case PushModel.ENTITY_TYPE_OUT_GROUP:
                        groups = groups.stream().map(group -> { group.setJoinAt(null); return group; })
                                 .collect(Collectors.toList());
                        DbHelper.save(Group.class, groups.toArray(new Group[0]));
                        break;
                    default:
                        //查/改: GroupPresenter刷新群列表, 如群名, 群头像的更改,
                        // 目前都归为: PushType == Common.Non_PushType, 以后也许展开
                        DbHelper.save(Group.class, groups.toArray(new Group[0]));
                        break;
                }


            }

        }
    }

}
