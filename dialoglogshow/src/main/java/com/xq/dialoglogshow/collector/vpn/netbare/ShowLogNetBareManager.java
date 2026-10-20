package com.xq.dialoglogshow.collector.vpn.netbare;

import android.app.Application;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.github.megatronking.netbare.NetBare;
import com.github.megatronking.netbare.NetBareConfig;
import com.github.megatronking.netbare.NetBareListener;
import com.github.megatronking.netbare.http.HttpInterceptorFactory;
import com.github.megatronking.netbare.ip.IpAddress;
import com.github.megatronking.netbare.ssl.JKS;
import com.xq.dialoglogshow.collector.vpn.VpnHealthMonitor;
import com.xq.dialoglogshow.collector.vpn.VpnCapturePackageStore;
import com.xq.dialoglogshow.collector.vpn.VpnSessionStore;
import com.xq.dialoglogshow.R;
import com.xq.dialoglogshow.collector.vpn.VpnStatusManager;
import com.xq.dialoglogshow.collector.vpn.repository.VpnTrafficLogRepository;
import com.xq.dialoglogshow.collector.vpn.tunnel.ShowLogVpnService;
import com.xq.dialoglogshow.ShowLog;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * NetBare 单方案管理器
 *
 * 为什么这样做：
 * 1. 用户已经明确要求 SDK 只保留一种 VPN 正式实现，因此 NetBare 的初始化、证书、启动配置和状态监听都要集中收口，避免再次散落出第二套链路。
 * 2. 现有 UI、Repository、ShowLog.init 都已经围绕 SDK 自己的管理器组织，单独增加这一层桥接可以最大限度复用现有对外接口与日志存储模型。
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class ShowLogNetBareManager implements NetBareListener {

    private static final String CERT_ALIAS = "ShowLogVpn";

    private static volatile ShowLogNetBareManager sInstance;

    /**
     * 为什么这样做：HTTPS 明文能力依赖证书安装，这个对象既要在初始化阶段预生成证书文件，也要在启动阶段参与构建默认 HTTP 配置，因此需要全局复用。
     */
    private JKS mJks;
    private boolean mInitialized;
    private boolean mListenerRegistered;

    private ShowLogNetBareManager() {
    }

    @NonNull
    public static ShowLogNetBareManager getInstance() {
        if (sInstance == null) {
            synchronized (ShowLogNetBareManager.class) {
                if (sInstance == null) {
                    sInstance = new ShowLogNetBareManager();
                }
            }
        }
        return sInstance;
    }

    /**
     * 初始化 NetBare 基础环境。
     * 为什么这样做：内部方法不再命名为 init，确保 SDK 对宿主只保留 ShowLog.init 这一条初始化契约。
     */
    public synchronized void initializeInternal(@NonNull Application application, boolean debug) {
        NetBare.get().attachApplication(application, debug, ShowLogVpnService.class);
        if (mJks == null) {
            // 为什么这样做：证书文件生成是异步的，越早初始化越能降低开发者第一次点击开启时还没生成完成的概率。
            mJks = new JKS(application, CERT_ALIAS, CERT_ALIAS.toCharArray(),
                    CERT_ALIAS, CERT_ALIAS, CERT_ALIAS, CERT_ALIAS, CERT_ALIAS);
        }
        if (!mListenerRegistered) {
            NetBare.get().registerNetBareListener(this);
            mListenerRegistered = true;
        }
        mInitialized = true;
    }

    /**
     * 当前是否已安装 HTTPS 调试证书
     */
    public boolean isCertificateInstalled(@NonNull Context context) {
        return JKS.isInstalled(context.getApplicationContext(), CERT_ALIAS);
    }

    /**
     * 当前证书安装所需的本地文件是否已经准备完毕
     * 为什么这样做：JKS 生成是异步的，只有在 PEM / p12 文件都准备好后再触发系统安装，才能避免开发者第一次点击就收到“安装失败”的误导反馈。
     */
    public boolean isCertificateFileReady() {
        if (mJks == null) {
            return false;
        }
        // 为什么这样做：.jks 标记文件只有用户完成系统证书安装回调后才会创建，
        // 如果这里也拿它作为“允许发起安装”的前置条件，就会把安装流程自己锁死。
        return mJks.aliasFile(JKS.KEY_STORE_FILE_EXTENSION).exists()
                && mJks.aliasFile(JKS.KEY_PEM_FILE_EXTENSION).exists();
    }

    /**
     * 发起证书安装
     */
    public void installCertificate(@NonNull Context context) {
        try {
            JKS.install(context, CERT_ALIAS, CERT_ALIAS);
        } catch (Exception e) {
            VpnStatusManager.getInstance().updateStatus(VpnStatusManager.Status.ERROR,
                    context.getString(R.string.show_sdk_vpn_certificate_install_failed));
        }
    }

    /**
     * 获取 VPN 系统授权 Intent
     */
    @Nullable
    public Intent getPrepareIntent() {
        if (!ensureInitialized()) {
            return null;
        }
        return NetBare.get().prepare();
    }

    /**
     * 保证 NetBare 已经持有宿主 Application。
     * 为什么这样做：即使旧宿主或内部时序遗漏了提前初始化，VPN 操作也应先尝试自愈，而不是把 null 传给系统 VpnService。
     *
     * @return true 表示可以安全调用 NetBare；false 表示 SDK 尚未获得 Application
     */
    public synchronized boolean ensureInitialized() {
        if (mInitialized) {
            return true;
        }
        Application application = ShowLog.getApplicationInternal();
        if (application == null) {
            return false;
        }
        initializeInternal(application, true);
        return true;
    }

    /**
     * 启动 NetBare 唯一采集链路
     */
    public synchronized void startCapture(@NonNull Context context) {
        startCaptureInternal(context, true);
    }

    /**
     * 动态应用最新抓包目标。
     * 为什么这样做：VpnService 的 allowedApplications 只能在建立虚拟网卡时设置，运行中变化必须通过同一串行入口重新建立 NetBare 链路。
     */
    public synchronized void applyCapturePackageChanges(@NonNull Context context) {
        VpnStatusManager.Status currentStatus =
                VpnStatusManager.getInstance().getCurrentStatus();
        if (!isCaptureActive()
                && currentStatus != VpnStatusManager.Status.STARTING) {
            return;
        }
        startCaptureInternal(context, false);
    }

    /**
     * 构建并启动唯一 NetBare 配置。
     * 为什么这样做：首次启动需要重置实时快照，动态重建则必须保留已有日志，拆分布尔边界可以避免改包名时把控制台数据清空。
     */
    private void startCaptureInternal(@NonNull Context context, boolean resetRealtimeLogs) {
        Application application = ShowLog.getApplicationInternal();
        if (application == null) {
            throw new IllegalStateException("ShowLog application is null.");
        }
        initializeInternal(application, true);
        if (mJks == null) {
            throw new IllegalStateException("NetBare JKS is null.");
        }
        VpnTrafficLogRepository.getInstance().cleanupHistoryIfNeed();
        if (resetRealtimeLogs) {
            VpnTrafficLogRepository.getInstance().resetRealtimeCache();
        }
        ShowLogNetBareHttpInterceptor.clearPendingLogs();
        Set<String> targetPackages = VpnCapturePackageStore.getEffectivePackageNames(application);
        VpnAppIdentityResolver identityResolver =
                new VpnAppIdentityResolver(application, targetPackages);
        String localVpnAddress = "10.23.0.2";
        NetBareConfig.Builder configBuilder = NetBareConfig.defaultHttpConfig(
                        mJks,
                        createInterceptorFactories(identityResolver),
                        request -> VpnHttpsFallbackLogRecorder.recordHttpsRequest(request)
                )
                .newBuilder()
                .setSession(context.getString(R.string.show_sdk_vpn_session_name))
                .setAddress(new IpAddress(localVpnAddress, 32))
                .addRoute(new IpAddress("0.0.0.0", 0))
                .addDnsServer("8.8.8.8")
                .dumpUid(true)
                .setUidProvider(new ConnectionOwnerUidProvider(
                        application,
                        localVpnAddress
                ));
        for (String packageName : targetPackages) {
            // 为什么这样做：宿主包和开发者动态包统一进入 allowed 列表后，其余 App 才会继续绕过调试 VPN。
            configBuilder.addAllowedApplication(packageName);
        }
        NetBare.get().start(configBuilder.build());
    }

    /**
     * 停止 NetBare 链路
     */
    public void stopCapture() {
        // 为什么这样做：未初始化时没有 SDK VPN 可停止，直接返回可避免 NetBare 内部访问空 Application。
        if (!ensureInitialized()) {
            return;
        }
        NetBare.get().stop();
    }

    /**
     * 当前 NetBare 是否仍认为自己的服务处于活跃态
     * 为什么这样做：控制台不能只看外层内存状态，需要直接读取底层 VPN 引擎的活跃标记，帮助识别“状态栏已开但 SDK 未同步”的问题。
     */
    public boolean isCaptureActive() {
        return NetBare.get().isActive();
    }

    @NonNull
    private List<HttpInterceptorFactory> createInterceptorFactories(
            @NonNull VpnAppIdentityResolver identityResolver) {
        return Collections.singletonList(
                ShowLogNetBareHttpInterceptor.createFactory(identityResolver)
        );
    }

    @Override
    public void onServiceStarted() {
        Application application = ShowLog.getApplicationInternal();
        if (application != null) {
            VpnSessionStore.resetRestoreThrottle(application);
            VpnHealthMonitor.getInstance().refresh(application);
        }
        VpnStatusManager.getInstance().updateStatus(VpnStatusManager.Status.RUNNING, null);
    }

    @Override
    public void onServiceStopped() {
        Application application = ShowLog.getApplicationInternal();
        if (application != null) {
            VpnHealthMonitor.getInstance().refresh(application);
            VpnHealthMonitor.getInstance().triggerImmediateCheck(application);
        }
        VpnStatusManager.getInstance().updateStatus(VpnStatusManager.Status.STOPPED, null);
    }

    @Override
    public void onServiceStartFailed(Throwable throwable) {
        Application application = ShowLog.getApplicationInternal();
        String reason = throwable != null && throwable.getMessage() != null
                ? throwable.getMessage()
                : "";
        String errorMessage = application != null
                ? application.getString(R.string.show_sdk_vpn_start_failed, reason)
                : reason;
        VpnStatusManager.getInstance().updateStatus(
                VpnStatusManager.Status.ERROR,
                errorMessage
        );
    }
}
