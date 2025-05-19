package com.xq.dialoglogshow.dialog;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SortedList;

import com.xq.dialoglogshow.IShowLoadDataCallback;
import com.xq.dialoglogshow.R;
import com.xq.dialoglogshow.activity.FileShareActivity;
import com.xq.dialoglogshow.adapter.MyAdapter;
import com.xq.dialoglogshow.entity.BaseShowData;
import com.xq.dialoglogshow.entity.HttpLogData;
import com.xq.dialoglogshow.entity.LogConfigData;
import com.xq.dialoglogshow.entity.PushData;
import com.xq.dialoglogshow.manager.ShowLogManager;
import com.xq.dialoglogshow.utils.DateUtils;
import com.xq.dialoglogshow.utils.MyItemDecoration;
import com.xq.dialoglogshow.utils.ShowTask;
import com.xq.dialoglogshow.utils.SizeUtils;

import org.json.JSONObject;

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
    private TextView mTab1, mSeeting, mTab2, mTab3, mTab4, mTab5, mTab6Doc, mBtnClose, mShare;
    private TextView mClearHttpLog, mCurrentDate, mPrevious, mNext;
    private ProgressBar mProgressBar;
    private ShowTask mAsyncTask;
    private FrameLayout setting_config;

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
        setting_config = findViewById(R.id.setting_config);
        mTab6Doc = findViewById(R.id.show_sdk_id_dialog_http_log_tv6);
        mRecyclerView = findViewById(R.id.show_app_dialog_httplog_list);
        mSeeting = findViewById(R.id.show_sdk_id_dialog_http_log_tv_config);
        mTab1 = findViewById(R.id.show_sdk_id_dialog_http_log_tv1);
        mTab2 = findViewById(R.id.show_sdk_id_dialog_http_log_tv2);
        mTab3 = findViewById(R.id.show_sdk_id_dialog_http_log_tv3);
        mTab4 = findViewById(R.id.show_sdk_id_dialog_http_log_tv4);
        mTab5 = findViewById(R.id.show_sdk_id_dialog_http_log_tv5);
        mProgressBar = findViewById(R.id.show_app_dialog_httplog_loading);
        mCurrentDate = findViewById(R.id.show_app_dialog_httplog_currentdate);
        mClearHttpLog = findViewById(R.id.show_app_dialog_clear_http);
        mPrevious = findViewById(R.id.show_app_dialog_httplog_previous);
        mNext = findViewById(R.id.show_app_dialog_httplog_next);
        mBtnClose = findViewById(R.id.show_app_dialog_confirm_btnok);
        mShare = findViewById(R.id.show_sdk_id_dialog_http_log_share);
        mTabRoot = findViewById(R.id.app_dialog_httplog_tablayout);


        mMyAdapter = new MyAdapter(getContext(), mRecyclerView);
        mMyAdapter.mClickAdapter = new MyAdapter.ClickAdapter() {
            @Override
            public void clickDeleteHttpLog(BaseShowData data, int position) {
                boolean b = ShowLogManager.getCallback().deleteHttpLog(data);
                if (!b) {
                    Toast.makeText(getContext(), "删除失败", Toast.LENGTH_SHORT).show();
                    return;
                }
                mMyAdapter.removedHttpData(data, position);

            }
        };
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
            setting_config.setVisibility(View.GONE);
            mRecyclerView.setVisibility(View.VISIBLE);
            loadDataHttp();
            changeTabState(v);
            mCurrentDate.setVisibility(View.VISIBLE);
            mClearHttpLog.setVisibility(View.VISIBLE);
            mPrevious.setVisibility(View.VISIBLE);
            mNext.setVisibility(View.VISIBLE);
        });
