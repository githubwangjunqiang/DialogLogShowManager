package com.xq.dialoglogshow.dialog;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.util.TypedValue;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.content.ContextCompat;

import com.xq.dialoglogshow.R;
import com.xq.dialoglogshow.activity.FileShareActivity;
import com.xq.dialoglogshow.adapter.MyAdapter;
import com.xq.dialoglogshow.collector.vpn.VpnController;
import com.xq.dialoglogshow.collector.vpn.VpnStatusManager;
import com.xq.dialoglogshow.collector.vpn.repository.VpnTrafficLogQueryHelper;
import com.xq.dialoglogshow.collector.vpn.repository.VpnTrafficLogRepository;
import com.xq.dialoglogshow.config.VpnPayloadDecryptor;
import com.xq.dialoglogshow.entity.BaseShowData;
import com.xq.dialoglogshow.entity.HttpLogData;
import com.xq.dialoglogshow.entity.PushData;
import com.xq.dialoglogshow.manager.ShowLogManager;
import com.xq.dialoglogshow.model.VpnTrafficLogModel;
import com.xq.dialoglogshow.utils.AppUtils;
import com.xq.dialoglogshow.utils.DateUtils;
import com.xq.dialoglogshow.utils.MyItemDecoration;
import com.xq.dialoglogshow.utils.ShowTask;

import org.json.JSONArray;
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
public class DialogShowLog extends Dialog implements VpnStatusManager.StatusListener {
    /**
     * 适配器
     */
    private MyAdapter mMyAdapter;
    private LinearLayout mTabRoot;
    private RecyclerView mRecyclerView;
    private TextView mTab1, mTabAutoHttp, mSeeting, mTab2, mTab3, mTab4, mTab5, mTab6Doc, mBtnClose, mShare;
    private TextView mClearHttpLog, mCurrentDate, mPrevious, mNext;
    private ProgressBar mProgressBar;
    private ShowTask mAsyncTask;
    private FrameLayout setting_config;
    /**
     * 为什么这样做：本轮新增的 VPN 入口按钮与 Tab 切换要遵守点击防抖规则，避免重复拉起权限页或重复启动 Service。
     */
    private long mLastClickTime = 0L;

    private long startTime, endTime;
    private View app_dialog_httplog_root_view;
    /**
     * 为什么这样做：只有当前展示的是 VPN 面板时才需要响应状态变化自动刷新，单独缓存开关可以避免其它 Tab 被无意义重绘。
     */
    private boolean mIsShowingVpnDebugPanel = false;
    /**
     * 为什么这样做：VPN 单方案落地后，VPN Tab 既可能显示授权/状态面板，也可能显示可按天查询的日志列表，需要单独记录当前视图形态。
     */
    private boolean mIsShowingVpnLogList = false;

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
        app_dialog_httplog_root_view = findViewById(R.id.app_dialog_httplog_root_view);
        setting_config = findViewById(R.id.setting_config);
        mTab6Doc = findViewById(R.id.show_sdk_id_dialog_http_log_tv6);
        mRecyclerView = findViewById(R.id.show_app_dialog_httplog_list);
        mSeeting = findViewById(R.id.show_sdk_id_dialog_http_log_tv_config);
        mTab1 = findViewById(R.id.show_sdk_id_dialog_http_log_tv1);
        mTabAutoHttp = findViewById(R.id.show_sdk_id_dialog_http_log_tv_auto);
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
        // 为什么这样做：项目已经收口为 VPN 单方案，因此控制台入口命名必须稳定指向 VPN，避免开发者误认为仍存在旧 Hook 自动采集模式。
        mTabAutoHttp.setText(R.string.vpn_network_log);


        mMyAdapter = new MyAdapter(getContext(), mRecyclerView, app_dialog_httplog_root_view);
        mMyAdapter.mClickAdapter = new MyAdapter.ClickAdapter() {
            @Override
            public void clickDeleteHttpLog(BaseShowData data, int position) {
                boolean b = ShowLogManager.getInstance().getProvider().onDeleteHttpLog(data);
                if (!b) {
                    Toast.makeText(getContext(), R.string.show_sdk_delete_failed, Toast.LENGTH_SHORT).show();
                    return;
                }
                mMyAdapter.removedHttpData(data, position);

            }

            @Override
            public void clickVpnHeaderAction() {
                handleVpnPrimaryAction();
            }

            @Override
            public void clickVpnHeaderCertificateAction() {
                handleVpnCertificateAction();
            }
        };
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        // 采用精致暗黑卡片自自带边框与间距，移除旧版突兀的荧光绿分割线
        // mRecyclerView.addItemDecoration(new MyItemDecoration(getContext()));
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

        // 手动网络日志
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

