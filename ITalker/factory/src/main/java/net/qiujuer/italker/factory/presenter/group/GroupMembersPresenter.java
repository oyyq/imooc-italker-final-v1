package net.qiujuer.italker.factory.presenter.group;

import android.annotation.SuppressLint;
import android.support.v7.util.DiffUtil;
import net.qiujuer.italker.common.widget.recycler.RecyclerAdapter;
import net.qiujuer.italker.factory.Factory;
import net.qiujuer.italker.factory.R;
import net.qiujuer.italker.factory.data.DataSource;
import net.qiujuer.italker.factory.data.group.GroupMembersDataSource;
import net.qiujuer.italker.factory.data.group.GroupMembersRepository;
import net.qiujuer.italker.factory.data.helper.GroupHelper;
import net.qiujuer.italker.factory.model.api.PushModel;
import net.qiujuer.italker.factory.model.api.RspModel;
import net.qiujuer.italker.factory.model.api.group.GroupMemberModel;
import net.qiujuer.italker.factory.model.card.GroupMemberCard;
import net.qiujuer.italker.factory.model.db.Group;
import net.qiujuer.italker.factory.model.db.GroupMember;
import net.qiujuer.italker.factory.net.Network;
import net.qiujuer.italker.factory.persistence.Account;
import net.qiujuer.italker.factory.presenter.BaseSourcePresenter;
import net.qiujuer.italker.factory.utils.DiffUiDataCallback;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class GroupMembersPresenter extends
        BaseSourcePresenter<GroupMember, GroupMember,
                GroupMembersDataSource, GroupMembersContract.View>
        implements GroupMembersContract.Presenter {


    private Set<String> users = new HashSet<>();
    private String groupId;
    private Group group;

    //只有Admin需要知道所有的Admin(包括自己) 并且存储在 Admins中, 对于普通群员, Admins == null
    //存储User.id, 这个群的所有Admin
    private Set<String> Admins = new HashSet<>();

    //"我"退出群的回调,
    private DataSource.SucceedCallback<List<GroupMember>> exitCallback;


    public GroupMembersPresenter(GroupMembersDataSource source, GroupMembersContract.View view) {
        super(source, view);
        this.groupId = mView.getGroupId();
        this.group = GroupHelper.findFromLocal(groupId);
    }



    @Override
    public void start() {
        super.start();
        //初始刷出群员
        Factory.runOnAsync(loader);

    }


    @Override
    public void reload() {
        onDataLoaded(mSource.getDataList());
    }


    @Override
    public DataSource.SucceedCallback<List<GroupMember>> getExitCallback() {
        return exitCallback;
    }


    @Override
    public void setExitCallback(DataSource.SucceedCallback<List<GroupMember>> exitCallback) {
        this.exitCallback = exitCallback;
    }



    @Override
    public void modifyAdmin() {

        GroupMembersContract.View view = mView;

        if (users.size() == 0){
            view.showError(R.string.no_Admin);
        }else if(users.contains(Account.getUserId())){
            view.showError(R.string.new_Admin_error);
        }
        view.showLoading();

        Factory.runOnAsync(new Runnable() {
            @Override
            public void run() {
                GroupMemberModel Model =
                        new GroupMemberModel(users);

                GroupHelper.ModifyAdmins(groupId, Model,
                        GroupMembersPresenter.this);

            }
        });

    }



    @Override
    public void changeSelect(GroupMember member, boolean isSelected) {
        if (isSelected)
            users.add( member.getUser().getId() );
        else
            users.remove( member.getUser().getId() );
    }



    @Override
    public void delete() {
        GroupMembersContract.View view = mView;

        //判断参数
        if (users.size() == 0){
            view.showError(R.string.label_group_member_delete_invalid);
            return;
        }else if ( users.contains(Account.getUserId())){
            view.showError(R.string.no_delete_self);
            return;
        }

        view.showLoading();

        //网络请求
        Factory.runOnAsync(new Runnable() {
            @Override
            public void run() {
                GroupMemberModel deleteModel =
                        new GroupMemberModel(users);
                GroupHelper.operateMembers(groupId, deleteModel,
                        GroupMembersPresenter.this);

            }
        });

    }


    /**
     * "我"退出了群聊的处理方式: 删掉我自己
     *  需要先判断我是不是Admin, 若是, 必须先添加一个新Admin, "我"才能退群
     */
    @Override
    public void MyExitGroup() {

        GroupMembersContract.View view = mView;
        if(Admins != null){
            //只有我一个Admin
            if(Admins.size() == 1 && Admins.contains(Account.getUserId()) ){
                view.showError(R.string.exit_only_Admin);
                return;
            }
        }

        view.showLoading();

        Factory.runOnAsync(new Runnable() {
             @Override
             public void run() {

                 Network.remote().ExitGroup(groupId)
                         .enqueue(new Callback<RspModel<List<GroupMemberCard>>>() {
                             @Override
                             public void onResponse(Call<RspModel<List<GroupMemberCard>>> call,
                                                    Response<RspModel<List<GroupMemberCard>>> response) {

                                 view.hideLoading();
                                 RspModel<List<GroupMemberCard>> rspModel = response.body();
                                 if (rspModel.success()) {
                                     List<GroupMemberCard> deleteCards = rspModel.getResult();
                                     Factory.getGroupCenter().dispatch(deleteCards,
                                             PushModel.ENTITY_TYPE_EXIT_GROUP_MEMBERS);

                                     exitCallback.onDataLoaded(null);
                                 }
                             }

                             @Override
                             public void onFailure(Call<RspModel<List<GroupMemberCard>>> call, Throwable t) {
                                 //"我"退群失败, 什么都不做
                                 view.hideLoading();
                             }
                         });

             }
        });

    }



    @SuppressLint("NewApi")
    @Override
    public void onDataLoaded(List<GroupMember> groupMembers) {
        //刷新
        GroupMembersContract.View view = mView;
        if (view == null)
            return;

        view.hideLoading();

        //差异刷新
        RecyclerAdapter<GroupMember> adapter = view.getRecyclerAdapter();
        List<GroupMember> old = adapter.getItems();

        // 进行数据对比
        DiffUtil.Callback callback = new DiffUiDataCallback<>(old, groupMembers);
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(callback);

        // 调用基类方法进行界面刷新
        refreshData(result, groupMembers);

        //由于this.onDataLoaded参数总是mSource.dataList, 群管理有更新时, dataList有更新, Admins重新赋值
        Admins = new HashSet<>();
        groupMembers.stream().filter(member -> {
           if(member.isAdmin()) Admins.add(member.getUser().getId());
           return member.isAdmin(); }).count();

    }



    private Runnable loader = new Runnable() {
        @Override
        public void run() {
            GroupMembersContract.View view = getView();
            if (view == null)
                return;
            GroupHelper.refreshGroupMember(group);
        }
    };



}