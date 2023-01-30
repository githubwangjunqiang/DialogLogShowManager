package com.xq.dialoglogshow.dialog;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.xq.dialoglogshow.IShowLoadDataCallback;
import com.xq.dialoglogshow.R;
import com.xq.dialoglogshow.activity.FileShareActivity;
import com.xq.dialoglogshow.adapter.MyAdapter;
import com.xq.dialoglogshow.entity.BaseShowData;
import com.xq.dialoglogshow.entity.HttpLogData;
import com.xq.dialoglogshow.entity.PushData;
import com.xq.dialoglogshow.manager.ShowLogManager;
import com.xq.dialoglogshow.utils.DateUtils;
import com.xq.dialoglogshow.utils.MyItemDecoration;
import com.xq.dialoglogshow.utils.ShowTask;
import com.xq.dialoglogshow.utils.SizeUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Android-小强 on 2023/1/9.
 * mailbox:980766134@qq.com
 * description: 网络日志显示
 */
public class DialogShowLog extends Dialog {
    /**
     * 适配器
     */
    private MyAdapter mMyAdapter;
    private LinearLayout mTabRoot;
    private RecyclerView mRecyclerView;
    private TextView mTab1, mTab2, mTab3, mTab4,mTab5, mCurrentDate, mPrevious, mNext, mBtnClose, mShare;
    private ProgressBar mProgressBar;
    private ShowTask mAsyncTask;

    private long startTime, endTime;

    public DialogShowLog(Context context) {
        super(context, R.style.dialog_httplog);
        setContentView(R.layout.show_log_sdk_httplog_layout);
        initView();
        setListener();

        startTime = DateUtils.getTimeMorning();
        endTime = System.currentTimeMillis();
        loadDataHttp();
        changeTabState(mTab1);
    }

    /**
     * 初始化
     */
    private void initView() {
        mRecyclerView = findViewById(R.id.show_app_dialog_httplog_list);
        mTab1 = findViewById(R.id.show_sdk_id_dialog_http_log_tv1);
        mTab2 = findViewById(R.id.show_sdk_id_dialog_http_log_tv2);
        mTab3 = findViewById(R.id.show_sdk_id_dialog_http_log_tv3);
        mTab4 = findViewById(R.id.show_sdk_id_dialog_http_log_tv4);
        mTab5 = findViewById(R.id.show_sdk_id_dialog_http_log_tv5);
        mProgressBar = findViewById(R.id.show_app_dialog_httplog_loading);
        mCurrentDate = findViewById(R.id.show_app_dialog_httplog_currentdate);
        mPrevious = findViewById(R.id.show_app_dialog_httplog_previous);
        mNext = findViewById(R.id.show_app_dialog_httplog_next);
        mBtnClose = findViewById(R.id.show_app_dialog_confirm_btnok);
        mShare = findViewById(R.id.show_sdk_id_dialog_http_log_share);
        mTabRoot = findViewById(R.id.app_dialog_httplog_tablayout);


        mMyAdapter = new MyAdapter(getContext(), mRecyclerView);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        mRecyclerView.addItemDecoration(new MyItemDecoration(getContext()));
        mRecyclerView.setAdapter(mMyAdapter);
    }

