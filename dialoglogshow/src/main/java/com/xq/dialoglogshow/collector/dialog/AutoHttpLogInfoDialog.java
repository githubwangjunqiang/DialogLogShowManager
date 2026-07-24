package com.xq.dialoglogshow.collector.dialog;

import android.app.Dialog;
import android.content.Context;
import android.view.View;

import com.xq.dialoglogshow.R;
import com.xq.dialoglogshow.collector.model.AutoHttpLogModel;
import com.xq.dialoglogshow.utils.ClipboardUtils;
import com.xq.dialoglogshow.utils.ShareUtils;
import com.xq.dialoglogshow.view.BigTextView;

/**
 * 自动网络日志专用的独立详情弹窗
 *
 * 为什么这样做：
 * 1. 彻底与老的 DialogShowLogInfo 解耦，保证自动网络日志架构职责单一、独立维护。
 * 2. 直观展现由 AutoHttpLogModel 导出的全套请求概览、Headers、Body 与 cURL 指令。
 * 3. 遵照项目规范对点击事件进行 500ms 防抖处理。
 *
 * Author: 王俊强 980766134@qq.com
 * Date: 2026/07/24
 */
public class AutoHttpLogInfoDialog extends Dialog {

    private final AutoHttpLogModel mLogModel;
    private final String mFullContent;
    private long mLastClickTime = 0;

    public AutoHttpLogInfoDialog(Context context, AutoHttpLogModel logModel) {
        super(context, R.style.dialog_httplog);
        setContentView(R.layout.show_log_sdk_cache_log_info_layout);
        this.mLogModel = logModel;
        this.mFullContent = logModel != null ? logModel.getFullDetailText() : "";
        initView();
        setListener();
        setCanceledOnTouchOutside(true);
    }

    private void initView() {
        BigTextView textView = findViewById(R.id.show_app_dialog_cache_file_log_tv_title);
        if (textView != null) {
            textView.setMessage(mFullContent);
            textView.setOnLongClickListener(v -> {
                performCopyAndShare(v.getContext());
                return true;
            });
        }
    }

    private void setListener() {
        View btnClose1 = findViewById(R.id.show_app_dialog_confirm_btnok);
        View btnClose2 = findViewById(R.id.show_app_dialog_confirm_btnok2);
        View btnShare = findViewById(R.id.show_app_dialog_confirm_btnshare);

        if (btnClose1 != null) {
            btnClose1.setOnClickListener(v -> {
                if (isFastClick()) return;
                dismiss();
            });
        }

        if (btnClose2 != null) {
            btnClose2.setOnClickListener(v -> {
                if (isFastClick()) return;
                dismiss();
            });
        }

        if (btnShare != null) {
            btnShare.setOnClickListener(v -> {
                if (isFastClick()) return;
                performCopyAndShare(v.getContext());
            });
        }
    }

    /**
     * 复制并分享详情文本
     */
    private void performCopyAndShare(Context context) {
        if (context == null || mFullContent == null) return;
        Context appContext = context.getApplicationContext();
        ClipboardUtils.copyText(mFullContent, appContext);
        ShareUtils.shareText(appContext, mFullContent);
    }

    /**
     * 点击防抖校验 (500ms 规则)
     */
    private boolean isFastClick() {
        long now = System.currentTimeMillis();
        if (now - mLastClickTime < 500) {
            return true;
        }
        mLastClickTime = now;
        return false;
    }
}
