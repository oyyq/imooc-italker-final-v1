package net.qiujuer.italker.push.frags.main;


import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.bumptech.glide.Glide;

import net.qiujuer.italker.common.Common;
import net.qiujuer.italker.common.app.PresenterFragment;
import net.qiujuer.italker.common.widget.EmptyView;
import net.qiujuer.italker.common.widget.PortraitView;
import net.qiujuer.italker.common.widget.recycler.RecyclerAdapter;
import net.qiujuer.italker.face.Face;
import net.qiujuer.italker.factory.Factory;
import net.qiujuer.italker.factory.data.helper.GroupHelper;
import net.qiujuer.italker.factory.data.helper.UserHelper;
import net.qiujuer.italker.factory.data.message.SessionRepository;
import net.qiujuer.italker.factory.model.api.PushModel;
import net.qiujuer.italker.factory.model.api.SysNotify.NonStateModel;
import net.qiujuer.italker.factory.model.api.group.GroupMemberModel;
import net.qiujuer.italker.factory.model.db.Group;
import net.qiujuer.italker.factory.model.db.Session;
import net.qiujuer.italker.factory.model.db.SysNotify;
import net.qiujuer.italker.factory.model.db.User;
import net.qiujuer.italker.factory.presenter.message.SessionContract;
import net.qiujuer.italker.factory.presenter.message.SessionPresenter;
import net.qiujuer.italker.push.R;
import net.qiujuer.italker.push.activities.MessageActivity;
import net.qiujuer.italker.utils.DateTimeUtil;;
import java.util.HashSet;
import java.util.Set;
import butterknife.BindView;



public class ActiveFragment
        extends PresenterFragment<SessionContract.Presenter>
        implements SessionContract.View {

    @BindView(R.id.empty)
    EmptyView mEmptyView;

    @BindView(R.id.recycler)
    RecyclerView mRecycler;

    private RecyclerAdapter<Session> mAdapter;

    public ActiveFragment() {
        // Required empty public constructor
    }


    @Override
    protected SessionContract.Presenter initPresenter() {
        return new SessionPresenter(this);
    }

    @Override
    protected int getContentLayoutId() {
        return R.layout.fragment_active;
    }

    @Override
    protected void initWidget(View root) {
        super.initWidget(root);

        // 初始化Recycler
        mRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        mRecycler.setAdapter(mAdapter = new RecyclerAdapter<Session>() {
            @Override
            protected int getItemViewType(int position, Session session) {
                // 返回cell的布局id
                return R.layout.cell_chat_list;
            }

            @Override
            protected ViewHolder<Session> onCreateViewHolder(View root, int viewType) {
                return new ActiveFragment.ViewHolder(root);
            }
        });

        // 点击事件监听
        mAdapter.setListener(new RecyclerAdapter.AdapterListenerImpl<Session>() {
            @Override
            public void onItemClick(RecyclerAdapter.ViewHolder holder, Session session) {
                // 跳转到聊天界面
                MessageActivity.show(getContext(), session);
            }
        });

        // 初始化占位布局
        mEmptyView.bind(mRecycler);
        setPlaceHolderView(mEmptyView);

    }



    //todo 经过测试, 点击session进入MessageActivity, ActiveFragment.onPause -> onResume
    //    切换到其他Fragment如GroupFragment / ContactFragment, ActiveFragment.onPause -> onStop -> ...
    @Override
    public void onStart() {
        super.onStart();

        if(mReloadLocalData) {
            //查询所有  本地  存储的Session, 退出后重新登陆, 初始刷新
            // 点击Session进入到MessageActivity要保证至少刷出Session.unReadCount条聊天记录(没看过)
            mPresenter.start();
            mReloadLocalData = false;
        }else {
            //把repository中的应用缓存数据 -> dataList 拿出来刷新到界面上
            //Fragment是界面层, 界面层显示的数据是本地数据库 增/删/改 被监听器监听到, 同步到应用内缓存的数据, 原则上和本地数据库的数据保持一致
            //mPresenter.reload: 将应用内缓存数据(dataList)同步到界面上
            ((SessionPresenter)this.mPresenter).reload();
        }

    }



    @Override
    public RecyclerAdapter<Session> getRecyclerAdapter() {
        return mAdapter;
    }

    @Override
    public void onAdapterDataChanged() {
        mPlaceHolderView.triggerOkOrEmpty(mAdapter.getItemCount() > 0);
    }

    @Override
    public RecyclerView getRecyclerView() {
        return mRecycler;
    }



    // 界面数据渲染
    class ViewHolder extends RecyclerAdapter.ViewHolder<Session> {
        @BindView(R.id.im_portrait)
        PortraitView mPortraitView;

        @BindView(R.id.txt_name)
        TextView mName;

        @BindView(R.id.txt_content)
        TextView mContent;

        @BindView(R.id.txt_time)
        TextView mTime;

        @BindView(R.id.red_circ)
        TextView ToRead;

        @BindView(R.id.permBtn)
        Button permBtn;

        ViewHolder(View itemView) {
            super(itemView);
        }


        @Override
        protected void onBind(Session session) {
            mPortraitView.setup(Glide.with(ActiveFragment.this), session.getPicture());
            mName.setText(session.getTitle());

            String str = TextUtils.isEmpty(session.getContent()) ? "" : session.getContent();
            Spannable spannable = new SpannableString(str);
            // 解析表情
            Face.decode(mContent, spannable, (int)mContent.getTextSize());
            // 把内容设置到布局上
            mContent.setText(spannable);


            if( !(session.getReceiverType()== Common.NON_STATE_PUSH) ) {
                permBtn.setVisibility(View.GONE);
                mTime.setText(DateTimeUtil.getSampleDate(session.getModifyAt()));

                final int unReadCount = session.getUnReadCount();
                if (unReadCount > 0) {
                    ToRead.setText(String.valueOf(unReadCount));
                    ToRead.setVisibility(View.VISIBLE);
                } else {
                    ToRead.setVisibility(View.GONE);
                }
            }else {

                SysNotify notify = session.getNotify();
                NonStateModel model= notify.getNonStateModel();
                //判断推送类型
                if( notify.getPushType() == PushModel.ENTITY_TYPE_APPLY_JOIN_GROUP) {

                    User user = UserHelper.findFromLocal( model.getUserId());     //目前只支持添加本地好友, 非"我"的好友不能通过, 设计上有些缺陷
                    Group group = GroupHelper.findFromLocal( model.getGroupId() );

                    if (user == null || group == null) return;       // 设置错误机制, 这样的推送是不应该收到的,
                    // 在SysNotify绑定无状态Session时就应该校验, 这里是二次校验

                    mTime.setVisibility(View.GONE);
                    ToRead.setVisibility(View.GONE);
                    permBtn.setVisibility(View.VISIBLE);
                    permBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Set<String> users = new HashSet<>();
                            users.add(model.getGroupId());
                            // 进行网络请求
                            GroupMemberModel groupMemberModel = new GroupMemberModel(users );
                            //"我"把申请入群的人添加入群
                            GroupHelper.addMembers( model.getGroupId(), groupMemberModel, null);
                            //改变permBtn
                            permBtn.setBackgroundColor(Color.LTGRAY);
                            permBtn.setText(R.string.added);
                            permBtn.setClickable(false);

                        }
                    });

                }
                //拓展其他推送类型

            }

        }
    }



}
