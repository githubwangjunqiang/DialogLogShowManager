package com.xq.dialoglogshow.manager;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.xq.dialoglogshow.config.InternalDefaultShowLogProvider;
import com.xq.dialoglogshow.config.ShowLogProvider;
import com.xq.dialoglogshow.utils.ShowLogActivityUtils;

/**
 * Description: SDK 全局核心单例管理者
 * 为什么这样做：收拢管理 Application 生命周期挂载与全局 Provider 的持用，
 * 并向 SDK 内部各弹窗/视图统一提供 100% 安全防空的 Provider 代理。
 *
 * Author: 王俊强 980766134@qq.com
 * Date: 2026/07/22
 */
public class ShowLogManager {

    private static volatile ShowLogManager instance;

    private Application application;
    private InternalDefaultShowLogProvider internalProvider;

    private ShowLogManager() {
    }

    @NonNull
    public static ShowLogManager getInstance() {
        if (instance == null) {
            synchronized (ShowLogManager.class) {
                if (instance == null) {
                    instance = new ShowLogManager();
                }
            }
        }
        return instance;
    }

    /**
     * SDK 初始化核心方法
     *
     * @param app          宿主 Application
     * @param userProvider 开发者实现的契约提供者实例
     */
    public void init(@NonNull Application app, @Nullable ShowLogProvider userProvider) {
        if (app == null) {
            throw new IllegalArgumentException("Application cannot be null");
        }
        this.application = app;

        // 使用 SDK 内部默认提供者包装开发者传入的 userProvider，收拢缺省值与空保护
        this.internalProvider = new InternalDefaultShowLogProvider(app, userProvider);

        // 如果开启了 Debug 控制台，注册 Activity 生命周期挂载悬浮球
        if (internalProvider.isDebug()) {
            ShowLogActivityUtils.getInstance().open = true;
            try {
                app.unregisterActivityLifecycleCallbacks(ShowLogActivityUtils.getInstance());
            } catch (Exception ignored) {
            }
            try {
                app.registerActivityLifecycleCallbacks(ShowLogActivityUtils.getInstance());
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            ShowLogActivityUtils.getInstance().open = false;
        }
    }

    /**
     * 获取 SDK 内部代理持有的 Provider 契约（100% 防空）
     */
    @NonNull
    public ShowLogProvider getProvider() {
        if (internalProvider == null) {
            // 未手动初始化时的默认保护
            if (application != null) {
                internalProvider = new InternalDefaultShowLogProvider(application, null);
            } else {
                throw new IllegalStateException("ShowLog SDK has not been initialized. Please call ShowLog.init(application, provider) first.");
            }
        }
        return internalProvider;
    }

    /**
     * 手动停止悬浮窗挂载
     */
    public void stop() {
        ShowLogActivityUtils.getInstance().open = false;
    }

    public Application getApplication() {
        return application;
    }
}
