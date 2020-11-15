package net.qiujuer.italker.factory.model.db;


public abstract class GetPushedImpl
        extends BaseDbModel<GetPushedImpl>
        implements GetPushed
{
    @Override
    public String getId() {
        return "";
    }


    public String getAttach(){
        return "";
    }


    //专属Message
    public int getStatus() {
        return 0;
    }

    @Override
    public boolean isSame(GetPushedImpl old) {
        return this.getClass().equals(old.getClass());
    }

}
