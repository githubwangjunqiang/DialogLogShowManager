package com.xq.dialoglogshow;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.xq.dialoglogshow.core.config.ShowLogConfig;
import com.xq.dialoglogshow.core.custom.CustomPanelManager;
import com.xq.dialoglogshow.core.custom.CustomPanelProvider;
import com.xq.dialoglogshow.core.interceptor.ShowLogOkHttpInterceptor;
import com.xq.dialoglogshow.core.model.HttpLogRecord;
import com.xq.dialoglogshow.core.repository.HttpLogRepository;
import com.xq.dialoglogshow.ui.dialog.HttpLogConsoleDialog;
import com.xq.dialoglogshow.ui.dialog.ShowLogHubDialog;
import com.xq.dialoglogshow.ui.view.HttpLogFloatView;

import okhttp3.Interceptor;

/**
 * ShowLog 唯一顶层门面类。
 * 为什么这样做：
 * 1. 采用门面模式（Facade），将内部仓储、SQLite 数据库、悬浮球生命周期以及 OkHttp 拦截器全部封装在极简门面之后；
 * 2. 开发者只需在 Application 中调用 ShowLog.init(this) 即可完成所有准备工作，零学习成本；
 * 3. 内部自动通过 ActivityLifecycleCallbacks 接管悬浮球挂载，宿主不需要申请 SYSTEM_ALERT_WINDOW 危险权限。
 */
public final class ShowLog {

    private static volatile Application sApplication;
    private static volatile ShowLogConfig sConfig;
    private static volatile ShowLogOkHttpInterceptor sOkHttpInterceptor;
    private static volatile com.xq.dialoglogshow.core.interceptor.HttpLogBusinessJudger sBusinessJudger;
    private static volatile boolean sEnabled = true;
    private static volatile java.lang.ref.WeakReference<Activity> sCurrentActivityRef;

    private ShowLog() {
    }

    /**
     * 判断当前是否启用了日志调试监控（生产环境关闭时返回 false）。
     */
    public static boolean isEnabled() {
        return sEnabled;
    }

    /**
     * 动态设置是否启用日志调试监控。
     * 为什么这样做：
     * 1. 当传入 false 时（生产环境或用户临时关闭），OkHttp 拦截器直接 100% 直通放行，绝不产生克隆 Buffer 与任何性能损耗；
     * 2. 悬浮球自动从当前界面隐去，控制台与上报接口静默无感，无需修改业务接入代码。
     */
    public static synchronized void setEnabled(boolean enabled) {
        sEnabled = enabled;
        if (!enabled) {
            if (sCurrentActivityRef != null) {
                Activity activity = sCurrentActivityRef.get();
                if (activity != null) {
                    detachFloatView(activity);
                }
            }
        } else {
            if (sConfig != null && sConfig.showFloatingView && sCurrentActivityRef != null) {
                Activity activity = sCurrentActivityRef.get();
                if (activity != null && !activity.isFinishing()) {
                    attachFloatView(activity);
                }
            }
        }
    }

    /**
     * 极简初始化（使用默认配置：保留最新 10000 条日志，自动开启免权限悬浮球）。
     *
     * @param application 宿主 Application
     */
    public static void init(@NonNull Application application) {
        init(application, ShowLogConfig.defaultConfig());
    }

    /**
     * 自定义配置初始化。
     *
     * @param application 宿主 Application
     * @param config      自定义配置对象
     */
    public static synchronized void init(@NonNull Application application, @NonNull ShowLogConfig config) {
        if (application == null) {
            throw new IllegalArgumentException("Application cannot be null!");
        }
        if (config == null) {
            config = ShowLogConfig.defaultConfig();
        }

        sApplication = application;
        sConfig = config;
        sEnabled = config.enabled;

        // 1. 初始化双层高性能仓储与数据库
        HttpLogRepository.initialize(application, config);

        // 2. 预热 OkHttp 拦截器单例
        sOkHttpInterceptor = new ShowLogOkHttpInterceptor(config);

        // 3. 注册生命周期接管 Activity 悬浮球挂载
        registerFloatingLifecycle(application);
    }

