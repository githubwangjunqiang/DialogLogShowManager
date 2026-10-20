package com.xq.dialoglogshowmanager.ui.utils;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.xq.dialoglogshow.collector.vpn.repository.VpnTrafficLogQueryHelper;
import com.xq.dialoglogshow.collector.vpn.repository.VpnTrafficLogRepository;
import com.xq.dialoglogshow.model.VpnTrafficLogModel;
import com.xq.dialoglogshowmanager.ui.entity.CaptureAbilityState;
import com.xq.dialoglogshowmanager.ui.entity.CertificateTrustState;
import com.xq.dialoglogshowmanager.ui.entity.InstalledAppInfo;
import com.xq.dialoglogshowmanager.ui.entity.TargetAppStatusItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 目标 App 抓包状态分析器。
 * 为什么这样做：证书是否信任、日志是否可拦截，不能从系统直接读取，只能结合当前日志落库结果做推断。
 */
public final class AppCaptureStatusAnalyzer {

    private AppCaptureStatusAnalyzer() {
        // 为什么这样做：该类只负责纯分析，不保存 UI 状态。
    }

    /**
     * 基于已安装 App 和当天抓包日志生成展示项。
     * 为什么这样做：把日志查询收口到这里，Activity 只负责发起刷新和渲染，后续更容易替换成 ViewModel。
     */
    @NonNull
    public static List<TargetAppStatusItem> buildStatusItems(@NonNull Context context,
                                                             @NonNull List<InstalledAppInfo> installedApps) {
        VpnTrafficLogRepository repository = VpnTrafficLogRepository.getInstance();
        VpnTrafficLogQueryHelper.TimeRange timeRange =
                VpnTrafficLogQueryHelper.buildDayRange(System.currentTimeMillis());
        ArrayList<VpnTrafficLogModel> logs = repository.queryLogsByRange(
                timeRange.startTime,
                timeRange.endTime
        );
        Map<String, TrafficSummary> summaryMap = new LinkedHashMap<>();
        for (VpnTrafficLogModel log : logs) {
            if (log == null || TextUtils.isEmpty(log.appPackageName)) {
                continue;
            }
            TrafficSummary summary = summaryMap.get(log.appPackageName);
            if (summary == null) {
                summary = new TrafficSummary();
                summaryMap.put(log.appPackageName, summary);
            }
            summary.consume(log);
        }

        ArrayList<TargetAppStatusItem> result = new ArrayList<>();
        for (InstalledAppInfo installedApp : installedApps) {
            TrafficSummary summary = summaryMap.get(installedApp.packageName);
            if (summary == null) {
                result.add(new TargetAppStatusItem(
                        installedApp,
                        CertificateTrustState.UNKNOWN,
                        CaptureAbilityState.UNKNOWN,
                        false,
                        false
                ));
                continue;
            }
            result.add(new TargetAppStatusItem(
                    installedApp,
                    summary.resolveTrustState(),
                    summary.resolveCaptureState(),
                    summary.hasTrafficSample,
                    summary.hasHttpsTrafficSample
            ));
        }
        return result;
    }

    /**
     * 让选择后的单个 App 也能快速补一次状态。
     * 为什么这样做：输入框选中某个包名后，用户最关心的就是它当前有没有样本，因此单独开放一个查询入口更顺手。
     */
    @NonNull
    public static TargetAppStatusItem buildSingleStatusItem(@NonNull Context context,
                                                            @NonNull InstalledAppInfo installedApp,
                                                            @NonNull List<VpnTrafficLogModel> logs) {
        TrafficSummary summary = new TrafficSummary();
        for (VpnTrafficLogModel log : logs) {
            if (log != null && installedApp.packageName.equals(log.appPackageName)) {
                summary.consume(log);
            }
        }
        return new TargetAppStatusItem(
                installedApp,
                summary.resolveTrustState(),
                summary.resolveCaptureState(),
                summary.hasTrafficSample,
                summary.hasHttpsTrafficSample
        );
    }

    /**
     * 为什么这样做：分析阶段只需要少量布尔值和最近状态，不必把完整日志都留在内存里。
     */
    private static final class TrafficSummary {
        private boolean hasTrafficSample;
        private boolean hasHttpsTrafficSample;
        private boolean hasHttpTrafficSample;
        private boolean hasHttpsSuccessSample;
        private boolean hasHttpsPartialSample;

        private void consume(@NonNull VpnTrafficLogModel model) {
            hasTrafficSample = true;
            boolean isHttps = "HTTPS".equalsIgnoreCase(model.protocol) || model.encrypted;
            if (isHttps) {
                hasHttpsTrafficSample = true;
                if ("SUCCESS".equalsIgnoreCase(model.parseStatus)) {
                    hasHttpsSuccessSample = true;
                } else if ("PARTIAL".equalsIgnoreCase(model.parseStatus)) {
                    hasHttpsPartialSample = true;
                }
            } else {
                hasHttpTrafficSample = true;
            }
        }

        @NonNull
        private CertificateTrustState resolveTrustState() {
            if (!hasTrafficSample) {
                return CertificateTrustState.UNKNOWN;
            }
            if (hasHttpsSuccessSample) {
                return CertificateTrustState.TRUSTED;
            }
            if (hasHttpsTrafficSample) {
                return CertificateTrustState.NOT_TRUSTED;
            }
            return hasHttpTrafficSample ? CertificateTrustState.NOT_REQUIRED : CertificateTrustState.UNKNOWN;
        }

        @NonNull
        private CaptureAbilityState resolveCaptureState() {
            if (!hasTrafficSample) {
                return CaptureAbilityState.UNKNOWN;
            }
            if (hasHttpsSuccessSample && !hasHttpsPartialSample) {
                return CaptureAbilityState.FULLY_CAPTURED;
            }
            if (hasHttpsSuccessSample || hasHttpsPartialSample || hasHttpsTrafficSample) {
                return CaptureAbilityState.PARTIALLY_CAPTURED;
            }
            if (hasHttpTrafficSample) {
                return CaptureAbilityState.FULLY_CAPTURED;
            }
            return CaptureAbilityState.NOT_CAPTURED;
        }
    }
}
