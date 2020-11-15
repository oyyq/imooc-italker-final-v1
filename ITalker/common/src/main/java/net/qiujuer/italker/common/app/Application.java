package net.qiujuer.italker.common.app;

import android.content.SharedPreferences;
import android.os.SystemClock;
import android.support.annotation.StringRes;
import android.widget.Toast;
import net.qiujuer.genius.kit.handler.Run;
import net.qiujuer.genius.kit.handler.runable.Action;
import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author qiujuer Email:qiujuer@live.cn
 * @version 1.0.
 */
public class Application extends android.app.Application {

    protected static Application instance;
    protected static boolean afterRegister = false;          //是否注册完毕

    public static boolean isAfterRegister() {
        return instance.afterRegister;
    }

    public static void setAfterRegister(boolean afterRegister) {
        Application.instance.afterRegister = afterRegister;
    }

    /**
     * 外部获取单例
     * @return App实例
     */
    public static Application getInstance() {
        return instance;
    }


    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }


    public void saveViewDisTime(String clzname){
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
        String dateStr =  sdf.format(new Date());
        SharedPreferences sp = getSharedPreferences(clzname, MODE_PRIVATE);
        // 存储数据
        sp.edit().putString("ViewDisappearT", dateStr)
                .apply();
    }


    //拿到上次界面消失的时间点
    public Date getLastDisTime(String clzname){
        SharedPreferences sp = getSharedPreferences(clzname, MODE_PRIVATE);
        String dateStr = sp.getString("ViewDisappearT", "");
        if(dateStr.equals("")) return null;

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
        Date lastDisTime = null;
        try {
            lastDisTime = sdf.parse(dateStr);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return lastDisTime;
    }


    /**
     * 获取缓存文件夹地址
     *
     * @return 当前APP的缓存文件夹地址
     */
    public static File getCacheDirFile() {
        return instance.getCacheDir();
    }

    /**
     * 获取头像的临时存储文件地址
     *
     * @return 临时文件
     */
    public static File getPortraitTmpFile() {
        // 得到头像目录的缓存地址
        File dir = new File(getCacheDirFile(), "portrait");
        // 创建所有的对应的文件夹
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();

        // 删除旧的一些缓存为文件
        File[] files = dir.listFiles();
        if (files != null && files.length > 0) {
            for (File file : files) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }

        // 返回一个当前时间戳的目录文件地址
        File path = new File(dir, SystemClock.uptimeMillis() + ".jpg");
        return path.getAbsoluteFile();
    }




    /**
     * 获取声音文件的本地地址
     *
     * @param isTmp 是否是缓存文件， True，每次返回的文件地址是一样的
     * @return 录音文件的地址
     */
    public static File getAudioTmpFile(boolean isTmp) {
        File dir = new File(getCacheDirFile(), "audio");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File[] files = dir.listFiles();
        if (files != null && files.length > 0) {
            for (File file : files) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }

        // aar
        File path = new File(getCacheDirFile(), isTmp ? "tmp.mp3" : SystemClock.uptimeMillis() + ".mp3");
        return path.getAbsoluteFile();
    }




    /**
     * 显示一个Toast
     *
     * @param msg 字符串
     */
    public static void showToast(final String msg) {
        // Toast 只能在主线程中显示，所以需要进行线程转换，
        // 保证一定是在主线程进行的show操作
        Run.onUiAsync(new Action() {
            @Override
            public void call() {
                // 这里进行回调的时候一定就是主线程状态了
                Toast.makeText(instance, msg, Toast.LENGTH_SHORT).show();
            }
        });

    }



    /**
     * 显示一个Toast
     *
     * @param msgId 传递的是字符串的资源
     */
    public static void showToast(@StringRes int msgId) {
        showToast(instance.getString(msgId));
    }



}
