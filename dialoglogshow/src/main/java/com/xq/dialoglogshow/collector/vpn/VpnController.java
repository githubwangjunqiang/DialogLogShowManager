package com.xq.dialoglogshow.collector.vpn;

import android.app.Application;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.VpnService;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.xq.dialoglogshow.R;
import com.xq.dialoglogshow.activity.VpnPermissionActivity;
import com.xq.dialoglogshow.collector.vpn.netbare.ShowLogNetBareManager;
import com.xq.dialoglogshow.collector.vpn.tunnel.ShowLogVpnService;
import com.xq.dialoglogshow.manager.ShowLogManager;

import java.util.List;

/**
 * VPN 生命周期控制器
 *
 * 为什么这样做：
 * 1. 即使底层已经切到 NetBare，控制台、权限页和 SDK 外部入口仍然应该只面对同一个控制器，避免 UI 与实现细节重新耦合。
 * 2. 这里统一串起“证书安装 -> VPN 系统授权 -> NetBare 启动”三段流程，开发者只需要继续使用原来的 SDK 开关与控制台入口。
 */
public class VpnController {

    private static final long MIN_AUTO_RESTORE_INTERVAL_MS = 10_000L;
    private static final long AUTO_RESTORE_COOLDOWN_MS = 60_000L;
    private static final int MAX_AUTO_RESTORE_FAIL_COUNT = 3;

    /**
     * 为什么这样做：VPN 状态栏和 SDK 内部回调并不总能严格同一时刻更新，
     * 这里给“最近流量仍在持续产生”预留一段容忍窗口，用来把短暂抖动识别为仍在运行。
     */
    private static final long ACTIVE_TRAFFIC_GRACE_MS = 15_000L;

    private static volatile VpnController sInstance;

    /**
     * VPN 运行态快照
     *
     * 为什么这样做：开发者看到的是“现在到底有没有在抓”，因此状态判断不能只依赖单一内存变量，
     * 需要把系统 VPN、前台 Service、NetBare 内部活跃态和最近流量统一折叠成一个只读快照。
     */
    public static final class RuntimeState {
        public final boolean permissionGranted;
        public final boolean systemVpnConnected;
        public final boolean netBareActive;
        public final boolean serviceRunning;
        public final boolean recentTrafficActive;
        public final boolean sdkLikelyRunning;
        public final boolean systemConnectedButSdkUnsynced;

        RuntimeState(boolean permissionGranted,
                     boolean systemVpnConnected,
                     boolean netBareActive,
                     boolean serviceRunning,
                     boolean recentTrafficActive) {
            this.permissionGranted = permissionGranted;
            this.systemVpnConnected = systemVpnConnected;
            this.netBareActive = netBareActive;
            this.serviceRunning = serviceRunning;
            this.recentTrafficActive = recentTrafficActive;
            this.sdkLikelyRunning = netBareActive || serviceRunning || recentTrafficActive;
            this.systemConnectedButSdkUnsynced = systemVpnConnected && !this.sdkLikelyRunning;
        }
    }

    private VpnController() {
    }

    @NonNull
    public static VpnController getInstance() {
        if (sInstance == null) {
            synchronized (VpnController.class) {
                if (sInstance == null) {
                    sInstance = new VpnController();
                }
            }
        }
        return sInstance;
    }

    /**
     * 检查当前是否已经具备 VPN 启动权限
     */
    public boolean isVpnPrepared(@NonNull Context context) {
        return ShowLogNetBareManager.getInstance().getPrepareIntent() == null;
    }

    /**
     * 获取系统 VPN 权限申请 Intent
     */
    @Nullable
    public Intent getPrepareIntent(@NonNull Context context) {
        return ShowLogNetBareManager.getInstance().getPrepareIntent();
    }

    /**
     * 启动 VPN
     */
    public void startVpn(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        ShowLogNetBareManager netBareManager = ShowLogNetBareManager.getInstance();
        Intent prepareIntent = getPrepareIntent(appContext);
        if (prepareIntent != null) {
            VpnStatusManager.getInstance().updateStatus(VpnStatusManager.Status.PREPARE_REQUIRED, null);
            Intent permissionIntent = new Intent(appContext, VpnPermissionActivity.class);
            permissionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            appContext.startActivity(permissionIntent);
            return;
        }
        // 为什么这样做：一旦用户已经完成过系统授权并明确点击开启，SDK 之后就应该把“自动恢复”视为默认期望，避免每次冷启动都再次漏抓日志。
        VpnSessionStore.setUserEnabled(appContext, true);
        VpnSessionStore.resetRestoreThrottle(appContext);
        VpnStatusManager.getInstance().updateStatus(VpnStatusManager.Status.STARTING, null);
        netBareManager.startCapture(appContext);
        VpnHealthMonitor.getInstance().refresh(appContext);
    }

