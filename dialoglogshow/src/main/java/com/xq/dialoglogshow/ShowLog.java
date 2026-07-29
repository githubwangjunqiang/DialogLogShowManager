package com.xq.dialoglogshow;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.xq.dialoglogshow.collector.vpn.VpnController;
import com.xq.dialoglogshow.collector.vpn.VpnCapturePackageStore;
import com.xq.dialoglogshow.collector.vpn.netbare.ShowLogNetBareManager;
import com.xq.dialoglogshow.collector.vpn.repository.VpnTrafficLogRepository;
import com.xq.dialoglogshow.config.InternalDefaultShowLogProvider;
import com.xq.dialoglogshow.config.ShowLogProvider;
import com.xq.dialoglogshow.model.VpnTrafficLogModel;
import com.xq.dialoglogshow.utils.ShowLogActivityUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * Description: 极简 SDK 顶层门面类
 * 为什么这样做：提供极简的 ShowLog.init(...) 单行静态入口，方便开发者在 Application 中初始化，
 * 避免记复杂的内部 Manager 实例方法。
 *
 * Author: 王俊强 980766134@qq.com
 * Date: 2026/07/22
 */
public final class ShowLog {

    /**
     * 为什么这样做：Application 是所有 VPN、仓库和生命周期能力的进程级根上下文，只保留应用实例可避免泄漏 Activity。
     */
    private static volatile Application application;

    /**
     * 为什么这样做：SDK 内部只消费统一默认代理，避免各调用点分别处理宿主返回的空值。
     */
    private static volatile InternalDefaultShowLogProvider internalProvider;

    /**
     * 为什么这样做：前后台巡检与自动恢复需要读取初始化时确定的能力开关，统一保存可避免不同组件各自猜测。
     */
    private static volatile boolean vpnNetworkLogEnabled;

    private ShowLog() {
        // 为什么这样做：ShowLog 只承载唯一静态接入入口，禁止实例化可避免开发者误以为需要持有 SDK 对象。
    }

    /**
     * SDK 唯一初始化入口。
     * 为什么这样做：VPN 能力开关由 Provider 强制声明，因此初始化只固定接收 Application 和 Provider，不再出现重载或隐藏默认值。
     *
     * @param application 宿主 Application
     * @param provider    开发者实现的 ShowLogProvider 契约接口实例
     */
    public static synchronized void init(@NonNull Application application, @NonNull ShowLogProvider provider) {
        if (application == null) {
            throw new IllegalArgumentException("Application cannot be null");
        }
        if (provider == null) {
            throw new IllegalArgumentException("ShowLogProvider cannot be null");
        }
        boolean enableVpnNetworkLog = provider.isVpnNetworkLogEnabled();
        InternalDefaultShowLogProvider newProvider = new InternalDefaultShowLogProvider(application, provider);
        InternalDefaultShowLogProvider oldProvider = internalProvider;

        // 为什么这样做：必须先发布完整运行时状态再初始化 NetBare，避免其异步证书或监听链路回读到半初始化数据。
        ShowLog.application = application;
        ShowLog.vpnNetworkLogEnabled = enableVpnNetworkLog;
        ShowLog.internalProvider = newProvider;

        // 为什么这样做：NetBare 必须在任何权限检查和自动恢复之前持有 Application，从根源避免 VpnService.prepare 收到 null。
        ShowLogNetBareManager.getInstance().initializeInternal(application, newProvider.isDebug());
        registerLifecycleCallbacks(application, newProvider.isDebug(), enableVpnNetworkLog);

        if (oldProvider != null && oldProvider != newProvider) {
            oldProvider.release();
        }

        // 为什么这样做：Android 12+ 禁止普通后台状态拉起前台服务，初始化阶段只保存恢复意图，真正启动统一等首个 Activity 可见后执行。
        if (enableVpnNetworkLog) {
            return;
        }
        // 为什么这样做：只有在 VPN 已经运行时才主动下发停止指令，避免宿主本来就关闭 VPN 功能时仍在 Application 启动阶段多发一次无意义的 Service 调用。
        if (VpnController.getInstance().isVpnRunning()) {
            VpnController.getInstance().stopVpn(application);
        }
    }

    /**
     * 获取 SDK 内部代理包装后的 Provider（绝对安全防空）
     *
     * @return InternalDefaultShowLogProvider
     */
    @NonNull
    public static ShowLogProvider getProvider() {
        InternalDefaultShowLogProvider provider = internalProvider;
        if (provider == null) {
            throw new IllegalStateException("ShowLog SDK has not been initialized. Please call ShowLog.init(application, provider) first.");
        }
        return provider;
    }

    /**
     * 获取 SDK Application。
     * 为什么这样做：Android Library 不具备 Java 模块级友元可见性，受限方法让跨包内部组件共享根上下文，同时避免重新暴露 Manager 类。
     */
    @Nullable
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public static Application getApplicationInternal() {
        return application;
    }

