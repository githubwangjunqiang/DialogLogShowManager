package com.xq.dialoglogshowmanager.ui.entity;

import androidx.annotation.NonNull;

/**
 * 已安装 App 选择项。
 * 为什么这样做：界面只需要稳定的名称和包名快照，不直接持有 PackageManager 对象可避免 Activity 生命周期泄漏。
 */
public final class InstalledAppInfo {

    /**
     * 为什么这样做：应用名称负责给用户识别，不能用不直观的包名完全替代。
     */
    @NonNull
    public final String appName;

    /**
     * 为什么这样做：最终传给 SDK 的唯一标识必须保留 applicationId，避免同名应用选错。
     */
    @NonNull
    public final String packageName;

    public InstalledAppInfo(@NonNull String appName, @NonNull String packageName) {
        this.appName = appName;
        this.packageName = packageName;
    }
}
