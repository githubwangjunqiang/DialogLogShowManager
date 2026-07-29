package com.xq.dialoglogshow.collector.vpn;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.xq.dialoglogshow.ShowLog;

/**
 * VPN 健康检查监视器
 *
 * 为什么这样做：
 * 1. 单靠冷启动恢复无法覆盖“App 还活着但 VPN 在运行中被系统悄悄回收”的场景，因此需要再补一层前后台切换检查和定时巡检。
 * 2. 这里统一收口前台切换与 5 秒轮询，避免多个类各自起 Timer/Handler 互相打架，导致重复拉起 VPN。
 */
public final class VpnHealthMonitor {

    private static final long POLL_INTERVAL_MS = 5_000L;

    private static volatile VpnHealthMonitor sInstance;

    /**
     * 为什么这样做：健康检查频率固定且逻辑很轻，直接挂主线程 Handler 足够稳定，也避免再引入新的线程生命周期管理成本。
     */
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final Runnable mHealthCheckRunnable = new Runnable() {
        @Override
        public void run() {
            Context appContext = ShowLog.getApplicationInternal();
            if (appContext == null) {
                return;
            }
            if (shouldKeepMonitoring(appContext)) {
                VpnController.getInstance().ensureVpnRunningIfNeeded(appContext, "poll_5s");
                mMainHandler.postDelayed(this, POLL_INTERVAL_MS);
            } else {
                mPolling = false;
            }
        }
    };

    private boolean mAppInForeground = false;
    private boolean mPolling = false;

    private VpnHealthMonitor() {
    }

    @NonNull
    public static VpnHealthMonitor getInstance() {
        if (sInstance == null) {
            synchronized (VpnHealthMonitor.class) {
                if (sInstance == null) {
                    sInstance = new VpnHealthMonitor();
                }
            }
        }
        return sInstance;
    }

    /**
     * 初始化或配置变更后刷新监视状态
     */
    public void refresh(@NonNull Context context) {
        if (shouldKeepMonitoring(context)) {
            startPolling();
            return;
        }
        stopPolling();
    }

    /**
     * 宿主切回前台时立即做一次健康检查
     */
    public void onAppForeground(@NonNull Context context) {
        mAppInForeground = true;
        if (!shouldKeepMonitoring(context)) {
            return;
        }
        VpnController.getInstance().ensureVpnRunningIfNeeded(context, "app_foreground");
        startPolling();
    }

    /**
     * 宿主进入后台时保留轻量巡检
     * 为什么这样做：只要进程还活着，后台阶段仍可能被系统回收 VPN 服务，因此不应该把巡检完全停掉。
     */
    public void onAppBackground(@NonNull Context context) {
        mAppInForeground = false;
        // 为什么这样做：Android 12+ 不允许普通后台状态反复拉起前台服务；已运行 VPN 自行维持，恢复动作延迟到下次回前台。
        stopPolling();
    }

    /**
     * Service 异常停止后的立即补偿检查
     * 为什么这样做：如果 VPN 在进程仍存活时被系统或 ROM 突然回收，只等下一轮 5 秒轮询会有短暂漏抓窗口，
     * 这里补一个延迟很短的主动检查，可以更快把抓包链路拉回稳定态。
     */
    public void triggerImmediateCheck(@NonNull Context context) {
        if (!shouldKeepMonitoring(context)) {
            return;
        }
        mMainHandler.removeCallbacks(mHealthCheckRunnable);
        mMainHandler.postDelayed(mHealthCheckRunnable, 1_000L);
    }

    private boolean shouldKeepMonitoring(@NonNull Context context) {
        if (!ShowLog.isVpnNetworkLogEnabledInternal()) {
            return false;
        }
        if (!VpnSessionStore.isUserEnabled(context)) {
            return false;
        }
        // 为什么这样做：前台可见状态是 Android 12+ 最稳定的 FGS 启动窗口，后台只保留已运行服务，不再主动恢复。
        return mAppInForeground;
    }

    private void startPolling() {
        if (mPolling) {
            return;
        }
        mPolling = true;
        mMainHandler.removeCallbacks(mHealthCheckRunnable);
        mMainHandler.postDelayed(mHealthCheckRunnable, POLL_INTERVAL_MS);
    }

    private void stopPolling() {
        mPolling = false;
        mMainHandler.removeCallbacks(mHealthCheckRunnable);
    }
}
