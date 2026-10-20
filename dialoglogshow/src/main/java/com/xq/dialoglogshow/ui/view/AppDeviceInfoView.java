package com.xq.dialoglogshow.ui.view;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.xq.dialoglogshow.R;
import com.xq.dialoglogshow.core.collector.AppDeviceInfoCollector;
import com.xq.dialoglogshow.core.collector.AppDeviceInfoExporter;
import com.xq.dialoglogshow.core.model.AppDeviceInfo;
import com.xq.dialoglogshow.ui.adapter.AppDeviceInfoAdapter;

/**
 * 设备与应用全景信息看板主视图组件。
 * 为什么这样做：
 * 抽离为独立的 View 组件，既能在主中枢（Hub）中作为同级核心页面直接承载，
 * 也能单独挂载，涵盖 48 项诊断指标、实时刷新、分类 Tab 切换、搜索过滤与 Markdown 报告导出。
 */
public final class AppDeviceInfoView extends FrameLayout {

    private static final long THROTTLE_MS = 350L;
    private long mLastClickTime = 0L;

    private TextView mTvItemsCount;
    private EditText mEtSearch;
    private RecyclerView mRvItems;
    private TextView mTvEmptyView;

    private TextView mTabAll;
    private TextView mTabApp;
    private TextView mTabOs;
    private TextView mTabHardware;
    private TextView mTabPerformance;

    private AppDeviceInfoAdapter mAdapter;
    private AppDeviceInfo mCurrentInfo;

    public AppDeviceInfoView(@NonNull Context context) {
        this(context, null);
    }

    public AppDeviceInfoView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppDeviceInfoView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        LayoutInflater.from(context).inflate(R.layout.show_layout_app_device_info, this, true);
        initViews();
        loadData();
    }

    private void initViews() {
        mTvItemsCount = findViewById(R.id.tv_info_items_count);
        mEtSearch = findViewById(R.id.et_search_device_info);
        mRvItems = findViewById(R.id.rv_device_info_items);
        mTvEmptyView = findViewById(R.id.tv_device_info_empty);

        mTabAll = findViewById(R.id.tab_category_all);
        mTabApp = findViewById(R.id.tab_category_app);
        mTabOs = findViewById(R.id.tab_category_os);
        mTabHardware = findViewById(R.id.tab_category_hardware);
        mTabPerformance = findViewById(R.id.tab_category_performance);

        // 刷新按钮
        findViewById(R.id.btn_refresh_info).setOnClickListener(v -> {
            if (isThrottled()) return;
            loadData();
            Toast.makeText(getContext(), "设备与运行状态已刷新", Toast.LENGTH_SHORT).show();
        });

        // 导出 Markdown 报告按钮
        findViewById(R.id.btn_export_markdown_report).setOnClickListener(v -> {
            if (isThrottled() || mCurrentInfo == null) return;
            String markdown = AppDeviceInfoExporter.exportToMarkdown(mCurrentInfo);
            ClipboardManager cm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("AppDeviceInfoReport", markdown));
                Toast.makeText(getContext(), "📋 全景诊断报告 (Markdown) 已复制到剪贴板！", Toast.LENGTH_LONG).show();
            }
        });

        // 分类切换监听
        mTabAll.setOnClickListener(v -> switchCategory(mTabAll, "全部"));
        mTabApp.setOnClickListener(v -> switchCategory(mTabApp, AppDeviceInfo.CATEGORY_APP));
        mTabOs.setOnClickListener(v -> switchCategory(mTabOs, AppDeviceInfo.CATEGORY_OS));
        mTabHardware.setOnClickListener(v -> switchCategory(mTabHardware, AppDeviceInfo.CATEGORY_HARDWARE));
        mTabPerformance.setOnClickListener(v -> switchCategory(mTabPerformance, AppDeviceInfo.CATEGORY_PERFORMANCE));

        // 搜索框过滤监听
        mEtSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (mAdapter != null) {
                    mAdapter.setKeyword(s != null ? s.toString() : "");
                    updateEmptyView();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        mAdapter = new AppDeviceInfoAdapter();
        mRvItems.setLayoutManager(new LinearLayoutManager(getContext()));
        mRvItems.setAdapter(mAdapter);
    }

    private void switchCategory(TextView selectedTab, String category) {
        if (isThrottled()) return;

        // 重置所有 Tab 样式
        TextView[] tabs = new TextView[]{mTabAll, mTabApp, mTabOs, mTabHardware, mTabPerformance};
        for (TextView tab : tabs) {
            tab.setBackgroundResource(R.drawable.bg_showlog_tab_unselected);
            tab.setTextColor(ContextCompat.getColor(getContext(), R.color.showlog_text_secondary));
            tab.setTypeface(null, android.graphics.Typeface.NORMAL);
        }

        // 高亮当前选中的 Tab
        selectedTab.setBackgroundResource(R.drawable.bg_showlog_tab_selected);
        selectedTab.setTextColor(Color.parseColor("#FF0F172A"));
        selectedTab.setTypeface(null, android.graphics.Typeface.BOLD);

        if (mAdapter != null) {
            mAdapter.setCategory(category);
            updateEmptyView();
        }
    }

    public void loadData() {
        mCurrentInfo = AppDeviceInfoCollector.collect(getContext());
        if (mAdapter != null) {
            mAdapter.setData(mCurrentInfo.allItems);
            mTvItemsCount.setText(mCurrentInfo.allItems.size() + "项");
            updateEmptyView();
        }
    }

    private void updateEmptyView() {
        if (mAdapter != null) {
            boolean isEmpty = mAdapter.getDisplayItemCount() == 0;
            mTvEmptyView.setVisibility(isEmpty ? VISIBLE : GONE);
            mRvItems.setVisibility(isEmpty ? GONE : VISIBLE);
        }
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
