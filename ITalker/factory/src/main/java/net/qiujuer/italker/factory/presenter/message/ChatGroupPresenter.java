package net.qiujuer.italker.factory.presenter.message;

import net.qiujuer.italker.common.Common;
import net.qiujuer.italker.factory.data.Pushed.PushedDataSource;
import net.qiujuer.italker.factory.data.Pushed.PushedGroupRepository;
import net.qiujuer.italker.factory.data.helper.GroupHelper;
import net.qiujuer.italker.factory.model.db.Group;
import net.qiujuer.italker.factory.model.db.GroupMember;
import net.qiujuer.italker.factory.model.db.Message;
import net.qiujuer.italker.factory.model.db.view.MemberUserModel;
import net.qiujuer.italker.factory.persistence.Account;

import java.util.List;

/**
 * 群聊天的逻辑
 *
 * @author qiujuer Email:qiujuer@live.cn
 * @version 1.0.0
 */
public class ChatGroupPresenter
        extends ChatPresenter<ChatContract.GroupView>
        implements ChatContract.Presenter {

    private Group group;
    private ChatContract.GroupView view;

    public ChatGroupPresenter(PushedDataSource repo, ChatContract.GroupView view, String groupId) {
        // 数据源，View，接收者，接收者的类型
        super(repo == null? new PushedGroupRepository(groupId): repo,  view, groupId, Common.RECEIVER_TYPE_GROUP);
    }

    @Override
    public void start() {
        super.start();
        // 拿群的信息
        group = GroupHelper.findFromLocal(mReceiverId);

        if (group != null) {
            view = getView();
            // 初始化操作
            ChatContract.GroupView view = this.view;

            GroupMember member = GroupHelper.getMember(Account.getUserId(), mReceiverId);
            if(member == null) return;
            boolean isAdmin = member.isAdmin();
            view.showAdminOption(isAdmin);
            // 基础信息初始化
            view.onInit(group);
            reloadMembers();
        }
    }


    // 刷新顶部 mLayMembers的显示群员
    public void reloadMembers(){

        Group group = this.group;
        // 本地查找群成员 --> 4个
        group.refreshLatelyGroupMembers();
        List<MemberUserModel> models = group.getLatelyGroupMembers();
        // 本地查找所有群成员数量
        group.refreshGroupMemberCount();
        final long memberCount = group.getGroupMemberCount();
        //没有显示的成员数量
        long moreCount = memberCount - models.size();
        //刷新群成员的头像
        view.onInitGroupMembers(models, moreCount);

    }



}