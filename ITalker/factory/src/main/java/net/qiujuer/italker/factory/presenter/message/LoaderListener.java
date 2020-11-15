package net.qiujuer.italker.factory.presenter.message;

import java.util.Date;

public interface LoaderListener {
    void loadBefore(Date date);
    void loadAfter(Date date);
}
