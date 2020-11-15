package net.qiujuer.italker.factory.data.group;

import android.text.TextUtils;

import com.raizlabs.android.dbflow.sql.language.SQLite;

import net.qiujuer.italker.factory.data.BaseDbRepository;
import net.qiujuer.italker.factory.data.helper.DbHelper;
import net.qiujuer.italker.factory.data.helper.GroupHelper;
import net.qiujuer.italker.factory.model.db.GetPushedImpl;
import net.qiujuer.italker.factory.model.db.Group;
import net.qiujuer.italker.factory.model.db.Group_Table;
import net.qiujuer.italker.factory.model.db.view.MemberUserModel;
import net.qiujuer.italker.factory.presenter.message.ChatGroupPresenter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 我的群组的数据仓库 是对GroupsDataSource的实现
 *
 * @author qiujuer Email:qiujuer@live.cn
 * @version 1.0.0
 */
public class GroupsRepository
        extends BaseDbRepository<Group>
        implements GroupsDataSource {


    String id = UUID.randomUUID().toString();

    @Override
    public String getId() {
        return "Groups"+id;
    }


    @Override
    public void load(SucceedCallback<List<Group>> callback) {
        super.load(callback);

        SQLite.select()
                .from(Group.class)
                .orderBy(Group_Table.name, true)
                .limit(100)
                .async()
                .queryListResultCallback(this)
                .execute();
    }


    @Override
    protected void insert(Group group) {
        dataList.add(group);
    }


    @Override
    protected boolean insertOrUpdateOrDelete(Group group) {
        int index = indexOf(group);
        if (index >= 0) {
            //"我"不在这个群里
            if(group.getJoinAt() == null){
                dataList.remove(index);
                return true;
            } else if(!group.isUiContentSame(dataList.get(index))) {
                replace(index, group);
                return true;
            }
            return false;
        } else {
            insert(group);
            return true;
        }
    }



    /**
     *  一个群的信息，只可能两种情况出现在数据库: 1. 你被别人加入群, 2. 你直接建立一个群
     *       无论什么情况, 你只拿到群的信息, 没有成员的信息
     *      你需要进行成员信息初始化操作
     * @param group
     * @return
     */
    @Override
    protected boolean isRequired(Group group) {
        //每次updateGroup都刷成员数量
        group.refreshGroupMemberCount();
        //本地存储的GroupMember与服务器端时时同步
        if (group.getGroupMemberCount() > 0) {
            group.holder = buildGroupHolder(group);
        } else {
            group.holder = null;
            GroupHelper.refreshGroupMember(group);
        }

        // "我"在该群里
        return group.getJoinAt() != null;
    }




    // 初始化界面显示的成员信息
    public static String buildGroupHolder(Group group) {
        //刷新缓存的4个成员
        group.refreshLatelyGroupMembers();
        List<MemberUserModel> userModels = group.getLatelyGroupMembers();

        if (userModels == null || userModels.size() == 0)
            return null;

        StringBuilder builder = new StringBuilder();
        for (MemberUserModel userModel : userModels) {
            builder.append(TextUtils.isEmpty(userModel.alias) ? userModel.name : userModel.alias);
            builder.append(", ");
        }

        builder.delete(builder.lastIndexOf(", "), builder.length());

        // 通知ChatGroupPresenter进行聊天顶部成员信息更改. todo 不要
        //ModifyChatGroupMembers(group.getId());

        return builder.toString();
    }






/*

    //todo 还有没有漏洞 ??, 会报空指针异常, 10-31 14:56 重新设计一下这里
    private static void ModifyChatGroupMembers(String groupId){
        final Map<String, DbHelper.ChangedListener> chatListeners = DbHelper.getChangedListeners()
                .get(GetPushedImpl.class);

        //todo 什么情况下是null ? 还没进入到MessageActivity
        if(chatListeners == null) return;
        final DbHelper.ChangedListener changedListener = chatListeners.get(groupId);
        if(changedListener == null) return;

        final List<SucceedCallback<List<GetPushedImpl>>> callbacks
                = ((BaseDbRepository) changedListener).getCallback();

        if(callbacks != null ){
            for( SucceedCallback<List<GetPushedImpl>> callback: callbacks ){
                //通知刷新聊天顶部人员
                ( (ChatGroupPresenter)callback).reloadMembers();
            }
        }

    }

*/



}
