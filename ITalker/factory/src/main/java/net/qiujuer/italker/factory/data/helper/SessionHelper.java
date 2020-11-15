package net.qiujuer.italker.factory.data.helper;


import com.raizlabs.android.dbflow.sql.language.SQLite;

import net.qiujuer.italker.factory.model.card.Card;
import net.qiujuer.italker.factory.model.db.GetPushedImpl;
import net.qiujuer.italker.factory.model.db.Session;
import net.qiujuer.italker.factory.model.db.Session_Table;


import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 会话辅助工具类
 *
 * @author qiujuer Email:qiujuer@live.cn
 * @version 1.0.0
 */
public class SessionHelper {
    // 从本地查询Session
    public static Session findFromLocal(String id) {
        return SQLite.select()
                .from(Session.class)
                .where(Session_Table.id.eq(id))
                .querySingle();
    }



    //本地Session_Table两日期之间的数据
    //    SimpleDateFormat sdf=new SimpleDateFormat("YYYY-MM-DD HH:MM:SS");
    //    final String earlierStr = sdf.format(earlier);
    //    final String laterStr = sdf.format(later);
    public static List<Session> LocalSessionsBet(Date earlier, Date later){

        return SQLite.select()
                .from(Session.class)
                .where(Session_Table.modifyAt.greaterThan(earlier).lessThanOrEq(later))             //半开半闭区间
                .queryList();


    }




}
