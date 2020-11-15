package net.qiujuer.italker.factory.model.card;

import com.raizlabs.android.dbflow.structure.BaseModel;

import net.qiujuer.italker.common.Common;

public abstract class Card<Model extends BaseModel> {

    //卡片的id
    public abstract String getId();

    public int getPushType() {
        return Common.NON_PushType;
    }

    public String getEXTRA() {
        return null;
    }

    public String getGroupId(){return "";}


    public  String getSenderId() {return ""; }


}