// 清空网络日志
        mClearHttpLog.setOnClickListener(view -> {
            boolean b = ShowLogManager.getCallback().deleteHttpLogAll();
            if (!b) {
                Toast.makeText(getContext(), "删除失败", Toast.LENGTH_SHORT).show();
                return;
            }
            mMyAdapter.setList(new ArrayList<>());
        });
        //自定义设置
        mSeeting.setOnClickListener(v -> {
            changeTabState(v);
            mCurrentDate.setVisibility(View.GONE);
            mClearHttpLog.setVisibility(View.GONE);
            mPrevious.setVisibility(View.GONE);
            mNext.setVisibility(View.GONE);
            setting_config.setVisibility(View.VISIBLE);
            mRecyclerView.setVisibility(View.GONE);

            ((IShowLoadDataCallback) ShowLogManager.getInstance()).setCustomView(setting_config, this);

        });
        //本地key-value
        mTab2.setOnClickListener(v -> {
            loadDataKeyValue();
            changeTabState(v);
            mCurrentDate.setVisibility(View.GONE);
            mClearHttpLog.setVisibility(View.GONE);
            mPrevious.setVisibility(View.GONE);
            mNext.setVisibility(View.GONE);
            setting_config.setVisibility(View.GONE);
            mRecyclerView.setVisibility(View.VISIBLE);
        });
        //push
        mTab3.setOnClickListener(v -> {
            loadDataPush();
            changeTabState(v);
            mCurrentDate.setVisibility(View.GONE);
            mClearHttpLog.setVisibility(View.GONE);
            mPrevious.setVisibility(View.GONE);
            mNext.setVisibility(View.GONE);
            setting_config.setVisibility(View.GONE);
            mRecyclerView.setVisibility(View.VISIBLE);
        });
        //个人信息
        mTab4.setOnClickListener(v -> {
            loadDataUserInfo();
            changeTabState(v);
            mCurrentDate.setVisibility(View.GONE);
            mClearHttpLog.setVisibility(View.GONE);
            mPrevious.setVisibility(View.GONE);
            mNext.setVisibility(View.GONE);
            setting_config.setVisibility(View.GONE);
            mRecyclerView.setVisibility(View.VISIBLE);
        });
        mTab6Doc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //缓存文件夹
                String cacheDocPath = ShowLogManager.getCallback().loadConfig().cacheDocPath;
                new DialogShowFileCache(getContext(), cacheDocPath).show();
            }
        });
        //其他信息
        mTab5.setOnClickListener(v -> {
            loadOtherInformation();
            changeTabState(v);
            mCurrentDate.setVisibility(View.GONE);
            mClearHttpLog.setVisibility(View.GONE);
            mPrevious.setVisibility(View.GONE);
            mNext.setVisibility(View.GONE);
            setting_config.setVisibility(View.GONE);
            mRecyclerView.setVisibility(View.VISIBLE);
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
        mAsyncTask.executeOnExecutor(ShowLogManager.getInstance().loadExecutor());
    }

    private void loadDataHttp() {
        LogConfigData logConfigData = ShowLogManager.getCallback().loadConfig();
        if (logConfigData != null && logConfigData.sortUrlForTime) {
            loadDataHttpSort();
        } else {
            loadDataHttpOld();
        }
    }

    /**
     * 拉取网络日志
     */
    @SuppressLint("StaticFieldLeak")
    private void loadDataHttpOld() {
        if (mAsyncTask != null) {
            mAsyncTask.cancel(true);
        }
        mAsyncTask = new ShowTask<ArrayList<BaseShowData>>() {
            @Override
            protected void postMainData(ArrayList<BaseShowData> o) {
                hideLoading();
                mCurrentDate.setText("日期：" + DateUtils.formatDd(startTime));
                mMyAdapter.setList(o);
            }

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                showLoading();
            }

            @Override
            protected ArrayList<BaseShowData> doInBackground(Object[] objects) {
                Log.d("DialogShowLog", "doInBackground:-1 ");


                ArrayList<HttpLogData> list = ((IShowLoadDataCallback) ShowLogManager.getInstance())
                        .loadHttpLog(startTime, endTime);
                Log.d("DialogShowLog", "doInBackground:-2 ");

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

                    for (int i = 0; i < retUrnList.size(); i++) {
                        retUrnList.get(i).setIndex(i + 1);
                    }
                    return retUrnList;
                }


                return null;
            }
        };
        mAsyncTask.executeOnExecutor(ShowLogManager.getInstance().loadExecutor());
    }

    /**
     * 拉取网络日志 平铺 排序
     */
    @SuppressLint("StaticFieldLeak")
    private void loadDataHttpSort() {
        if (mAsyncTask != null) {
            mAsyncTask.cancel(true);
        }
        mAsyncTask = new ShowTask<ArrayList<BaseShowData>>() {
            @Override
            protected void postMainData(ArrayList<BaseShowData> o) {
                hideLoading();
                mCurrentDate.setText("日期：" + DateUtils.formatDd(startTime));
                mMyAdapter.setList(o);
            }

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                showLoading();
            }

            @Override
            protected ArrayList<BaseShowData> doInBackground(Object[] objects) {
                Log.d("DialogShowLog", "doInBackground:-1 ");


                ArrayList<HttpLogData> list = ((IShowLoadDataCallback) ShowLogManager.getInstance())
                        .loadHttpLog(startTime, endTime);
                Log.d("DialogShowLog", "doInBackground:-2 ");


                if (list != null && !list.isEmpty()) {
                    ArrayList<BaseShowData> retUrnList = new ArrayList<>();
                    for (HttpLogData data : list) {
                        long time = data.getTime();
                        String url = data.getUrl();
                        BaseShowData zi = new HttpLogData(
                                url, time, data.getContent(), data.getResMsg(), null, false, 0);
                        zi.setItemType(BaseShowData.TYE_THREE);
                        data.setExpansion(false);
                        data.setItemType(BaseShowData.TYE_THREE);
                        retUrnList.add(data);
                    }
                    Collections.sort(retUrnList, new Comparator<BaseShowData>() {
                        @Override
                        public int compare(BaseShowData baseShowData, BaseShowData t1) {
                            return -Long.compare(baseShowData.getTime(), t1.getTime());
                        }
                    });

                    for (int i = 0; i < retUrnList.size(); i++) {
                        retUrnList.get(i).setIndex(retUrnList.size() - i);
                    }
                    return retUrnList;
                }


                return null;
            }
        };
        mAsyncTask.executeOnExecutor(ShowLogManager.getInstance().loadExecutor());
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
                hideLoading();
            }
        };
        mAsyncTask.executeOnExecutor(ShowLogManager.getInstance().loadExecutor());
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
                hideLoading();
            }
        };
        mAsyncTask.executeOnExecutor(ShowLogManager.getInstance().loadExecutor());
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
                hideLoading();
            }
        };
        mAsyncTask.executeOnExecutor(ShowLogManager.getInstance().loadExecutor());
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
            attributes.width = WindowManager.LayoutParams.MATCH_PARENT;
            attributes.height = WindowManager.LayoutParams.WRAP_CONTENT;
            attributes.gravity = Gravity.CENTER;
            getWindow().setAttributes(attributes);
        }
        getWindow().setWindowAnimations(R.style.dialog_httplog);


    }


}
