package net.qiujuer.italker.factory.data.helper;

import android.annotation.SuppressLint;
import android.os.Build;
import android.text.TextUtils;
import com.raizlabs.android.dbflow.config.DatabaseDefinition;
import com.raizlabs.android.dbflow.config.FlowManager;
import com.raizlabs.android.dbflow.sql.language.SQLite;
import com.raizlabs.android.dbflow.structure.BaseModel;
import com.raizlabs.android.dbflow.structure.ModelAdapter;
import com.raizlabs.android.dbflow.structure.database.DatabaseWrapper;
import com.raizlabs.android.dbflow.structure.database.transaction.ITransaction;
import net.qiujuer.italker.common.Common;
import net.qiujuer.italker.factory.data.BaseDbRepository;
import net.qiujuer.italker.factory.data.Pushed.PushedGroupRepository;
import net.qiujuer.italker.factory.data.Pushed.PushedUserRepository;
import net.qiujuer.italker.factory.model.db.AppDatabase;
import net.qiujuer.italker.factory.model.db.BaseDbModel;
import net.qiujuer.italker.factory.model.db.GetPushed;
import net.qiujuer.italker.factory.model.db.GetPushedImpl;
import net.qiujuer.italker.factory.model.db.Group;
import net.qiujuer.italker.factory.model.db.GroupMember;
import net.qiujuer.italker.factory.model.db.Group_Table;
import net.qiujuer.italker.factory.model.db.Session;
import net.qiujuer.italker.factory.model.db.User;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * 数据库的辅助工具类
 * 辅助完成：增删改
 * todo MySql数据库事务的隔离级别的锁实现机制:https://tech.meituan.com/2014/08/20/innodb-lock.html
 * @author qiujuer Email:qiujuer@live.cn
 * @version 1.0.0
 */
public class DbHelper {
    private static final DbHelper instance;

    static {
        instance = new DbHelper();
    }

    public static DbHelper getInstance(){
        return instance;
    }

    private DbHelper() { }

    /**
     * 观察者的集合
     * Class<?>： 观察的 表
     * Map<String, ChangedListener>：每一个表对应的观察者有很多, 同一个表的每个 观察者 有序号String id
     */
    private static final Map<Class<?>, Map<String, ChangedListener>> changedListeners = new HashMap<>();
    //private final Map<Class<?>, Set<ChangedListener>> changedListeners = new HashMap<>();


    public static Map<Class<?>, Map<String, ChangedListener>> getChangedListeners() {
        return changedListeners;
    }


    /**
     * 从所有的监听者中，获取某一个表的所有监听者
     *
     * @param modelClass 表对应的Class信息
     * @param <Model>    范型
     * @return Set<ChangedListener>
     */
    private static <Model extends BaseModel>  Map<String, ChangedListener> getListeners(Class<Model> modelClass) {

        if (changedListeners.containsKey(modelClass)) {
            return changedListeners.get(modelClass);
        }
        return null;
    }



    /**
     * 添加一个监听
     * @param tClass   对某个表关注
     * @param listener 监听者
     * @param <Model>  表的范型
     */
    public static <Model extends BaseModel> void addChangedListener(final Class<Model> tClass,
                                                                    ChangedListener<Model> listener) {

        Map<String, ChangedListener> changedListeners = getListeners(tClass);
        if (changedListeners == null) {
            // 初始化某一类型的容器
            changedListeners = new HashMap<>();
            // 添加到中的Map
            DbHelper.changedListeners.put(tClass, changedListeners);
        }

        ChangedListener originListener = changedListeners.get(listener.getId());       //listener: **Repository实例
        //同一个对象, 监听器已经在列表中
        if( originListener != null && originListener == listener ) return;

        changedListeners.put(listener.getId(), listener);

    }


    /**
     * 删除某一个表的某一个监听器
     *
     * @param tClass   表
     * @param listener 监听器
     * @param <Model>  表的范型
     */
    public static <Model extends BaseModel> void removeChangedListener(final Class<Model> tClass,
                                                                       ChangedListener<Model> listener) {

        Map<String, ChangedListener> changedListeners = getListeners(tClass);
        if (changedListeners == null) {
            // 容器本身为null
            return;
        }
        // 从容器中删除你这个监听者
        changedListeners.remove(listener.getId());

    }





