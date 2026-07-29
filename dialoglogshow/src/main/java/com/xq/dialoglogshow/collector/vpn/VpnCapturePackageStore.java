package com.xq.dialoglogshow.collector.vpn;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * VPN 抓包目标包名存储。
 * 为什么这样做：动态目标必须跨进程重建和冷启动保持一致，单独存储额外包名可确保宿主包始终由运行时自动加入，避免 applicationId 变化后残留旧值。
 */
public final class VpnCapturePackageStore {

    private static final String PREF_NAME = "showlog_vpn_capture_packages";
    private static final String KEY_EXTRA_PACKAGE_NAMES = "extra_package_names";

    private VpnCapturePackageStore() {
        // 为什么这样做：该类只提供进程级静态存储能力，禁止实例化可避免出现多份状态入口。
    }

    /**
     * 保存开发者额外指定的包名。
     * 为什么这样做：写入前统一清洗和排除宿主包，读取时才能稳定恢复“宿主 + 额外包”的最终集合。
     */
    public static void saveExtraPackageNames(@NonNull Context context,
                                             @NonNull Collection<String> packageNames) {
        LinkedHashSet<String> normalizedPackages = normalize(packageNames);
        normalizedPackages.remove(context.getApplicationContext().getPackageName());
        getPreferences(context).edit()
                .putStringSet(KEY_EXTRA_PACKAGE_NAMES, normalizedPackages)
                .apply();
    }

    /**
     * 获取开发者额外指定的包名快照。
     */
    @NonNull
    public static Set<String> getExtraPackageNames(@NonNull Context context) {
        Set<String> storedPackages = getPreferences(context)
                .getStringSet(KEY_EXTRA_PACKAGE_NAMES, Collections.emptySet());
        if (storedPackages == null || storedPackages.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(storedPackages));
    }

    /**
     * 获取最终生效包名集合。
     * 为什么这样做：宿主包必须始终排在第一位并参与 VPN，额外包只能扩展而不能替换默认目标。
     */
    @NonNull
    public static Set<String> getEffectivePackageNames(@NonNull Context context) {
        LinkedHashSet<String> effectivePackages = new LinkedHashSet<>();
        effectivePackages.add(context.getApplicationContext().getPackageName());
        effectivePackages.addAll(getExtraPackageNames(context));
        return Collections.unmodifiableSet(effectivePackages);
    }

    @NonNull
    private static SharedPreferences getPreferences(@NonNull Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    private static LinkedHashSet<String> normalize(@NonNull Collection<String> packageNames) {
        LinkedHashSet<String> normalizedPackages = new LinkedHashSet<>();
        for (String packageName : packageNames) {
            if (packageName == null) {
                continue;
            }
            String normalizedPackage = packageName.trim();
            if (!normalizedPackage.isEmpty()) {
                normalizedPackages.add(normalizedPackage);
            }
        }
        return normalizedPackages;
    }
}
