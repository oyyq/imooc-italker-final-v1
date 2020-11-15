package net.qiujuer.italker.factory.presenter.group;

import net.qiujuer.italker.factory.data.DataSource;
import net.qiujuer.italker.factory.model.db.GroupMember;
import net.qiujuer.italker.factory.model.db.view.MemberUserModel;
import net.qiujuer.italker.factory.presenter.BaseContract;

import java.util.List;

/**
 * 群成员的契约
 *
 * @author qiujuer Email:qiujuer@live.cn
 * @version 1.0.0
 */
public interface GroupMembersContract {
    interface Presenter extends BaseContract.Presenter {
        // 具有一个刷新的方法
        //void refresh();


        // 更改一个Model的选中状态
        void changeSelect(GroupMember member, boolean isSelected);

        //删除群员
        void delete();

        //自己退群: 不发群通知给其他群员
        void MyExitGroup();


        DataSource.SucceedCallback<List<GroupMember>> getExitCallback();

        void setExitCallback(DataSource.SucceedCallback<List<GroupMember>> exitCallback);


        void modifyAdmin();



    }

    // 界面
    interface View extends BaseContract.RecyclerView<Presenter, GroupMember> {
        // 获取群的ID
        String getGroupId();

        void AdminPerms();

        void onDeleted();
    }
}
