package com.xq.dialoglogshowmanager.ui.entity;

import androidx.annotation.Keep;

/**
 * 目标 App 的证书信任推断结果。
 * 为什么这样做：Android 没有公开 API 直接读取第三方 App 是否信任用户证书，因此这里只能基于当前抓包记录推断。
 */
@Keep
public enum CertificateTrustState {
    /**
     * 为什么这样做：当前已经出现 HTTPS 明文抓包，说明目标 App 至少在当前环境里接受了调试证书。
     */
    TRUSTED,
    /**
     * 为什么这样做：只看到 HTTPS 连接级日志时，说明目标 App 暂时没有接受调试证书。
     */
    NOT_TRUSTED,
    /**
     * 为什么这样做：如果当前只有 HTTP 流量，证书信任并不参与判断，避免误导用户。
     */
    NOT_REQUIRED,
    /**
     * 为什么这样做：没有任何可分析的日志时，不能把状态硬判成失败。
     */
    UNKNOWN
}