        // 自动网络日志
        if (mTabAutoHttp != null) {
            mTabAutoHttp.setOnClickListener(v -> {
                if (isFastClick()) {
                    return;
                }
                // 为什么这样做：自动网络日志能力已经统一收口到 VPN，点击入口后只允许进入 VPN 状态面板或 VPN 历史日志列表。
                showVpnEntry();
            });
        }

        // 清空网络日志
        mClearHttpLog.setOnClickListener(view -> {
            if (isVpnTabActive()) {
                VpnTrafficLogRepository.getInstance().clearLogsByRange(startTime, endTime);
                loadVpnLogsByDay();
                Toast.makeText(getContext(), R.string.show_sdk_clear_data, Toast.LENGTH_SHORT).show();
                return;
            }
            boolean b = ShowLogManager.getInstance().getProvider().onClearAllHttpLogs();
            if (!b) {
                Toast.makeText(getContext(), R.string.show_sdk_delete_failed, Toast.LENGTH_SHORT).show();
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

            ShowLogManager.getInstance().getProvider().setupCustomTab(setting_config, this);

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
                String cacheDocPath = ShowLogManager.getInstance().getProvider().provideAllCacheDocPath();
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
            if (isVpnTabActive()) {
                startTime -= 1000 * 60 * 60 * 24;
                endTime = startTime + 1000 * 60 * 60 * 24;
                loadVpnLogsByDay();
                return;
            }
            //上一天
            startTime -= 1000 * 60 * 60 * 24;
            endTime = startTime + 1000 * 60 * 60 * 24;
            loadDataHttp();
        });
        mNext.setOnClickListener(v -> {
            if (isVpnTabActive()) {
                long start = startTime + 1000 * 60 * 60 * 24;
                if (start > DateUtils.getTimeMorning()) {
                    Toast.makeText(getContext().getApplicationContext(), R.string.show_sdk_last_day, Toast.LENGTH_SHORT).show();
                } else {
                    startTime = start;
                    endTime = start + 1000 * 60 * 60 * 24;
                    loadVpnLogsByDay();
                }
                return;
            }
            //下一天
            long start = startTime + 1000 * 60 * 60 * 24;
            if (start > DateUtils.getTimeMorning()) {
                Toast.makeText(getContext().getApplicationContext(), R.string.show_sdk_last_day, Toast.LENGTH_SHORT).show();
            } else {
                startTime = start;
                endTime = start + 1000 * 60 * 60 * 24;
                loadDataHttp();
            }
        });

    }

    /**
     * 加载“其他信息”Tab 数据
     * 为什么这样做：优先加载系统及应用基础诊断信息（包名、版本号/名、安装/更新时间、安装来源等），
     * 并在其后追加开发者传入的自定义拓展信息，保证开箱即用且不影响外部业务接入。
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
                // 1. 获取预制的 App 及设备诊断信息列表作为前缀
                ArrayList<BaseShowData> resultList = AppUtils.getPresetAppInformation(getContext());
                
                if (isCancelled()) {
                    return null;
                }
                
                // 2. 获取开发者通过回调传入的额外自定义数据，追加到预制信息后面
                ArrayList<BaseShowData> developerData = ShowLogManager.getInstance().getProvider().provideRestData();
                if (developerData != null && !developerData.isEmpty()) {
                    resultList.addAll(developerData);
                }
                
                return resultList;
            }
        };
        mAsyncTask.executeOnExecutor(ShowLogManager.getInstance().getProvider().getLoadExecutor());
    }

    private void loadDataHttp() {
        if (mRecyclerView.getAdapter() != mMyAdapter) {
            mRecyclerView.setAdapter(mMyAdapter);
        }
        if (ShowLogManager.getInstance().getProvider().isSortUrlForTime()) {
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
                mCurrentDate.setText(getContext().getString(R.string.show_sdk_date_prefix) + "：" + DateUtils.formatDd(startTime));
                mMyAdapter.setList(o);
            }

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                showLoading();
            }

            @Override
            protected ArrayList<BaseShowData> doInBackground(Object[] objects) {
                ArrayList<HttpLogData> list = ShowLogManager.getInstance().getProvider()
                        .provideHttpLogs(startTime, endTime);

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
        mAsyncTask.executeOnExecutor(ShowLogManager.getInstance().getProvider().getLoadExecutor());
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
                mCurrentDate.setText(getContext().getString(R.string.show_sdk_date_prefix) + "：" + DateUtils.formatDd(startTime));
                mMyAdapter.setList(o);
            }

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                showLoading();
            }

            @Override
            protected ArrayList<BaseShowData> doInBackground(Object[] objects) {
                long start = startTime;

                ArrayList<HttpLogData> list = ShowLogManager.getInstance().getProvider()
                        .provideHttpLogs(start, endTime);

                if (list != null && !list.isEmpty()) {
                    ArrayList<BaseShowData> retUrnList = new ArrayList<>();
                    for (HttpLogData data : list) {
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
        mAsyncTask.executeOnExecutor(ShowLogManager.getInstance().getProvider().getLoadExecutor());
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
                ArrayList<BaseShowData> baseShowData = ShowLogManager.getInstance().getProvider().provideKeyValueData();
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
        mAsyncTask.executeOnExecutor(ShowLogManager.getInstance().getProvider().getLoadExecutor());
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
                ArrayList<PushData> baseShowData = ShowLogManager.getInstance().getProvider().providePushLogs();
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
                ArrayList<BaseShowData> returnList = new ArrayList<>(baseShowData);
                return returnList;
            }

            @Override
            protected void postMainData(ArrayList<BaseShowData> baseShowData) {
                mMyAdapter.setList(baseShowData);
                hideLoading();
            }
        };
        mAsyncTask.executeOnExecutor(ShowLogManager.getInstance().getProvider().getLoadExecutor());
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
                ArrayList<BaseShowData> baseShowData = ShowLogManager.getInstance().getProvider().provideUserInfo();
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
        mAsyncTask.executeOnExecutor(ShowLogManager.getInstance().getProvider().getLoadExecutor());
    }

    private void showLoading() {
        mProgressBar.setVisibility(View.VISIBLE);
    }

    private void hideLoading() {
        mProgressBar.setVisibility(View.GONE);
    }

    /**
     * 展示 VPN 调试骨架面板
     * 为什么这样做：一期先把权限申请与状态展示收口到控制台，开发者可以直接从 SDK 界面触发 VPN，而不用记额外的接入步骤。
     */
    private void showVpnDebugPanel() {
        mIsShowingVpnDebugPanel = true;
        mIsShowingVpnLogList = false;
        changeTabState(mTabAutoHttp);
        showVpnDateSwitcher();
        updateVpnCurrentDateLabel();
        mClearHttpLog.setVisibility(View.GONE);
        mRecyclerView.setVisibility(View.GONE);
        setting_config.setVisibility(View.VISIBLE);
        refreshVpnDebugPanel();
    }

