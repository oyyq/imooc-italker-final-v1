package net.qiujuer.italker.factory.data.user;

import com.raizlabs.android.dbflow.sql.language.SQLite;

import net.qiujuer.italker.factory.data.BaseDbRepository;
import net.qiujuer.italker.factory.data.DataSource;
import net.qiujuer.italker.factory.model.db.User;
import net.qiujuer.italker.factory.model.db.User_Table;
import net.qiujuer.italker.factory.persistence.Account;

import java.util.List;
import java.util.UUID;

/**
 * 联系人仓库
 *
 * @author qiujuer Email:qiujuer@live.cn
 * @version 1.0.0
 */
public class ContactRepository extends BaseDbRepository<User>
        implements ContactDataSource {

    String id = UUID.randomUUID().toString();

    @Override
    public String getId() {
        return "Contact"+id;
    }


    @Override
    public void load(DataSource.SucceedCallback<List<User>> callback) {
        super.load(callback);

        // 加载本地数据库数据
        SQLite.select()
                .from(User.class)
                .where(User_Table.isFollow.eq(true))
                .and(User_Table.id.notEq(Account.getUserId()))
                .orderBy(User_Table.name, true)
                .limit(100)
                .async()
                .queryListResultCallback(this)
                .execute();
    }

    @Override
    protected void insert(User user) {
        dataList.add(user);
    }


    @Override
    protected boolean insertOrUpdateOrDelete(User user) {
        int index = indexOf(user);
        if (index >= 0) {
            //user不再是"我"好友
            if(!user.isFollow()){
                dataList.remove(index);
                return true;
            } else if(!user.isUiContentSame(dataList.get(index))) {
                replace(index, user);
                return true;
            }
            return false;
        } else {
            insert(user);
            return true;
        }
    }



    @Override
    protected boolean isRequired(User user) {
        return user.isFollow() && !user.getId().equals(Account.getUserId());
    }

}