    /**
     * 获取开箱即用的 OkHttp 拦截器实例。
     * 开发者在 OkHttpClient.Builder 中仅需调用：.addInterceptor(ShowLog.getOkHttpInterceptor())
     *
     * @return okhttp3.Interceptor 实例
     */
    @NonNull
    public static Interceptor getOkHttpInterceptor() {
        if (sOkHttpInterceptor == null) {
            throw new IllegalStateException("ShowLog has not been initialized! Please call ShowLog.init(application) in Application.onCreate() first.");
        }
        return sOkHttpInterceptor;
    }

    /**
     * 设置自定义业务成功裁决拦截器。
     * 为什么这样做：
     * 宿主接入自动拦截时，不同接口的业务成功标准各异（例如 HTTP 200 但业务 json code != 0 实际为失败）。
     * 配置该拦截器后，OkHttp 拦截器会自动将报文上下文交给业务判断，并在数据库与控制台中如实以业务成功与否呈现。
     */
    public static void setBusinessJudger(@Nullable com.xq.dialoglogshow.core.interceptor.HttpLogBusinessJudger judger) {
        sBusinessJudger = judger;
    }

    /**
     * 获取当前配置的自定义业务成功裁决拦截器。
     */
    @Nullable
    public static com.xq.dialoglogshow.core.interceptor.HttpLogBusinessJudger getBusinessJudger() {
        return sBusinessJudger;
    }

    /**
     * 强契约非 OkHttp / 自定义网络库日志上报入口。
     * 为什么这样做：
     * 利用纯抽象接口 HttpLogPayload 强制要求开发者覆盖所有属性方法，IDE 一键补全避免文档翻找；
     * 自动完成 JSONObject 美化序列化、业务 success 状态与异常堆栈解析入库。
     *
     * @param payload 纯抽象上报载荷接口实现
     */
    public static void report(@NonNull com.xq.dialoglogshow.core.model.HttpLogPayload payload) {
        if (!sEnabled || payload == null) {
            return;
        }
        report(HttpLogRecord.fromPayload(payload));
    }

    /**
     * 开放式极简数据上报入口（供非 OkHttp 或自定义网络库使用）。
     *
     * @param record 构造好的网络日志记录
     */
    public static void report(@NonNull HttpLogRecord record) {
        if (!sEnabled || record == null) {
            return;
        }
        HttpLogRepository.getInstance().enqueueLog(record);
    }

    /**
     * 呼出 ShowLog 全景调试中枢（默认自动定位至上次浏览的 Tab，或网络日志）。
     *
     * @param activity 宿主 Activity 上下文
     */
    public static void show(@NonNull Activity activity) {
        if (!sEnabled || activity == null || activity.isFinishing()) {
            return;
        }
        new ShowLogHubDialog(activity).show();
    }

    /**
     * 呼出 ShowLog 全景调试中枢并精准定位到指定 Tab。
     *
     * @param activity   宿主 Activity 上下文
     * @param initialTab 初始选中的 Tab：ShowLogHubDialog.TAB_NETWORK / TAB_DEVICE / TAB_CUSTOM
     */
    public static void show(@NonNull Activity activity, int initialTab) {
        if (!sEnabled || activity == null || activity.isFinishing()) {
            return;
        }
        new ShowLogHubDialog(activity, initialTab).show();
    }

    /**
     * 呼出网络日志调试面板（兼容旧调用，直接定位到中枢网络日志 Tab）。
     *
     * @param activity 宿主 Activity 上下文
     */
    public static void showConsole(@NonNull Activity activity) {
        show(activity, ShowLogHubDialog.TAB_NETWORK);
    }

    /**
     * 呼出网络日志调试面板。
     *
     * @param activity 宿主 Activity 上下文
     */
    public static void showNetworkLogs(@NonNull Activity activity) {
        show(activity, ShowLogHubDialog.TAB_NETWORK);
    }

    /**
     * 呼出设备与应用系统全景信息看板（定位至中枢设备信息 Tab）。
     *
     * @param activity 宿主 Activity 上下文
     */
    public static void showAppDeviceInfo(@NonNull Activity activity) {
        show(activity, ShowLogHubDialog.TAB_DEVICE);
    }

