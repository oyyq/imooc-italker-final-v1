package net.qiujuer.italker.factory.model.db;

import com.raizlabs.android.dbflow.annotation.ConflictAction;
import com.raizlabs.android.dbflow.annotation.Database;

/**
 * 数据库的基本信息, 数据库升级
 * todo 指定了数据库的冲突解决方式是ROLLBACK, 但没指定事务隔离级别
 * @author qiujuer Email:qiujuer@live.cn
 * @version 1.0.0
 */
@Database(name = AppDatabase.NAME, version = AppDatabase.VERSION, insertConflict = ConflictAction.IGNORE,
updateConflict = ConflictAction.IGNORE)
public class AppDatabase {
    public static final String NAME = "AppDatabase";
    public static final int VERSION = 2;
}
