package net.qiujuer.italker.factory.data.Pushed;

import com.raizlabs.android.dbflow.config.DatabaseDefinition;
import com.raizlabs.android.dbflow.config.FlowManager;
import com.raizlabs.android.dbflow.structure.ModelAdapter;
import com.raizlabs.android.dbflow.structure.database.DatabaseWrapper;
import com.raizlabs.android.dbflow.structure.database.transaction.ITransaction;

import net.qiujuer.italker.factory.data.helper.DbHelper;
import net.qiujuer.italker.factory.data.helper.SessionHelper;
import net.qiujuer.italker.factory.model.db.AppDatabase;
import net.qiujuer.italker.factory.model.db.GetPushedImpl;
import net.qiujuer.italker.factory.model.db.Message;
import net.qiujuer.italker.factory.model.db.Session;
import net.qiujuer.italker.factory.model.db.SysNotify;

public class PushedUserRepository
        extends PushedUserRepo<Message, SysNotify> {

    public PushedUserRepository(String otherId) {
        super(otherId);
    }

    @Override
    protected void insert(GetPushedImpl pushed) {
        if(dataList.size() == 0) { dataList.add(pushed);}
        else {
            final int len = dataList.size();
            for (int i = 0; i <= len; i++) {
                if(i == len){
                    dataList.add(pushed);
                } else {
                    if (pushed.getCreateAt().compareTo(dataList.get(i).getCreateAt()) < 0) {
                        dataList.add(i, pushed);
                        Session session = SessionHelper.findFromLocal(otherId);
                        if(session != null && session.isNeedUpdateUnReadCount()){
                            Session.updateSessionOuter(session, session1 -> session1.increUnread());
                        }
                        break;
                    }
                }
            }
        }
        unread.incrementAndGet();

    }

}