package com.xq.dialoglogshowmanager.ui.utils;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;

import androidx.annotation.NonNull;

import com.xq.dialoglogshowmanager.ui.entity.InstalledAppInfo;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 可选已安装 App 加载器。
 * 为什么这样做：PackageManager 查询和排序可能比较耗时，将其从 Activity 拆出后可以明确要求在后台线程执行。
 */
public final class InstalledAppLoader {

    private InstalledAppLoader() {
        // 为什么这样做：加载逻辑不保存界面状态，禁止实例化可避免无意义的生命周期管理。
    }

    /**
     * 加载系统当前允许宿主查询、且具有桌面启动入口的 App。
     * 为什么这样做：桌面应用对用户更可识别，也与 SDK Manifest 中保留的 MAIN/LAUNCHER queries 范围一致。
     */
    @NonNull
    public static List<InstalledAppInfo> load(@NonNull Context context) {
        PackageManager packageManager =
                context.getApplicationContext().getPackageManager();
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolveInfos = queryLauncherActivities(
                packageManager,
                launcherIntent
        );
        Map<String, InstalledAppInfo> uniqueApps = new LinkedHashMap<>();
        for (ResolveInfo resolveInfo : resolveInfos) {
            if (resolveInfo.activityInfo == null
                    || resolveInfo.activityInfo.applicationInfo == null) {
                continue;
            }
            String packageName = resolveInfo.activityInfo.packageName;
            CharSequence label = resolveInfo.loadLabel(packageManager);
            String appName = label != null && label.length() > 0
                    ? label.toString()
                    : packageName;
            uniqueApps.put(
                    packageName,
                    new InstalledAppInfo(appName, packageName)
            );
        }
        ArrayList<InstalledAppInfo> installedApps =
                new ArrayList<>(uniqueApps.values());
        Collator collator = Collator.getInstance(Locale.getDefault());
        Collections.sort(installedApps, (first, second) -> {
            int nameResult = collator.compare(first.appName, second.appName);
            return nameResult != 0
                    ? nameResult
                    : first.packageName.compareTo(second.packageName);
        });
        return installedApps;
    }

    /**
     * 检查手动输入的 applicationId 是否对应当前已安装且可查询的 App。
     * 为什么这样做：SDK 只能把已安装包加入 VPN 白名单，确认前校验可避免界面提示成功但系统实际跳过无效目标。
     */
    public static boolean isInstalled(@NonNull Context context,
                                      @NonNull String packageName) {
        PackageManager packageManager =
                context.getApplicationContext().getPackageManager();
        try {
            ApplicationInfo applicationInfo;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                applicationInfo = packageManager.getApplicationInfo(
                        packageName,
                        PackageManager.ApplicationInfoFlags.of(0L)
                );
            } else {
                // 为什么这样做：旧重载只在 Android 12 及以下调用，局部抑制废弃警告可以明确兼容边界。
                applicationInfo = getApplicationInfoLegacy(
                        packageManager,
                        packageName
                );
            }
            return applicationInfo != null;
        } catch (PackageManager.NameNotFoundException exception) {
            return false;
        }
    }

    /**
     * 调用 Android 12 及以下的 PackageManager 重载。
     */
    @NonNull
    @SuppressWarnings("deprecation")
    private static ApplicationInfo getApplicationInfoLegacy(
            @NonNull PackageManager packageManager,
            @NonNull String packageName)
            throws PackageManager.NameNotFoundException {
        return packageManager.getApplicationInfo(packageName, 0);
    }

    /**
     * 兼容 Android 13 前后的 PackageManager 查询重载。
     * 为什么这样做：使用新 Flags API 可以消除高版本废弃调用，低版本分支则维持 minSdk 23 可运行。
     */
    @NonNull
    @SuppressWarnings("deprecation")
    private static List<ResolveInfo> queryLauncherActivities(
            @NonNull PackageManager packageManager,
            @NonNull Intent launcherIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return packageManager.queryIntentActivities(
                    launcherIntent,
                    PackageManager.ResolveInfoFlags.of(0L)
            );
        }
        return packageManager.queryIntentActivities(launcherIntent, 0);
    }
}