    /**
     * VPN Tab 入口分发
     * 为什么这样做：当 VPN 已经运行时，开发者更关心日志列表；当 VPN 尚未运行时，则应该先看到授权和状态面板。
     */
    private void showVpnEntry() {
        VpnController.getInstance().syncRuntimeStatus(getContext());
        // 为什么这样做：VPN Tab 的第一优先级是展示“今天已经抓到的历史日志”，而不是先把用户拦在按钮面板；
        // 只有当天没有日志时，才退回到默认按钮面板承接开启 VPN / 安装证书操作。
        loadVpnLogsByDay();
    }

    /**
     * 展示 VPN 日志列表
     */
    private void showVpnLogList() {
        mIsShowingVpnDebugPanel = false;
        mIsShowingVpnLogList = true;
        changeTabState(mTabAutoHttp);
        setting_config.setVisibility(View.GONE);
        mRecyclerView.setVisibility(View.VISIBLE);
        showVpnDateSwitcher();
        updateVpnCurrentDateLabel();
        mClearHttpLog.setVisibility(View.VISIBLE);
    }

    /**
     * 按天加载 VPN 日志
     */
    private void loadVpnLogsByDay() {
        if (mRecyclerView.getAdapter() != mMyAdapter) {
            mRecyclerView.setAdapter(mMyAdapter);
        }
        showLoading();
        VpnTrafficLogQueryHelper.TimeRange timeRange = VpnTrafficLogQueryHelper.buildDayRange(startTime);
        startTime = timeRange.startTime;
        endTime = timeRange.endTime;
        updateVpnCurrentDateLabel();
        ArrayList<VpnTrafficLogModel> vpnTrafficLogModels = VpnTrafficLogRepository.getInstance().queryLogsByRange(startTime, endTime);
        if (vpnTrafficLogModels.isEmpty()) {
            hideLoading();
            mMyAdapter.setList(new ArrayList<>());
            showVpnDebugPanel();
            return;
        }
        showVpnLogList();
        ArrayList<BaseShowData> baseShowDataList = new ArrayList<>();
        baseShowDataList.add(buildVpnActionHeaderItem());
        for (int i = 0; i < vpnTrafficLogModels.size(); i++) {
            VpnTrafficLogModel vpnTrafficLogModel = vpnTrafficLogModels.get(i);
            BaseShowData baseShowData = new BaseShowData();
            baseShowData.setItemType(BaseShowData.TYE_ONE);
            baseShowData.setId(vpnTrafficLogModel.logId);
            baseShowData.setTime(vpnTrafficLogModel.timestamp);
            baseShowData.setIndex(vpnTrafficLogModels.size() - i);
            baseShowData.setContent(buildVpnLogContent(vpnTrafficLogModel, vpnTrafficLogModels.size() - i));
            // 为什么这样做：一级列表的目标是“快速扫一眼”，因此只保留时间、接口、结果、状态码、请求方式几个关键指标，完整头体统一放到详情弹框查看。
            baseShowData.setResMsg(buildVpnListSummary(vpnTrafficLogModel));
            baseShowDataList.add(baseShowData);
        }
        mMyAdapter.setList(baseShowDataList);
        hideLoading();
    }

