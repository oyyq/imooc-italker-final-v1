package net.qiujuer.italker.factory.data.Pushed;

import com.raizlabs.android.dbflow.config.DatabaseDefinition;
import com.raizlabs.android.dbflow.config.FlowManager;
import com.raizlabs.android.dbflow.sql.language.OperatorGroup;
import com.raizlabs.android.dbflow.sql.language.SQLite;
import com.raizlabs.android.dbflow.structure.ModelAdapter;
import com.raizlabs.android.dbflow.structure.database.DatabaseWrapper;
import com.raizlabs.android.dbflow.structure.database.transaction.ITransaction;

import net.qiujuer.genius.kit.reflect.Reflector;
import net.qiujuer.italker.factory.data.BaseDbRepository;
import net.qiujuer.italker.factory.data.DataSource;
import net.qiujuer.italker.factory.data.helper.DbHelper;
import net.qiujuer.italker.factory.data.helper.SessionHelper;
import net.qiujuer.italker.factory.model.db.AppDatabase;
import net.qiujuer.italker.factory.model.db.GetPushedImpl;
import net.qiujuer.italker.factory.model.db.Message;
import net.qiujuer.italker.factory.model.db.Message_Table;
import net.qiujuer.italker.factory.model.db.Session;
import net.qiujuer.italker.factory.model.db.SysNotify;
import net.qiujuer.italker.factory.model.db.SysNotify_Table;
import net.qiujuer.italker.factory.persistence.Account;
import net.qiujuer.italker.factory.presenter.message.ChatPresenter;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public abstract class
        PushedGroupRepo<Mes extends GetPushedImpl, Note extends GetPushedImpl>
        extends BaseDbRepository<GetPushedImpl>
        implements PushedDataSource {

    // 聊天的群Id,
    protected String groupId;
    private Class<Mes> MesClass;
    private Class<Note> NoteClass;


    public PushedGroupRepo(String groupId) {
        super();                  //BaseDbRepository()无参构造函数拿当前类的范型数组信息
        this.groupId = groupId;
    }


    @Override
    public String getId() {
        return groupId;
    }

    /**
     * 第一次界面刷新, 加载群聊数据
     *
     * @param callback 传递一个callback回调，由Presenter实现
     */
    @Override
    public void load(DataSource.SucceedCallback<List<GetPushedImpl>> callback) {
        super.load(callback);

        int SessionunReadCount = 0;
        Session session = SessionHelper.findFromLocal(groupId);
        if(session != null) {
            SessionunReadCount = session.getUnReadCount();
            Session.updateSessionOuter(session, session1 -> {session1.setUnReadCount(0);
                    session1.setNeedUpdateUnReadCount(false);});
        }

        int cacheUnread = dataList.size() == 0? 0:this.unread.get();
        this.unread.set(0);
        //cacheUnread >= SessionunReadCount
        int maxUnread = Math.max(SessionunReadCount, cacheUnread);
        //告知界面端初始未读消息数量
        ((ChatPresenter)callback).setInitialUnread(maxUnread);

        if(maxUnread == 0) {
            if (dataList.size() > 0) {
                //直接走通知Presenter刷出 初始数据,
                callback.onDataLoaded(dataList);
            } else {
                final List<Message> messages
                        = initRefreshMes(10);

                List<SysNotify> notifies = null;
                if( messages != null && messages.size() > 0) {
                    notifies = initRefreshSys(messages.get(0).getCreateAt());
                }else {
                    notifies = initRefreshSys(10);
                }

                //将两者混合起来, 按时间排序
                List<GetPushedImpl> pushes = new LinkedList<>();
                mergeResultbyTime(messages, notifies, pushes);
                onListQueryResult(null, pushes);

            }
        } else if(maxUnread > 0){

            if( dataList.size() >= maxUnread ){
                callback.onDataLoaded(dataList);
            }else {

                final List<Message> messages
                        = initRefreshMes( maxUnread );
                final List<SysNotify> notifies = initRefreshSys( maxUnread );


                List<GetPushedImpl> pushes = new LinkedList<>();
                mergeResultbyTime(messages, notifies, pushes);
                List<GetPushedImpl> subpushes = pushes.subList(pushes.size()-maxUnread, pushes.size());
                onListQueryResult(null, subpushes);

            }
        }
    }






    private List<Message> initRefreshMes(int num){

        List<Message> mes =  SQLite.select().from(Message.class)
                .where(Message_Table.group_id.eq(groupId))
                .orderBy(Message_Table.createAt, false)         //时间近的排在前面
                .limit(num)
                .queryList();

        if(mes != null && mes.size() > 0)
            Collections.reverse(mes);

        return mes;
    }





    private List<SysNotify> initRefreshSys(Date earlier){

        List<SysNotify> sys =  SQLite.select()
                .from(SysNotify.class)
                .where(SysNotify_Table.group_id.eq(groupId))
                .and(SysNotify_Table.createAt.greaterThanOrEq(earlier))

                .orderBy(SysNotify_Table.createAt, true)                //时间正序
                .queryList();

        return sys;
    }



    private List<SysNotify> initRefreshSys(int num){
        List<SysNotify> sys = SQLite.select()
                .from(SysNotify.class)
                .where(SysNotify_Table.group_id.eq(groupId))
                .orderBy(SysNotify_Table.createAt, false)
                .limit(num)                     //最近不多于num条数据
                .queryList();

        if(sys != null && sys.size() > 0)
            Collections.reverse(sys);
        return sys;
    }




    //按照时间先后顺序排序, 时间晚的在后面, mes & sys 是时间正序
    private void mergeResultbyTime(List<Message> mes, List<SysNotify> sys,
                                   List<GetPushedImpl> pushes){


        if(mes == null ||  mes.size() == 0){
            pushes.addAll(sys);
            return;
        }else if (sys == null || sys.size() == 0){
            pushes.addAll(mes);
            return;
        }

        int curmes = 0;
        int cursys = 0;

        Message message = mes.size() > 0 ? mes.get(curmes++):null;
        SysNotify notify = sys.size() > 0 ? sys.get(cursys++) :null;

        while (true){
            if(message != null && notify != null ){
                if(message.getCreateAt().compareTo(notify.getCreateAt()) <= 0) {
                    pushes.add(message);
                    message = curmes < mes.size() ? mes.get(curmes++) : null;
                } else {
                    pushes.add(notify);
                    notify = cursys < sys.size() ? sys.get(cursys++) : null;
                }
            } else {
                if (notify == null){
                    pushes.addAll(mes.subList(curmes-1, mes.size()));
                } else {
                    pushes.addAll(sys.subList(cursys-1, sys.size()));
                }
                break;
            }
        }
    }




    @Override
    public void loadBefore(Date date){
        //耗时操作放在线程里
        new Thread(){
            @Override
            public void run() {
                super.run();
                List<Message> EalierMessages = SQLite.select().from(Message.class)
                        .where(Message_Table.createAt.lessThan(date))
                        .and( Message_Table.group_id.eq( groupId) )
                        .orderBy(Message_Table.createAt, false)
                        //其他过滤条件
                        .limit(10)
                        .queryList();


                List<SysNotify> EarlierNotifies = null;
                if(EalierMessages != null && EalierMessages.size() > 0) {
                    Collections.reverse(EalierMessages);
                    //最早的
                    Date date1 = EalierMessages.get(0).getCreateAt();
                    EarlierNotifies = SQLite.select().from(SysNotify.class)
                            .where(OperatorGroup.clause(SysNotify_Table.createAt.greaterThanOrEq(date1))
                                    .and(SysNotify_Table.createAt.lessThan(date)))

                            .and(SysNotify_Table.group_id.eq(groupId))
                            .orderBy(SysNotify_Table.createAt, true)
                            .queryList();
                }else {

                    EarlierNotifies = SQLite.select().from(SysNotify.class)
                            .where(SysNotify_Table.createAt.lessThan(date))

                            .and(SysNotify_Table.group_id.eq(groupId))

                            .orderBy(SysNotify_Table.createAt, false)
                            .limit(10)
                            .queryList();

                    if(EarlierNotifies != null && EarlierNotifies.size() > 0)
                        Collections.reverse(EarlierNotifies);
                }

                List<GetPushedImpl> pushes = new LinkedList<>();
                mergeResultbyTime(EalierMessages, EarlierNotifies, pushes);

                //向dataList头插入
                dataList.addAll(0, pushes);
                notifyDataChange();

            }
        }.start();

    }




    @Override
    protected boolean isRequired(GetPushedImpl getPushed) {
        //无论是Message还是SysNotify, 都这么判断
        return getPushed.getGroup() != null && groupId.equalsIgnoreCase(getPushed.getGroup().getId());

    }


}
