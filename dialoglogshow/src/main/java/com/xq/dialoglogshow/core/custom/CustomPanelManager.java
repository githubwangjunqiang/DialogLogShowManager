package com.xq.dialoglogshow.core.custom;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 自定义扩展面板管理器。
 * 为什么这样做：
 * 统一集中管理开发者注入的自定义调试扩展，解耦 UI 渲染与业务逻辑；
 * 本类只负责保存当前提供者与 Tab 标题，面板视图的创建和销毁统一由承载弹框驱动。
 */
public final class CustomPanelManager {

    private static volatile CustomPanelManager sInstance;

    /** 标题和提供者作为一个快照发布，避免 UI 分别读取时组合出不同次注册的数据。 */
    private volatile Registration mRegistration = new Registration("自定义扩展", null);

    private CustomPanelManager() {
    }

    @NonNull
    public static CustomPanelManager getInstance() {
        if (sInstance == null) {
            synchronized (CustomPanelManager.class) {
                if (sInstance == null) {
                    sInstance = new CustomPanelManager();
                }
            }
        }
        return sInstance;
    }

    /**
     * 注册自定义面板。
     * 为什么这样做：标题与提供者需要作为一组配置原子替换，避免并发注册时读到半更新状态。
     *
     * @param title    Tab 显示标题（如 "业务环境"、"Mock控制" 等，为 null 时沿用当前标题）
     * @param provider 面板提供者
     */
    public synchronized void registerCustomPanel(@Nullable String title, @NonNull CustomPanelProvider provider) {
        Registration current = mRegistration;
        String resolvedTitle = title != null && !title.trim().isEmpty()
                ? title.trim()
                : current.title;
        // 单次 volatile 替换保证 UI 线程看到的标题与 Provider 始终来自同一次注册。
        mRegistration = new Registration(resolvedTitle, provider);
    }

    /**
     * 注销自定义面板。
     * 为什么这样做：
     * 这里只解除注册关系，不在此时回调 onDestroyView；
     * 若弹框仍处于打开状态，业务视图尚未销毁，生命周期清理由承载视图统一触发，避免视图仍可见时被提前释放。
     */
    public synchronized void unregisterCustomPanel() {
        mRegistration = new Registration("自定义扩展", null);
    }

    @NonNull
    public Registration getRegistration() {
        return mRegistration;
    }

    public boolean hasCustomPanel() {
        return mRegistration.provider != null;
    }

    /**
     * 一次注册的不可变快照，供界面在同一逻辑操作中一致读取标题与面板实现。
     */
    public static final class Registration {
        @NonNull
        public final String title;
        @Nullable
        public final CustomPanelProvider provider;

        private Registration(@NonNull String title, @Nullable CustomPanelProvider provider) {
            this.title = title;
            this.provider = provider;
        }
    }
}