    /**
     * 新增或者修改的统一方法
     * todo 数据库事务并发机制: 事务是一个不可分割操作序列，也是数据库并发控制的基本单位，其执行的结果必须使数据库从一种一致性状态变到另一种一致性状态.
     *      原子性: 所有操作要么全部成功，要么全部失败回滚, 一致性: 事务执行的结果必须使数据库从一种一致性状态变到另一种一致性状态
     *      隔离性: 与事务并发直接相关, 并发执行的事务不能相互影响, 持久性: 一个事务一旦被提交了, 对数据库中的数据的改变就是永久性的
     *      MySql的默认隔离级别是:  Repeatable read (查阅脏读, 不可重复读(简单理解读时不能写), 幻读(写时不能读)), 该隔离级别对我们来说足够
     *
     *      目前没有显式设定事务隔离级别的时候, 可以通过synchronized关键字来避免多个事务并发的问题, 但显然开销比设定隔离级别大
     *
     * @param tClass  传递一个Class信息
     * @param models  这个Class对应的实例的数组
     * @param <Model> 这个实例的范型，限定条件是BaseModel
     */
    public static <Model extends BaseModel> void save(final Class<Model> tClass,
                                                      final Model... models) {
        if (models == null || models.length == 0)
            return;

        // 当前数据库的一个管理者
        DatabaseDefinition definition = FlowManager.getDatabase(AppDatabase.class);
        // 提交一个事务
        definition.beginTransactionAsync(new ITransaction() {
            @Override
            public void execute(DatabaseWrapper databaseWrapper) {
                // 执行
                ModelAdapter<Model> adapter = FlowManager.getModelAdapter(tClass);
                // 保存
                adapter.saveAll(Arrays.asList(models));
                // 唤起通知
                instance.notifySave(tClass, models);
            }
        }).build().execute();         //异步执行
    }





    /**
     * 进行删除数据库的统一封装方法
     *
     * @param tClass  传递一个Class信息
     * @param models  这个Class对应的实例的数组
     * @param <Model> 这个实例的范型，限定条件是BaseModel
     */
    public static <Model extends BaseModel> void delete(final Class<Model> tClass,
                                                        final Model... models) {
        if (models == null || models.length == 0)
            return;

        // 当前数据库的一个管理者
        DatabaseDefinition definition = FlowManager.getDatabase(AppDatabase.class);
        // 提交一个事物
        definition.beginTransactionAsync(new ITransaction() {
            @Override
            public void execute(DatabaseWrapper databaseWrapper) {
                // 执行
                ModelAdapter<Model> adapter = FlowManager.getModelAdapter(tClass);
                // 删除
                adapter.deleteAll(Arrays.asList(models));
                // 唤起通知
                instance.notifyDelete(tClass, models);
            }
        }).build().execute();
    }




    /**
     * 更新数据表字段
     * @param tClass
     * @param models
     * @param <Model>
     */
    public static <Model extends BaseModel> void update(final Class<Model> tClass,
                                                        final Model... models) {

        if (models == null || models.length == 0)
            return;

        DatabaseDefinition definition = FlowManager.getDatabase(AppDatabase.class);
        definition.beginTransactionAsync(new ITransaction() {
              @Override
              public void execute(DatabaseWrapper databaseWrapper) {

                   ModelAdapter<Model> adapter = FlowManager.getModelAdapter(tClass);
                   //更新
                   adapter.updateAll(Arrays.asList( models));
                   //唤起通知
                   instance.notifySave(tClass, models);
              }
        }).build().execute();
    }





