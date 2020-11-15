package net.qiujuer.italker.common;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.raizlabs.android.dbflow.structure.ModelAdapter;

import java.io.ObjectInputStream;
import java.lang.reflect.Type;
import java.util.List;

/**
 * @author qiujuer
 */

public class Common {

    /**
     * 一些不可变的永恒的参数
     * 通常用于一些配置
     */
    public static final Gson gson = new GsonBuilder()
            // 设置时间格式
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")
            // 设置一个过滤器，数据库级别的Model不进行Json转换
            .setExclusionStrategies(new DBFlowExclusionStrategy())
            .create();


    public interface Constance {
        // 手机号的正则,11位手机号
        String REGEX_MOBILE = "[1][3,4,5,7,8][0-9]{9}$";

        // 基础的网络请求地址
        //String API_URL = "http://192.168.1.104:8080/api/";
        String API_URL = "http://10.162.143.59:8080/api/";

    }



    public static class DBFlowExclusionStrategy implements ExclusionStrategy {
        @Override
        public boolean shouldSkipField(FieldAttributes f) {
            // 被跳过的字段
            // 只要是属于DBFlow数据的
            return f.getDeclaredClass().equals(ModelAdapter.class);
        }

        @Override
        public boolean shouldSkipClass(Class<?> clazz) {
            // 别跳过的Class
            return false;
        }
    }



    // Session类型, 判断Session是单聊还是群聊
    public static final int RECEIVER_TYPE_NONE = 1;             //单聊
    public static final int RECEIVER_TYPE_GROUP = 2;            // 群聊
    public static final int NON_STATE_PUSH = 3;                 //无状态推送, 系统通知

    //任何推送类型都不是
    public static final int NON_PushType = -1024;
    // 最大的上传图片大小860kb
    public static long MAX_UPLOAD_IMAGE_LENGTH = 860 * 1024;


    //针对DbHelper.updateSession, Message / SysNotify ..增加, (新到达), 更新Session.modifyAt, 增加Session.unread, Session.Message / Session.SysNotify
    public static int pushed_Add = 1;
    //针对DbHelper.updateSession, Message / SysNotify ..减少, (删除 / 撤回) 更新Session.modifyAt,Session.Message / Session.SysNotify, 不需要减少Session.unread
    public static int pushed_Del = -1;

}
