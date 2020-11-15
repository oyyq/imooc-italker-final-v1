package net.qiujuer.italker.factory.model.db;

import java.util.Date;

public interface GetPushed {


    public String getId();

    public String getContent();

    public Date getCreateAt();

    public User getSender();

    public User getReceiver();

    public Group getGroup();

    public int getPushType();

    public String getSampleContent();


}