    /**
     * 进行通知调用
     *
     * @param tClass  通知的类型
     * @param models  通知的Model数组
     * @param <Model> 这个实例的范型，限定条件是BaseModel
     */
    public synchronized final <Model extends BaseModel> void notifySave(final Class<Model> tClass,
                                                                        final Model... models) {

        Set<Session.Identify> identifies = null;
        //群成员变更
//        if (GroupMember.class.equals(tClass)) {
//            updateGroup((GroupMember[]) models);
//        }//GetPushed 是tClass父类或者父接口, clazz.isAssignableFrom(obj.getClass()) == clazz.isInstance(obj)
//      else
        if (GetPushedImpl.class.isAssignableFrom(tClass)) {     //Message.class or SysNotify.class
            //消息变化，通知会话列表更新
            identifies = updateSession(Common.pushed_Add, (GetPushedImpl[])models);
        } else if ( User.class.equals(tClass) ){
            //找到与这个好友以前是否有Session
            findSession(User.class, (BaseDbModel[]) models);
        } else if (Group.class.equals(tClass)){
            //找到与这个群以前是否有Session, 也就是聊天记录
            findSession(Group.class, (BaseDbModel[]) models);
        }



        //只注册消息类监听器
        if(identifies != null) {

            for(Session.Identify identify: identifies ) {
                Map<String, ChangedListener> pushedlisteners = changedListeners.get(GetPushedImpl.class);
                if( pushedlisteners != null && pushedlisteners.get(identify.id) != null ) continue;

                ChangedListener repo =
                        identify.type == Common.RECEIVER_TYPE_NONE ? new PushedUserRepository(identify.id) :
                        (identify.type == Common.RECEIVER_TYPE_GROUP ? new PushedGroupRepository(identify.id) : null);

                if (repo != null)
                    addChangedListener(GetPushedImpl.class, repo);
            }

        }



        // 找监听器
        final Map<String, ChangedListener> listeners = GetPushedImpl.class.isAssignableFrom(tClass) ?
                getListeners(GetPushedImpl.class) : getListeners(tClass);

        if (listeners != null && listeners.size() > 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                //stream
                listeners.entrySet().stream()
                        .forEach(entry -> entry.getValue().onDataSave(models));
            }else {
                //for-loop
                Iterator<Map.Entry<String, ChangedListener>> iterator = listeners.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<String,  ChangedListener> entry = iterator.next();
                    entry.getValue().onDataSave(models);
                }
            }
        }

    }



    /**
     * 进行通知调用
     *
     * @param tClass  通知的类型
     * @param models  通知的Model数组
     * @param <Model> 这个实例的范型，限定条件是BaseModel
     */
    @SuppressWarnings("unchecked")
    public synchronized final <Model extends BaseModel> void notifyDelete(final Class<Model> tClass,
                                                              final Model... models) {

        if ( GetPushedImpl.class.isAssignableFrom(tClass) ) {
              updateSession(Common.pushed_Del, (GetPushedImpl[])models);
        } else if ( User.class.equals(tClass) ){
            //删除好友, 隐藏掉与他会话的session
            hideSession(User.class,  (BaseDbModel[]) models);
        } else if( Group.class.equals(tClass) ){
            // 退群, 隐藏与群聊天的session
            hideSession(Group.class,  (BaseDbModel[]) models);
        }



        // 找监听器
        final Map<String, ChangedListener> listeners = GetPushedImpl.class.isAssignableFrom(tClass) ?
                getListeners(GetPushedImpl.class) : getListeners(tClass);

        if (listeners != null && listeners.size() > 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                //stream
                listeners.entrySet().stream()
                        .forEach(entry -> entry.getValue().onDataDelete(models));
            }else {
                //for-loop
                Iterator<Map.Entry<String, ChangedListener>> iterator = listeners.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<String,  ChangedListener> entry = iterator.next();
                    entry.getValue().onDataDelete(models);
                }
            }
        }

    }


    /**
     * 从成员中找出成员对应的群，并对群进行成员信息修改
     * @param members 群成员列表
     */
    public static void updateGroup(boolean updateGroupTime, GroupMember... members) {
        // 不重复集合
        final Set<String> groupIds = new HashSet<>();
        for (GroupMember member : members) {
            // 添加群Id
            groupIds.add(member.getGroup().getId());
        }

        // 异步的数据库查询，并异步的发起二次通知
        DatabaseDefinition definition = FlowManager.getDatabase(AppDatabase.class);
        definition.beginTransactionAsync(new ITransaction() {
            @Override
            public void execute(DatabaseWrapper databaseWrapper) {
                // 找到需要通知的群
                List<Group> groups = SQLite.select()
                        .from(Group.class)
                        .where(Group_Table.id.in(groupIds))
                        .queryList();

                if(updateGroupTime) {
                    @SuppressLint({"NewApi", "LocalSuppress"})
                    List<Group> newer = groups.stream()
                            .map(group -> {
                                group.setModifyAt(new Date());
                                return group;
                            })
                            .collect(Collectors.toList());

                    //重新保存数据库并唤起通知
                    ModelAdapter<Group> adapter = FlowManager.getModelAdapter(Group.class);
                    adapter.saveAll(newer);
                    instance.notifySave(Group.class, newer.toArray(new Group[0]));
                } else {
                    instance.notifySave(Group.class, groups.toArray(new Group[0]));
                }
            }
        }).build().execute();
    }





    /**
     * 从消息列表中，筛选出对应的会话，并对会话进行更新
     * 同步方法
     * @param pushes 推送列表, 消息或系统通知, 所有元素的类型相同
     */
    private Set<Session.Identify> updateSession(int AddOrDel, GetPushed... pushes) {

        if(pushes == null || pushes.length == 0) return null;
        // Set无序唯一, Identify对应Session的唯一性
        final Set<Session.Identify> identifies = new HashSet<>();


        Map<String, List<GetPushed>> pushedToSessions = new HashMap<>();
        for (GetPushed pushed : pushes) {
            Session.Identify identify = Session.createSessionIdentify(pushed);
            //无效pushed,  忽略
            if( identify == null || TextUtils.isEmpty(identify.id) || identifies.contains(identify) )
                continue;

            identifies.add(identify);
            List<GetPushed> IdPusheds = pushedToSessions.get(identify.id);
            if(IdPusheds == null){
                IdPusheds = new LinkedList<>();
                pushedToSessions.put(identify.id, IdPusheds);
            }

            //每个Session的信息按照时间顺序插入IdPusheds
            if(IdPusheds.size() == 0) { IdPusheds.add(pushed);}
            else {
                final int len = IdPusheds.size();
                for (int i = 0; i <= len; i++) {
                    if(i == len){
                        IdPusheds.add(pushed);
                    }else {
                        if (pushed.getCreateAt().compareTo(IdPusheds.get(i).getCreateAt()) < 0) {
                            IdPusheds.add(i, pushed);
                            break;
                        }
                    }
                }
            }
        }

        Class<? extends GetPushed> tClass = pushes[0].getClass();
        if(AddOrDel == Common.pushed_Add){
            //利用数据库事务, 更新到Session上去
            SessionTransaction(identifies, pushedToSessions, tClass);
        }else if(AddOrDel == Common.pushed_Del){
            // 删除消息还没处理
            // 消息类被删除 Session.modifyAt以及Content, Message / SysNotify要改变
        }

        return identifies;

    }



