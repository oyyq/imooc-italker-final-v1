package net.qiujuer.italker.push.frags.media;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomSheetBehavior;
import android.support.design.widget.BottomSheetDialog;
import android.support.design.widget.BottomSheetDialogFragment;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;

import net.qiujuer.italker.common.tools.UiTool;
import net.qiujuer.italker.common.widget.GalleryView;
import net.qiujuer.italker.push.R;

/**
 * 图片选择Fragment
 */
public class GalleryFragment
        extends BottomSheetDialogFragment
        implements GalleryView.SelectedChangeListener {

    private GalleryView mGallery;
    private OnSelectedListener mListener;

    private int height;

    public GalleryFragment(){ }

    @SuppressLint("ValidFragment")
    public GalleryFragment(int height) {
        this();
        this.height = height > 0? height : 500;
    }


    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // 返回一个我们复写的
        return new TransStatusBottomSheetDialog(getContext());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // 获取我们的GalleryView
        View root = inflater.inflate(R.layout.fragment_gallery, container, false);
        mGallery = (GalleryView) root.findViewById(R.id.galleryView);
        return root;
    }


    @SuppressLint("RestrictedApi")
    @Override
    public void setupDialog(Dialog dialog, int style) {
        super.setupDialog(dialog, style);
        View view = View.inflate(getContext(), R.layout.fragment_gallery, null);
        dialog.setContentView(view);
        final View bottomSheet = dialog.findViewById(R.id.tab_layout);

//        BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
//        behavior.setPeekHeight(height);
//        behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);


        //取出behavior
        //BottomSheetBehavior behavior = BottomSheetBehavior.from(bottomSheet);
        //占满整个页面
        //bottomSheet.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
        //设置弹出高度
        bottomSheet.getLayoutParams().height = this.height;
    }



    @Override
    public void onStart() {
        super.onStart();
        mGallery.setup(getLoaderManager(), this);
    }

    @Override
    public void onSelectedCountChanged(int count) {
        // 如果选中的一张图片
        if (count > 0) {
            // 隐藏自己
            dismiss();
            if (mListener != null) {
                // 得到所有的选中的图片的路径
                String[] paths = mGallery.getSelectedPath();
                // 返回第一张
                mListener.onSelectedImage(paths[0]);
                // 取消和唤起者之间的应用，加快内存回收
                mListener = null;
            }
        }
    }


    /**
     * 设置事件监听，并返回自己
     *
     * @param listener OnSelectedListener
     * @return GalleryFragment
     */
    public GalleryFragment setListener(OnSelectedListener listener) {
        mListener = listener;
        return this;
    }


    /**
     * 选中图片的监听器
     */
    public interface OnSelectedListener {
        void onSelectedImage(String path);
    }


    /**
     * 为了解决顶部状态栏变黑而写的TransStatusBottomSheetDialog
     * 并且固定弹出高度
     */
    public static class TransStatusBottomSheetDialog extends BottomSheetDialog {

        public TransStatusBottomSheetDialog(@NonNull Context context) {
            super(context);
        }

        public TransStatusBottomSheetDialog(@NonNull Context context, int theme) {
            super(context, theme);
        }

        protected TransStatusBottomSheetDialog(@NonNull Context context, boolean cancelable, OnCancelListener cancelListener) {
            super(context, cancelable, cancelListener);
        }



        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            final Window window = getWindow();
            if (window == null)
                return;

            // 得到屏幕高度
            int screenHeight = UiTool.getScreenHeight(getOwnerActivity());
            // 得到状态栏的高度
            int statusHeight = UiTool.getStatusBarHeight(getOwnerActivity());

            // 计算dialog的高度并设置
            int dialogHeight = screenHeight - statusHeight;

            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    dialogHeight <= 0 ? ViewGroup.LayoutParams.MATCH_PARENT : dialogHeight);

        }


        private void setPeekHeight(int peekHeight){
            if(peekHeight <= 0)
                return;

            final BottomSheetBehavior<View> mBehavior
                    = BottomSheetBehavior.from(findViewById(R.id.tab_layout));

            mBehavior.setPeekHeight(peekHeight);
        }


        private void setMaxHeight(int maxHeight){
            if(maxHeight <= 0)
                return;

            final Window window = getWindow();
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, maxHeight);
            window.setGravity(Gravity.BOTTOM);
        }


    }

}
