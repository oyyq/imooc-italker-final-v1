package net.qiujuer.italker.factory.data.message;

import android.support.annotation.NonNull;

import com.google.gson.Gson;
import com.raizlabs.android.dbflow.sql.language.SQLite;
import com.raizlabs.android.dbflow.structure.database.transaction.QueryTransaction;

import net.qiujuer.italker.factory.Factory;
import net.qiujuer.italker.factory.data.BaseDbRepository;
import net.qiujuer.italker.factory.data.helper.SessionHelper;
import net.qiujuer.italker.factory.model.card.Card;
import net.qiujuer.italker.factory.model.db.GetPushedImpl;
import net.qiujuer.italker.factory.model.db.Session;
import net.qiujuer.italker.factory.model.db.Session_Table;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 最近聊天列表仓库，是对SessionDataSource的实现
 *
 * @author qiujuer Email:qiujuer@live.cn
 * @version 1.0.0
 */
public class SessionRepository
        extends BaseDbRepository<Session>
        implements SessionDataSource {


    String id= UUID.randomUUID().toString();

    @Override
    public String getId() {
        return "Session"+ id;
    }


    @Override
    public void load(SucceedCallback<List<Session>> callback) {
        super.load(callback);
        // 数据库查询出所有Session
        SQLite.select()
                .from(Session.class)
                .orderBy(Session_Table.modifyAt, false) // false 是倒序
                .limit(-1)
                .async()
                .queryListResultCallback(this)
                .execute();

        //10, 9, 8
        // 复写insert之后导致的问题 8, 9, 10

    }


    @Override
    protected boolean isRequired(Session session) {
        // 所有的会话我都需要，不需要过滤
        return true;
    }

    @Override
    protected void insert(Session session) {
        // 复写方法，让新的数据加到头部
        dataList.addFirst(session);
    }

    @Override
    public void onListQueryResult(QueryTransaction transaction, @NonNull List<Session> tResult) {
        // 复写数据库回来的方法, 进行一次反转
        Collections.reverse(tResult);

        super.onListQueryResult(transaction, tResult);
    }


}