    /**
     * 发起 HTTPS 调试证书安装
     *
     * 为什么这样做：
     * 1. VPN 抓包主链路不再被证书安装卡死，但开发者仍然需要一个明确入口升级到 HTTPS 明文能力。
     * 2. 继续经由控制器统一转发，避免 UI 直接依赖 NetBare 细节。
     */
    public void requestCertificateInstall(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        ShowLogNetBareManager netBareManager = ShowLogNetBareManager.getInstance();
        if (netBareManager.isCertificateInstalled(appContext)) {
            return;
        }
        if (!netBareManager.isCertificateFileReady()) {
            VpnStatusManager.getInstance().updateStatus(
                    VpnStatusManager.Status.ERROR,
                    appContext.getString(R.string.show_sdk_vpn_certificate_generating)
            );
            return;
        }
        VpnStatusManager.getInstance().updateStatus(
                VpnStatusManager.Status.ERROR,
                appContext.getString(R.string.show_sdk_vpn_certificate_required)
        );
        netBareManager.installCertificate(context);
    }

    /**
     * 当前是否已完成证书安装
     */
    public boolean isCertificateInstalled(@NonNull Context context) {
        return ShowLogNetBareManager.getInstance().isCertificateInstalled(context.getApplicationContext());
    }

    /**
     * 停止 VPN
     */
    public void stopVpn(@NonNull Context context) {
        // 为什么这样做：只有用户主动点了“停止”，才应该清掉自动恢复意图；否则进程被系统回收后的临时中断不应被当成用户取消。
        Context appContext = context.getApplicationContext();
        VpnSessionStore.setUserEnabled(appContext, false);
        VpnSessionStore.resetRestoreThrottle(appContext);
        ShowLogNetBareManager.getInstance().stopCapture();
        // 为什么这样做：用户点击“停止”后希望控制台立即反馈，而不是依赖异步 Service 回调慢一拍再刷新。
        VpnStatusManager.getInstance().updateStatus(VpnStatusManager.Status.STOPPED, null);
        VpnHealthMonitor.getInstance().refresh(appContext);
    }

    /**
     * 应用冷启动时按用户上一次选择自动恢复 VPN
     * 为什么这样做：SDK 形态下让开发者和测试同学每次启动 App 都手动点一次 VPN 成本太高，
     * 因此这里把“首次授权一次，之后自动恢复”的策略集中到初始化阶段统一执行。
     */
    public void tryAutoRestoreVpn(@NonNull Context context) {
        ensureVpnRunningIfNeeded(context, "cold_start");
    }

    /**
     * 统一的自动恢复入口
     * 为什么这样做：冷启动、回前台、5 秒轮询和后续其它补偿策略都应该复用同一套限流与权限判断，避免各入口行为不一致。
     */
    public void ensureVpnRunningIfNeeded(@NonNull Context context, @NonNull String reason) {
        Context appContext = context.getApplicationContext();
        if (!ShowLogManager.getInstance().isVpnNetworkLogEnabled()) {
            return;
        }
        if (!VpnSessionStore.isUserEnabled(appContext)) {
            return;
        }
        RuntimeState runtimeState = inspectRuntimeState(appContext);
        syncRuntimeStatus(appContext, runtimeState);
        if (runtimeState.sdkLikelyRunning || runtimeState.systemVpnConnected) {
            VpnSessionStore.resetRestoreThrottle(appContext);
            return;
        }
        if (!runtimeState.permissionGranted) {
            // 为什么这样做：冷启动阶段不能擅自弹系统授权页打断宿主流程，因此这里只保留“待授权”状态，等开发者或测试再手动点一次。
            VpnStatusManager.getInstance().updateStatus(VpnStatusManager.Status.PREPARE_REQUIRED, null);
            return;
        }
        long now = System.currentTimeMillis();
        long lastRestoreAttemptTime = VpnSessionStore.getLastRestoreAttemptTime(appContext);
        int restoreFailCount = VpnSessionStore.getRestoreFailCount(appContext);
        if (restoreFailCount >= MAX_AUTO_RESTORE_FAIL_COUNT
                && now - lastRestoreAttemptTime < AUTO_RESTORE_COOLDOWN_MS) {
            return;
        }
        if (now - lastRestoreAttemptTime < MIN_AUTO_RESTORE_INTERVAL_MS) {
            return;
        }
        VpnSessionStore.setLastRestoreAttemptTime(appContext, now);
        VpnStatusManager.getInstance().updateStatus(VpnStatusManager.Status.STARTING, null);
        try {
            ShowLogNetBareManager.getInstance().startCapture(appContext);
        } catch (Throwable throwable) {
            // 为什么这样做：自动恢复失败通常是系统层问题，必须节流记录失败次数，避免 5 秒轮询把异常放大成无限拉起。
            VpnSessionStore.setRestoreFailCount(appContext, restoreFailCount + 1);
            VpnStatusManager.getInstance().updateStatus(VpnStatusManager.Status.ERROR, throwable.getMessage() != null ? throwable.getMessage() : reason);
        }
    }

