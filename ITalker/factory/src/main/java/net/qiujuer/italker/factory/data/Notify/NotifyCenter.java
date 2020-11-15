package net.qiujuer.italker.factory.data.Notify;

import net.qiujuer.italker.factory.model.card.SysNotifyCard;

public interface NotifyCenter {
    void dispatch(SysNotifyCard... cards);
}
