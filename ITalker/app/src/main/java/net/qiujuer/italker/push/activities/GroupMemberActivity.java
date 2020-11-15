package net.qiujuer.italker.push.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.ViewTarget;
import net.qiujuer.italker.common.app.PresenterToolbarActivity;
import net.qiujuer.italker.common.widget.PortraitView;
import net.qiujuer.italker.common.widget.recycler.RecyclerAdapter;
import net.qiujuer.italker.common.widget.refresh.RefreshLayout;
import net.qiujuer.italker.common.widget.refresh.WaveView3;
import net.qiujuer.italker.factory.data.DataSource;
import net.qiujuer.italker.factory.data.group.GroupMembersRepository;
import net.qiujuer.italker.factory.data.helper.GroupHelper;
import net.qiujuer.italker.factory.model.db.Group;
import net.qiujuer.italker.factory.model.db.GroupMember;
import net.qiujuer.italker.factory.model.db.User;
import net.qiujuer.italker.factory.model.db.view.MemberUserModel;
import net.qiujuer.italker.factory.presenter.group.GroupMembersContract;
import net.qiujuer.italker.factory.presenter.group.GroupMembersPresenter;
import net.qiujuer.italker.push.R;
import net.qiujuer.italker.push.frags.group.GroupMemberAddFragment;
import net.qiujuer.italker.recycler.RecycleViewDivider;

import java.util.ArrayList;
import java.util.List;
import butterknife.BindView;
import butterknife.OnCheckedChanged;
import butterknife.OnClick;

