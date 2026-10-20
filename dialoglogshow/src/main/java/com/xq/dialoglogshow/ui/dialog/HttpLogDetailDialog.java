package com.xq.dialoglogshow.ui.dialog;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.xq.dialoglogshow.R;
import com.xq.dialoglogshow.core.model.HttpLogRecord;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 全新 HTTP 网络日志详情弹窗。
 * 为什么这样做：
 * 1. 采用 Overview、Request、Response、Error 四级分 Tab 浏览，结构清晰；
 * 2. 内置 JSON 安全美化缩进器，报文可读性高；
 * 3. 自动根据请求头与请求体组装标准 cURL 重放命令，极大便利开发者在终端或 Postman 复制复现。
 */
public final class HttpLogDetailDialog extends Dialog {

    private static final int TAB_OVERVIEW = 0;
    private static final int TAB_REQUEST = 1;
    private static final int TAB_RESPONSE = 2;
    private static final int TAB_ERROR = 3;

    private static final long THROTTLE_MS = 350L;
    private long mLastClickTime = 0L;

    private final HttpLogRecord mRecord;
    private int mCurrentTab = TAB_OVERVIEW;

    private TextView mTvStatus;
    private TextView mTvMethod;
    private TextView mTvFullUrl;
    private TextView mTabOverview;
    private TextView mTabRequest;
    private TextView mTabResponse;
    private TextView mTabError;
    private TextView mTvContent;
    private TextView mBtnCopyCurrentTab;