    /**
     * 获取 VPN 网络日志能力开关。
     * 为什么这样做：仅供 SDK 的巡检与恢复链路读取初始化快照，宿主配置仍统一从 Provider 提供。
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public static boolean isVpnNetworkLogEnabledInternal() {
        return vpnNetworkLogEnabled;
    }

    /**
     * 动态设置需要额外抓取的 App 包名。
     * 为什么这样做：宿主 App 始终是默认目标，独立门面 API 可在不破坏 ShowLogProvider 既有实现的前提下动态扩展其它 App。
     *
     * @param packageNames 额外目标 applicationId/packageName；传 null 或空集合恢复为仅宿主 App
     */
    public static synchronized void setVpnCapturePackageNames(
            @Nullable Collection<String> packageNames) {
        Application currentApplication = requireApplication();
        Set<String> oldEffectivePackages =
                VpnCapturePackageStore.getEffectivePackageNames(currentApplication);
        VpnCapturePackageStore.saveExtraPackageNames(
                currentApplication,
                packageNames != null ? packageNames : Collections.emptySet()
        );
        Set<String> newEffectivePackages =
                VpnCapturePackageStore.getEffectivePackageNames(currentApplication);
        if (oldEffectivePackages.equals(newEffectivePackages)) {
            return;
        }
        ShowLogNetBareManager.getInstance()
                .applyCapturePackageChanges(currentApplication);
    }

    /**
     * 获取当前最终生效的抓包目标。
     * 为什么这样做：返回值包含宿主 App，开发者可以直接核对实际白名单，而不必自己合并默认值。
     */
    @NonNull
    public static Set<String> getVpnCapturePackageNames() {
        return VpnCapturePackageStore.getEffectivePackageNames(requireApplication());
    }

    /**
     * 恢复为仅抓取宿主 App。
     */
    public static void resetVpnCapturePackageNames() {
        setVpnCapturePackageNames(Collections.emptySet());
    }

    /**
     * 异步查询指定 App 在时间范围内的 VPN 日志。
     * 为什么这样做：数据库读取不能阻塞宿主主线程，统一借用 SDK 已约定的后台 Executor，并把结果切回主线程交付。
     *
     * @param appPackageName 目标 applicationId/packageName
     * @param startTime      开始时间，包含
     * @param endTime        结束时间，不包含
     * @param callback       查询结果回调
     */
    public static void queryVpnLogsByApp(@NonNull String appPackageName,
                                         long startTime,
                                         long endTime,
                                         @NonNull VpnLogQueryCallback callback) {
        if (appPackageName == null || appPackageName.trim().isEmpty()) {
            throw new IllegalArgumentException("appPackageName cannot be empty");
        }
        if (callback == null) {
            throw new IllegalArgumentException("callback cannot be null");
        }
        requireApplication();
        Handler mainHandler = new Handler(Looper.getMainLooper());
        try {
            getProvider().getLoadExecutor().execute(() -> {
                try {
                    ArrayList<VpnTrafficLogModel> logs = VpnTrafficLogRepository
                            .getInstance()
                            .queryLogsByAppAndRange(
                                    appPackageName.trim(),
                                    startTime,
                                    endTime
                            );
                    mainHandler.post(() -> callback.onQueryResult(logs));
                } catch (RuntimeException exception) {
                    mainHandler.post(() -> callback.onQueryError(exception));
                }
            });
        } catch (RuntimeException exception) {
            // 为什么这样做：宿主 Executor 可能已经关闭或拒绝任务，此时也必须通过统一回调交付错误，不能让调用方永久等待。
            mainHandler.post(() -> callback.onQueryError(exception));
        }
    }

    /**
     * VPN 日志异步查询回调。
     * 为什么这样做：结果与异常分开交付，宿主既不会在主线程访问数据库，也不会因 SDK 查询失败而丢失诊断信息。
     */
    public interface VpnLogQueryCallback {

        /**
         * 查询完成。
         */
        void onQueryResult(@NonNull ArrayList<VpnTrafficLogModel> logs);

        /**
         * 查询失败。
         */
        void onQueryError(@NonNull Throwable throwable);
    }

    /**
     * 获取已初始化的 Application。
     * 为什么这样做：所有新增动态 API 都依赖进程级状态，统一校验可避免每个入口产生不同的未初始化行为。
     */
    @NonNull
    private static Application requireApplication() {
        Application currentApplication = application;
        if (currentApplication == null) {
            throw new IllegalStateException("ShowLog SDK has not been initialized. Please call ShowLog.init(application, provider) first.");
        }
        return currentApplication;
    }

    /**
     * 注册 SDK 生命周期监听。
     * 为什么这样做：悬浮入口和 VPN 前后台巡检共用同一监听器，统一重注册可保证重复初始化时不会叠加回调。
     */
    private static void registerLifecycleCallbacks(@NonNull Application application,
                                                   boolean debugEnabled,
                                                   boolean vpnEnabled) {
        ShowLogActivityUtils lifecycleCallbacks = ShowLogActivityUtils.getInstance();
        lifecycleCallbacks.open = debugEnabled;
        try {
            application.unregisterActivityLifecycleCallbacks(lifecycleCallbacks);
        } catch (Exception ignored) {
        }
        if (!debugEnabled && !vpnEnabled) {
            return;
        }
        try {
            application.registerActivityLifecycleCallbacks(lifecycleCallbacks);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }
}