    /**
     * 为什么这样做：VPN 场景下“上一天 / 日期 / 下一天”是查询历史记录的主入口，不应该随着列表是否为空而消失。
     */
    private void showVpnDateSwitcher() {
        mCurrentDate.setVisibility(View.VISIBLE);
        mPrevious.setVisibility(View.VISIBLE);
        mNext.setVisibility(View.VISIBLE);
    }

    /**
     * 为什么这样做：日期文案属于 VPN 历史查询的当前上下文，进入空态面板时也必须同步显示，否则用户会误以为切日功能失效。
     */
    private void updateVpnCurrentDateLabel() {
        mCurrentDate.setText(getContext().getString(R.string.show_sdk_date_prefix) + "：" + DateUtils.formatDd(startTime));
    }

    /**
     * 为什么这样做：VPN Tab 同时承载“空态面板”和“历史日志列表”两种视图形态，翻页/刷新逻辑必须统一识别，避免切到空态后日期按钮失效。
     */
    private boolean isVpnTabActive() {
        return mIsShowingVpnDebugPanel || mIsShowingVpnLogList || (mTabAutoHttp != null && mTabAutoHttp.isSelected());
    }

    @NonNull
    private String buildVpnListSummary(@NonNull VpnTrafficLogModel vpnTrafficLogModel) {
        String resultText = resolveVpnRequestResultText(vpnTrafficLogModel);
        String statusCode = normalizeStatusCode(vpnTrafficLogModel.statusCode);
        String methodText = normalizeRequestMethod(vpnTrafficLogModel.method);
        String urlText = normalizeDetailText(vpnTrafficLogModel.url);
        return getContext().getString(
                R.string.show_sdk_vpn_log_list_summary,
                DateUtils.format(vpnTrafficLogModel.timestamp),
                urlText,
                resultText,
                statusCode,
                methodText
        );
    }

    @NonNull
    private String resolveVpnRequestResultText(@NonNull VpnTrafficLogModel vpnTrafficLogModel) {
        if (vpnTrafficLogModel.statusCode != null && vpnTrafficLogModel.statusCode.trim().length() > 0) {
            try {
                int statusCode = Integer.parseInt(vpnTrafficLogModel.statusCode.trim());
                return statusCode >= 200 && statusCode < 300
                        ? getContext().getString(R.string.show_sdk_vpn_log_request_success)
                        : getContext().getString(R.string.show_sdk_vpn_log_request_failed);
            } catch (Throwable ignored) {
                // 为什么这样做：有些失败会话没有标准数字状态码，此时回退到解析状态比直接抛错更稳。
            }
        }
        return "SUCCESS".equalsIgnoreCase(vpnTrafficLogModel.parseStatus)
                ? getContext().getString(R.string.show_sdk_vpn_log_request_success)
                : getContext().getString(R.string.show_sdk_vpn_log_request_failed);
    }

    @NonNull
    private String normalizeStatusCode(@Nullable String statusCode) {
        if (statusCode == null || statusCode.trim().length() == 0) {
            return getContext().getString(R.string.show_sdk_unknown);
        }
        return statusCode.trim();
    }

    @NonNull
    private String normalizeRequestMethod(@Nullable String method) {
        if (method == null || method.trim().length() == 0) {
            return getContext().getString(R.string.show_sdk_unknown);
        }
        return method.trim().toUpperCase();
    }

    /**
     * 为什么这样做：当列表已经有数据时，把开关按钮插入到列表头部并一起滚动，能让开发者在查看历史记录的同时顺手操作，而不需要来回切面板。
     */
    @NonNull
    private BaseShowData buildVpnActionHeaderItem() {
        BaseShowData headerData = new BaseShowData();
        headerData.setItemType(BaseShowData.TYE_FOUR);
        headerData.setContent(getContext().getString(resolveVpnActionText()));
        headerData.setResMsg(VpnController.getInstance().isCertificateInstalled(getContext())
                ? null
                : getContext().getString(R.string.show_sdk_vpn_install_certificate));
        return headerData;
    }

    /**
     * 处理 VPN 主按钮点击
     */
    private void handleVpnPrimaryAction() {
        if (VpnController.getInstance().isVpnRunning(getContext())) {
            VpnController.getInstance().stopVpn(getContext());
        } else {
            VpnController.getInstance().startVpn(getContext());
        }
        refreshVpnDebugPanel();
        if (mIsShowingVpnLogList) {
            loadVpnLogsByDay();
        }
    }