public class GroupMemberActivity
        extends PresenterToolbarActivity<GroupMembersContract.Presenter>
        implements AppBarLayout.OnOffsetChangedListener,
        GroupMembersContract.View,
        GroupMemberAddFragment.Callback {

    private static final String KEY_GROUP_ID = "KEY_GROUP_ID";
    private static final String KEY_GROUP_ADMIN = "KEY_GROUP_ADMIN";
    private boolean mReloadLocalData = true;


    private String groupId;
    private boolean mIsAdmin = false ;

    private Group group;

    @BindView(R.id.app_bar)
    AppBarLayout mAppBarLayout;

    @BindView(R.id.im_portrait)
    PortraitView mPortrait;


    @BindView(R.id.collapsingToolbarLayout)
    CollapsingToolbarLayout mCollapsinglayout;

    //Toolbar上的所有MenuItem
    private List<MenuItem> mInfoMenuItems = new ArrayList<>();


    private RefreshLayout mRefreshLayout;
    private WaveView3 mWaveView3;

    RecyclerView mRecycler;

    private RecyclerAdapter<GroupMember> mAdapter;







    //"我"添加新的Admin
    public void addAdmins(View view){
        mPresenter.modifyAdmin();
    }

    //"我"是群主时删除群成员
    public void deleteMembers(View view){
        mPresenter.delete();
    }
    //"我"退出群聊
    public void exitGroup(View view){
        mPresenter.MyExitGroup();
    }


    //改变接收消息状态, 比如不再接收此群消息
    public void noMessage(View view) {
        //todo 不想做了
    }






    public static void show(Context context, String groupId) {
        show(context, groupId, false);
    }

    public static void showAdmin(Context context, String groupId) {
        show(context, groupId, true);
    }

    public static void show(Context context, String groupId, boolean isAdmin) {
        if (TextUtils.isEmpty(groupId))
            return;

        Intent intent = new Intent(context, GroupMemberActivity.class);
        intent.putExtra(KEY_GROUP_ID, groupId);
        intent.putExtra(KEY_GROUP_ADMIN, isAdmin);
        context.startActivity(intent);
    }


    @Override
    protected int getContentLayoutId() {
        return R.layout.activity_group_member;
    }


    @Override
    protected boolean initArgs(Bundle bundle) {
        groupId = bundle.getString(KEY_GROUP_ID);
        mIsAdmin = bundle.getBoolean(KEY_GROUP_ADMIN);
        group = GroupHelper.findFromLocal(groupId);
        //校验, 如果groupId不为空就打开界面, 并给mIsAdmin赋值
        return !TextUtils.isEmpty(groupId);
    }


    @Override
    protected void initWidget() {
        super.initWidget();
        setTitle(R.string.title_member_list);

        initAppbar();
        initPortrait();
        initCollapsingLayout();

        AdminPerms();
        initRecycler();
    }

    /**
     * 给AppBarLayout设置一个打开与折叠的监听器 -> ChatFragment.this
     */
    private void initAppbar(){
        mAppBarLayout.addOnOffsetChangedListener(this);
    }


    private void initPortrait() {
        mPortrait.setup(Glide.with(this), group.getPicture());    //群图片在OSS存储路径
    }

    private void initCollapsingLayout(){

        Glide.with(this)
                .load(R.drawable.default_banner_chat)
                .centerCrop()
                //into(ImageView),但mCollapsinglayout并不是ImageView, 所以要ViewTarget, 并将mColl..传递进去
                .into(new ViewTarget<CollapsingToolbarLayout, GlideDrawable>(mCollapsinglayout) {

                    @Override
                    public void onResourceReady(GlideDrawable resource, GlideAnimation<? super GlideDrawable> glideAnimation) {
                        this.view.setContentScrim(resource.getCurrent());       //保持比例折叠
                    }

                });

        mCollapsinglayout.setTitle(group.getName());

    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        if(mIsAdmin) {
            getMenuInflater().inflate(R.menu.base_tool_bar_menu, menu);
            mInfoMenuItems.add(mToolbar.getMenu().findItem(R.id.toolbar_add));
            mInfoMenuItems.add(mToolbar.getMenu().findItem(R.id.toolbar_search ));
        }
        else {
            getMenuInflater().inflate(R.menu.nonadmin_menu, menu);
            mInfoMenuItems.add(mToolbar.getMenu().findItem(R.id.toolbar_search ));
        }

        return true;
    }



    @Override
    public boolean onOptionsItemSelected(MenuItem item){

        switch (item.getItemId()) {
            case R.id.toolbar_add:
                //将GroupMemberAddFragment放入到GroupMemberActivity.getSupportFragmentManager()中
                new GroupMemberAddFragment()
                        .show(getSupportFragmentManager(), GroupMemberAddFragment.class.getName());

                break;
            case R.id.toolbar_search:
                // 搜索群成员, 不拓展了
                break;
        }
        return super.onOptionsItemSelected(item);

    }


    @Override
    protected void onStart() {
        super.onStart();
        if(mReloadLocalData) {
            mPresenter.start();
            mReloadLocalData = false;
        }else{
            ((GroupMembersPresenter)this.mPresenter).reload();
        }
    }



    @Override
    public RecyclerAdapter<GroupMember> getRecyclerAdapter() {
        return mAdapter;
    }

    @Override
    public void onAdapterDataChanged() {
        // 隐藏Loading就可以
        hideLoading();
    }


    @Override
    public RecyclerView getRecyclerView() {
        return mRecycler;
    }


    @Override
    protected GroupMembersContract.Presenter initPresenter() {
        //当GroupMemberActivity销毁过后,  GroupMembersRepository是否要随之销毁? 倾向销毁
        GroupMembersPresenter presenter  = new GroupMembersPresenter(new GroupMembersRepository(groupId),this);

        //"我"退群的回调
        presenter.setExitCallback(new DataSource.SucceedCallback<List<GroupMember>>() {
            @Override
            public void onDataLoaded(List<GroupMember> members) {
                hideLoading();
                //GroupMemberActivity.onDestroy,
                // 销毁Activity & Presenter & GroupMemberRepository (监听GroupMember),  回到MessageActivity
                onBackPressed();
            }
        });

        return presenter;
    }




    @Override
    public void AdminPerms() {
        mRefreshLayout = (RefreshLayout) findViewById(R.id.refreshlayout);
        //生成RefreshHeader 2种情况
        if(mIsAdmin){
            mRefreshLayout.setRefreshHeader(LayoutInflater.from(this).inflate(R.layout.admin_headerview,null));
        }else {
            mRefreshLayout.setRefreshHeader(LayoutInflater.from(this).inflate(R.layout.nonadmin_headerview,null));
        }

        mWaveView3 = (WaveView3) findViewById(R.id.wave_view);

        if (mRefreshLayout != null) {
            // 刷新状态的回调
            mRefreshLayout.setRefreshListener(new RefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    mWaveView3.setVisibility(View.VISIBLE);
                    // 延迟3秒后刷新成功
                    mRefreshLayout.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mRefreshLayout.refreshComplete();
                            mWaveView3.setVisibility(View.GONE);
                        }
                    }, WaveView3.totalduration);
                }
            });

        }

    }





    private void initRecycler(){
        mRecycler = (RecyclerView) findViewById(R.id.recycler);
        RecyclerView recyclerView = mRecycler;
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        //true: layout is measured by recyclerview
        linearLayoutManager.setAutoMeasureEnabled(true);
        recyclerView.setLayoutManager(linearLayoutManager);
        //todo 使用NestedScrollView嵌套RecyclerView时,滑动lRecyclerView列表会出现强烈的卡顿感 -> 解决
        recyclerView.setNestedScrollingEnabled(false);
        //todo recyclerView抢焦点问题
        recyclerView.setFocusableInTouchMode(false);

        recyclerView.setAdapter(mAdapter = new RecyclerAdapter<GroupMember>() {
            @Override
            protected int getItemViewType(int position, GroupMember memberUserModel) {
                return R.layout.cell_group_create_contact;
            }

            @Override
            protected ViewHolder<GroupMember> onCreateViewHolder(View root, int viewType) {
                return new GroupMemberActivity.ViewHolder(root);
            }
        });

        //设置分割线
        recyclerView.addItemDecoration(new RecycleViewDivider(
                this, LinearLayoutManager.HORIZONTAL, 1, getResources().getColor(R.color.DarkGrey)));



        /*

        //todo 刷新历史消息(上面), 11-06 14:20 暂时注释
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            //todo 参考: https://www.jianshu.com/p/ce347cf991db
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                //判断是否已经滚动到底
                if(!recyclerView.canScrollVertically(1)){
                    loadMoreNewData();
                }
                //是否已经滚动到顶
                else if (!recyclerView.canScrollVertically(-1)){
                    loadMoreOldData();
                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {

            }
        });

        */

    }





    private void loadMoreNewData(){ }

    private void loadMoreOldData() { }



    @Override
    public void onDeleted() {
        hideLoading();
    }


    @Override
    public void hideLoading() {
        super.hideLoading();
    }

    @Override
    public void refreshMembers() {
        // 重新加载成员信息
//        if (mPresenter != null)
//            mPresenter.refresh();
    }



    @Override
    public String getGroupId() {
        return groupId;
    }


    //当AppBarLayout有滑动, 收起到展开到收起 的回调
    @Override
    public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {

        View view = mPortrait;
        List<MenuItem> menuItems = mInfoMenuItems;

        if(view == null || menuItems == null)
            return;

        if(verticalOffset == 0){
            //完全展开
            view.setVisibility(View.VISIBLE);
            //没有缩放
            view.setScaleX(1);
            view.setScaleY(1);
            //设置透明度
            view.setAlpha(1);

            for(MenuItem item: menuItems) {
                item.setVisible(false);
                item.getIcon().setAlpha(0);
            }
            //在完全展开时refreshLayout能够监听并分发拖动, 能调用dispatchTouchEvent
            // 参考: https://www.jianshu.com/p/44769ef64ffa
            mRefreshLayout.setEnabled(true);

        }else{

            verticalOffset = Math.abs(verticalOffset);
            //todo AppBarLayout最多能够滚动的距离, 必须在onOffsetChanged中获取才是准确值
            final int totalScrollRange = appBarLayout.getTotalScrollRange();

            if(verticalOffset >= totalScrollRange){
                //拉动距离已经不小于最大拉动距离, 说明已经关闭掉了
                view.setVisibility(View.INVISIBLE);
                view.setScaleX(0);
                view.setScaleY(0);
                view.setAlpha(0);

                for(MenuItem item: menuItems) {
                    item.setVisible(true);
                    item.getIcon().setAlpha(255);          //对Drawable setAlpha与View.setAlpha是不同的
                }
            }else{
                //没完全拉动到关闭
                float progress =1- verticalOffset/(float)totalScrollRange;
                view.setVisibility(View.VISIBLE);
                view.setScaleX(progress);
                view.setScaleY(progress);
                view.setAlpha(progress);

                //和头像正好相反
                for(MenuItem item: menuItems) {
                    item.setVisible(true);
                    item.getIcon().setAlpha(255 - (int) (255 * progress));
                }

            }

            //不分发touchEvent, 不能调用dispatchTouchEvent
            mRefreshLayout.setEnabled(false);

        }

    }



    class ViewHolder extends RecyclerAdapter.ViewHolder<GroupMember> {
        @BindView(R.id.im_portrait)
        PortraitView mPortrait;

        @BindView(R.id.txt_name)
        TextView mName;

        @BindView(R.id.cb_select)
        CheckBox mSelect;

        private boolean isSelected = false;


        ViewHolder(View itemView) {
            super(itemView);
            //itemView.findViewById(R.id.cb_select).setVisibility(View.GONE);
        }

        @Override
        protected void onBind(GroupMember member) {
            mPortrait.setup(Glide.with(GroupMemberActivity.this), member.getUser().getPortrait());
            User user = member.getUser();
            mName.setText(user.getName());
            mSelect.setChecked(isSelected);
        }


        @OnCheckedChanged(R.id.cb_select)
        void onCheckedChanged(boolean checked){
            isSelected = checked;
            mPresenter.changeSelect(mData, checked);
        }


        @OnClick(R.id.im_portrait)
        void onPortraitClick() {
            PersonalActivity.show(GroupMemberActivity.this, mData.getUser().getId());
        }
    }
}
