package net.qiujuer.italker.factory.data.Pushed;

import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.widget.LinearLayout;

import com.raizlabs.android.dbflow.config.DatabaseDefinition;
import com.raizlabs.android.dbflow.config.FlowManager;
import com.raizlabs.android.dbflow.sql.language.OperatorGroup;
import com.raizlabs.android.dbflow.sql.language.SQLite;
import com.raizlabs.android.dbflow.structure.ModelAdapter;
import com.raizlabs.android.dbflow.structure.database.DatabaseWrapper;
import com.raizlabs.android.dbflow.structure.database.transaction.ITransaction;

import net.qiujuer.genius.kit.reflect.Reflector;
import net.qiujuer.italker.factory.data.BaseDbRepository;
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

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;


/**
 * 和别人单聊"我"拿到的推送, 有可能是消息, 或者是系统通知
 * @param <Mes> Message
 * @param <Note> SysNotify  都是GetPushedImpl的子类
 */
public abstract class
        PushedUserRepo<Mes extends GetPushedImpl, Note extends GetPushedImpl>
        extends BaseDbRepository<GetPushedImpl>
        implements PushedDataSource {

    // 聊天的对象Id, "对方", 人
    protected String otherId;
    private Class<Mes> MesClass;
    private Class<Note> NoteClass;

    public PushedUserRepo(String otherId) {
        super();                    //BaseDbRepository()无参构造函数拿当前类的范型数组信息, GetPushedImpl
        this.otherId = otherId;
    }

    @Override
    public String getId() {
        return otherId;
    }


    @Override
    protected boolean isRequired(GetPushedImpl getPushed) {

        if(getPushed instanceof Message){
            if(getPushed.getSender() == null) return false;
            // otherId 如果是发送者，那么Group==null情况下一定是发送给我的消息
            return (otherId.equalsIgnoreCase(getPushed.getSender().getId())
                    && getPushed.getGroup() == null)                                        //"他"发给我的, 不是发给群的消息
                    || (getPushed.getReceiver() != null                                   //getReceiver() != null, getGroup()只能是null
                     && otherId.equalsIgnoreCase(getPushed.getReceiver().getId())               //"我"发给"他"消息, "他"是人
                    );
        }else if(getPushed instanceof SysNotify){
            if(getPushed.getSender() == null) return false;
            //单聊中推给"我"的系统通知
            return (otherId.equalsIgnoreCase(getPushed.getSender().getId())
                    && getPushed.getGroup() == null );
        }

        //其他情况都不是我需要的数据
        return false;
    }



    @SuppressWarnings("unchecked")
    // 初始化要查最少Session.unReadCount条出来,
    // 本地缓存的getPushedImpl不少于Session.unReadCount, 并且Session.unReadCount >= BaseDbRepository.unread
    @Override
    public void load(SucceedCallback<List<GetPushedImpl>> callback) {
        super.load(callback);

        int SessionunReadCount =  0;
        Session session = SessionHelper.findFromLocal(otherId);
        if(session != null) {
            SessionunReadCount = session.getUnReadCount();
            Session.updateSessionOuter(session, session1 -> {session1.setUnReadCount(0);
                session1.setNeedUpdateUnReadCount(false);});
        }

        int cacheUnread = dataList.size() == 0? 0:this.unread.get();
        this.unread.set(0);
        //cacheUnread >= SessionunReadCount
        int maxUnread = Math.max(SessionunReadCount, cacheUnread);
        ((ChatPresenter)callback).setInitialUnread(maxUnread);

        if(maxUnread == 0) {
            if (dataList.size() > 0) {
                //直接走通知Presenter刷出 初始数据.
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
                //dataList数据量足够
                callback.onDataLoaded(dataList);
            }else {

                //同步查询, 也许单张表数据量不足, 但两张表加起来是足够的,
                // 这种情况不应该出现
                final List<Message> messages
                        = initRefreshMes(maxUnread);
                final List<SysNotify> notifies = initRefreshSys(maxUnread);

                //将两者混合起来, 按时间排序
                List<GetPushedImpl> pushes = new LinkedList<>();
                mergeResultbyTime(messages, notifies, pushes);
                //只要最后的maxRead条数据, 不会缺数据
                List<GetPushedImpl> subpushes = pushes.subList(pushes.size()-maxUnread, pushes.size());
                onListQueryResult(null, subpushes);


            }
        }
    }






    private List<Message> initRefreshMes(int num){
        List<Message> mes = SQLite.select().from(Message.class)
                .where(OperatorGroup.clause(Message_Table.sender_id.eq(otherId))
                        .and(Message_Table.group_id.isNull())
                        .and(Message_Table.receiver_id.eq(Account.getUserId())))          //"他"发送给我的消息

                .or(Message_Table.receiver_id.eq(otherId))               //"我"发送给"他"的消息
                .orderBy(Message_Table.createAt, false)
                .limit(num)                         //不多于num条最近的消息
                .queryList();

        if(mes != null && mes.size() > 0)
            Collections.reverse(mes);
        return mes;
    }



    private List<SysNotify> initRefreshSys(Date earlier){

        List<SysNotify> sys =  SQLite.select()
                .from(SysNotify.class)
                .where(OperatorGroup.clause(SysNotify_Table.sender_id.eq(otherId))
                        .and(SysNotify_Table.group_id.isNull()))
                .and(SysNotify_Table.createAt.greaterThanOrEq(earlier))

                .orderBy(SysNotify_Table.createAt, true)                //时间正序
                .queryList();

        return sys;
    }



    private List<SysNotify> initRefreshSys(int num){
        List<SysNotify> sys = SQLite.select()
                .from(SysNotify.class)
                .where(OperatorGroup.clause(SysNotify_Table.sender_id.eq(otherId))
                        .and(SysNotify_Table.group_id.isNull()))
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


        if( mes == null || mes.size() == 0){
            pushes.addAll(sys);
            return;
        }else if ( sys == null || sys.size() == 0){
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



    //todo 这个方法在数据量小的时候会多次触发, 因此在本地没有更早的数据时应该做提示
    @Override
    public void loadBefore(Date date){
        //耗时操作放在线程里
        new Thread(){
            @Override
            public void run() {
                super.run();
                List<Message> EalierMessages = SQLite.select().from(Message.class)
                        .where(Message_Table.createAt.lessThan(date))

                        .and(OperatorGroup.clause(OperatorGroup.clause(Message_Table.sender_id.eq(Account.getUserId()))
                                .and(Message_Table.receiver_id.eq(otherId))
                                .and(Message_Table.group_id.isNull()))

                                .or(OperatorGroup.clause(Message_Table.receiver_id.eq(Account.getUserId()))
                                        .and(Message_Table.sender_id.isNotNull())))

                        .orderBy(Message_Table.createAt, false)

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

                            .and(OperatorGroup.clause(SysNotify_Table.sender_id.eq(otherId))
                                    .and(SysNotify_Table.group_id.isNull()))

                            .orderBy(SysNotify_Table.createAt, true)
                            .queryList();
                } else {

                    EarlierNotifies = SQLite.select().from(SysNotify.class)
                            .where(SysNotify_Table.createAt.lessThan(date))

                            .and(OperatorGroup.clause(SysNotify_Table.sender_id.eq(otherId))
                                    .and(SysNotify_Table.group_id.isNull()))

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


}