    /**
     * 呼出自定义扩展面板（定位至中枢自定义扩展 Tab）。
     *
     * @param activity 宿主 Activity 上下文
     */
    public static void showCustomPanel(@NonNull Activity activity) {
        show(activity, ShowLogHubDialog.TAB_CUSTOM);
    }

    /**
     * 注册业务自定义面板。
     *
     * @param title    Tab 显示标题（例如 "业务环境"、"Mock控制" 等，为 null 时使用默认标题）
     * @param provider 面板提供者
     */
    public static void registerCustomPanel(@Nullable String title, @NonNull CustomPanelProvider provider) {
        CustomPanelManager.getInstance().registerCustomPanel(title, provider);
    }

    /**
     * 动态设置 VPN 额外抓取的目标应用包名。
     */
    public static synchronized void setVpnCapturePackageNames(@Nullable java.util.Collection<String> packageNames) {
        if (sApplication == null) return;
        com.xq.dialoglogshow.collector.vpn.VpnCapturePackageStore.saveExtraPackageNames(
                sApplication,
                packageNames != null ? packageNames : java.util.Collections.emptySet()
        );
        com.xq.dialoglogshow.collector.vpn.netbare.ShowLogNetBareManager.getInstance()
                .applyCapturePackageChanges(sApplication);
    }

    /**
     * 获取当前生效的全部抓包目标包名（含宿主自身）。
     */
    @NonNull
    public static java.util.Set<String> getVpnCapturePackageNames() {
        if (sApplication == null) return java.util.Collections.emptySet();
        return com.xq.dialoglogshow.collector.vpn.VpnCapturePackageStore.getEffectivePackageNames(sApplication);
    }

    /**
     * 恢复为仅抓取宿主 App。
     */
    public static void resetVpnCapturePackageNames() {
        setVpnCapturePackageNames(java.util.Collections.emptySet());
    }

    /**
     * 获取全局 Application 上下文。
     */
    @Nullable
    public static Application getApplication() {
        return sApplication;
    }

    @Nullable
    public static Application getApplicationInternal() {
        return sApplication;
    }

    public static boolean isVpnNetworkLogEnabledInternal() {
        return true;
    }

    /**
     * 获取全局配置。
     */
    @NonNull
    public static ShowLogConfig getConfig() {
        return sConfig != null ? sConfig : ShowLogConfig.defaultConfig();
    }

    private static void registerFloatingLifecycle(@NonNull Application application) {
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                // 为什么这样做：在 Resumed 时记录当前前台 Activity，并按状态决定是否挂载悬浮球
                sCurrentActivityRef = new java.lang.ref.WeakReference<>(activity);
                attachFloatView(activity);
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
                // 为什么这样做：在 Paused 时安全移除，防止跨页面遮挡或持有旧 Activity 造成泄漏
                detachFloatView(activity);
                if (sCurrentActivityRef != null && sCurrentActivityRef.get() == activity) {
                    sCurrentActivityRef = null;
                }
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
                detachFloatView(activity);
                if (sCurrentActivityRef != null && sCurrentActivityRef.get() == activity) {
                    sCurrentActivityRef = null;
                }
            }
        });
    }

    private static final String FLOAT_VIEW_TAG = "SHOWLOG_FLOAT_VIEW_TAG";

    private static void attachFloatView(@NonNull Activity activity) {
        try {
            ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
            // 为什么这样做：如果全局调试已关闭，或者配置不展示悬浮球，确保彻底从界面上移除
            if (!sEnabled || (sConfig != null && !sConfig.showFloatingView)) {
                detachFloatView(activity);
                return;
            }
            if (decorView.findViewWithTag(FLOAT_VIEW_TAG) == null) {
                HttpLogFloatView floatView = new HttpLogFloatView(activity);
                floatView.setTag(FLOAT_VIEW_TAG);
                decorView.addView(floatView);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void detachFloatView(@NonNull Activity activity) {
        try {
            ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
            HttpLogFloatView floatView = decorView.findViewWithTag(FLOAT_VIEW_TAG);
            if (floatView != null) {
                decorView.removeView(floatView);
            }
        } catch (Throwable ignored) {
        }
    }
}
