package net.qiujuer.italker.factory;

import android.support.annotation.StringRes;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.raizlabs.android.dbflow.config.FlowConfig;
import com.raizlabs.android.dbflow.config.FlowManager;

import net.qiujuer.italker.common.Common;
import net.qiujuer.italker.common.app.Application;
import net.qiujuer.italker.factory.data.DataSource;
import net.qiujuer.italker.factory.data.Notify.NotifyCenter;
import net.qiujuer.italker.factory.data.Notify.NotifyDispatcher;
import net.qiujuer.italker.factory.data.Pushed.PushedCenter;
import net.qiujuer.italker.factory.data.Pushed.PushedDispatcher;
import net.qiujuer.italker.factory.data.group.GroupCenter;
import net.qiujuer.italker.factory.data.group.GroupDispatcher;
import net.qiujuer.italker.factory.data.helper.NotifyHelper;
import net.qiujuer.italker.factory.data.message.MessageCenter;
import net.qiujuer.italker.factory.data.message.MessageDispatcher;
import net.qiujuer.italker.factory.data.user.UserCenter;
import net.qiujuer.italker.factory.data.user.UserDispatcher;
import net.qiujuer.italker.factory.model.api.PushModel;
import net.qiujuer.italker.factory.model.api.RspModel;
import net.qiujuer.italker.factory.model.card.ApplyCard;
import net.qiujuer.italker.factory.model.card.GroupCard;
import net.qiujuer.italker.factory.model.card.GroupMemberCard;
import net.qiujuer.italker.factory.model.card.MessageCard;
import net.qiujuer.italker.factory.model.card.SysNotifyCard;
import net.qiujuer.italker.factory.model.card.UserCard;
import net.qiujuer.italker.factory.model.db.User;
import net.qiujuer.italker.factory.persistence.Account;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * @author qiujuer Email:qiujuer@live.cn
 * @version 1.0.0
 */
public class Factory {
    private static final String TAG = Factory.class.getSimpleName();
    // 单例模式
    private static final Factory instance;
    // 全局的线程池
    private final Executor executor;
    // 全局的Gson
    private final Gson gson;


    static {
        instance = new Factory();
    }

    private Factory() {
        // 新建一个4个线程的线程池
        executor = Executors.newFixedThreadPool(4);
        gson = Common.gson;
    }

    /**
     * Factory 中的初始化
     */
    public static void setup() {
        // 初始化数据库
        FlowManager.init(new FlowConfig.Builder(app())
                .openDatabasesOnInit(true) // 数据库初始化的时候就开始打开
                .build());

        // 持久化的数据进行初始化
        Account.load(app());
    }

    /**
     * 返回全局的Application
     *
     * @return Application
     */
    public static Application app() {
        return Application.getInstance();
    }


    /**
     * 异步运行的方法
     *
     * @param runnable Runnable
     */
    public static void runOnAsync(Runnable runnable) {
        // 拿到单例，拿到线程池，然后异步执行
        instance.executor.execute(runnable);
    }

    /**
     * 返回一个全局的Gson，在这可以进行Gson的一些全局的初始化
     *
     * @return Gson
     */
    public static Gson getGson() {
        return instance.gson;
    }


    /**
     * 进行错误Code的解析，
     * 把网络返回的Code值进行统一的规划并返回为一个String资源
     *
     * @param model    RspModel
     * @param callback DataSource.FailedCallback 用于返回一个错误的资源Id
     */
    public static void decodeRspCode(RspModel model, DataSource.FailedCallback callback) {
        if (model == null)
            return;

        // 进行Code区分
        switch (model.getCode()) {
            case RspModel.SUCCEED:
                return;
            case RspModel.ERROR_SERVICE:
                decodeRspCode(R.string.data_rsp_error_service, callback);
                break;
            case RspModel.ERROR_NOT_FOUND_USER:
                decodeRspCode(R.string.data_rsp_error_not_found_user, callback);
                break;
            case RspModel.ERROR_NOT_FOUND_GROUP:
                decodeRspCode(R.string.data_rsp_error_not_found_group, callback);
                break;
            case RspModel.ERROR_NOT_FOUND_GROUP_MEMBER:
                decodeRspCode(R.string.data_rsp_error_not_found_group_member, callback);
                break;
            case RspModel.ERROR_CREATE_USER:
                decodeRspCode(R.string.data_rsp_error_create_user, callback);
                break;
            case RspModel.ERROR_CREATE_GROUP:
                decodeRspCode(R.string.data_rsp_error_create_group, callback);
                break;
            case RspModel.ERROR_CREATE_MESSAGE:
                decodeRspCode(R.string.data_rsp_error_create_message, callback);
                break;
            case RspModel.ERROR_PARAMETERS:
                decodeRspCode(R.string.data_rsp_error_parameters, callback);
                break;
            case RspModel.ERROR_PARAMETERS_EXIST_ACCOUNT:
                decodeRspCode(R.string.data_rsp_error_parameters_exist_account, callback);
                break;
            case RspModel.ERROR_PARAMETERS_EXIST_NAME:
                decodeRspCode(R.string.data_rsp_error_parameters_exist_name, callback);
                break;
            case RspModel.ERROR_ACCOUNT_TOKEN:
                Application.showToast(R.string.data_rsp_error_account_token);
                instance.logout();
                break;
            case RspModel.ERROR_ACCOUNT_LOGIN:
                decodeRspCode(R.string.data_rsp_error_account_login, callback);
                break;
            case RspModel.ERROR_ACCOUNT_REGISTER:
                decodeRspCode(R.string.data_rsp_error_account_register, callback);
                break;
            case RspModel.ERROR_ACCOUNT_NO_PERMISSION:
                decodeRspCode(R.string.data_rsp_error_account_no_permission, callback);
                break;
            case RspModel.ERROR_UNKNOWN:
            default:
                decodeRspCode(R.string.data_rsp_error_unknown, callback);
                break;
        }
    }

