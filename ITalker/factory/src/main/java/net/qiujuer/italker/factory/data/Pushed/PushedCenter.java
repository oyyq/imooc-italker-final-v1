package net.qiujuer.italker.factory.data.Pushed;

import net.qiujuer.italker.factory.model.card.Card;
import net.qiujuer.italker.factory.model.card.SysNotifyCard;

import java.util.List;

public interface PushedCenter {

    void dispatch(List<Card> cards);
}
