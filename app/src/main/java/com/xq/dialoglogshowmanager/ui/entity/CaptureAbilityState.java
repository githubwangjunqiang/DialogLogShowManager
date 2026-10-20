package com.xq.dialoglogshowmanager.ui.entity;

import androidx.annotation.Keep;

/**
 * 目标 App 的抓包能力推断结果。
 * 为什么这样做：用户关心的不是“理论上能不能”，而是当前这台手机、当前这份证书环境里到底能看到什么。
 */
@Keep
public enum CaptureAbilityState {
    /**
     * 为什么这样做：已经拿到完整 HTTP/HTTPS 明文，说明当前抓包链路是可用的。
     */
    FULLY_CAPTURED,
    /**
     * 为什么这样做：只拿到连接级元数据时，说明请求发生了，但正文没有被解出来。
     */
    PARTIALLY_CAPTURED,
    /**
     * 为什么这样做：没有任何有效日志时，不能误报为已拦截成功。
     */
    NOT_CAPTURED,
    /**
     * 为什么这样做：当前没有可分析样本，只能保持未知。
     */
    UNKNOWN
}
