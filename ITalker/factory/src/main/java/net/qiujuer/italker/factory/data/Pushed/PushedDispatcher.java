package net.qiujuer.italker.factory.data.Pushed;

import android.app.Application;

import net.qiujuer.italker.factory.Factory;
import net.qiujuer.italker.factory.data.Notify.NotifyCenter;
import net.qiujuer.italker.factory.data.group.GroupCenter;
import net.qiujuer.italker.factory.data.group.GroupDispatcher;
import net.qiujuer.italker.factory.data.helper.NotifyHelper;
import net.qiujuer.italker.factory.data.message.MessageCenter;
import net.qiujuer.italker.factory.data.message.MessageDispatcher;
import net.qiujuer.italker.factory.model.card.ApplyCard;
import net.qiujuer.italker.factory.model.card.Card;
import net.qiujuer.italker.factory.model.card.MessageCard;
import net.qiujuer.italker.factory.model.card.SysNotifyCard;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class PushedDispatcher implements PushedCenter {

    private static PushedCenter instance;
    private Executor executor = Executors.newSingleThreadExecutor();

    public static PushedCenter instance() {
        if (instance == null) {
            synchronized (PushedDispatcher.class) {
                if (instance == null)
                    instance = new PushedDispatcher();
            }
        }
        return instance;
    }


    private List<MessageCard> messageCards;
    private List<SysNotifyCard> notifyCards;

    private final MessageCenter mesCenter = Factory.getMessageCenter();
    private final NotifyCenter notifyCenter = Factory.getNotifyCenter();

    @Override
    public void dispatch(List<Card> cards) {

        executor.execute(new Runnable() {
            @Override
            public void run() {

                for(Card card: cards){
                    if(card instanceof MessageCard){
                        if(notifyCards.size() > 0){
                            notifyCenter.dispatch(notifyCards.toArray(new SysNotifyCard[0]));
                            notifyCards.clear();
                        }
                        messageCards.add((MessageCard)card);
                    }else {
                        if(messageCards.size() > 0){
                            mesCenter.dispatch(messageCards.toArray(new MessageCard[0]));
                            messageCards.clear();
                        }
                        SysNotifyCard syscard = NotifyHelper.toCard(card);
                        notifyCards.add(syscard);
                    }
                }

                //最后
                if(messageCards.size() > 0){
                    mesCenter.dispatch(messageCards.toArray(new MessageCard[0]));
                    messageCards.clear();
                }
                if(notifyCards.size() > 0){
                    notifyCenter.dispatch(notifyCards.toArray(new SysNotifyCard[0]));
                    notifyCards.clear();
                }

            }
        });

    }
}