    /**
     * 处理证书按钮点击
     */
    private void handleVpnCertificateAction() {
        VpnController.getInstance().requestCertificateInstall(getContext());
        refreshVpnDebugPanel();
        if (mIsShowingVpnLogList) {
            loadVpnLogsByDay();
        }
    }

    private String buildVpnLogContent(VpnTrafficLogModel vpnTrafficLogModel, int index) {
        StringBuilder builder = new StringBuilder();
        // 为什么这样做：VPN 列表展示的是 SDK 最终对外可读的调试结果，这里直接拼出完整请求/响应细节，开发者点击一条记录即可看到完整头体。
        appendLabelLine(builder, R.string.show_sdk_vpn_log_protocol, vpnTrafficLogModel.protocol != null ? vpnTrafficLogModel.protocol : getContext().getString(R.string.show_sdk_unknown));
        appendLabelLine(builder, R.string.show_sdk_vpn_log_host, vpnTrafficLogModel.host);
        appendLabelLine(builder, R.string.show_sdk_vpn_log_url, vpnTrafficLogModel.url);
        appendLabelLine(builder, R.string.show_sdk_vpn_log_method, vpnTrafficLogModel.method);
        appendLabelLine(builder, R.string.show_sdk_vpn_log_status, vpnTrafficLogModel.statusCode);
        appendLabelLine(builder, R.string.show_sdk_vpn_log_time, DateUtils.format(vpnTrafficLogModel.timestamp));
        appendLabelLine(builder, R.string.show_sdk_vpn_log_cost, getContext().getString(R.string.show_sdk_vpn_log_cost_value, vpnTrafficLogModel.costTimeMs));
        appendLabelLine(builder, R.string.show_sdk_vpn_log_request_bytes, String.valueOf(vpnTrafficLogModel.requestBytes));
        appendLabelLine(builder, R.string.show_sdk_vpn_log_response_bytes, String.valueOf(vpnTrafficLogModel.responseBytes));
        appendLabelLine(builder, R.string.show_sdk_vpn_log_parse_status, vpnTrafficLogModel.parseStatus);
        appendLabelLine(builder, R.string.show_sdk_vpn_log_encrypted, vpnTrafficLogModel.encrypted ? getContext().getString(R.string.show_sdk_yes) : getContext().getString(R.string.show_sdk_no));
        appendLabelLine(builder, R.string.show_sdk_vpn_log_source, vpnTrafficLogModel.sourceDescription);
        appendLabelLine(builder, R.string.show_sdk_vpn_log_package_name, vpnTrafficLogModel.appPackageName);

        appendSection(builder, R.string.show_sdk_vpn_log_request_headers, formatHeaders(vpnTrafficLogModel.requestHeaders));
        appendSection(builder, R.string.show_sdk_vpn_log_request_body, resolveDisplayRequestBody(vpnTrafficLogModel));
        appendSection(builder, R.string.show_sdk_vpn_log_response_headers, formatHeaders(vpnTrafficLogModel.responseHeaders));
        appendSection(builder, R.string.show_sdk_vpn_log_response_body, resolveDisplayResponseBody(vpnTrafficLogModel));

        if (vpnTrafficLogModel.errorMessage != null && vpnTrafficLogModel.errorMessage.length() > 0) {
            appendSection(builder, R.string.show_sdk_vpn_log_message, vpnTrafficLogModel.errorMessage);
        }
        return builder.toString();
    }

    /**
     * 为什么这样做：业务加密体的原始落库值必须保持不变，避免历史数据与导出链路被改写；
     * 因此这里只在详情展示前尝试调用宿主解密器，成功则显示明文，失败仍回退原始内容。
     */
    @Nullable
    private String resolveDisplayRequestBody(@NonNull VpnTrafficLogModel vpnTrafficLogModel) {
        return normalizeDetailText(resolveDecryptedBody(vpnTrafficLogModel, true));
    }

    @Nullable
    private String resolveDisplayResponseBody(@NonNull VpnTrafficLogModel vpnTrafficLogModel) {
        return normalizeDetailText(resolveDecryptedBody(vpnTrafficLogModel, false));
    }

