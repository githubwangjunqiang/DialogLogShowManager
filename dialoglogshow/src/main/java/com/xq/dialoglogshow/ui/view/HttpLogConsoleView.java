package com.xq.dialoglogshow.ui.view;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
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

    private final List<HttpLogRecord> mRawList = new ArrayList<>();
    private final List<HttpLogRecord> mFilteredList = new ArrayList<>();

    private HttpLogFlatAdapter mFlatAdapter;
    private HttpLogGroupAdapter mGroupAdapter;

    private TextView mTvCurrentDate;
    private TextView mTvTotalSummary;
    private TextView mBtnSegFlat;
    private TextView mBtnSegGroup;
    private TextView mBtnToggleSort;
    private TextView mTabFilterAll;
    private TextView mTabFilter2xx;
    private TextView mTabFilterError;
    private EditText mEtSearch;
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
        mTvTotalSummary = findViewById(R.id.tv_total_summary);
        mBtnSegFlat = findViewById(R.id.btn_seg_flat);
        mBtnSegGroup = findViewById(R.id.btn_seg_group);
        mBtnToggleSort = findViewById(R.id.btn_toggle_sort);
        mTabFilterAll = findViewById(R.id.tab_filter_all);
        mTabFilter2xx = findViewById(R.id.tab_filter_2xx);
        mTabFilterError = findViewById(R.id.tab_filter_error);
        mEtSearch = findViewById(R.id.et_search_filter);
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

        // 分段视图选择：扁平列表
        mBtnSegFlat.setOnClickListener(v -> {
            if (isThrottled() || !mIsGroupMode) return;
            mIsGroupMode = false;
            updateViewModeSegmentedUI();
            applyFilterAndRender();
        });

        // 分段视图选择：接口折叠分组
        mBtnSegGroup.setOnClickListener(v -> {
            if (isThrottled() || mIsGroupMode) return;
            mIsGroupMode = true;
            updateViewModeSegmentedUI();
            applyFilterAndRender();
        });

        // 时间正倒序切换按钮
        mBtnToggleSort.setOnClickListener(v -> {
            if (isThrottled()) return;
            mIsSortAsc = !mIsSortAsc;
            updateSortButtonUI();
            loadDataForCurrentDay();
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

        // 搜索框过滤监听
        mEtSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                mSearchKeyword = s != null ? s.toString().trim() : "";
                applyFilterAndRender();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        updateViewModeSegmentedUI();
        updateSortButtonUI();
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

        long[] range = getDayRange(mCurrentCalendar);
        HttpLogRepository.getInstance().queryLogsAsync(range[0], range[1], mIsSortAsc, new HttpLogRepository.QueryCallback() {
            @Override
            public void onSuccess(@NonNull List<HttpLogRecord> records) {
                post(() -> {
                    mPbLoading.setVisibility(View.GONE);
                    mRawList.clear();
                    mRawList.addAll(records);
                    applyFilterAndRender();
                });
            }

            @Override
            public void onError(@NonNull Throwable throwable) {
                post(() -> {
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
        mTvCurrentDate.setText(isToday ? "📅 " + dateStr + " (今天) ▾" : "📅 " + dateStr + " ▾");
    }

    private void updateViewModeSegmentedUI() {
        if (!mIsGroupMode) {
            mBtnSegFlat.setBackgroundResource(R.drawable.bg_showlog_tab_selected);
            mBtnSegFlat.setTextColor(Color.parseColor("#FF0F172A"));
            mBtnSegFlat.setTypeface(null, android.graphics.Typeface.BOLD);

            mBtnSegGroup.setBackgroundColor(Color.TRANSPARENT);
            mBtnSegGroup.setTextColor(ContextCompat.getColor(getContext(), R.color.showlog_text_secondary));
            mBtnSegGroup.setTypeface(null, android.graphics.Typeface.NORMAL);
        } else {
            mBtnSegGroup.setBackgroundResource(R.drawable.bg_showlog_tab_selected);
            mBtnSegGroup.setTextColor(Color.parseColor("#FF0F172A"));
            mBtnSegGroup.setTypeface(null, android.graphics.Typeface.BOLD);

            mBtnSegFlat.setBackgroundColor(Color.TRANSPARENT);
            mBtnSegFlat.setTextColor(ContextCompat.getColor(getContext(), R.color.showlog_text_secondary));
            mBtnSegFlat.setTypeface(null, android.graphics.Typeface.NORMAL);
        }
    }

    private void updateSortButtonUI() {
        mBtnToggleSort.setText(mIsSortAsc ? R.string.showlog_sort_asc_label : R.string.showlog_sort_desc_label);
    }

    private void applyFilterAndRender() {
        mFilteredList.clear();
        int totalRaw = mRawList.size();
        int successCount = 0;
        int failedCount = 0;

        for (HttpLogRecord record : mRawList) {
            boolean isSuccess = record.success;
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

            // 搜索关键字筛选
            if (!mSearchKeyword.isEmpty()) {
                String kw = mSearchKeyword.toLowerCase(Locale.ROOT);
                boolean matchUrl = record.url != null && record.url.toLowerCase(Locale.ROOT).contains(kw);
                boolean matchHost = record.host != null && record.host.toLowerCase(Locale.ROOT).contains(kw);
                boolean matchPath = record.path != null && record.path.toLowerCase(Locale.ROOT).contains(kw);
                boolean matchStatus = String.valueOf(record.statusCode).contains(kw);
                boolean matchMsg = record.message != null && record.message.toLowerCase(Locale.ROOT).contains(kw);
                if (!matchUrl && !matchHost && !matchPath && !matchStatus && !matchMsg) {
                    continue;
                }
            }
            mFilteredList.add(record);
        }

        mTvTotalSummary.setText(totalRaw + "条");
        updateFilterTabsUI(totalRaw, successCount, failedCount);

        if (mFilteredList.isEmpty()) {
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

    private void updateFilterTabsUI(int total, int success, int error) {
        mTabFilterAll.setText("全部 (" + total + ")");
        mTabFilter2xx.setText("成功 (" + success + ")");
        mTabFilterError.setText("失败/异常 (" + error + ")");

        mTabFilterAll.setBackgroundResource(R.drawable.bg_showlog_tab_unselected);
        mTabFilterAll.setTextColor(ContextCompat.getColor(getContext(), R.color.showlog_text_secondary));
        mTabFilterAll.setTypeface(null, android.graphics.Typeface.NORMAL);

        mTabFilter2xx.setBackgroundResource(R.drawable.bg_showlog_tab_unselected);
        mTabFilter2xx.setTextColor(ContextCompat.getColor(getContext(), R.color.showlog_text_secondary));
        mTabFilter2xx.setTypeface(null, android.graphics.Typeface.NORMAL);

        mTabFilterError.setBackgroundResource(R.drawable.bg_showlog_tab_unselected);
        mTabFilterError.setTextColor(error > 0 ? ContextCompat.getColor(getContext(), R.color.showlog_status_error)
                : ContextCompat.getColor(getContext(), R.color.showlog_text_secondary));
        mTabFilterError.setTypeface(null, android.graphics.Typeface.NORMAL);

        switch (mCurrentFilter) {
            case FILTER_ALL:
                mTabFilterAll.setBackgroundResource(R.drawable.bg_showlog_tab_selected);
                mTabFilterAll.setTextColor(Color.parseColor("#FF0F172A"));
                mTabFilterAll.setTypeface(null, android.graphics.Typeface.BOLD);
                break;
            case FILTER_2XX:
                mTabFilter2xx.setBackgroundResource(R.drawable.bg_showlog_tab_selected);
                mTabFilter2xx.setTextColor(Color.parseColor("#FF0F172A"));
                mTabFilter2xx.setTypeface(null, android.graphics.Typeface.BOLD);
                break;
            case FILTER_ERROR:
                mTabFilterError.setBackgroundResource(R.drawable.bg_showlog_tab_error_selected);
                mTabFilterError.setTextColor(Color.WHITE);
                mTabFilterError.setTypeface(null, android.graphics.Typeface.BOLD);
                break;
        }
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
                    mTvTotalSummary.setText(mRawList.size() + "条");
                    updateFilterTabsUI(mRawList.size(), calculate2xxCount(mRawList), calculateFailedCount(mRawList));
                    mTvEmptyView.setVisibility(View.GONE);
                    mRvLogs.setVisibility(View.VISIBLE);
                } else {
                    mRawList.add(record);
                    applyFilterAndRender();
                }
            }
        });
    }

    private int calculate2xxCount(@NonNull List<HttpLogRecord> list) {
        int count = 0;
        for (HttpLogRecord r : list) {
            if (r.statusCode >= 200 && r.statusCode < 300) count++;
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