    private static void decodeRspCode(@StringRes final int resId,
                                      final DataSource.FailedCallback callback) {
        if (callback != null)
            callback.onDataNotAvailable(resId);
    }


    /**
     * 收到账户退出的消息需要进行账户退出重新登录
     */
    private void logout() {

    }


    /**
     * 处理推送来的消息
     *
     * @param str 消息
     */
    public static void dispatchPush(String str) {
        // 首先检查登录状态
        if (!Account.isLogin())
            return;

        PushModel model = PushModel.decode(str);
        if (model == null)
            return;

        // 对推送集合进行遍历
        for (PushModel.Entity entity : model.getEntities()) {
            Log.e(TAG, "dispatchPush-Entity:" + entity.toString());

            switch (entity.type) {
                case PushModel.ENTITY_TYPE_LOGOUT:
                    instance.logout();
                    return;

                case PushModel.ENTITY_TYPE_MESSAGE: {
                    // 普通消息
                    MessageCard card = getGson().fromJson(entity.content, MessageCard.class);
                    getMessageCenter().dispatch(card);
                    break;
                }

                //关注
                case PushModel.ENTITY_TYPE_ADD_FRIEND: {
                    UserCard card = getGson().fromJson(entity.content, UserCard.class);
                    getUserCenter().dispatch(UserCenter.USER_FOLLOW, card);
                    break;
                }

                //解关注
                case PushModel.ENTITY_TYPE_DEL_FRIEND: {
                    UserCard card = getGson().fromJson(entity.content, UserCard.class);
                    getUserCenter().dispatch(UserCenter.USRE_UNFOLLOW, card);
                    break;
                }

                //我的群列表+1/ -1
                case PushModel.ENTITY_TYPE_ADD_GROUP:
                case PushModel.ENTITY_TYPE_OUT_GROUP: {
                    // 添加群
                    GroupCard card = getGson().fromJson(entity.content, GroupCard.class);
                    getGroupCenter().dispatch(entity.type, card);
                    break;
                }

                /*
                //"我"被加群的系统通知
                case PushModel.ENTITY_TYPE_NOTI_ADD_GROUP:
                //通知有其他人加入了群
                case PushModel.ENTITY_TYPE_NOTI_ADD_GROUP_MEMBERS:
                //通知有其他人被移除了群
                case PushModel.ENTITY_TYPE_NOTI_DEL_GROUP_MEMBERS:
                case PushModel.ENTITY_TYPE_NOTI_EXIT_GROUP_MEMBERS:
                //有人成为新群主
                */
                case PushModel.ENTITY_TYPE_SYSTEM_NOTIFICATION: {
                    //收到系统通知, 该通知来源是单聊 / 群聊
                    SysNotifyCard card =  getGson().fromJson(entity.content, SysNotifyCard.class);
                    getNotifyCenter().dispatch(card);
                    break;
                }

                //系统通知我被移除群
                case PushModel.ENTITY_TYPE_NOTI_OUT_GROUP: {
                    //我被移除的群给我发了一条系统通知消息, 不需要处理此消息了, 因为session隐藏了
                    break;
                }

                //"我"被通知群里有新成员添加 / 有成员被移除 / 有成员被修改 --> "只刷新群员列表"
                case PushModel.ENTITY_TYPE_ADD_GROUP_MEMBERS:
                case PushModel.ENTITY_TYPE_DEL_GROUP_MEMBERS:
                //有人退群
                case PushModel.ENTITY_TYPE_EXIT_GROUP_MEMBERS:
                //收到新添加的群管理的卡片 -> 更新本地GroupMember记录,  "我"有可能是其中之一
                case PushModel.ENTITY_TYPE_MODIFY_GROUP_MEMBERS_PERMISSION: {
                    // 群成员变更, 回来的是一个群成员的列表
                    Type type = new TypeToken<List<GroupMemberCard>>() {
                    }.getType();
                    List<GroupMemberCard> cards = getGson().fromJson(entity.content, type);   //entity.content解析出多张卡片
                    getGroupCenter().dispatch(cards, entity.type);
                    break;
                }

                case PushModel.ENTITY_TYPE_APPLY_JOIN_GROUP: {
                    ApplyCard card = getGson().fromJson(entity.content, ApplyCard.class);
                    SysNotifyCard syscard = NotifyHelper.toCard(card);
                    getNotifyCenter().dispatch(syscard);
                    break;
                }
                default:
                    break;
            }


        }
    }



    /**
     * 获取一个推送中心的实现类
     *
     * @return 推送中心的规范接口
     */
    public static NotifyCenter getNotifyCenter() {
        return NotifyDispatcher.instance();
    }

    /**
     * 获取一个用户中心的实现类
     *
     * @return 用户中心的规范接口
     */
    public static UserCenter getUserCenter() {
        return UserDispatcher.instance();
    }

    /**
     * 获取一个消息中心的实现类
     *
     * @return 消息中心的规范接口
     */
    public static MessageCenter getMessageCenter() {
        return MessageDispatcher.instance();
    }


    /**
     * 获取一个群处理中心的实现类
     *
     * @return 群中心的规范接口
     */
    public static GroupCenter getGroupCenter() {
        return GroupDispatcher.instance();
    }


//    public static PushedCenter getPushedCenter() {
//        return PushedDispatcher.instance();
//    }

}