    @Nullable
    private String resolveDecryptedBody(@NonNull VpnTrafficLogModel vpnTrafficLogModel, boolean isRequestBody) {
        String rawBody = isRequestBody ? vpnTrafficLogModel.requestBody : vpnTrafficLogModel.responseBody;
        if (rawBody == null || rawBody.trim().length() == 0) {
            return rawBody;
        }
        // 为什么这样做：解密器能力已经并入 SDK 对外唯一契约 ShowLogProvider，内部统一从 Provider 读取，避免再维护第二套注册入口。
        VpnPayloadDecryptor vpnPayloadDecryptor = ShowLogManager.getInstance().getProvider().getVpnPayloadDecryptor();
        if (vpnPayloadDecryptor == null) {
            return rawBody;
        }
        try {
            VpnPayloadDecryptor.DecryptRequest decryptRequest = new VpnPayloadDecryptor.DecryptRequest(
                    vpnTrafficLogModel.protocol,
                    vpnTrafficLogModel.host,
                    vpnTrafficLogModel.url,
                    vpnTrafficLogModel.method,
                    vpnTrafficLogModel.statusCode,
                    vpnTrafficLogModel.encrypted,
                    vpnTrafficLogModel.parseStatus,
                    vpnTrafficLogModel.requestHeaders,
                    vpnTrafficLogModel.responseHeaders,
                    rawBody
            );
            String decryptedBody = isRequestBody
                    ? vpnPayloadDecryptor.decryptRequestBody(decryptRequest)
                    : vpnPayloadDecryptor.decryptResponseBody(decryptRequest);
            return decryptedBody != null ? decryptedBody : rawBody;
        } catch (Throwable ignored) {
            // 为什么这样做：解密失败属于业务侧扩展能力异常，绝不能反向拖垮 SDK 日志面板；这里必须兜底回退原始密文继续展示。
            return rawBody;
        }
    }

    /**
     * 为什么这样做：标题行使用资源标签统一输出，可避免后续多语言或字段顺序调整时散落修改。
     */
    private void appendLabelLine(@NonNull StringBuilder builder, int labelRes, @Nullable String value) {
        builder.append(getContext().getString(labelRes))
                .append("：")
                .append(normalizeDetailText(value))
                .append("\n");
    }

    /**
     * 为什么这样做：头部/正文属于多行信息，需要用分段形式展示，否则详情页阅读时会和基础元数据混在一起。
     */
    private void appendSection(@NonNull StringBuilder builder, int titleRes, @Nullable String value) {
        builder.append("\n")
                .append(getContext().getString(titleRes))
                .append("：\n")
                .append(normalizeDetailText(value))
                .append("\n");
    }

    @NonNull
    private String normalizeDetailText(@Nullable String value) {
        if (value == null || value.trim().length() == 0) {
            return getContext().getString(R.string.show_sdk_no_data);
        }
        return formatJsonIfNeed(value);
    }

    /**
     * 为什么这样做：日志详情的核心诉求是“点开就能读”，请求体/响应体里如果本来就是 JSON，直接原样展示可读性很差，
     * 这里仅在展示时做美化，不改数据库原始值，既保证排查体验，也避免影响导出、复制和历史兼容。
     */
    @NonNull
    private String formatJsonIfNeed(@NonNull String value) {
        String trimValue = value.trim();
        if (trimValue.length() == 0) {
            return value;
        }
        try {
            if (trimValue.startsWith("{") && trimValue.endsWith("}")) {
                return new JSONObject(trimValue).toString(4);
            }
            if (trimValue.startsWith("[") && trimValue.endsWith("]")) {
                return new JSONArray(trimValue).toString(4);
            }
        } catch (Throwable ignored) {
            // 为什么这样做：日志正文里可能出现“看起来像 JSON 但并不完整”的半包内容，格式化失败时必须保持原文，避免开发者误以为正文丢失。
        }
        return value;
    }

