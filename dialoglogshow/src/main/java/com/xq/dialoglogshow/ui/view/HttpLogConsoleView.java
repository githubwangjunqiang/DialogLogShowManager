package com.xq.dialoglogshow.ui.view;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.xq.dialoglogshow.R;
import com.xq.dialoglogshow.core.model.HttpLogGroupItem;
import com.xq.dialoglogshow.core.model.HttpLogRecord;
import com.xq.dialoglogshow.core.repository.HttpLogRepository;
import com.xq.dialoglogshow.ui.adapter.HttpLogFlatAdapter;
import com.xq.dialoglogshow.ui.adapter.HttpLogGroupAdapter;
import com.xq.dialoglogshow.ui.dialog.HttpLogDetailDialog;
import com.xq.dialoglogshow.ui.dialog.SearchKeywordDialog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 网络日志调试控制台主视图组件。
 * 为什么这样做：
 * 将网络日志交互从单一弹窗中解耦抽离为独立可复用的 View 组件，既能在主中枢（Hub）中作为同级核心页面呈现，
 * 也能单独挂载，保证 100% 完整继承所有交互（日历、正倒序、分组折叠、实时监听、长按单条/分组删除、清理等）。
 */
public final class HttpLogConsoleView extends FrameLayout implements HttpLogRepository.OnNewLogListener {

    private static final long THROTTLE_MS = 350L;
    private long mLastClickTime = 0L;

    private static final int FILTER_ALL = 0;
    private static final int FILTER_2XX = 1;
    private static final int FILTER_ERROR = 2;

    private final Calendar mCurrentCalendar = Calendar.getInstance();
    private final SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    private boolean mIsSortAsc = false;         // 默认时间倒序（最新在前）
    private boolean mIsGroupMode = false;       // 默认扁平瀑布流
    private int mCurrentFilter = FILTER_ALL;
    private String mSearchKeyword = "";

    /**
     * 查询代次：日期或排序快速切换时，旧查询可能晚于新查询返回；
     * 只允许最新代次的结果落地，避免旧日期/旧排序的数据覆盖当前界面。
     */
    private int mQueryGeneration;

    private final List<HttpLogRecord> mRawList = new ArrayList<>();
    private final List<HttpLogRecord> mFilteredList = new ArrayList<>();

    private HttpLogFlatAdapter mFlatAdapter;
    private HttpLogGroupAdapter mGroupAdapter;

    private TextView mTvCurrentDate;
    private ShowLogToggleSwitch mViewModeSwitch;
    private ShowLogToggleSwitch mSortSwitch;
    private View mBtnSearch;
    private TextView mTvSearchKeyword;
    private TextView mTabFilterAll;
    private TextView mTabFilter2xx;
    private TextView mTabFilterError;
    private RecyclerView mRvLogs;
    private ProgressBar mPbLoading;
    private TextView mTvEmptyView;

    public HttpLogConsoleView(@NonNull Context context) {
        this(context, null);
    }