    public HttpLogDetailDialog(@NonNull Context context, @NonNull HttpLogRecord record) {
        super(context);
        this.mRecord = record;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.show_dialog_http_log_detail);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.95);
            params.height = (int) (getContext().getResources().getDisplayMetrics().heightPixels * 0.88);
            window.setAttributes(params);
        }

        initViews();
        bindData();
    }

    private void initViews() {
        mTvStatus = findViewById(R.id.tv_detail_status);
        mTvMethod = findViewById(R.id.tv_detail_method);
        mTvFullUrl = findViewById(R.id.tv_detail_full_url);
        mTabOverview = findViewById(R.id.tab_overview);
        mTabRequest = findViewById(R.id.tab_request);
        mTabResponse = findViewById(R.id.tab_response);
        mTabError = findViewById(R.id.tab_error);
        mTvContent = findViewById(R.id.tv_detail_content);
        mBtnCopyCurrentTab = findViewById(R.id.btn_copy_current_tab);

        findViewById(R.id.btn_close_detail).setOnClickListener(v -> dismiss());

        findViewById(R.id.btn_copy_url).setOnClickListener(v -> {
            if (isThrottled()) return;
            copyToClipboard("URL", mRecord.url);
        });

        findViewById(R.id.btn_copy_curl).setOnClickListener(v -> {
            if (isThrottled()) return;
            copyToClipboard("cURL", buildCurlCommand(mRecord));
        });

        mTabOverview.setOnClickListener(v -> switchTab(TAB_OVERVIEW));
        mTabRequest.setOnClickListener(v -> switchTab(TAB_REQUEST));
        mTabResponse.setOnClickListener(v -> switchTab(TAB_RESPONSE));
        mTabError.setOnClickListener(v -> switchTab(TAB_ERROR));

        mBtnCopyCurrentTab.setOnClickListener(v -> {
            if (isThrottled()) return;
            copyToClipboard("Content", mTvContent.getText().toString());
        });
    }

    private void bindData() {
        mTvMethod.setText(mRecord.method);
        mTvFullUrl.setText(mRecord.url);

        if (!mRecord.success) {
            mTvStatus.setText(mRecord.statusCode < 0 ? "FAIL" : (mRecord.statusCode + " (FAIL)"));
            mTvStatus.setTextColor(ContextCompat.getColor(getContext(), R.color.showlog_status_error));
        } else {
            mTvStatus.setText(String.valueOf(mRecord.statusCode));
            if (mRecord.statusCode >= 200 && mRecord.statusCode < 300) {
                mTvStatus.setTextColor(ContextCompat.getColor(getContext(), R.color.showlog_status_2xx));
            } else if (mRecord.statusCode >= 300 && mRecord.statusCode < 400) {
                mTvStatus.setTextColor(ContextCompat.getColor(getContext(), R.color.showlog_status_3xx));
            } else if (mRecord.statusCode >= 400 && mRecord.statusCode < 500) {
                mTvStatus.setTextColor(ContextCompat.getColor(getContext(), R.color.showlog_status_4xx));
            } else {
                mTvStatus.setTextColor(ContextCompat.getColor(getContext(), R.color.showlog_status_5xx));
            }
        }

        if ((mRecord.errorMessage != null && !mRecord.errorMessage.trim().isEmpty()) || !mRecord.success) {
            mTabError.setVisibility(View.VISIBLE);
        } else {
            mTabError.setVisibility(View.GONE);
        }

        switchTab(TAB_OVERVIEW);
    }

    private void switchTab(int tab) {
        mCurrentTab = tab;
        resetTabStyles();

        int activeColor = ContextCompat.getColor(getContext(), R.color.showlog_text_primary);
        switch (tab) {
            case TAB_OVERVIEW:
                mTabOverview.setTextColor(activeColor);
                mTabOverview.setTypeface(null, android.graphics.Typeface.BOLD);
                renderOverview();
                break;
            case TAB_REQUEST:
                mTabRequest.setTextColor(activeColor);
                mTabRequest.setTypeface(null, android.graphics.Typeface.BOLD);
                renderRequest();
                break;
            case TAB_RESPONSE:
                mTabResponse.setTextColor(activeColor);
                mTabResponse.setTypeface(null, android.graphics.Typeface.BOLD);
                renderResponse();
                break;
            case TAB_ERROR:
                mTabError.setTextColor(ContextCompat.getColor(getContext(), R.color.showlog_status_error));
                mTabError.setTypeface(null, android.graphics.Typeface.BOLD);
                renderError();
                break;
        }
    }

    private void resetTabStyles() {
        int inactiveColor = ContextCompat.getColor(getContext(), R.color.showlog_text_tertiary);
        mTabOverview.setTextColor(inactiveColor);
        mTabOverview.setTypeface(null, android.graphics.Typeface.NORMAL);
        mTabRequest.setTextColor(inactiveColor);
        mTabRequest.setTypeface(null, android.graphics.Typeface.NORMAL);
        mTabResponse.setTextColor(inactiveColor);
        mTabResponse.setTypeface(null, android.graphics.Typeface.NORMAL);
        mTabError.setTextColor(ContextCompat.getColor(getContext(), R.color.showlog_status_error));
        mTabError.setTypeface(null, android.graphics.Typeface.NORMAL);
    }

    private void renderOverview() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
        String timeStr = sdf.format(new Date(mRecord.timestamp));

        StringBuilder sb = new StringBuilder();
        sb.append("=== 请求概览 (Overview) ===\n\n");
        sb.append("• 完整地址: ").append(mRecord.url).append("\n");
        sb.append("• 域名 Host: ").append(mRecord.host).append("\n");
        sb.append("• 接口 Path: ").append(mRecord.path).append("\n");
        sb.append("• 请求方式: ").append(mRecord.method).append("\n");
        sb.append("• 业务裁决: ").append(mRecord.success ? "✅ 成功 (SUCCESS)" : "❌ 失败 (FAILED)").append("\n");
        sb.append("• 业务提示: ").append(mRecord.message != null ? mRecord.message : "N/A").append("\n");
        sb.append("• 传输代码: ").append(mRecord.statusCode < 0 ? "FAILED (-1)" : mRecord.statusCode).append("\n");
        sb.append("• 耗时统计: ").append(mRecord.durationMs).append(" ms\n");
        sb.append("• 发起时间: ").append(timeStr).append("\n");
        sb.append("• 请求体大小: ").append(mRecord.requestSize).append(" bytes\n");
        sb.append("• 响应体大小: ").append(mRecord.responseSize).append(" bytes\n");
        sb.append("• 报文类型: ").append(mRecord.contentType != null ? mRecord.contentType : "N/A").append("\n");

        if (mRecord.errorMessage != null) {
            sb.append("\n⚠️ 异常警告:\n").append(mRecord.errorMessage).append("\n");
        }

        mTvContent.setText(sb.toString());
    }

    private void renderRequest() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 请求头 (Request Headers) ===\n\n");
        if (mRecord.requestHeaders != null && !mRecord.requestHeaders.trim().isEmpty()) {
            sb.append(mRecord.requestHeaders).append("\n");
        } else {
            sb.append("(Empty Headers)\n\n");
        }

        sb.append("=== 请求体 (Request Body) ===\n\n");
        if (mRecord.requestBody != null && !mRecord.requestBody.trim().isEmpty()) {
            sb.append(formatJsonSafe(mRecord.requestBody));
        } else {
            sb.append("(Empty Body)");
        }

        mTvContent.setText(sb.toString());
    }

    private void renderResponse() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 响应头 (Response Headers) ===\n\n");
        if (mRecord.responseHeaders != null && !mRecord.responseHeaders.trim().isEmpty()) {
            sb.append(mRecord.responseHeaders).append("\n");
        } else {
            sb.append("(Empty Headers)\n\n");
        }

        sb.append("=== 响应体 (Response Body) ===\n\n");
        if (mRecord.responseBody != null && !mRecord.responseBody.trim().isEmpty()) {
            sb.append(formatJsonSafe(mRecord.responseBody));
        } else {
            sb.append("(Empty Body)");
        }

        mTvContent.setText(sb.toString());
    }

    private void renderError() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 异常与失败诊断 (Error / Failure) ===\n\n");
        sb.append("• 业务状态: ").append(mRecord.success ? "成功" : "失败").append("\n");
        sb.append("• 提示信息: ").append(mRecord.message != null ? mRecord.message : "N/A").append("\n");
        if (mRecord.errorMessage != null && !mRecord.errorMessage.trim().isEmpty()) {
            sb.append("\n• 异常堆栈:\n").append(mRecord.errorMessage);
        }
        mTvContent.setText(sb.toString());
    }

    /**
     * 安全美化 JSON 文本，非合法 JSON 时原样返回。
     */
    @NonNull
    private static String formatJsonSafe(@NonNull String text) {
        String trimmed = text.trim();
        try {
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                return new JSONObject(trimmed).toString(2);
            } else if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                return new JSONArray(trimmed).toString(2);
            }
        } catch (Throwable ignored) {
        }
        return text;
    }

    /**
     * 根据请求记录自动构建标准可执行的 cURL 命令行。
     */
    @NonNull
    private static String buildCurlCommand(@NonNull HttpLogRecord record) {
        StringBuilder curl = new StringBuilder("curl -X ").append(record.method);
        if (record.requestHeaders != null) {
            String[] lines = record.requestHeaders.split("\n");
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    curl.append(" -H \"").append(line.trim().replace("\"", "\\\"")).append("\"");
                }
            }
        }
        if (record.requestBody != null && !record.requestBody.trim().isEmpty()) {
            curl.append(" --data-raw '").append(record.requestBody.replace("'", "'\\''")).append("'");
        }
        curl.append(" \"").append(record.url).append("\"");
        return curl.toString();
    }

    private void copyToClipboard(@NonNull String label, @NonNull String text) {
        ClipboardManager cm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(label, text));
            Toast.makeText(getContext(), R.string.showlog_copied_toast, Toast.LENGTH_SHORT).show();
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
