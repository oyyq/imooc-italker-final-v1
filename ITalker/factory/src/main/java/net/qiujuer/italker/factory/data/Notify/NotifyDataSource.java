package net.qiujuer.italker.factory.data.Notify;

import net.qiujuer.italker.factory.data.DbDataSource;
import net.qiujuer.italker.factory.model.db.SysNotify;

/**
 * SYsNotify的数据源定义, 实现是：NotifyRepository & NotifyGroupRepository
 * 关注的对象是SysNotify表
 *
 * @author qiujuer Email:qiujuer@live.cn
 * @version 1.0.0
 */
public interface NotifyDataSource extends DbDataSource<SysNotify> {

}
