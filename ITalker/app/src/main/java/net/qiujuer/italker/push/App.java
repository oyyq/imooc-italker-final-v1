package net.qiujuer.italker.push;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import com.igexin.sdk.PushManager;
import net.qiujuer.italker.common.app.Application;
import net.qiujuer.italker.factory.Factory;
import net.qiujuer.italker.factory.model.api.RspModel;
import net.qiujuer.italker.factory.model.card.Card;
import net.qiujuer.italker.factory.net.Network;
import net.qiujuer.italker.factory.persistence.Account;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * @author qiujuer Email:qiujuer@live.cn
 * @version 1.0.0
 * Q模式没有息屏
 */
public class App extends Application
implements android.app.Application.ActivityLifecycleCallbacks {

    private List<Activity> activities = new ArrayList<>();

    private int countActivity = 0;       //在onStart - onStop之间的Activity的数量

    //除LaunchActivity, UserActivity以外的**Activity中任何一个, 回调onStart的时间点, 取最早
    private Date inActivePoint;
    //除LaunchActivity, USerActivity以外的所有**Activity进入onStop时间点, 取最晚
    private Date ActivePoint;

    //每个Activity回调onStart的时间
    private Map<String, Date> ActivityStarts = new HashMap<>();
    //每个Activity回调onStop方法的时间
    private Map<String, Date> ActivityStops = new HashMap<>();


    private static BroadcastReceiver br = new MessageReceiver();
    private static IntentFilter filter = new IntentFilter();
    private boolean unRegisterBr = true;

    //APP进程启动时调用一次onCreate()方法, 仅在首次启动，或者强行停止app，杀死进程才会调用，
    //而普通的按返回键或者调用Activity的finish()方法之后重新进入不会调用Application.onCreate()
    @Override
    public void onCreate() {
        super.onCreate();

        filter.addAction("com.igexin.sdk.action.iC6jpvfDCqAIxBowlQhF41");
        //注册Activity生命周期监听器
        registerActivityLifecycleCallbacks(this);
        // 调用Factory进行初始化
        Factory.setup();
        // 推送进行初始化
        PushManager.getInstance().initialize(this);

        if(unRegisterBr) {
            this.registerReceiver(br, filter, null, null);
            unRegisterBr = false;
        }

    }


    // 退出所有
    public void finishAll(){
        for (Activity activity : activities) {
            activity.finish();
        }

        showAccountView(this);
    }


    private void showAccountView(Context context) {
        // 登录界面的显示 AccountActivity
    }


    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {

    }

    @Override
    public void onActivityStarted(Activity activity) {
        countActivity++;
        activities.add(activity);
        Date date = new Date();
        ActivityStarts.put(activity.getClass().getSimpleName(), date);
        //只要有Activity进入活动状态就注册BoradcastReceiver
        if(unRegisterBr) {
            //"1000"是什么权限? 解答http://coding.imooc.com/learn/questiondetail/209771.html
            this.registerReceiver(br, filter, null, null);
            unRegisterBr = false;
        }

        //afterRegister 的值在外部手动设置
        if(instance.afterRegister){
            if (ActivePoint == null) {
                ActivePoint = date;

                inActivePoint = instance.getLastDisTime("inActivePoint");
                if (inActivePoint != null) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
                    // 从服务器端拉取所有推送(inActivePoint -- ActivePoint 时间段内)过来, 有两种做法: 1. 直接拉取, 2. 服务器重推送一遍
                    //这里选择 2
                    String earlierStr = sdf.format(inActivePoint);
                    String laterStr = sdf.format(ActivePoint);

                    Log.e("Background Time", earlierStr);
                    Log.e("Foreground Time", laterStr);

                    Network.remote().getPushedCards(earlierStr, laterStr, Account.getUserId())
                            .enqueue(new Callback<RspModel<List<Card>>>() {
                                @Override
                                public void onResponse(Call<RspModel<List<Card>>> call, Response<RspModel<List<Card>>> response) {
                                    RspModel<List<Card>> rspModel = response.body();
                                    if(rspModel != null) {
                                        if(rspModel.success()) {
                                            //请求重发成功了, 显示一个Toast
                                            Application.showToast("收取中");
                                        }else {
                                            Application.showToast("参数不合法");
                                        }
                                    }else{
                                        Application.showToast("连接异常");
                                    }
                                }

                                @Override
                                public void onFailure(Call<RspModel<List<Card>>> call, Throwable t) {
                                    //请求重发失败了, 显示一个Toast
                                    Application.showToast("连接断开");
                                }
                            });
                }
            }
        }

    }



    @Override
    public void onActivityResumed(Activity activity) {

    }

    @Override
    public void onActivityPaused(Activity activity) {

    }

    @Override
    public void onActivityStopped(Activity activity) {
        countActivity--;
        activities.remove(activity);
        ActivityStops.put(activity.getClass().getSimpleName(), new Date());

        if(countActivity <= 0){
            //当countActivity == 0时, 说明所有Activity都不活跃了, APP在后台或者退出了

            ActivePoint = null;
            instance.saveViewDisTime("inActivePoint");

            //当所有Activity都不活动了就注销BroadcastReceiver
            if(!unRegisterBr) {
                this.unregisterReceiver(br);
                unRegisterBr = true;
            }
        }

    }


    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {


    }


    @Override
    public void onActivityDestroyed(Activity activity) {

    }



}
