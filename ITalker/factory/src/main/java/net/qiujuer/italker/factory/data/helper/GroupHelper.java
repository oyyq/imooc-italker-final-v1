package net.qiujuer.italker.factory.data.helper;

import android.os.Build;
import android.support.annotation.RequiresApi;
import com.raizlabs.android.dbflow.sql.language.Join;
import com.raizlabs.android.dbflow.sql.language.OperatorGroup;
import com.raizlabs.android.dbflow.sql.language.SQLite;
import net.qiujuer.italker.common.Common;
import net.qiujuer.italker.common.interfaces.ApplyCallback;
import net.qiujuer.italker.factory.Factory;
import net.qiujuer.italker.factory.R;
import net.qiujuer.italker.factory.data.DataSource;
import net.qiujuer.italker.factory.data.Pushed.PushedUserRepository;
import net.qiujuer.italker.factory.model.api.PushModel;
import net.qiujuer.italker.factory.model.api.RspModel;
import net.qiujuer.italker.factory.model.api.SysNotify.NotifyCreateModel;
import net.qiujuer.italker.factory.model.api.group.GroupCreateModel;
import net.qiujuer.italker.factory.model.api.group.GroupMemberModel;
import net.qiujuer.italker.factory.model.card.ApplyCard;
import net.qiujuer.italker.factory.model.card.GroupCard;
import net.qiujuer.italker.factory.model.card.GroupMemberCard;
import net.qiujuer.italker.factory.model.db.GetPushedImpl;
import net.qiujuer.italker.factory.model.db.Group;
import net.qiujuer.italker.factory.model.db.GroupMember;
import net.qiujuer.italker.factory.model.db.GroupMember_Table;
import net.qiujuer.italker.factory.model.db.Group_Table;
import net.qiujuer.italker.factory.model.db.User;
import net.qiujuer.italker.factory.model.db.User_Table;
import net.qiujuer.italker.factory.model.db.view.MemberUserModel;
import net.qiujuer.italker.factory.net.Network;
import net.qiujuer.italker.factory.net.RemoteService;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;



/**
 * 对群的一个简单的辅助工具类
 *
 * @author qiujuer Email:qiujuer@live.cn
 * @version 1.0.0
 */
public class GroupHelper {


    public static Group find(String groupId) {
        Group group = findFromLocal(groupId);
        if (group == null)
            group = findFormNet(groupId);
        return group;
    }




    // 从本地找Group, "我"必须是群成员
    public static Group findFromLocal(String groupId) {
        return SQLite.select()
                .from(Group.class)
                .where(Group_Table.id.eq(groupId))
                //"我"加入的时间不为空 -> "我"在这个群里
                .and(Group_Table.joinAt.isNotNull())
                .querySingle();
    }