    /**
     * 是否正在运行
     */
    public boolean isVpnRunning() {
        Application application = ShowLogManager.getInstance().getApplication();
        if (application != null) {
            return isVpnRunning(application);
        }
        VpnStatusManager.Status status = VpnStatusManager.getInstance().getCurrentStatus();
        return status == VpnStatusManager.Status.RUNNING || status == VpnStatusManager.Status.STARTING;
    }

    /**
     * 基于多信号综合判断当前是否仍在运行
     * 为什么这样做：仅依赖内存态会在进程重建、回调丢失或系统状态栏延迟刷新时误判，因此这里统一走运行态快照。
     */
    public boolean isVpnRunning(@NonNull Context context) {
        RuntimeState runtimeState = inspectRuntimeState(context);
        syncRuntimeStatus(context, runtimeState);
        return runtimeState.sdkLikelyRunning;
    }

    /**
     * 读取当前 VPN 运行态快照
     */
    @NonNull
    public RuntimeState inspectRuntimeState(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        boolean permissionGranted = VpnService.prepare(appContext) == null;
        boolean systemVpnConnected = isSystemVpnConnected(appContext);
        boolean netBareActive = ShowLogNetBareManager.getInstance().isCaptureActive();
        boolean serviceRunning = isShowLogVpnServiceRunning(appContext);
        boolean recentTrafficActive = isRecentTrafficActive();
        return new RuntimeState(permissionGranted, systemVpnConnected, netBareActive, serviceRunning, recentTrafficActive);
    }

    /**
     * 把真实运行态同步回状态中心
     * 为什么这样做：控制台 UI 和外部开关仍然围绕 `VpnStatusManager` 组织，因此需要把更可信的运行态结果回写，减少状态栏与 SDK 文案打架。
     */
    public void syncRuntimeStatus(@NonNull Context context) {
        syncRuntimeStatus(context, inspectRuntimeState(context));
    }

    private void syncRuntimeStatus(@NonNull Context context, @NonNull RuntimeState runtimeState) {
        VpnStatusManager vpnStatusManager = VpnStatusManager.getInstance();
        VpnStatusManager.Status currentStatus = vpnStatusManager.getCurrentStatus();
        if (runtimeState.sdkLikelyRunning) {
            if (currentStatus != VpnStatusManager.Status.RUNNING) {
                vpnStatusManager.updateStatus(VpnStatusManager.Status.RUNNING, null);
            }
            return;
        }
        if (runtimeState.systemConnectedButSdkUnsynced) {
            if (currentStatus != VpnStatusManager.Status.STARTING && currentStatus != VpnStatusManager.Status.ERROR) {
                // 为什么这样做：系统栏已显示 VPN，但 SDK 暂时还没确认是自己的链路时，先保持“同步中”而不是武断显示已停止。
                vpnStatusManager.updateStatus(VpnStatusManager.Status.STARTING, null);
            }
            return;
        }
        if (!runtimeState.permissionGranted) {
            if (currentStatus != VpnStatusManager.Status.PREPARE_REQUIRED) {
                vpnStatusManager.updateStatus(VpnStatusManager.Status.PREPARE_REQUIRED, null);
            }
            return;
        }
        if (currentStatus == VpnStatusManager.Status.RUNNING || currentStatus == VpnStatusManager.Status.STARTING) {
            vpnStatusManager.updateStatus(VpnStatusManager.Status.STOPPED, null);
        }
    }

    private boolean isRecentTrafficActive() {
        VpnStatusManager.TrafficSnapshot trafficSnapshot = VpnStatusManager.getInstance().getTrafficSnapshot();
        if (trafficSnapshot == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        return trafficSnapshot.updateTimeMs > 0L
                && now - trafficSnapshot.updateTimeMs <= ACTIVE_TRAFFIC_GRACE_MS
                && (trafficSnapshot.packetCount > 0L || trafficSnapshot.activeSessionCount > 0);
    }

    private boolean isSystemVpnConnected(@NonNull Context context) {
        ConnectivityManager connectivityManager = context.getSystemService(ConnectivityManager.class);
        if (connectivityManager == null) {
            return false;
        }
        Network[] networks = connectivityManager.getAllNetworks();
        if (networks == null || networks.length == 0) {
            return false;
        }
        for (Network network : networks) {
            NetworkCapabilities networkCapabilities = connectivityManager.getNetworkCapabilities(network);
            if (networkCapabilities != null && networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                return true;
            }
        }
        return false;
    }

    private boolean isShowLogVpnServiceRunning(@NonNull Context context) {
        ActivityManager activityManager = context.getSystemService(ActivityManager.class);
        if (activityManager == null) {
            return false;
        }
        List<ActivityManager.RunningServiceInfo> runningServiceInfos = activityManager.getRunningServices(Integer.MAX_VALUE);
        if (runningServiceInfos == null || runningServiceInfos.isEmpty()) {
            return false;
        }
        String targetServiceName = ShowLogVpnService.class.getName();
        for (ActivityManager.RunningServiceInfo runningServiceInfo : runningServiceInfos) {
            if (runningServiceInfo == null || runningServiceInfo.service == null) {
                continue;
            }
            if (targetServiceName.equals(runningServiceInfo.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
