
package com.fei_ke.chiphellclient.ui.fragment;

import android.app.ActionBar;
import android.app.ActionBar.OnNavigationListener;
import android.app.Activity;
import android.content.Intent;
import android.text.format.DateUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SpinnerAdapter;

import com.fei_ke.chiphellclient.R;
import com.fei_ke.chiphellclient.api.ApiCallBack;
import com.fei_ke.chiphellclient.api.ChhApi;
import com.fei_ke.chiphellclient.bean.Plate;
import com.fei_ke.chiphellclient.bean.Thread;
import com.fei_ke.chiphellclient.bean.ThreadListWrap;
import com.fei_ke.chiphellclient.ui.activity.MainActivity;
import com.fei_ke.chiphellclient.ui.activity.ThreadDetailActivity;
import com.fei_ke.chiphellclient.ui.adapter.ThreadListAdapter;
import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnLastItemVisibleListener;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnRefreshListener;
import com.handmark.pulltorefresh.library.PullToRefreshListView;

import org.androidannotations.annotations.EFragment;
import org.androidannotations.annotations.FragmentArg;
import org.androidannotations.annotations.ViewById;

import java.util.List;

/**
 * 帖子列表
 * 
 * @author 杨金阳
 * @2014-6-14
 */
@EFragment(R.layout.fragment_thread_list)
public class ThreadListFragment extends BaseContentFragment implements OnClickListener, OnItemClickListener {
    @ViewById(R.id.listView_threads)
    PullToRefreshListView mListViewThreads;
    ThreadListAdapter mThreadListAdapter;

    @ViewById
    View emptyView;

    @ViewById(R.id.layout_fast_reply)
    View layoutFastReply;

    @FragmentArg
    Plate mPlate;

    FastReplyFragment mFastReplyFragment;

    int mPage = 1;

    private MainActivity mMainActivity;
    private boolean mIsFreshing;

    /**
     * 获取实例
     * 
     * @param plate
     * @return
     */
    public static ThreadListFragment getInstance(Plate plate) {
        return ThreadListFragment_.builder().mPlate(plate).build();

    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mMainActivity = (MainActivity) activity;
    }

    @Override
    protected void onAfterViews() {
        mFastReplyFragment = FastReplyFragment.getInstance();
        getChildFragmentManager().beginTransaction().replace(R.id.layout_fast_reply, mFastReplyFragment).commit();

        mThreadListAdapter = new ThreadListAdapter();
        mListViewThreads.setAdapter(mThreadListAdapter);
        mListViewThreads.setEmptyView(emptyView);
        emptyView.setOnClickListener(this);
        mListViewThreads.setOnItemClickListener(this);
        mThreadListAdapter.setOnFastReplylistener(this);
        // mListViewThreads.getRefreshableView().setOnScrollListener(onScrollListener);
        mListViewThreads.setOnRefreshListener(new OnRefreshListener<ListView>() {

            @Override
            public void onRefresh(PullToRefreshBase<ListView> refreshView) {
                String label = DateUtils.formatDateTime(getActivity(),
                        System.currentTimeMillis(),
                        DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE
                                | DateUtils.FORMAT_ABBREV_ALL);

                // Update the LastUpdatedLabel
                refreshView.getLoadingLayoutProxy().setLastUpdatedLabel(label);
                getThreadList();
            }
        });
        mListViewThreads.setOnLastItemVisibleListener(new OnLastItemVisibleListener() {

            @Override
            public void onLastItemVisible() {
                if (!mIsFreshing) {
                    getThreadList(++mPage);
                }
            }
        });
        mListViewThreads.setRefreshing();

        mListViewThreads.setOnScrollListener(onScrollListener);
    }

    private OnScrollListener onScrollListener = new OnScrollListener() {
        int lastVisibleItem;

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {

        }

        @Override
        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            if (firstVisibleItem > lastVisibleItem) {// 向上滑动中
                hideFastReplyPanel();
            }
            lastVisibleItem = firstVisibleItem;

        }
    };

    private void getThreadList() {
        mPage = 1;
        getThreadList(1);
    }

    private void getThreadList(final int page) {
        if (mIsFreshing) {
            return;
        }
        mIsFreshing = true;

        ChhApi api = new ChhApi();
        api.getThreadList(mPlate, page, new ApiCallBack<ThreadListWrap>() {
            @Override
            public void onStart() {
                mMainActivity.onStartRefresh();
            }

            @Override
            public void onSuccess(ThreadListWrap result) {
                if (page == 1) {
                    mThreadListAdapter.clear();
                    creadSubPlate(result.getPlates());
                }
                mThreadListAdapter.update(result.getThreads());
            }

            @Override
            public void onFinish() {
                mIsFreshing = false;
                mListViewThreads.onRefreshComplete();
                mMainActivity.onEndRefresh();
            }

        });

    }

    // 创建子版块列表
    protected void creadSubPlate(final List<Plate> plates) {
        if (plates == null || plates.size() == 0) {
            return;
        }
        ActionBar actionBar = getActivity().getActionBar();
        System.out.println("ThreadListFragment.creadSubPlate()");
        if (actionBar != null) {
            System.out.println("ThreadListFragment.creadSubPlate()2222222");
            SpinnerAdapter adapter = new ArrayAdapter<Plate>(getActivity(), android.R.layout.simple_list_item_1, plates);
            actionBar.setListNavigationCallbacks(adapter, new OnNavigationListener() {

                @Override
                public boolean onNavigationItemSelected(int itemPosition, long itemId) {
                    mMainActivity.replaceContent(plates.get(itemPosition));
                    return true;
                }
            });
            actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        }
    }

    @Override
    public void onRefresh() {
        mListViewThreads.setRefreshing();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Thread thread = mThreadListAdapter.getItem((int) id);
        Intent intent = ThreadDetailActivity.getStartIntent(getActivity(), mPlate, thread);
        startActivity(intent);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.textView_count:
                Thread thread = (Thread) v.getTag();
                layoutFastReply.setVisibility(View.VISIBLE);
                mFastReplyFragment.setPlateAndThread(mPlate, thread);
                break;

            case R.id.emptyView:
                getThreadList();
                break;
            default:
                break;
        }
    }

    private void hideFastReplyPanel() {
        if (layoutFastReply.getVisibility() == View.VISIBLE) {
            Animation animation = AnimationUtils.loadAnimation(getActivity(), R.anim.hide_view_anim);
            layoutFastReply.startAnimation(animation);
            layoutFastReply.setVisibility(View.GONE);
            mFastReplyFragment.hide();
        }
    }
}