    @NonNull
    private String formatHeaders(@Nullable Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return getContext().getString(R.string.show_sdk_no_data);
        }
        StringBuilder headerBuilder = new StringBuilder();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (headerBuilder.length() > 0) {
                headerBuilder.append("\n");
            }
            headerBuilder.append(entry.getKey()).append(": ").append(entry.getValue());
        }
        return headerBuilder.toString();
    }

    /**
     * 刷新 VPN 面板内容
     */
    private void refreshVpnDebugPanel() {
        if (!mIsShowingVpnDebugPanel) {
            return;
        }
        setting_config.removeAllViews();

        LinearLayout container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER_HORIZONTAL);
        int padding = getContext().getResources().getDimensionPixelSize(R.dimen.show_sdk_vpn_panel_padding);
        container.setPadding(padding, padding, padding, padding);

        TextView titleView = new TextView(getContext());
        titleView.setText(R.string.show_sdk_vpn_status_title);
        titleView.setTextColor(ContextCompat.getColor(getContext(), R.color.show_sdk_vpn_title_text));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_PX, getContext().getResources().getDimension(R.dimen.show_sdk_vpn_title_text_size));
        container.addView(titleView);

        TextView statusView = new TextView(getContext());
        statusView.setText(resolveVpnStatusText());
        statusView.setTextColor(ContextCompat.getColor(getContext(), R.color.show_sdk_vpn_status_text));
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_PX, getContext().getResources().getDimension(R.dimen.show_sdk_vpn_status_text_size));
        int statusTopMargin = getContext().getResources().getDimensionPixelSize(R.dimen.show_sdk_vpn_status_margin_top);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        statusParams.topMargin = statusTopMargin;
        container.addView(statusView, statusParams);

        TextView tipView = new TextView(getContext());
        tipView.setText(R.string.show_sdk_vpn_action_tip);
        tipView.setTextColor(ContextCompat.getColor(getContext(), R.color.show_sdk_vpn_tip_text));
        tipView.setTextSize(TypedValue.COMPLEX_UNIT_PX, getContext().getResources().getDimension(R.dimen.show_sdk_vpn_tip_text_size));
        LinearLayout.LayoutParams tipParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        tipParams.topMargin = getContext().getResources().getDimensionPixelSize(R.dimen.show_sdk_vpn_tip_margin_top);
        container.addView(tipView, tipParams);

        TextView actionView = new TextView(getContext());
        actionView.setBackgroundResource(R.drawable.show_sdk_btn_dark_bg);
        actionView.setPadding(
                getContext().getResources().getDimensionPixelSize(R.dimen.show_sdk_vpn_action_padding_horizontal),
                getContext().getResources().getDimensionPixelSize(R.dimen.show_sdk_vpn_action_padding_vertical),
                getContext().getResources().getDimensionPixelSize(R.dimen.show_sdk_vpn_action_padding_horizontal),
                getContext().getResources().getDimensionPixelSize(R.dimen.show_sdk_vpn_action_padding_vertical)
        );
        actionView.setText(resolveVpnActionText());
        actionView.setTextColor(ContextCompat.getColor(getContext(), R.color.show_sdk_vpn_action_text));
        actionView.setTextSize(TypedValue.COMPLEX_UNIT_PX, getContext().getResources().getDimension(R.dimen.show_sdk_vpn_action_text_size));
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        actionParams.topMargin = getContext().getResources().getDimensionPixelSize(R.dimen.show_sdk_vpn_action_margin_top);
        container.addView(actionView, actionParams);
        actionView.setOnClickListener(v -> {
            if (isFastClick()) {
                return;
            }
            handleVpnPrimaryAction();
        });

        if (!VpnController.getInstance().isCertificateInstalled(getContext())) {
            TextView certActionView = new TextView(getContext());
            certActionView.setBackgroundResource(R.drawable.show_sdk_btn_dark_bg);
            certActionView.setPadding(
                    getContext().getResources().getDimensionPixelSize(R.dimen.show_sdk_vpn_action_padding_horizontal),
                    getContext().getResources().getDimensionPixelSize(R.dimen.show_sdk_vpn_action_padding_vertical),
                    getContext().getResources().getDimensionPixelSize(R.dimen.show_sdk_vpn_action_padding_horizontal),
                    getContext().getResources().getDimensionPixelSize(R.dimen.show_sdk_vpn_action_padding_vertical)
            );
            // 为什么这样做：VPN 启动已不再强依赖证书，但 HTTPS 明文仍需要可见入口触发安装，否则开发者不知道如何升级抓取能力。
            certActionView.setText(R.string.show_sdk_vpn_install_certificate);
            certActionView.setTextColor(ContextCompat.getColor(getContext(), R.color.show_sdk_vpn_action_text));
            certActionView.setTextSize(TypedValue.COMPLEX_UNIT_PX, getContext().getResources().getDimension(R.dimen.show_sdk_vpn_action_text_size));
            LinearLayout.LayoutParams certActionParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            certActionParams.topMargin = getContext().getResources().getDimensionPixelSize(R.dimen.show_sdk_vpn_action_margin_top);
            container.addView(certActionView, certActionParams);
            certActionView.setOnClickListener(v -> {
                if (isFastClick()) {
                    return;
                }
                handleVpnCertificateAction();
            });
        }

        setting_config.addView(container, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
    }

    private CharSequence resolveVpnStatusText() {
        VpnController.getInstance().syncRuntimeStatus(getContext());
        VpnController.RuntimeState runtimeState = VpnController.getInstance().inspectRuntimeState(getContext());
        VpnStatusManager vpnStatusManager = VpnStatusManager.getInstance();
        VpnStatusManager.Status status = vpnStatusManager.getCurrentStatus();
        String statusText;
        if (runtimeState.systemConnectedButSdkUnsynced) {
            // 为什么这样做：系统状态栏已显示 VPN，但 SDK 还没确认当前调试链路是否被自己接管时，优先明确提示“状态同步中”，避免误判成已停止。
            statusText = getContext().getString(R.string.show_sdk_vpn_status_system_active_unsynced);
        } else if (status == VpnStatusManager.Status.PREPARE_REQUIRED) {
            statusText = getContext().getString(R.string.show_sdk_vpn_status_prepare_required);
        } else if (status == VpnStatusManager.Status.STARTING) {
            statusText = getContext().getString(R.string.show_sdk_vpn_status_starting);
        } else if (status == VpnStatusManager.Status.RUNNING) {
            statusText = getContext().getString(R.string.show_sdk_vpn_status_running);
        } else if (status == VpnStatusManager.Status.STOPPED) {
            statusText = getContext().getString(R.string.show_sdk_vpn_status_stopped);
        } else if (status == VpnStatusManager.Status.ERROR) {
            String errorMessage = vpnStatusManager.getErrorMessage();
            String prefix = getContext().getString(R.string.show_sdk_vpn_status_error_prefix);
            statusText = errorMessage == null || errorMessage.length() == 0 ? prefix : prefix + "：" + errorMessage;
        } else {
            statusText = getContext().getString(R.string.show_sdk_vpn_status_idle);
        }
        return statusText + "\n" + buildVpnTrafficSummary();
    }

    private int resolveVpnActionText() {
        return VpnController.getInstance().isVpnRunning(getContext())
                ? R.string.show_sdk_vpn_stop
                : R.string.show_sdk_vpn_enable;
    }

    private CharSequence buildVpnTrafficSummary() {
        VpnStatusManager.TrafficSnapshot trafficSnapshot = VpnStatusManager.getInstance().getTrafficSnapshot();
        if (trafficSnapshot.packetCount <= 0 && trafficSnapshot.activeSessionCount <= 0) {
            return getContext().getString(R.string.show_sdk_vpn_snapshot_none);
        }
        StringBuilder builder = new StringBuilder();
        builder.append(getContext().getString(R.string.show_sdk_vpn_snapshot_label_packets))
                .append("：")
                .append(trafficSnapshot.packetCount)
                .append("  ")
                .append(getContext().getString(R.string.show_sdk_vpn_snapshot_label_tcp))
                .append("：")
                .append(trafficSnapshot.tcpPacketCount)
                .append("  ")
                .append(getContext().getString(R.string.show_sdk_vpn_snapshot_label_udp))
                .append("：")
                .append(trafficSnapshot.udpPacketCount)
                .append("\n")
                .append(getContext().getString(R.string.show_sdk_vpn_snapshot_label_sessions))
                .append("：")
                .append(trafficSnapshot.activeSessionCount);
        if (trafficSnapshot.lastSessionSummary != null && trafficSnapshot.lastSessionSummary.length() > 0) {
            builder.append("\n")
                    .append(getContext().getString(R.string.show_sdk_vpn_snapshot_label_latest))
                    .append("：")
                    .append(trafficSnapshot.lastSessionSummary);
        }
        return builder.toString();
    }

    /**
     * 点击防抖
     */
    private boolean isFastClick() {
        long now = SystemClock.elapsedRealtime();
        if (now - mLastClickTime < 500L) {
            return true;
        }
        mLastClickTime = now;
        return false;
    }

    /**
     * 改变状态
     *
     * @param view
     */
    private void changeTabState(View view) {
        if (view != mTabAutoHttp) {
            mIsShowingVpnDebugPanel = false;
            mIsShowingVpnLogList = false;
        }
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
        VpnController.getInstance().syncRuntimeStatus(getContext());
        VpnStatusManager.getInstance().addStatusListener(this);
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        if (attributes != null) {
            attributes.width = WindowManager.LayoutParams.MATCH_PARENT;
            attributes.height = WindowManager.LayoutParams.WRAP_CONTENT;
            attributes.gravity = Gravity.CENTER;
            getWindow().setAttributes(attributes);
        }
        getWindow().setWindowAnimations(R.style.dialog_httplog);
        ShowLogManager.getInstance().getProvider().onConsoleShow();
    }

    @Override
    public void onVpnStatusChanged(@NonNull VpnStatusManager.Status status, @Nullable String errorMessage) {
        VpnController.getInstance().syncRuntimeStatus(getContext());
        if (isVpnTabActive()) {
            // 为什么这样做：无论 VPN 是启动、停止还是重新授权，只要用户当前停留在 VPN Tab，都应该基于“当前日期是否有日志”自动重算界面，而不是粗暴切走列表。
            mRecyclerView.post(this::loadVpnLogsByDay);
            return;
        }
    }

    @Override
    public void onVpnTrafficSnapshotChanged(@NonNull VpnStatusManager.TrafficSnapshot trafficSnapshot) {
        VpnController.getInstance().syncRuntimeStatus(getContext());
        if (isVpnTabActive()) {
            mRecyclerView.post(this::loadVpnLogsByDay);
            return;
        }
    }

    @Override
    public void dismiss() {
        VpnStatusManager.getInstance().removeStatusListener(this);
        super.dismiss();
        if (mAsyncTask != null) {
            mAsyncTask.cancel(true);
        }
        ShowLogManager.getInstance().getProvider().onConsoleDismiss();
    }


}