    /**
     * 监听器
     */
    private void setListener() {
        //关闭
        mBtnClose.setOnClickListener(v -> cancel());
        //分享
        mShare.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), FileShareActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
        });

        //网络日志
        mTab1.setOnClickListener(v -> {
            loadDataHttp();
            changeTabState(v);
            mCurrentDate.setVisibility(View.VISIBLE);
            mPrevious.setVisibility(View.VISIBLE);
            mNext.setVisibility(View.VISIBLE);
        });
        //本地key-value
        mTab2.setOnClickListener(v -> {
            loadDataKeyValue();
            changeTabState(v);
            mCurrentDate.setVisibility(View.GONE);
            mPrevious.setVisibility(View.GONE);
            mNext.setVisibility(View.GONE);
        });
        //push
        mTab3.setOnClickListener(v -> {
            loadDataPush();
            changeTabState(v);
            mCurrentDate.setVisibility(View.GONE);
            mPrevious.setVisibility(View.GONE);
            mNext.setVisibility(View.GONE);
        });
        //个人信息
        mTab4.setOnClickListener(v -> {
            loadDataUserInfo();
            changeTabState(v);
            mCurrentDate.setVisibility(View.GONE);
            mPrevious.setVisibility(View.GONE);
            mNext.setVisibility(View.GONE);
        });
        //其他信息
        mTab5.setOnClickListener(v -> {
            loadOtherInformation();
            changeTabState(v);
            mCurrentDate.setVisibility(View.GONE);
            mPrevious.setVisibility(View.GONE);
            mNext.setVisibility(View.GONE);
        });
        mPrevious.setOnClickListener(v -> {
            //上一天
            startTime -= 1000 * 60 * 60 * 24;
            endTime = startTime + 1000 * 60 * 60 * 24;
            loadDataHttp();
        });
        mNext.setOnClickListener(v -> {
            //下一天
            long start = startTime + 1000 * 60 * 60 * 24;
            if (start > DateUtils.getTimeMorning()) {
                Toast.makeText(getContext().getApplicationContext(), "最后一天了", Toast.LENGTH_SHORT).show();
            } else {
                startTime = start;
                endTime = start + 1000 * 60 * 60 * 24;
                loadDataHttp();
            }
        });

    }

    /**
     * 其他信息
     */
    @SuppressLint("StaticFieldLeak")
    private void loadOtherInformation() {
        if (mAsyncTask != null) {
            mAsyncTask.cancel(true);
        }
        mAsyncTask = new ShowTask<ArrayList<BaseShowData>>() {
            @Override
            protected void postMainData(ArrayList<BaseShowData> o) {
                hideLoading();
                mMyAdapter.setList(o);
                mMyAdapter.notifyDataSetChanged();
            }

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                showLoading();
            }

            @Override
            protected ArrayList<BaseShowData> doInBackground(Object[] objects) {

                ArrayList<BaseShowData> baseShowData = ((IShowLoadDataCallback) ShowLogManager.getInstance()).loadRestData();
                if (isCancelled()) {
                    return null;
                }
                if (baseShowData == null) {
                    return null;
                }
                return baseShowData;
            }
        };
        mAsyncTask.execute();
    }

    /**
     * 拉取网络日志
     */
    @SuppressLint("StaticFieldLeak")
    private void loadDataHttp() {
        if (mAsyncTask != null) {
            mAsyncTask.cancel(true);
        }
        mAsyncTask = new ShowTask<ArrayList<BaseShowData>>() {
            @Override
            protected void postMainData(ArrayList<BaseShowData> o) {
                hideLoading();
                mCurrentDate.setText("日期：" + DateUtils.formatDd(startTime));
                mMyAdapter.setList(o);
                mMyAdapter.notifyDataSetChanged();
            }

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                showLoading();
            }

            @Override
            protected ArrayList<BaseShowData> doInBackground(Object[] objects) {

                ArrayList<HttpLogData> list = ((IShowLoadDataCallback) ShowLogManager.getInstance())
                        .loadHttpLog(startTime, endTime);


                if (list != null && !list.isEmpty()) {
                    Map<String, ArrayList<BaseShowData>> map = new HashMap<>();
                    for (HttpLogData data : list) {
                        data.setItemType(BaseShowData.TYE_THREE);
                        String url = data.getUrl();
                        if (map.containsKey(url)) {
                            ArrayList<BaseShowData> listChild = map.get(url);
                            listChild.add(data);
                        } else {
                            ArrayList<BaseShowData> list1 = new ArrayList<>();
                            list1.add(data);
                            map.put(url, list1);
                        }
                    }
                    list.clear();
                    ArrayList<BaseShowData> retUrnList = new ArrayList<>();
                    for (Map.Entry<String, ArrayList<BaseShowData>> data : map.entrySet()) {
                        String key = data.getKey();
                        ArrayList<BaseShowData> value = data.getValue();
                        Collections.sort(value, new Comparator<BaseShowData>() {
                            @Override
                            public int compare(BaseShowData o1, BaseShowData o2) {
                                if (o1.getTime() > o2.getTime()) {
                                    return -1;
                                } else if (o1.getTime() < o2.getTime()) {
                                    return 1;
                                } else {
                                    return 0;
                                }
                            }
                        });

                        for (int i = 0; i < value.size(); i++) {
                            value.get(i).setIndex(i + 1);
                        }
                        BaseShowData fu = new HttpLogData(
                                key, 0, null, null, value, false, 0);
                        fu.setItemType(BaseShowData.TYE_TOW);
                        retUrnList.add(fu);
                    }

                    for (int i = 0; i < list.size(); i++) {
                        retUrnList.get(i).setIndex(i + 1);
                    }
                    return retUrnList;
                }


                return null;
            }
        };
        mAsyncTask.execute();
    }

    /**
     * 拉取本地缓存 key-value
     */
    @SuppressLint("StaticFieldLeak")
    private void loadDataKeyValue() {
        if (mAsyncTask != null) {
            mAsyncTask.cancel(true);
        }
        mAsyncTask = new ShowTask<ArrayList<BaseShowData>>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                showLoading();
            }

            @Override
            protected ArrayList<BaseShowData> doInBackground(Object... voids) {
                ArrayList<BaseShowData> baseShowData = ((IShowLoadDataCallback) ShowLogManager.getInstance()).loadKeyValue();
//
                if (isCancelled()) {
                    return null;
                }
                return baseShowData;

            }


            @Override
            protected void postMainData(ArrayList<BaseShowData> baseShowData) {
                mMyAdapter.setList(baseShowData);
                mMyAdapter.notifyDataSetChanged();
                hideLoading();
            }
        };
        mAsyncTask.execute();
    }

    /**
     * 拉取push 信息
     */
    @SuppressLint("StaticFieldLeak")
    private void loadDataPush() {
        if (mAsyncTask != null) {
            mAsyncTask.cancel(true);
        }
        mAsyncTask = new ShowTask<ArrayList<BaseShowData>>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                showLoading();
            }

            @Override
            protected ArrayList<BaseShowData> doInBackground(Object... voids) {
                ArrayList<PushData> baseShowData = ((IShowLoadDataCallback) ShowLogManager.getInstance()).loadPush();
                if (isCancelled()) {
                    return null;
                }
                if (baseShowData == null) {
                    return null;
                }
                Collections.sort(baseShowData, new Comparator<PushData>() {
                    @Override
                    public int compare(PushData o1, PushData o2) {
                        if (o1.getTime() > o2.getTime()) {
                            return -1;
                        } else if (o1.getTime() < o2.getTime()) {
                            return 1;
                        } else {
                            return 0;
                        }
                    }
                });
                ArrayList<BaseShowData> returnList = new ArrayList<>();

                for (int i = 0; i < baseShowData.size(); i++) {
                    PushData pushData = baseShowData.get(i);
                    returnList.add(pushData);
                }
                return returnList;
            }

            @Override
            protected void postMainData(ArrayList<BaseShowData> baseShowData) {
                mMyAdapter.setList(baseShowData);
                mMyAdapter.notifyDataSetChanged();
                hideLoading();
            }
        };
        mAsyncTask.execute();
    }

    @Override
    public void dismiss() {
        super.dismiss();
        if (mAsyncTask != null) {
            mAsyncTask.cancel(true);
        }
    }

    /**
     * 拉取个人信息
     */
    @SuppressLint("StaticFieldLeak")
    private void loadDataUserInfo() {
        if (mAsyncTask != null) {
            mAsyncTask.cancel(true);
        }
        mAsyncTask = new ShowTask<ArrayList<BaseShowData>>() {

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                showLoading();
            }

            @Override
            protected ArrayList<BaseShowData> doInBackground(Object... voids) {
                ArrayList<BaseShowData> baseShowData = ((IShowLoadDataCallback) ShowLogManager.getInstance()).loadUserInfo();
//
                if (isCancelled()) {
                    return null;
                }

                return baseShowData;
//                // 反射获取 参数
//                Class<?> aClass = showSdkUserInfoData.getClass();
//                JSONObject jsonObject = new JSONObject();
//                for (Field declaredField : aClass.getDeclaredFields()) {
//                    try {
//                        declaredField.setAccessible(true);
//                        String name = declaredField.getName();
//                        Object o = declaredField.get(showSdkUserInfoData);
//                        jsonObject.put(name, o);
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                    }
//                }

            }


            @Override
            protected void postMainData(ArrayList<BaseShowData> baseShowData) {
                mMyAdapter.setList(baseShowData);
                mMyAdapter.notifyDataSetChanged();
                hideLoading();
            }
        };
        mAsyncTask.execute();
    }

    private void showLoading() {
        mProgressBar.setVisibility(View.VISIBLE);
    }

    private void hideLoading() {
        mProgressBar.setVisibility(View.GONE);
    }

    /**
     * 改变状态
     *
     * @param view
     */
    private void changeTabState(View view) {
        int childCount = mTabRoot.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View childAt = mTabRoot.getChildAt(i);
            if (view != childAt) {
                childAt.setSelected(false);
            }
        }
        view.setSelected(true);
    }

    @Override
    public void show() {
        super.show();
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        if (attributes != null) {
            attributes.width = SizeUtils.dpToPx(getContext().getApplicationContext(), 335F);
            attributes.height = WindowManager.LayoutParams.WRAP_CONTENT;
            attributes.gravity = Gravity.CENTER;
            getWindow().setAttributes(attributes);
        }
        getWindow().setWindowAnimations(R.style.dialog_httplog);


    }


}
