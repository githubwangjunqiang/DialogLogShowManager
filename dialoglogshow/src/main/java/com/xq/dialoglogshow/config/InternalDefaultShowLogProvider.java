package com.xq.dialoglogshow.config;

import android.app.Dialog;
import android.content.Context;
import android.graphics.RectF;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.xq.dialoglogshow.entity.BaseShowData;
import com.xq.dialoglogshow.entity.HttpLogData;
import com.xq.dialoglogshow.entity.PushData;
import com.xq.dialoglogshow.utils.AppUtils;

import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * Description: SDK 内部统一默认提供者与代理装饰器。
 * 为什么这样做：统一收拢 SDK 全局所有缺省默认值（如路径、线程池、默认尺寸、标题等），
 * 并包装开发者传入的 ShowLogProvider，实现 100% 安全的防空兜底，确保 SDK 内部消费数据时永无 NPE。
 *
 * Author: 王俊强 980766134@qq.com
 * Date: 2026/07/22
 */
public class InternalDefaultShowLogProvider implements ShowLogProvider {

    private final Context appContext;
    private final ShowLogProvider delegate;
    private final Executor defaultExecutor;

    public InternalDefaultShowLogProvider(@NonNull Context context, @Nullable ShowLogProvider userProvider) {
        this.appContext = context.getApplicationContext();
        this.delegate = userProvider;
        this.defaultExecutor = new ScheduledThreadPoolExecutor(1);
    }

    @Override
    public boolean isDebug() {
        return delegate != null && delegate.isDebug();
    }

    @NonNull
    @Override
    public String getCacheDocPath() {
        if (delegate != null && delegate.getCacheDocPath() != null) {
            return delegate.getCacheDocPath();
        }
        return appContext.getCacheDir().getAbsolutePath();
    }

    @Override
    public boolean isSortUrlForTime() {
        return delegate != null && delegate.isSortUrlForTime();
    }

    @NonNull
    @Override
    public Executor getLoadExecutor() {
        if (delegate != null && delegate.getLoadExecutor() != null) {
            return delegate.getLoadExecutor();
        }
        return defaultExecutor;
    }

    @Override
    public boolean isShowVersionCodeView() {
        return delegate != null && delegate.isShowVersionCodeView();
    }

    @Nullable
    @Override
    public String getShowVersionCodeText() {
        if (delegate != null && delegate.getShowVersionCodeText() != null) {
            return delegate.getShowVersionCodeText();
        }
        return null;
    }

    @Nullable
    @Override
    public RectF getDebugViewSize() {
        if (delegate != null) {
            return delegate.getDebugViewSize();
        }
        return null;
    }

    @Nullable
    @Override
    public View getCustomEntranceView(@NonNull Context context) {
        if (delegate != null) {
            return delegate.getCustomEntranceView(context);
        }
        return null;
    }

    @NonNull
    @Override
    public String getConsoleTitle() {
        if (delegate != null && delegate.getConsoleTitle() != null) {
            return delegate.getConsoleTitle();
        }
        return "Geek Console";
    }

    @Nullable
    @Override
    public VpnPayloadDecryptor getVpnPayloadDecryptor() {
        if (delegate != null) {
            return delegate.getVpnPayloadDecryptor();
        }
        return null;
    }

    @NonNull
    @Override
    public ArrayList<HttpLogData> provideHttpLogs(long startTime, long endTime) {
        if (delegate != null) {
            ArrayList<HttpLogData> list = delegate.provideHttpLogs(startTime, endTime);
            if (list != null) {
                return list;
            }
        }
        return new ArrayList<>();
    }

    @Override
    public void setupCustomTab(@NonNull FrameLayout container, @NonNull Dialog dialog) {
        if (delegate != null) {
            delegate.setupCustomTab(container, dialog);
        }
        if (container.getChildCount() == 0) {
            // 为什么这样做：当宿主未自定义设置 Tab 视图时，默认展示包含 Process ABI、APK ABI 及硬件 ABI 在内的完整 App 系统诊断报告
            android.widget.ScrollView scrollView = new android.widget.ScrollView(container.getContext());
            scrollView.setPadding(30, 30, 30, 30);

            android.widget.TextView textView = new android.widget.TextView(container.getContext());
            textView.setTextSize(13f);
            textView.setTextColor(0xFF333333);
            textView.setTextIsSelectable(true);
            textView.setText(com.xq.dialoglogshow.utils.AppUtils.getAppDiagnosticReport(container.getContext()));

            scrollView.addView(textView);
            container.addView(scrollView);
        }
    }

    @NonNull
    @Override
    public ArrayList<BaseShowData> provideKeyValueData() {
        if (delegate != null) {
            ArrayList<BaseShowData> list = delegate.provideKeyValueData();
            if (list != null) {
                return list;
            }
        }
        return new ArrayList<>();
    }

    @NonNull
    @Override
    public ArrayList<PushData> providePushLogs() {
        if (delegate != null) {
            ArrayList<PushData> list = delegate.providePushLogs();
            if (list != null) {
                return list;
            }
        }
        return new ArrayList<>();
    }

    @NonNull
    @Override
    public ArrayList<BaseShowData> provideUserInfo() {
        if (delegate != null) {
            ArrayList<BaseShowData> list = delegate.provideUserInfo();
            if (list != null) {
                return list;
            }
        }
        return new ArrayList<>();
    }

    @NonNull
    @Override
    public String provideAllCacheDocPath() {
        if (delegate != null && delegate.provideAllCacheDocPath() != null) {
            return delegate.provideAllCacheDocPath();
        }
        return getCacheDocPath();
    }

    @NonNull
    @Override
    public ArrayList<BaseShowData> provideRestData() {
        if (delegate != null) {
            ArrayList<BaseShowData> list = delegate.provideRestData();
            if (list != null) {
                return list;
            }
        }
        return new ArrayList<>();
    }

    @Override
    public boolean onDeleteHttpLog(@NonNull BaseShowData data) {
        return delegate != null && delegate.onDeleteHttpLog(data);
    }

    @Override
    public boolean onClearAllHttpLogs() {
        return delegate != null && delegate.onClearAllHttpLogs();
    }

    @Override
    public void onOpenTextFile(@NonNull String filePath) {
        if (delegate != null) {
            delegate.onOpenTextFile(filePath);
        }
    }

    @Override
    public void onShareFile(@NonNull Context context, @NonNull String filePath) {
        if (delegate != null) {
            delegate.onShareFile(context, filePath);
        }
    }

    @Override
    public void onConsoleShow() {
        if (delegate != null) {
            delegate.onConsoleShow();
        }
    }

    @Override
    public void onConsoleDismiss() {
        if (delegate != null) {
            delegate.onConsoleDismiss();
        }
    }
}