    // 从网络找Group, "我"必须是群的成员
    public static Group findFormNet(String id) {
        RemoteService remoteService = Network.remote();
        try {
            Response<RspModel<GroupCard>> response = remoteService.groupFind(id).execute();
            GroupCard card = response.body().getResult();
            if (card != null) {
                //存储并通知
                Factory.getGroupCenter()
                        .dispatch(Common.NON_PushType, card);

                User user = UserHelper.search(card.getOwnerId());
                if (user != null) {
                    return card.build(user);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }



    // 群的创建
    public static void create(GroupCreateModel model,
                              final DataSource.Callback<GroupCard> callback) {
        RemoteService service = Network.remote();
        service.groupCreate(model)
                .enqueue(new Callback<RspModel<GroupCard>>() {
                    @Override
                    public void onResponse(Call<RspModel<GroupCard>> call,
                                           Response<RspModel<GroupCard>> response) {
                        RspModel<GroupCard> rspModel = response.body();
                        if (rspModel.success()) {
                            GroupCard groupCard = rspModel.getResult();
                            // 唤起进行保存的操作
                            Factory.getGroupCenter().dispatch(PushModel.ENTITY_TYPE_ADD_GROUP, groupCard);
                            // GroupCreatePresenter拿到groupCard
                            callback.onDataLoaded(groupCard);
                        } else {
                            Factory.decodeRspCode(rspModel, callback);
                        }
                    }

                    @Override
                    public void onFailure(Call<RspModel<GroupCard>> call, Throwable t) {
                        callback.onDataNotAvailable(R.string.data_network_error);
                    }
                });
    }



    // 搜索所有群, "我"在或者不在群里
    public static Call search(String name, final DataSource.Callback<List<GroupCard>> callback) {
        RemoteService service = Network.remote();
        Call<RspModel<List<GroupCard>>> call = service.groupSearch(name);

        call.enqueue(new Callback<RspModel<List<GroupCard>>>() {
            @Override
            public void onResponse(Call<RspModel<List<GroupCard>>> call,
                                   Response<RspModel<List<GroupCard>>> response) {
                RspModel<List<GroupCard>> rspModel = response.body();
                if (rspModel.success()) {
                    // 返回数据直接刷新到界面, 查询群无需存储
                    callback.onDataLoaded(rspModel.getResult());
                } else {
                    Factory.decodeRspCode(rspModel, callback);
                }
            }

            @Override
            public void onFailure(Call<RspModel<List<GroupCard>>> call, Throwable t) {
                callback.onDataNotAvailable(R.string.data_network_error);
            }
        });

        // 把当前的调度者返回
        return call;
    }




    // 刷新"我"的群组列表
    public static void refreshGroups() {
        RemoteService service = Network.remote();
        service.groups("").enqueue(new Callback<RspModel<List<GroupCard>>>() {
            @Override
            public void onResponse(Call<RspModel<List<GroupCard>>> call, Response<RspModel<List<GroupCard>>> response) {
                RspModel<List<GroupCard>> rspModel = response.body();
                if (rspModel.success()) {
                    final List<GroupCard> groupCards = rspModel.getResult();
                    if (groupCards != null && groupCards.size() > 0) {
                        // 进行调度显示
                        Factory.getGroupCenter()
                                .dispatch(Common.NON_PushType,
                                groupCards.toArray(new GroupCard[0]));
                    }
                } else {
                    Factory.decodeRspCode(rspModel, null);
                }
            }

            @Override
            public void onFailure(Call<RspModel<List<GroupCard>>> call, Throwable t) {
                // 不做任何事情
            }
        });
    }



    //  获取一个群的成员数量, 本地数据库的数据
    public static long getMemberCount(String id) {
        return SQLite.selectCountOf()
                .from(GroupMember.class)
                .where(GroupMember_Table.group_id.eq(id))
                .count();
    }


    //找到一个群的某个群员
    public static GroupMember getMember(String userId, String groupId){
        GroupMember member = findMemberFromLocal(userId, groupId);
        if(member == null){
            member = findMemberFromNet(userId, groupId);
        }
        return member;
    }


    public static GroupMember findMemberFromLocal(String userId, String groupId){
        return SQLite.select()
                .from(GroupMember.class)
                .where(OperatorGroup.clause()
                        .and(GroupMember_Table.user_id.eq(userId))
                        .and(GroupMember_Table.group_id.eq(groupId))
                ).querySingle();

    }



    public static GroupMember findMemberFromNet(String userId, String groupId){

        Group group = GroupHelper.findFromLocal(groupId);
        // 也许需要网络搜索
        User user = UserHelper.search(userId);
        RemoteService remoteService = Network.remote();
        try {
            Response<RspModel<GroupMemberCard>> response =
                    remoteService.groupMemberFind(groupId, userId).execute();
            RspModel<GroupMemberCard> rspModel = response.body();

            if(rspModel.success()) {
                GroupMemberCard card = rspModel.getResult();
                if (card != null) {
                    GroupMember member = card.build(group, user);
                    return member;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }


    //拿到GroupCard, 不知群员, 初始拉取群里面的所有群员
    public static void refreshGroupMember(Group group) {
        RemoteService service = Network.remote();
        service.groupMembers(group.getId())
                .enqueue(new Callback<RspModel<List<GroupMemberCard>>>() {
            @Override
            public void onResponse(Call<RspModel<List<GroupMemberCard>>> call,
                                   Response<RspModel<List<GroupMemberCard>>> response) {

                RspModel<List<GroupMemberCard>> rspModel = response.body();
                if (rspModel.success()) {
                    List<GroupMemberCard> memberCards = rspModel.getResult();
                    if (memberCards != null && memberCards.size() > 0) {
                        // 分发群员信息, 群员加入群
                        Factory.getGroupCenter()
                                .dispatch(memberCards, Common.NON_PushType);
                    }
                } else {
                    Factory.decodeRspCode(rspModel, null);
                }
            }

            @Override
            public void onFailure(Call<RspModel<List<GroupMemberCard>>> call, Throwable t) {
                // 不做任何事情
            }
        });
    }





    // 关联查询一个用户和群成员的表，返回一个MemberUserModel表的集合
    public static List<MemberUserModel> getMemberUsers(String groupId, int size) {
        return SQLite.select(GroupMember_Table.alias.withTable().as("alias"),
                User_Table.id.withTable().as("userId"),
                User_Table.name.withTable().as("name"),
                User_Table.portrait.withTable().as("portrait"))
                .from(GroupMember.class)
                .join(User.class, Join.JoinType.INNER)
                .on(GroupMember_Table.user_id.withTable().eq(User_Table.id.withTable()))
                .where(GroupMember_Table.group_id.withTable().eq(groupId))
                .orderBy(GroupMember_Table.user_id, true)
                .limit(size)
                .queryCustomList(MemberUserModel.class);
    }



    // 网络请求进行成员添加
    public static void addMembers(String groupId, GroupMemberModel model,
                                  final DataSource.Callback<List<GroupMemberCard>> callback) {
        RemoteService service = Network.remote();
        service.groupMemberAdd(groupId, model)
                .enqueue(new Callback<RspModel<List<GroupMemberCard>>>() {

                    @RequiresApi(api = Build.VERSION_CODES.N)
                    @Override
                    public void onResponse(Call<RspModel<List<GroupMemberCard>>> call, Response<RspModel<List<GroupMemberCard>>> response) {
                        RspModel<List<GroupMemberCard>> rspModel = response.body();
                        if (rspModel.success()) {
                            List<GroupMemberCard> memberCards = rspModel.getResult();
                            if (memberCards != null && memberCards.size() > 0) {
                                // 新增成员分发给本地存储以及数据监听器
                                Factory.getGroupCenter().dispatch(memberCards,
                                        PushModel.ENTITY_TYPE_ADD_GROUP_MEMBERS);
                                // 给自己创建一个群推送SysNotify
                                final List<NotifyCreateModel> notifyModels =
                                        NotifyHelper.createNotifyModel(GroupMember.class,
                                                PushModel.ENTITY_TYPE_NOTI_ADD_GROUP_MEMBERS,
                                                memberCards.toArray(new GroupMemberCard[0]));
                                NotifyHelper.pushNotify(notifyModels);

                                if(callback != null) {
                                    //memberCards回送给GroupMemberAddPresenter
                                    callback.onDataLoaded(memberCards);
                                }
                            }
                        } else {
                            Factory.decodeRspCode(rspModel, null);
                        }
                    }

                    @Override
                    public void onFailure(Call<RspModel<List<GroupMemberCard>>> call, Throwable t) {
                        if(callback != null) {
                            callback.onDataNotAvailable(R.string.data_network_error);
                        }
                    }
                });
    }



    //对群成员的删除操作
    public static void operateMembers(String groupId, GroupMemberModel deleteModel,
                                      final DataSource.SucceedCallback<List<GroupMember>> callback) {

        RemoteService service = Network.remote();
        service.groupMemberDelete(groupId, deleteModel)
                .enqueue(new Callback<RspModel<List<GroupMemberCard>>>() {

                    @RequiresApi(api = Build.VERSION_CODES.N)               //api >= 24
                    @Override
                    public void onResponse(Call<RspModel<List<GroupMemberCard>>> call,
                                           Response<RspModel<List<GroupMemberCard>>> response) {

                        RspModel<List<GroupMemberCard>> rspModel = response.body();       //GroupMember信息以服务器端存储的数据为准
                        if(rspModel.success()){

                            final List<GroupMemberCard> memberCards = rspModel.getResult();

                            if (memberCards != null && memberCards.size() > 0) {
                                //刷新自己的群员列表
                                Factory.getGroupCenter().dispatch(memberCards,
                                        PushModel.ENTITY_TYPE_DEL_GROUP_MEMBERS);

                                // 给自己创建一个群推送SysNotify
                                final List<NotifyCreateModel> notifyModels =
                                        NotifyHelper.createNotifyModel(GroupMember.class,
                                                PushModel.ENTITY_TYPE_NOTI_DEL_GROUP_MEMBERS,
                                                memberCards.toArray(new GroupMemberCard[0]));
                                NotifyHelper.pushNotify(notifyModels);
                            }
                        }else {
                            //1. 解析服务端传过来的错误
                            Factory.decodeRspCode(rspModel, null);
                        }

                    }

                    @Override
                    public void onFailure(Call<RspModel<List<GroupMemberCard>>> call, Throwable t) {
                        //2. Restful通信框架出错
                    }
                });

    }



    public static void ModifyAdmins(String groupId, GroupMemberModel model,
                                    final DataSource.SucceedCallback<List<GroupMember>> callback) {

        RemoteService service = Network.remote();
        //返回的GroupMemberCard: 新添加的Admin, xixihe
        service.AddAdmin(groupId, model).enqueue(new Callback<RspModel<List<GroupMemberCard>>>() {
            @RequiresApi(api = Build.VERSION_CODES.N)
            @Override
            public void onResponse(Call<RspModel<List<GroupMemberCard>>> call,
                                   Response<RspModel<List<GroupMemberCard>>> response) {

                RspModel<List<GroupMemberCard>> rspModel = response.body();
                if(rspModel.success()){
                    final List<GroupMemberCard> memberCards = rspModel.getResult();

                    if (memberCards != null && memberCards.size() > 0) {            //不校验了
                        //刷新自己的群员列表
                        Factory.getGroupCenter().dispatch(memberCards,
                                PushModel.ENTITY_TYPE_MODIFY_GROUP_MEMBERS_PERMISSION);

                        // 给自己创建一个群推送SysNotify
                        final List<NotifyCreateModel> notifyModels =
                                NotifyHelper.createNotifyModel(GroupMember.class,
                                        PushModel.ENTITY_TYPE_MODIFY_GROUP_MEMBERS_PERMISSION,
                                        memberCards.toArray(new GroupMemberCard[0]));
                        NotifyHelper.pushNotify(notifyModels);
                    }
                }
            }

            @Override
            public void onFailure(Call<RspModel<List<GroupMemberCard>>> call, Throwable t) {

            }
        });

    }




    public static void JoinGroup(String groupId, String userId, ApplyCallback callback){
        RemoteService service = Network.remote();
        //回调显示已发送申请 or 请求失败 !
        service.applyJoin(groupId, userId)
                .enqueue(new Callback<RspModel<ApplyCard>>() {
            @Override
            public void onResponse(Call<RspModel<ApplyCard>> call,
                                   Response<RspModel<ApplyCard>> response) {
                if(response.body().success()){
                    callback.onApplySucceed();
                }else {
                    callback.onApplyFailed();
                }
            }

            @Override
            public void onFailure(Call<RspModel<ApplyCard>> call, Throwable t) {
                callback.onApplyFailed();
            }
        });

    }

}
