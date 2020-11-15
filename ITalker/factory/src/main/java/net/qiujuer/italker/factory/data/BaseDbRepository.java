package net.qiujuer.italker.factory.data;

import android.annotation.SuppressLint;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;

import com.raizlabs.android.dbflow.structure.database.transaction.QueryTransaction;

import net.qiujuer.genius.kit.reflect.Reflector;
import net.qiujuer.italker.factory.data.helper.DbHelper;
import net.qiujuer.italker.factory.model.db.BaseDbModel;
import net.qiujuer.italker.utils.CollectionUtil;

import java.lang.reflect.Type;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 基础的数据库仓库
 * 实现对数据库的基本的监听操作
 *
 *  改进: 1. 在某些情况
 *          当Presenter层随着View层(Fragment..)被销毁时, Repo不被销毁, 继续监听,
 *          只有当MainActivity finish 后才进行Repo的同一销毁
 *       2. 一个Repo监听一类数据表, 但是其中的callback(Presenter)可以有多个.
 *
 * @author qiujuer Email:qiujuer@live.cn
 * @version 1.0.0
 */
public abstract class
        BaseDbRepository<Data extends BaseDbModel<Data>>
        implements DbDataSource<Data>,
        DbHelper.ChangedListener<Data>,
        QueryTransaction.QueryResultListCallback<Data> {

    // 和Presenter交互的回调
    private List<SucceedCallback<List<Data>> > callbacks = new LinkedList<>();

    protected final LinkedList<Data> dataList = new LinkedList<>();         // 当前缓存的数据

    private Class<Data> dataClass;                  // 当前范型对应的真实的Class信息
    protected AtomicInteger unread = new AtomicInteger(0);              //针对消息类仓库存储的未读数量

    @Override
    public LinkedList<Data> getDataList() {
        return dataList;
    }

    //拿到监听者的id, 子类复写
    @Override
    public String getId(){
        return "";
    }

    public List<SucceedCallback<List<Data>>> getCallback() {
        return callbacks;
    }

    public int getUnread() {
        return unread.get();
    }

    public void setUnread(int unread) {
        this.unread.set(unread);
    }


    @SuppressWarnings("unchecked")
    public BaseDbRepository() {
        // 拿当前类的范型数组信息
        Type[] types = Reflector.getActualTypeArguments(BaseDbRepository.class, this.getClass());
        dataClass = (Class<Data>) types[0];
    }

    @Override
    public void load(SucceedCallback<List<Data>> callback) {
        //已经在监听回调列表里, 说明不需要再次注册了 -> 直接返回
        if (this.callbacks.contains(callback)) return;
        //在监听回调列表里增加一个
        this.callbacks.add(callback);
        // 进行数据库监听操作
        registerDbChangedListener();
    }


    public void removeCallback( SucceedCallback<List<Data>> callback ){
        //在监听回调列表里移除一个
        this.callbacks.remove(callback);
    }


    @Override
    public void dispose() {
        // 取消监听，销毁数据
        this.callbacks = null;
        DbHelper.removeChangedListener(dataClass, this);
        dataList.clear();
    }


    // 数据库统一通知的地方: 本地数据库 增加／更改时通知到dataList, dataList可以add / update / delete
    @Override
    public void onDataSave(Data... list) {

        boolean isChanged = false;
        // 当数据库数据变更的操作
        for (Data data : list) {
            // 是关注的人，同时不是我自己
            if (isRequired(data)) {
                //dataList的增删改
                if(insertOrUpdateOrDelete(data))
                    isChanged = true;
            }
        }
        // 有数据变更，则进行界面刷新
        if (isChanged)
            notifyDataChange();
    }



    // 数据库统一通知删除的地方, 将dataList中对应项也删除
    @Override
    public void onDataDelete(Data... list) {

        boolean isChanged = false;
        for(Data data: list){
            int index = indexOf(data);
            if( index >= 0 ){
                dataList.remove(index);
                isChanged= true;
            }
        }
        if (isChanged)
            notifyDataChange();
    }



    // DbFlow 框架通知的回调
    @Override
    public void onListQueryResult(QueryTransaction transaction,
                                  @NonNull List<Data> tResult) {
        // 数据库加载数据成功
        if (tResult.size() == 0) {
            dataList.clear();
            notifyDataChange();
            return;
        }

        Data[] data = CollectionUtil.toArray(tResult, dataClass);
        // 回到数据集更新的操作中
        onDataSave(data);
    }


    // 插入或者更新, 没有"删除"的逻辑, 删除逻辑在子类复写, 只有onDataSave调用
    protected boolean insertOrUpdateOrDelete(Data data) {
        int index = indexOf(data);
        if (index >= 0) {
            //内容有改变才替换, 否则不换
            if(!data.isUiContentSame(dataList.get(index))) {
                replace(index, data);
                return true;
            }
            return false;
        } else {
            insert(data);
            return true;
        }
    }


    // 更新操作，更新某个坐标下的数据
    protected void replace(int index, Data data) {
        dataList.remove(index);
        dataList.add(index, data);
    }

    // 添加方法, data按可以排序的Date属性插入, 由子类复写
    protected abstract void insert(Data data);


    // 查询一个数据是否在当前的缓存数据中，如果在则返回坐标
    protected int indexOf(Data newData) {
        int index = -1;
        for (Data data : dataList) {
            index++;
            //首先判断运行时类是否一样, 其次进行自定义的isSame
            if (data.getClass().equals(newData.getClass()) && data.isSame(newData)) {
                return index;
            }
        }
        return -1;
    }


    /**
     * 检查一个User是否是我需要关注的数据
     *
     * @param data Data
     * @return True是我关注的数据
     */
    protected abstract boolean isRequired(Data data);

    /**
     * 添加数据库的监听操作
     */
    protected void registerDbChangedListener() {
        DbHelper.addChangedListener(dataClass, this);
    }

    // 通知界面刷新的方法, 对所有关注dataList变化的回调通知刷新
    //当callback被移除后, 界面端无法刷新
    @SuppressLint("NewApi")
    protected void notifyDataChange() {

        final List<SucceedCallback<List<Data>>> callbacks = this.callbacks;
        callbacks.stream().forEach(callback -> {
            if(callback != null)
                callback.onDataLoaded(dataList);
        });

    }


    public void loadBefore(Date date) {}

    public void loadAfter(Date date) {}


}
