package com.xq.dialoglogshowmanager.ui.entity;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

/**
 * 目标 App 的完整展示项。
 * 为什么这样做：列表不仅要展示静态安装信息，还要承载当前抓包状态，拆成独立模型后适合直接驱动 Adapter。
 */
@Keep
public final class TargetAppStatusItem {

    /**
     * 为什么这样做：静态 App 元信息和运行时状态是两条来源不同的数据线，合并在一个模型里能减少 UI 层拼装成本。
     */
    @NonNull
    public final InstalledAppInfo appInfo;

    /**
     * 为什么这样做：证书是否被信任需要基于当前抓包结果推断，不能当成静态真值。
     */
    @NonNull
    public final CertificateTrustState certificateTrustState;

    /**
     * 为什么这样做：是否可拦截到日志，最终反映的是当前 VPN 证书和目标 App 的共同结果。
     */
    @NonNull
    public final CaptureAbilityState captureAbilityState;

    /**
     * 为什么这样做：没有抓到日志时，UI 需要知道这是“未出现样本”还是“已经部分命中”。
     */
    public final boolean hasTrafficSample;

    /**
     * 为什么这样做：HTTPS 样本能帮助区分“证书信任成功”还是“只是普通 HTTP”。
     */
    public final boolean hasHttpsTrafficSample;

    public TargetAppStatusItem(@NonNull InstalledAppInfo appInfo,
                               @NonNull CertificateTrustState certificateTrustState,
                               @NonNull CaptureAbilityState captureAbilityState,
                               boolean hasTrafficSample,
                               boolean hasHttpsTrafficSample) {
        this.appInfo = appInfo;
        this.certificateTrustState = certificateTrustState;
        this.captureAbilityState = captureAbilityState;
        this.hasTrafficSample = hasTrafficSample;
        this.hasHttpsTrafficSample = hasHttpsTrafficSample;
    }
}
