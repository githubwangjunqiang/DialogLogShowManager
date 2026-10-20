package com.xq.dialoglogshowmanager.ui.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Keep;

/**
 * 已安装 App 选择项。
 * 为什么这样做：界面只需要稳定的名称和包名快照，不直接持有 PackageManager 对象可避免 Activity 生命周期泄漏。
 */
@Keep
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

    /**
     * 为什么这样做：静态信息里顺手保留 targetSdk，可以给用户一个“为什么这类 App 更容易出现证书限制”的上下文。
     */
    public final int targetSdkVersion;

    /**
     * 为什么这样做：系统 App 对证书、网络限制和可见性判断常常不同，列表里带上这个标记便于排查抓包差异。
     */
    public final boolean systemApp;

    public InstalledAppInfo(@NonNull String appName, @NonNull String packageName) {
        this(appName, packageName, 0, false);
    }

    public InstalledAppInfo(@NonNull String appName,
                            @NonNull String packageName,
                            int targetSdkVersion,
                            boolean systemApp) {
        this.appName = appName;
        this.packageName = packageName;
        this.targetSdkVersion = targetSdkVersion;
        this.systemApp = systemApp;
    }
}
