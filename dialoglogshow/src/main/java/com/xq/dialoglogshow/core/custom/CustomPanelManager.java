package com.xq.dialoglogshow.core.custom;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 自定义扩展面板管理器。
 * 为什么这样做：
 * 统一集中管理开发者注入的自定义调试扩展，解耦 UI 渲染与业务逻辑。
 */
public final class CustomPanelManager {

    private static volatile CustomPanelManager sInstance;

    private String mTitle = "自定义扩展";
    private CustomPanelProvider mProvider;

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
     *
     * @param title    Tab 显示标题（如 "业务环境"、"Mock控制" 等，为 null 时使用默认标题）
     * @param provider 面板提供者
     */
    public void registerCustomPanel(@Nullable String title, @NonNull CustomPanelProvider provider) {
        if (title != null && !title.trim().isEmpty()) {
            this.mTitle = title.trim();
        }
        this.mProvider = provider;
    }

    /**
     * 注销自定义面板。
     */
    public void unregisterCustomPanel() {
        if (mProvider != null) {
            try {
                mProvider.onDestroyView();
            } catch (Throwable ignored) {
            }
        }
        this.mProvider = null;
        this.mTitle = "自定义扩展";
    }

    @NonNull
    public String getTitle() {
        return mTitle;
    }

    @Nullable
    public CustomPanelProvider getProvider() {
        return mProvider;
    }

    public boolean hasCustomPanel() {
        return mProvider != null;
    }
}