    public HttpLogConsoleView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HttpLogConsoleView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        LayoutInflater.from(context).inflate(R.layout.show_layout_http_log_console, this, true);
        initViews();
        setupAdapters();
        loadDataForCurrentDay();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        HttpLogRepository.getInstance().registerNewLogListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        HttpLogRepository.getInstance().unregisterNewLogListener(this);
    }

    private void initViews() {
        mTvCurrentDate = findViewById(R.id.tv_current_date);
        mViewModeSwitch = findViewById(R.id.btn_view_mode_switch);
        mSortSwitch = findViewById(R.id.btn_sort_switch);
        mBtnSearch = findViewById(R.id.btn_search);
        mTvSearchKeyword = findViewById(R.id.tv_search_keyword);
        mTabFilterAll = findViewById(R.id.tab_filter_all);
        mTabFilter2xx = findViewById(R.id.tab_filter_2xx);
        mTabFilterError = findViewById(R.id.tab_filter_error);
        mRvLogs = findViewById(R.id.rv_http_logs);
        mPbLoading = findViewById(R.id.pb_loading);
        mTvEmptyView = findViewById(R.id.tv_empty_view);

        // 清理按钮：点击后弹出范围选择框
        findViewById(R.id.btn_action_clear).setOnClickListener(v -> {
            if (isThrottled()) return;
            showClearOptionDialog();
        });

        // 日期切换：前一天
        findViewById(R.id.btn_date_prev).setOnClickListener(v -> {
            if (isThrottled()) return;
            mCurrentCalendar.add(Calendar.DAY_OF_MONTH, -1);
            loadDataForCurrentDay();
        });

        // 日期切换：后一天
        findViewById(R.id.btn_date_next).setOnClickListener(v -> {
            if (isThrottled()) return;
            mCurrentCalendar.add(Calendar.DAY_OF_MONTH, 1);
            loadDataForCurrentDay();
        });

        // 点击中间日期唤起日历选择器
        mTvCurrentDate.setOnClickListener(v -> {
            if (isThrottled()) return;
            showDatePicker();
        });

        // 视图模式开关：左侧列表、右侧分组，点击任意一侧即切换
        mViewModeSwitch.setLabels(
                getContext().getString(R.string.showlog_view_flat_short),
                getContext().getString(R.string.showlog_group));
        mViewModeSwitch.setChecked(false, false);
        // 开关切换是纯 UI 状态变更，且 applyFilterAndRender 幂等，不参与全局点击防抖；
        // 否则用户刚点过日期/搜索后立刻切换，会被统一节流回滚，表现为开关“点不动”。
        mViewModeSwitch.setOnCheckedChangeListener((view, isChecked) -> {
            if (mIsGroupMode == isChecked) return;
            mIsGroupMode = isChecked;
            applyFilterAndRender();
        });

        // 时间排序开关：左侧倒序、右侧正序
        mSortSwitch.setLabels(
                getContext().getString(R.string.showlog_sort_desc),
                getContext().getString(R.string.showlog_sort_asc));
        mSortSwitch.setChecked(false, false);
        // 排序切换同样不做全局节流；重复查询由 mQueryGeneration 保证只有最后一次结果落地
        mSortSwitch.setOnCheckedChangeListener((view, isChecked) -> {
            if (mIsSortAsc == isChecked) return;
            mIsSortAsc = isChecked;
            loadDataForCurrentDay();
        });

        // 搜索入口：主界面只保留图标，输入与确定/取消/清除都在独立弹框内完成
        mBtnSearch.setOnClickListener(v -> {
            if (isThrottled()) return;
            showSearchDialog();
        });
        // 已有关键字胶囊点击后重新打开搜索弹框，方便修改或清除
        mTvSearchKeyword.setOnClickListener(v -> {
            if (isThrottled()) return;
            showSearchDialog();
        });

        // 状态筛选 Tab 1：全部
        mTabFilterAll.setOnClickListener(v -> {
            if (isThrottled() || mCurrentFilter == FILTER_ALL) return;
            mCurrentFilter = FILTER_ALL;
            applyFilterAndRender();
        });

        // 状态筛选 Tab 2：2xx 成功
        mTabFilter2xx.setOnClickListener(v -> {
            if (isThrottled() || mCurrentFilter == FILTER_2XX) return;
            mCurrentFilter = FILTER_2XX;
            applyFilterAndRender();
        });

        // 状态筛选 Tab 3：错误/异常
        mTabFilterError.setOnClickListener(v -> {
            if (isThrottled() || mCurrentFilter == FILTER_ERROR) return;
            mCurrentFilter = FILTER_ERROR;
            applyFilterAndRender();
        });

        // 首屏先按 0 条渲染一次 Tab，避免异步加载完成前出现空白标签
        updateFilterTabsUI(0, 0, 0);
    }

    private void setupAdapters() {
        mFlatAdapter = new HttpLogFlatAdapter();
        mGroupAdapter = new HttpLogGroupAdapter();
        mRvLogs.setLayoutManager(new LinearLayoutManager(getContext()));
        mRvLogs.setAdapter(mFlatAdapter);

        // 扁平列表模式：长按单条日志弹出删除确认
        mFlatAdapter.setOnItemLongClickListener((view, record, position) -> {
            showDeleteSingleDialog(record);
        });

        // 分组折叠模式：长按分组 Header 卡片弹出删除该分组全部日志确认
        mGroupAdapter.setOnGroupLongClickListener((view, group, position) -> {
            showDeleteGroupDialog(group);
        });

        // 分组折叠模式：展开后长按内部单条子项弹出删除确认
        mGroupAdapter.setOnChildLongClickListener((view, record, position) -> {
            showDeleteSingleDialog(record);
        });
    }

    public void reload() {
        loadDataForCurrentDay();
    }

    private void loadDataForCurrentDay() {
        updateDateTitle();
        mPbLoading.setVisibility(View.VISIBLE);
        mTvEmptyView.setVisibility(View.GONE);

        // 每次发起查询都递增代次；回调只在代次仍然最新时才允许更新界面
        final int generation = ++mQueryGeneration;
        long[] range = getDayRange(mCurrentCalendar);
        HttpLogRepository.getInstance().queryLogsAsync(range[0], range[1], mIsSortAsc, new HttpLogRepository.QueryCallback() {
            @Override
            public void onSuccess(@NonNull List<HttpLogRecord> records) {
                post(() -> {
                    if (generation != mQueryGeneration) {
                        return;
                    }
                    mPbLoading.setVisibility(View.GONE);
                    mRawList.clear();
                    mRawList.addAll(records);
                    applyFilterAndRender();
                });
            }

            @Override
            public void onError(@NonNull Throwable throwable) {
                post(() -> {
                    if (generation != mQueryGeneration) {
                        return;
                    }
                    mPbLoading.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "加载日志异常: " + throwable.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void updateDateTitle() {
        Calendar today = Calendar.getInstance();
        boolean isToday = today.get(Calendar.YEAR) == mCurrentCalendar.get(Calendar.YEAR)
                && today.get(Calendar.DAY_OF_YEAR) == mCurrentCalendar.get(Calendar.DAY_OF_YEAR);

        String dateStr = mDateFormat.format(mCurrentCalendar.getTime());
        mTvCurrentDate.setText(getContext().getString(
                isToday ? R.string.showlog_date_today : R.string.showlog_date_selected, dateStr));
    }

    private void applyFilterAndRender() {
        mFilteredList.clear();
        int matchedTotal = 0;
        int successCount = 0;
        int failedCount = 0;
        // 关键字只转换一次，避免在日志量大时对每条记录重复做大小写转换
        String lowerKeyword = mSearchKeyword.toLowerCase(Locale.ROOT);

        for (HttpLogRecord record : mRawList) {
            // 先应用搜索条件：Tab 计数必须与搜索结果一致，否则会出现“成功 (2)”但点进去为空
            if (!matchesSearchKeyword(record, lowerKeyword)) {
                continue;
            }

            boolean isSuccess = record.success;
            matchedTotal++;
            if (isSuccess) {
                successCount++;
            } else {
                failedCount++;
            }

            // 状态码/成败筛选
            if (mCurrentFilter == FILTER_2XX && !isSuccess) {
                continue;
            }
            if (mCurrentFilter == FILTER_ERROR && isSuccess) {
                continue;
            }
            mFilteredList.add(record);
        }

        updateFilterTabsUI(matchedTotal, successCount, failedCount);
        updateSearchKeywordChip();

        if (mFilteredList.isEmpty()) {
            // 有搜索或状态筛选时，“暂无日志记录”会误导用户；改为明确提示是筛选结果为空
            boolean hasActiveFilter = !mSearchKeyword.isEmpty() || mCurrentFilter != FILTER_ALL;
            mTvEmptyView.setText(hasActiveFilter
                    ? R.string.showlog_empty_filter_tip : R.string.showlog_empty_tip);
            mTvEmptyView.setVisibility(View.VISIBLE);
            mRvLogs.setVisibility(View.GONE);
            return;
        }

        mTvEmptyView.setVisibility(View.GONE);
        mRvLogs.setVisibility(View.VISIBLE);

        if (mIsGroupMode) {
            mRvLogs.setAdapter(mGroupAdapter);
            mGroupAdapter.setData(buildGroups(mFilteredList));
        } else {
            mRvLogs.setAdapter(mFlatAdapter);
            mFlatAdapter.setData(mFilteredList);
        }
    }

    /**
     * 判断记录是否命中当前搜索关键字。
     * 为什么这样做：搜索范围覆盖用户最常定位接口的 URL、Host、Path、状态码与错误信息五个维度；
     * 关键字为空时不做任何过滤，保证主列表默认全量展示。
     */
    private static boolean matchesSearchKeyword(@NonNull HttpLogRecord record, @NonNull String lowerKeyword) {
        if (lowerKeyword.isEmpty()) {
            return true;
        }
        boolean matchUrl = record.url != null && record.url.toLowerCase(Locale.ROOT).contains(lowerKeyword);
        boolean matchHost = record.host != null && record.host.toLowerCase(Locale.ROOT).contains(lowerKeyword);
        boolean matchPath = record.path != null && record.path.toLowerCase(Locale.ROOT).contains(lowerKeyword);
        boolean matchStatus = String.valueOf(record.statusCode).contains(lowerKeyword);
        boolean matchMsg = record.message != null && record.message.toLowerCase(Locale.ROOT).contains(lowerKeyword);
        return matchUrl || matchHost || matchPath || matchStatus || matchMsg;
    }

    private void updateFilterTabsUI(int total, int success, int error) {
        mTabFilterAll.setText(getContext().getString(R.string.showlog_filter_all_count, total));
        mTabFilter2xx.setText(getContext().getString(R.string.showlog_filter_success_count, success));
        mTabFilterError.setText(getContext().getString(R.string.showlog_filter_failure_count, error));

        // 未选中分段直接透明，露出外层长胶囊轨道；选中态只覆盖当前分段
        mTabFilterAll.setBackgroundResource(0);
        mTabFilterAll.setTextColor(ContextCompat.getColor(getContext(), R.color.showlog_text_secondary));
        mTabFilterAll.setTypeface(null, android.graphics.Typeface.NORMAL);

        mTabFilter2xx.setBackgroundResource(0);
        mTabFilter2xx.setTextColor(ContextCompat.getColor(getContext(), R.color.showlog_text_secondary));
        mTabFilter2xx.setTypeface(null, android.graphics.Typeface.NORMAL);

        mTabFilterError.setBackgroundResource(0);
        mTabFilterError.setTextColor(error > 0 ? ContextCompat.getColor(getContext(), R.color.showlog_status_error)
                : ContextCompat.getColor(getContext(), R.color.showlog_text_secondary));
        mTabFilterError.setTypeface(null, android.graphics.Typeface.NORMAL);

        switch (mCurrentFilter) {
            case FILTER_ALL:
                mTabFilterAll.setBackgroundResource(R.drawable.bg_showlog_filter_segment_selected);
                mTabFilterAll.setTextColor(ContextCompat.getColor(getContext(), R.color.showlog_text_on_accent));
                mTabFilterAll.setTypeface(null, android.graphics.Typeface.BOLD);
                break;
            case FILTER_2XX:
                mTabFilter2xx.setBackgroundResource(R.drawable.bg_showlog_filter_segment_selected);
                mTabFilter2xx.setTextColor(ContextCompat.getColor(getContext(), R.color.showlog_text_on_accent));
                mTabFilter2xx.setTypeface(null, android.graphics.Typeface.BOLD);
                break;
            case FILTER_ERROR:
                mTabFilterError.setBackgroundResource(R.drawable.bg_showlog_filter_segment_error_selected);
                mTabFilterError.setTextColor(ContextCompat.getColor(getContext(), R.color.showlog_text_primary));
                mTabFilterError.setTypeface(null, android.graphics.Typeface.BOLD);
                break;
        }
    }

    /**
     * 将当前搜索关键字同步到主界面胶囊。
     * 为什么这样做：搜索框已经被收敛到独立弹框，主界面需要一个常驻但低干扰的提示，
     * 让用户随时知道当前列表处于筛选状态；没有关键字时隐藏，避免占用列表高度。
     */
    private void updateSearchKeywordChip() {
        if (mSearchKeyword == null || mSearchKeyword.isEmpty()) {
            mTvSearchKeyword.setVisibility(View.GONE);
            return;
        }
        mTvSearchKeyword.setVisibility(View.VISIBLE);
        mTvSearchKeyword.setText(getContext().getString(R.string.showlog_search_keyword, mSearchKeyword));
    }

    /**
     * 打开独立搜索弹框。
     * 为什么这样做：主界面只保留搜索图标，避免常驻输入框抢占焦点、弹出键盘并压缩日志列表空间；
     * 搜索动作统一通过回调回到主视图，保证弹框关闭后列表立即按新关键字刷新。
     */
    private void showSearchDialog() {
        SearchKeywordDialog dialog = new SearchKeywordDialog(
                getContext(),
                mSearchKeyword,
                new SearchKeywordDialog.OnSearchActionListener() {
                    @Override
                    public void onConfirm(@NonNull String keyword) {
                        applySearchKeyword(keyword);
                    }

                    @Override
                    public void onClear() {
                        applySearchKeyword("");
                    }

                    @Override
                    public void onCancel() {
                        // 取消不改变当前搜索条件，保持主界面原状
                    }
                });
        dialog.show();
    }

    /**
     * 应用搜索关键字并刷新列表；空字符串等价于清除筛选。
     */
    private void applySearchKeyword(@Nullable String keyword) {
        mSearchKeyword = keyword != null ? keyword.trim() : "";
        applyFilterAndRender();
    }

    @NonNull
    private List<HttpLogGroupItem> buildGroups(@NonNull List<HttpLogRecord> list) {
        Map<String, HttpLogGroupItem> groupMap = new LinkedHashMap<>();
        for (HttpLogRecord record : list) {
            String path = record.path != null ? record.path : "";
            String method = record.method != null ? record.method : "GET";
            String host = record.host != null ? record.host : "";
            String key = method + "_" + path;
            HttpLogGroupItem group = groupMap.get(key);
            if (group == null) {
                group = new HttpLogGroupItem(path, method, host);
                groupMap.put(key, group);
            }
            group.addRecord(record);
        }
        return new ArrayList<>(groupMap.values());
    }

    private void showDatePicker() {
        new DatePickerDialog(
                getContext(),
                (view, year, month, dayOfMonth) -> {
                    mCurrentCalendar.set(Calendar.YEAR, year);
                    mCurrentCalendar.set(Calendar.MONTH, month);
                    mCurrentCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    loadDataForCurrentDay();
                },
                mCurrentCalendar.get(Calendar.YEAR),
                mCurrentCalendar.get(Calendar.MONTH),
                mCurrentCalendar.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    private void showClearOptionDialog() {
        Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.show_dialog_clear_confirm);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.88);
            window.setAttributes(params);
        }

        String currentDateStr = mDateFormat.format(mCurrentCalendar.getTime());
        TextView btnClearToday = dialog.findViewById(R.id.btn_clear_option_today);
        TextView btnClearAll = dialog.findViewById(R.id.btn_clear_option_all);
        TextView btnCancel = dialog.findViewById(R.id.btn_clear_option_cancel);

        btnClearToday.setText(getContext().getString(R.string.showlog_clear_scope_today, currentDateStr));

        btnClearToday.setOnClickListener(v -> {
            dialog.dismiss();
            long[] range = getDayRange(mCurrentCalendar);
            HttpLogRepository.getInstance().clearRangeAsync(range[0], range[1], () -> post(() -> {
                Toast.makeText(getContext(), "已清理 " + currentDateStr + " 当天日志", Toast.LENGTH_SHORT).show();
                loadDataForCurrentDay();
            }));
        });

        btnClearAll.setOnClickListener(v -> {
            dialog.dismiss();
            HttpLogRepository.getInstance().clearAllAsync(() -> post(() -> {
                Toast.makeText(getContext(), "已清空所有历史网络日志", Toast.LENGTH_SHORT).show();
                loadDataForCurrentDay();
            }));
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showDeleteSingleDialog(@NonNull HttpLogRecord record) {
        Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.show_dialog_confirm_delete);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.88);
            window.setAttributes(params);
        }

        TextView tvTitle = dialog.findViewById(R.id.tv_delete_dialog_title);
        TextView tvMsg = dialog.findViewById(R.id.tv_delete_dialog_message);
        TextView btnConfirm = dialog.findViewById(R.id.btn_confirm_delete);
        TextView btnCancel = dialog.findViewById(R.id.btn_cancel_delete);

        tvTitle.setText("删除单条日志");
        String pathStr = record.path != null && !record.path.isEmpty() ? record.path : record.url;
        String statusStr = record.statusCode < 0 ? "FAILED" : String.valueOf(record.statusCode);
        tvMsg.setText("确定删除这条网络日志记录吗？\n\n• 方式: " + record.method + "\n• 接口: " + pathStr + "\n• 状态: " + statusStr + " (" + record.durationMs + " ms)");

        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            HttpLogRepository.getInstance().deleteRecordAsync(record, () -> post(() -> {
                Toast.makeText(getContext(), "已删除该条日志", Toast.LENGTH_SHORT).show();
                loadDataForCurrentDay();
            }));
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showDeleteGroupDialog(@NonNull HttpLogGroupItem group) {
        Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.show_dialog_confirm_delete);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.88);
            window.setAttributes(params);
        }

        TextView tvTitle = dialog.findViewById(R.id.tv_delete_dialog_title);
        TextView tvMsg = dialog.findViewById(R.id.tv_delete_dialog_message);
        TextView btnConfirm = dialog.findViewById(R.id.btn_confirm_delete);
        TextView btnCancel = dialog.findViewById(R.id.btn_cancel_delete);

        String currentDateStr = mDateFormat.format(mCurrentCalendar.getTime());
        tvTitle.setText("删除分组网络日志");
        tvMsg.setText("确定删除该接口分组下的全部 " + group.totalCount + " 条日志吗？\n\n• 接口: [" + group.method + "] " + group.path + "\n• 范围: 仅删除 " + currentDateStr + " 当天该接口的日志记录。");

        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            HttpLogRepository.getInstance().deleteRecordsBatchAsync(new ArrayList<>(group.records), () -> post(() -> {
                Toast.makeText(getContext(), "已删除 [" + group.path + "] 分组下的 " + group.totalCount + " 条日志", Toast.LENGTH_SHORT).show();
                loadDataForCurrentDay();
            }));
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    @Override
    public void onNewLog(@NonNull HttpLogRecord record) {
        post(() -> {
            long[] range = getDayRange(mCurrentCalendar);
            if (record.timestamp >= range[0] && record.timestamp <= range[1]) {
                if (!mIsSortAsc && !mIsGroupMode && mSearchKeyword.isEmpty() && mCurrentFilter == FILTER_ALL) {
                    mRawList.add(0, record);
                    mFlatAdapter.addFirst(record);
                    updateFilterTabsUI(mRawList.size(), calculateSuccessCount(mRawList), calculateFailedCount(mRawList));
                    mTvEmptyView.setVisibility(View.GONE);
                    mRvLogs.setVisibility(View.VISIBLE);
                } else {
                    mRawList.add(record);
                    applyFilterAndRender();
                }
            }
        });
    }

    /**
     * 统计业务成功数量。
     * 为什么这样做：筛选逻辑以 record.success 为准，计数也必须使用同一口径，
     * 否则会出现“成功 Tab 计数与列表实际条数不一致”的问题。
     */
    private int calculateSuccessCount(@NonNull List<HttpLogRecord> list) {
        int count = 0;
        for (HttpLogRecord r : list) {
            if (r.success) count++;
        }
        return count;
    }

    private int calculateFailedCount(@NonNull List<HttpLogRecord> list) {
        int count = 0;
        for (HttpLogRecord r : list) {
            if (r.isFailed) count++;
        }
        return count;
    }

    @NonNull
    private static long[] getDayRange(@NonNull Calendar cal) {
        Calendar start = (Calendar) cal.clone();
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);

        Calendar end = (Calendar) cal.clone();
        end.set(Calendar.HOUR_OF_DAY, 23);
        end.set(Calendar.MINUTE, 59);
        end.set(Calendar.SECOND, 59);
        end.set(Calendar.MILLISECOND, 999);

        return new long[]{start.getTimeInMillis(), end.getTimeInMillis()};
    }

    private boolean isThrottled() {
        long now = System.currentTimeMillis();
        if (now - mLastClickTime < THROTTLE_MS) {
            return true;
        }
        mLastClickTime = now;
        return false;
    }
}
