package com.xq.dialoglogshow.ui.dialog;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.xq.dialoglogshow.R;
import com.xq.dialoglogshow.core.collector.AppDeviceInfoCollector;
import com.xq.dialoglogshow.core.collector.AppDeviceInfoExporter;
import com.xq.dialoglogshow.core.model.AppDeviceInfo;
import com.xq.dialoglogshow.ui.adapter.AppDeviceInfoAdapter;

/**
 * 设备与应用全景信息看板对话框。
 * 为什么这样做：
 * 1. 采用与网络控制台一致的暗黑科技风设计，视觉统一；
 * 2. 具备实时分类切换、关键词过滤、一键刷新与导出 Markdown 诊断报告功能；
 * 3. 彻底与网络日志解耦，既可独立调起，也可与网络日志无缝互通。
 */
public final class AppDeviceInfoDialog extends Dialog {

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

    public AppDeviceInfoDialog(@NonNull Context context) {
        super(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.show_dialog_app_device_info);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.96);
            params.height = (int) (getContext().getResources().getDisplayMetrics().heightPixels * 0.90);
            window.setAttributes(params);
        }

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

        // 关闭按钮
        findViewById(R.id.btn_close_device_info).setOnClickListener(v -> dismiss());

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

    private void switchCategory(@NonNull TextView selectedTab, @NonNull String category) {
        if (isThrottled()) return;

        // 重置所有 Tab 样式
        TextView[] allTabs = new TextView[]{mTabAll, mTabApp, mTabOs, mTabHardware, mTabPerformance};
        for (TextView tab : allTabs) {
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

    private void loadData() {
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
            mTvEmptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            mRvItems.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
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
