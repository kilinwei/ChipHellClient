package com.fei_ke.chiphellclient.ui.fragment;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import com.fei_ke.chiphellclient.ChhApplication;
import com.fei_ke.chiphellclient.R;
import com.fei_ke.chiphellclient.api.ChhApi;
import com.fei_ke.chiphellclient.api.support.ApiCallBack;
import com.fei_ke.chiphellclient.api.support.ApiHelper;
import com.fei_ke.chiphellclient.bean.Plate;
import com.fei_ke.chiphellclient.bean.PlateClass;
import com.fei_ke.chiphellclient.bean.Thread;
import com.fei_ke.chiphellclient.bean.ThreadListWrap;
import com.fei_ke.chiphellclient.event.FavoriteChangeEvent;
import com.fei_ke.chiphellclient.ui.activity.BaseActivity;
import com.fei_ke.chiphellclient.ui.activity.LoginActivity;
import com.fei_ke.chiphellclient.ui.activity.MainActivity;
import com.fei_ke.chiphellclient.ui.activity.ThreadDetailActivity;
import com.fei_ke.chiphellclient.ui.adapter.ThreadListAdapter;
import com.fei_ke.chiphellclient.ui.customviews.ExtendListView;
import com.fei_ke.chiphellclient.ui.customviews.PlateHead;
import com.fei_ke.chiphellclient.utils.SmileyPickerUtility;
import com.fei_ke.chiphellclient.utils.ToastUtil;

import org.androidannotations.annotations.EFragment;
import org.androidannotations.annotations.FragmentArg;
import org.androidannotations.annotations.ViewById;

import java.util.ArrayList;
import java.util.List;

import de.greenrobot.event.EventBus;
import de.greenrobot.event.Subscribe;

/**
 * 帖子列表
 *
 * @author fei-ke
 * @2014-6-14
 */
