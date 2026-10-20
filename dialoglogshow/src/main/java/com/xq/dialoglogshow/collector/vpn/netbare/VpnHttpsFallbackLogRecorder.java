package com.xq.dialoglogshow.collector.vpn.netbare;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.content.Context;
import android.content.pm.PackageManager;

import com.github.megatronking.netbare.http.HttpRequest;
import com.xq.dialoglogshow.R;
import com.xq.dialoglogshow.ShowLog;
import com.xq.dialoglogshow.collector.vpn.VpnCapturePackageStore;
import com.xq.dialoglogshow.collector.vpn.repository.VpnTrafficLogRepository;
import com.xq.dialoglogshow.model.VpnTrafficLogModel;
import com.xq.dialoglogshow.model.VpnTrafficLogParseStatus;

import java.util.Set;

/**
 * HTTPS 证书未安装时的连接级兜底日志记录器。
 *
 * 为什么这样做：当前主链路是基于 HTTP(S) 解析生成业务日志，一旦用户还没安装调试证书，
 * HTTPS 应用会完全绕开正文解析。这里单独补一层“连接级元数据”落库，至少保证开发者能看到
 * 目标 App 的实际网络发生了，而不是控制台空白。
 */
public final class VpnHttpsFallbackLogRecorder {

    private static final String FALLBACK_LOG_PREFIX = "https_fallback_";

    private VpnHttpsFallbackLogRecorder() {
        // 为什么这样做：该类只负责把缺证书场景转成可落库日志，不应该被实例化。
    }

    /**
     * 记录一个 HTTPS 连接的最小可视化日志。
     *
     * 为什么这样做：没有证书时拿不到明文请求体，但 session 的 host / url / method /
     * uid 依然有调试价值，足以证明 VPN 已经拦到了该请求。
     */
    public static void recordHttpsRequest(@NonNull HttpRequest request) {
        if (!request.isHttps()) {
            return;
        }
        VpnTrafficLogModel model = new VpnTrafficLogModel();
        model.logId = FALLBACK_LOG_PREFIX + request.id();
        model.protocol = request.protocol() != null ? request.protocol().name() : "TCP";
        model.host = normalizeHost(request.host(), request.ip());
        model.url = normalizeUrl(request, model.host);
        model.method = request.method() != null ? request.method().name() : null;
        model.appPackageName = resolvePackageName(request.uid());
        model.timestamp = request.time();
        model.startTime = request.time();
        model.endTime = request.time();
        model.costTimeMs = 0L;
        model.requestBytes = 0;
        model.responseBytes = 0;
        model.encrypted = true;
        // 为什么这样做：没有证书时只能保住主机、URL 和时间等最小元数据，因此单独标记为 MINIMAL，避免和“已部分解析”混淆。
        model.parseStatus = VpnTrafficLogParseStatus.MINIMAL;
        model.errorMessage = resolveString(R.string.show_sdk_vpn_certificate_required);
        model.sourceDescription = "VPN";
        VpnTrafficLogRepository.getInstance().appendLog(model);
    }

    @NonNull
    private static String normalizeHost(@Nullable String host, @Nullable String fallbackIp) {
        if (host != null && !host.trim().isEmpty()) {
            return host.trim();
        }
        if (fallbackIp != null && !fallbackIp.trim().isEmpty()) {
            return fallbackIp.trim();
        }
        return "unknown";
    }

    @NonNull
    private static String normalizeUrl(@NonNull HttpRequest request, @NonNull String host) {
        String path = request.path();
        StringBuilder builder = new StringBuilder();
        builder.append(request.isHttps() ? "https://" : "http://");
        builder.append(host);
        if (path != null && !path.trim().isEmpty()) {
            builder.append(path.trim());
        }
        return builder.toString();
    }

    /**
     * 为什么这样做：该类运行在抓包回调里，没有 UI 上下文，只能从 Application 读取资源文案，保证提示文本依然走字符串资源。
     */
    @NonNull
    private static String resolveString(int resId) {
        if (ShowLog.getApplicationInternal() != null) {
            try {
                return ShowLog.getApplicationInternal().getString(resId);
            } catch (Throwable ignored) {
                // 为什么这样做：提示文案读取失败不应阻断日志写入，直接返回资源 ID 比抛异常更安全。
            }
        }
        return String.valueOf(resId);
    }

    /**
     * 为什么这样做：HTTPS 证书未安装时，落库链路不能依赖宿主拦截器补包名，因此这里直接根据 UID 再做一次轻量映射，尽量减少列表里出现“未知”。
     */
    @NonNull
    private static String resolvePackageName(int uid) {
        Context application = ShowLog.getApplicationInternal();
        if (application == null || uid <= 0) {
            return "";
        }
        try {
            Set<String> effectivePackages = VpnCapturePackageStore.getEffectivePackageNames(application);
            String[] packagesForUid = application.getPackageManager().getPackagesForUid(uid);
            if (packagesForUid == null || packagesForUid.length == 0) {
                return "";
            }
            if (effectivePackages.size() == 1) {
                return effectivePackages.iterator().next();
            }
            String matchedPackage = null;
            for (String packageName : packagesForUid) {
                if (!effectivePackages.contains(packageName)) {
                    continue;
                }
                if (matchedPackage != null && !matchedPackage.equals(packageName)) {
                    return "";
                }
                matchedPackage = packageName;
            }
            return matchedPackage != null ? matchedPackage : "";
        } catch (Throwable ignored) {
            // 为什么这样做：这里属于最佳努力映射，失败时保持空值比误判来源更安全。
            return "";
        }
    }
}