//    interface Query {
//        void query(Session session);
//    }


    //Session的创建和存储
    private void SessionTransaction(final Set<Session.Identify> identifies,
                                    Map<String, List<GetPushed>> pushedToSessions,
                                    final Class<? extends GetPushed> tClass){

        // 异步的数据库查询，并异步的发起二次通知
        DatabaseDefinition definition = FlowManager.getDatabase(AppDatabase.class);
        definition.beginTransactionAsync(new ITransaction() {

            @Override
            public void execute(DatabaseWrapper databaseWrapper) {

                ModelAdapter<Session> adapter = FlowManager.getModelAdapter(Session.class);
                Session[] sessions = new Session[identifies.size()];

                int index = 0;
                for (Session.Identify identify : identifies) {
                    if(TextUtils.isEmpty(identify.id)) continue;

                    Session session = SessionHelper.findFromLocal(identify.id);
                    if (session == null) {
                        // 第一次聊天，创建一个你和对方的一个会话
                        session = new Session(identify);
                    }


                    if(identify.type == Common.RECEIVER_TYPE_NONE || identify.type == Common.RECEIVER_TYPE_GROUP) {
                        // 判断"我"当前是否在聊天页面 ?
                        Map<String, ChangedListener> listenerMap = changedListeners.get(GetPushedImpl.class);
                        if (listenerMap != null) {
                            ChangedListener listener = listenerMap.get( identify.id );
                            if (listener != null)
                                if (((BaseDbRepository) listener).getCallback().size() > 0)
                                    //"我"当前在聊天页面
                                    session.setNeedUpdateUnReadCount(false);
                        }
                    }


                    List<GetPushed> IdPusheds = pushedToSessions.get(identify.id);
                    //final Class<? extends GetPushed> tClass = IdPusheds.get(0).getClass();
                    session.refreshToNow(tClass, IdPusheds);
                    // 数据存储, 连续多次save只会保存一条记录, 和update一样
                    adapter.save(session);
                    // 添加到集合
                    sessions[index++] = session;
                }
                // 调用直接进行一次通知分发
                instance.notifySave(Session.class, sessions);

            }
        }).build().execute();

    }





    /**
     * 添加好友或者加入群后, 要查找之前与 人 或 群  在本地 是否有历史聊天记录, 是否有Session
     * @param tClass   User.class || Group.class
     * @param models
     * @param <Model>
     */
    private <Model extends BaseDbModel> void findSession(Class<Model> tClass, BaseDbModel[] models){

        if(models == null || models.length == 0) return;
        Set<Session> sessions = new HashSet<>();

        for(BaseDbModel model: models){
            String id = model.getId();
            if(TextUtils.isEmpty(id)) continue;
            Session session = SessionHelper.findFromLocal( id );
            if(session != null) {
                sessions.add(session);
            }
        }
        //session重新放到RecyclerView中去
        notifySave(Session.class, sessions.toArray(new Session[0]));

    }


    /**
     * 删除好友或退群后, 隐藏与之对话的Session
     * @param <Model>
     * @param tClass  User.class || Group.class || session.class,
     * @param models
     */
    private <Model extends BaseDbModel> void hideSession(Class<Model> tClass, BaseDbModel[] models){

        if(models == null || models.length == 0) return;
        Set<Session> sessions = new HashSet<>();


        for(BaseDbModel model: models){
            Session session;
            String id = model.getId();
            if(TextUtils.isEmpty(id)) continue;
            session = SessionHelper.findFromLocal(id);
            if(session != null)
                sessions.add(session);
        }

        //不把本地的Session记录给删除, 只是隐藏, 一旦重新加回好友, 还要刷新出来
        notifyDelete(Session.class,sessions.toArray(new Session[0]));

    }



    /**
     * 通知监听器
     */
    @SuppressWarnings({"unused", "unchecked"})
    public interface ChangedListener<Data extends BaseModel> {
        void onDataSave(Data... list);

        void onDataDelete(Data... list);

        String getId();
    }


}
