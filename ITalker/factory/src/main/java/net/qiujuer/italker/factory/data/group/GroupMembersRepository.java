package net.qiujuer.italker.factory.data.group;

import com.raizlabs.android.dbflow.sql.language.SQLite;

import net.qiujuer.italker.factory.data.BaseDbRepository;
import net.qiujuer.italker.factory.model.db.GroupMember;
import net.qiujuer.italker.factory.model.db.GroupMember_Table;

import java.util.List;
import java.util.UUID;

public class GroupMembersRepository
        extends BaseDbRepository<GroupMember>
        implements GroupMembersDataSource {

    private String groupId;

    public GroupMembersRepository(String groupId){
        this.groupId = groupId;
    }

    @Override
    public String getId() {
        return "GroupMembers"+groupId;
    }


    @Override
    public void load(SucceedCallback<List<GroupMember>> callback) {
        super.load(callback);

//        //初始化:  本地查找群员, limit(-1): 全部查询 todo 11-06 15:03 认为不需要
//        final List<GroupMember> members = SQLite.select()
//                .from(GroupMember.class)
//                .where(GroupMember_Table.group_id.eq(groupId))
//                .limit(-1)
//                .queryList();
//
//        onListQueryResult(null, members);

    }


    @Override
    protected void insert(GroupMember member) {
        dataList.add(member);
    }


    @Override
    protected boolean isRequired(GroupMember groupMember) {
        //是群里成员
        return groupMember.getGroup().getId().equals(groupId);
    }



}
