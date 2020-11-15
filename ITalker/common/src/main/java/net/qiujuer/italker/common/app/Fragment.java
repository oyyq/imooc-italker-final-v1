package net.qiujuer.italker.common.app;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import net.qiujuer.italker.common.Common;
import net.qiujuer.italker.common.widget.convention.PlaceHolderView;

import butterknife.ButterKnife;
import butterknife.Unbinder;

/**
 * @author qiujuer
 */

public abstract class Fragment extends android.support.v4.app.Fragment {
    protected View mRoot;
    protected Unbinder mRootUnBinder;
    protected PlaceHolderView mPlaceHolderView;
    // 标示是否需要SQLite.select查询本地数据库所有数据, --> 初始化
    protected boolean mReloadLocalData = true;


    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        // 初始化参数
        initArgs(getArguments());
    }


    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (mRoot == null) {
            int layId = getContentLayoutId();
            View root = inflater.inflate(layId, container, false);
            initWidget(root);
            mRoot = root;
        } else {
            if (mRoot.getParent() != null) {
                // 把当前Root从其父控件中移除
                ((ViewGroup) mRoot.getParent()).removeView(mRoot);
            }
        }

        return mRoot;
    }



    //Called immediately after {@link #onCreateView(LayoutInflater, ViewGroup, Bundle)}
    // has returned, but before any saved state has been restored in to the view.
    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // 当View创建完成后初始化数据
        initData();
    }


    @Override
    public void onStart() {
        super.onStart();
    }

    /**
     * 初始化相关参数
     */
    protected void initArgs(Bundle bundle) {

    }

    /**
     * 得到当前界面的资源文件Id
     *
     * @return 资源文件Id
     */
    @LayoutRes
    protected abstract int getContentLayoutId();

    /**
     * 初始化控件
     */
    protected void initWidget(View root) {
        mRootUnBinder = ButterKnife.bind(this, root);
    }

    /**
     * 初始化数据
     */
    protected void initData() {

    }

    /**
     * 当首次初始化数据的时候会调用的方法
     */
    protected void onFirstInit() {

    }

    /**
     * 返回按键触发时调用
     *
     * @return 返回True代表我已处理返回逻辑，Activity不用自己finish。
     * 返回False代表我没有处理逻辑，Activity自己走自己的finish逻辑
     */
    public boolean onBackPressed() {
        return false;
    }


    /**
     * 设置占位布局
     *
     * @param placeHolderView 继承了占位布局规范的View
     */
    public void setPlaceHolderView(PlaceHolderView placeHolderView) {
        this.mPlaceHolderView = placeHolderView;
    }



    @Override
    public void onStop() {
        super.onStop();
    }


    @Override
    public void onDetach() {
        super.onDetach();
        mReloadLocalData = true;
    }
}
