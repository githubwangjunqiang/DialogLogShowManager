package com.xq.dialoglogshow.collector.vpn.netbare;

import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * VPN 会话 UID 与目标包名映射器。
 * 为什么这样做：拦截器只能拿到连接 UID，集中映射并缓存后可以避免每个请求分片都查询 PackageManager，同时防止 Shared UID 被错误归属。
 */
public final class VpnAppIdentityResolver {

    private static final String UNKNOWN_PACKAGE = "";

    private final PackageManager mPackageManager;
    private final Set<String> mTargetPackageNames;
    private final ConcurrentHashMap<Integer, String> mUidPackageCache =
            new ConcurrentHashMap<>();

    public VpnAppIdentityResolver(@NonNull Context context,
                                  @NonNull Set<String> targetPackageNames) {
        mPackageManager = context.getApplicationContext().getPackageManager();
        mTargetPackageNames = Collections.unmodifiableSet(
                new LinkedHashSet<>(targetPackageNames)
        );
    }

    /**
     * 把连接 UID 解析为唯一目标包。
     * 为什么这样做：仅有一个抓包目标时即使系统暂未返回 UID 也可安全归属；多目标场景必须唯一匹配，否则宁可为空也不能伪造数据。
     */
    @Nullable
    public String resolvePackageName(int uid) {
        if (mTargetPackageNames.size() == 1) {
            return mTargetPackageNames.iterator().next();
        }
        if (uid <= 0) {
            return null;
        }
        String cachedPackage = mUidPackageCache.get(uid);
        if (cachedPackage != null) {
            return cachedPackage.isEmpty() ? null : cachedPackage;
        }
        String resolvedPackage = findUniqueTargetPackage(uid);
        mUidPackageCache.put(uid, resolvedPackage != null
                ? resolvedPackage
                : UNKNOWN_PACKAGE);
        return resolvedPackage;
    }

    @Nullable
    private String findUniqueTargetPackage(int uid) {
        String[] packagesForUid = mPackageManager.getPackagesForUid(uid);
        if (packagesForUid == null || packagesForUid.length == 0) {
            return null;
        }
        String matchedPackage = null;
        for (String packageName : packagesForUid) {
            if (!mTargetPackageNames.contains(packageName)) {
                continue;
            }
            // 为什么这样做：同一 UID 命中多个动态目标时无法从连接层继续区分，返回未知可避免把日志错误归到任意一个 App。
            if (matchedPackage != null && !matchedPackage.equals(packageName)) {
                return null;
            }
            matchedPackage = packageName;
        }
        return matchedPackage;
    }
}