@EFragment(R.layout.fragment_thread_list)
public class ThreadListFragment extends BaseContentFragment implements OnClickListener,
        OnItemClickListener, AdapterView.OnItemLongClickListener {
    private static final int REQUEST_CODE_LOGIN = 100;
    @ViewById(R.id.refreshLayout)
    protected SwipeRefreshLayout refreshLayout;

    @ViewById(R.id.listView_threads)
    protected ExtendListView mListViewThreads;

    ThreadListAdapter mThreadListAdapter;

    @ViewById
    protected View emptyView;

    @ViewById
    protected TextView textViewError;

    @ViewById(R.id.plateHead)
    protected PlateHead mPlateHeadView;

    @ViewById(R.id.layout_fast_reply)
    protected View layoutFastReply;

    //@ViewById(R.id.bottomProgress)
    //protected SmoothProgressBar bottomProgressBar;

    @FragmentArg
    protected Plate mPlate;

    private FastReplyFragment mFastReplyFragment;

    private List<PlateClass> mPlateClasses;

    private int mPage = 1;

    private String url;
    private boolean orderByDate = false;
    // 存储子版块列表
    private List<Plate> platesHold;
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
        if (mFastReplyFragment == null) {
            mFastReplyFragment = FastReplyFragment.getInstance();
            getChildFragmentManager().beginTransaction().replace(R.id.layout_fast_reply, mFastReplyFragment).commit();
        }

        if (mThreadListAdapter == null) {
            mThreadListAdapter = new ThreadListAdapter();
        }

        refreshLayout.setColorSchemeColors(getResources().getIntArray(R.array.gplus_colors));
        refreshLayout.setProgressViewOffset(false, 0, SmileyPickerUtility.dip2px(getActivity(), 64));
        refreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                getThreadList();
            }
        });
        //ActionBarPullToRefresh.from(getActivity())
        //        .allChildrenArePullable()
        //        .listener(onRefreshListener)
        //        .options(Options.create()
        //                .scrollDistance(.30f)
        //                .build())
        //        .setup(refreshLayout);


        mListViewThreads.setAdapter(mThreadListAdapter);
        mListViewThreads.setEmptyView(emptyView);
        emptyView.setOnClickListener(this);
        mListViewThreads.setOnItemClickListener(this);
        mThreadListAdapter.setOnFastReplylistener(this);
        // mListViewThreads.getRefreshableView().setOnScrollListener(onScrollListener);
        //        mListViewThreads.setOnRefreshListener(new OnRefreshListener<ListView>() {
        //
        //            @Override
        //            public void onRefresh(PullToRefreshBase<ListView> refreshView) {
        //                String label = DateUtils.formatDateTime(getActivity(),
        //                        System.currentTimeMillis(),
        //                        DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE
        //                                | DateUtils.FORMAT_ABBREV_ALL);
        //
        //                // Update the LastUpdatedLabel
        //                refreshView.getLoadingLayoutProxy().setLastUpdatedLabel(label);
        //
        //            }
        //        });
        mListViewThreads.setOnLastItemVisibleListener(new ExtendListView.OnLastItemVisibleListener() {

            @Override
            public void onLastItemVisible() {
                if (!mIsFreshing) {
                    getThreadList(++mPage);
                }
            }
        });

        mListViewThreads.setOnScrollListener(onScrollListener);

        // 设置标题或子版块列表
        handSubPlate(platesHold);

        // 没有数据进行数据刷新
        if (mThreadListAdapter.getCount() == 0) {
            getThreadList();
        }

        //设置头部
        mPlateHeadView.bindValue(mPlate, mPlateClasses);

        mPlateHeadView.setOnClassSelectedListener(new PlateHead.OnClassSelectedListener() {

            @Override
            public void onClassSelected(PlateClass plateClass) {
                url = plateClass.getUrl();
                getThreadList();
            }
        });
        mPlateHeadView.setOnOrderBySelectedListener(new PlateHead.OnOrderBySelectedListener() {
            @Override
            public void onOrderBySelected(int index) {
                orderByDate = index == ORDER_BY_DATE;
                getThreadList();
            }
        });

        mPlateHeadView.setOnBtnFavoriteClickListener(this);

        mListViewThreads.setOnItemLongClickListener(this);
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
                hideHeadPanel();
            }
            if (firstVisibleItem < lastVisibleItem) {
                showHeadPanel();
            }
            lastVisibleItem = firstVisibleItem;

        }
    };

    private void getThreadList() {
        mPage = 1;
        mListViewThreads.setSelection(0);
        getThreadList(1);
    }

    private void getThreadList(final int page) {
        if (mIsFreshing) {
            return;
        }
        mIsFreshing = true;
        String orderBy = orderByDate ? "dateline" : null;
        ApiHelper.requestApi(ChhApi.getThreadList(url != null ? url : mPlate.getUrl(), page, orderBy), new ApiCallBack<ThreadListWrap>() {
            @Override
            public void onStart() {
                mMainActivity.postStartRefresh();
                if (page == 1) {
                    refreshLayout.setRefreshing(true);
                } else {
                    //bottomProgressBar.setVisibility(View.VISIBLE);
                    //bottomProgressBar.setProgress(bottomProgressBar.getMax());
                    //bottomProgressBar.setIndeterminate(true);
                }
            }

            @Override
            public void onCache(ThreadListWrap result) {
                if (mThreadListAdapter.getCount() == 0) {
                    onSuccess(result);
                }
            }

            @Override
            public void onSuccess(ThreadListWrap result) {
                if (page == 1) {
                    mThreadListAdapter.clear();
                    List<Plate> plates = result.getPlates();
                    if (!mPlate.isSubPlate() && plates != null) {// 对子版块不进行设置
                        plates.add(0, mPlate);
                        handSubPlate(plates);
                    }

                    // 设置主题分类
                    List<PlateClass> plateClasses = result.getPlateClasses();
                    if (plateClasses != null) {
                        mPlateClasses = plateClasses;
                    } else {
                        List<PlateClass> list = new ArrayList<PlateClass>();
                        PlateClass plateClass = new PlateClass();
                        plateClass.setTitle("全部");
                        plateClass.setUrl(mPlate.getUrl());
                        list.add(plateClass);
                        mPlateClasses = list;
                    }
                    mPlateHeadView.bindValue(mPlate, mPlateClasses);
                }
                mThreadListAdapter.update(result.getThreads(), page == 1 ? mListViewThreads.getLastVisiblePosition() : -1);

                if (result.getError() != null) {
                    textViewError.setText(result.getError());
                }
            }

            @Override
            public void onFailure(Throwable error, String content) {
                ToastUtil.show(getActivity(), "oops 刷新失败了");
            }

            @Override
            public void onFinish() {
                mIsFreshing = false;
                refreshLayout.setRefreshing(false);
                //bottomProgressBar.setIndeterminate(false);
                //bottomProgressBar.setVisibility(View.INVISIBLE);
                mMainActivity.postEndRefresh();
            }

        });

    }

    // 创建子版块列表
    protected void handSubPlate(final List<Plate> plates) {
        if (mPlate.isSubPlate() && plates == null) {
            return;
        }
        ActionBar actionBar = ((BaseActivity) getActivity()).getSupportActionBar();
        Toolbar toolbar = ((BaseActivity) getActivity()).getToolbar();
        Spinner spinner = (Spinner) toolbar.findViewById(R.id.spinner);

        if (actionBar != null && (plates == null || plates.size() == 0)) {
            //actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
            actionBar.setDisplayShowTitleEnabled(true);
            if (spinner != null) {
                spinner.setVisibility(View.GONE);
            }
            return;
        }

        // 保存子版块记录
        platesHold = plates;


        actionBar.setDisplayShowTitleEnabled(false);
        if (spinner == null) {
            SpinnerAdapter adapter = new ArrayAdapter<>(actionBar.getThemedContext(), R.layout.main_spinner_item, plates);
            spinner = new Spinner(actionBar.getThemedContext());
            spinner.setId(R.id.spinner);
            spinner.setAdapter(adapter);
            ((BaseActivity) getActivity()).getToolbar().addView(spinner);
            spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    mMainActivity.replaceContent(plates.get(position));
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {

                }
            });
        } else {
            ArrayAdapter adapter = (ArrayAdapter) spinner.getAdapter();
            adapter.clear();
            adapter.addAll(plates);
        }
        spinner.setVisibility(View.VISIBLE);
        //actionBar.setListNavigationCallbacks(adapter, new android.support.v7.app.ActionBar.OnNavigationListener() {
        //
        //    @Override
        //    public boolean onNavigationItemSelected(int itemPosition, long itemId) {
        //        mMainActivity.replaceContent(plates.get(itemPosition));
        //        return true;
        //    }
        //});
        //actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
    }

    @Override
    public void onRefresh() {
        getThreadList();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Thread thread = mThreadListAdapter.getItem((int) id);
        Intent intent = ThreadDetailActivity.getStartIntent(getActivity(), mPlate, thread);
        if (Build.VERSION.SDK_INT >= 16) {
            Bundle scaleBundle = ActivityOptions.makeScaleUpAnimation(view, 0, 0,
                    view.getWidth(), view.getHeight()).toBundle();
            int[] location = new int[2];
            view.getLocationInWindow(location);
            getActivity().startActivity(intent, scaleBundle);
        } else {
            startActivity(intent);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mThreadListAdapter.notifyDataSetChanged();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.textView_count:
                if (!ChhApplication.getInstance().isLogin()) {
                    ToastUtil.show(getActivity(), R.string.need_login);
                    startActivityForResult(LoginActivity.getStartIntent(getActivity()), REQUEST_CODE_LOGIN);
                    break;
                }
                Thread thread = (Thread) v.getTag();
                layoutFastReply.setVisibility(View.VISIBLE);
                mFastReplyFragment.setPlateAndThread(mPlate, thread);
                break;

            case R.id.emptyView:
                getThreadList();
                break;
            case R.id.btnFavorite:
                handleFavorite();
                break;
            default:
                break;
        }
    }

    private void handleFavorite() {

        //未登录时
        if (!ChhApplication.getInstance().isLogin()) {
            startActivityForResult(LoginActivity.getStartIntent(getActivity()), REQUEST_CODE_LOGIN);
            return;
        }

        final boolean isFavorite = mPlate.isFavorite();
        ApiCallBack<String> apiCallBack = new ApiCallBack<String>() {
            @Override
            public void onSuccess(String result) {
                ToastUtil.show(getActivity(), result);
                EventBus.getDefault().post(new FavoriteChangeEvent());
            }
        };
        if (isFavorite) {//取消收藏
            ApiHelper.requestApi(ChhApi.deleteFavorite(mPlate.getFavoriteId(), ChhApplication.getInstance().getFormHash()), apiCallBack);
        } else {
            ApiHelper.requestApi(ChhApi.favorite(mPlate.getFid(), ChhApi.TYPE_FORUM, ChhApplication.getInstance().getFormHash()), apiCallBack);
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

    protected void showHeadPanel() {
        if (mPlateHeadView.getVisibility() != View.VISIBLE) {
            mPlateHeadView.setVisibility(View.VISIBLE);
            Animation animation = AnimationUtils.loadAnimation(getActivity(), R.anim.slide_in_from_top);
            mPlateHeadView.startAnimation(animation);
        }
    }

    protected void hideHeadPanel() {
        if (mPlateHeadView.getVisibility() == View.VISIBLE) {
            Animation animation = AnimationUtils.loadAnimation(getActivity(), R.anim.slide_out_to_top);
            mPlateHeadView.startAnimation(animation);
            mPlateHeadView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onDestroy() {
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    /**
     * 收到收藏状态发生变化事件时
     *
     * @param event
     */
    @Subscribe
    public void onEventMainThread(final FavoriteChangeEvent event) {
        if (event != null && event.getFavoritePlate() != null) {
            List<Plate> favoritePlate = event.getFavoritePlate();
            int index = favoritePlate.indexOf(mPlate);
            if (index != -1) {
                Plate plate = favoritePlate.get(index);
                this.mPlate.setFavoriteId(plate.getFavoriteId());
                this.mPlateHeadView.setFavorite(plate.isFavorite());
            } else {
                this.mPlate.setFavoriteId(null);
                this.mPlateHeadView.setFavorite(false);
            }
        }
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        final Thread thread = mThreadListAdapter.getItem((int) id);
        PopupMenu popupMenu = new PopupMenu(getActivity(), view);
        final MenuItem menuItemFavorite = popupMenu.getMenu().add("收藏");
        popupMenu.show();
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item == menuItemFavorite) {
                    ApiHelper.requestApi(ChhApi.favorite(thread.getTid(), ChhApi.TYPE_THREAD, ChhApplication.getInstance().getFormHash())
                            , new ApiCallBack<String>() {
                                @Override
                                public void onSuccess(String result) {
                                    ToastUtil.show(getActivity(), result);
                                }
                            });
                }
                return true;
            }
        });
        return true;
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_LOGIN && resultCode == Activity.RESULT_OK) {
            EventBus.getDefault().post(new FavoriteChangeEvent());
        }
    }
}
