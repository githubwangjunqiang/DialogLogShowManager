package com.xq.dialoglogshow.model;

import androidx.annotation.Nullable;

import java.util.Map;

/**
 * VPN 网络日志模型
 *
 * 为什么这样做：
 * 1. 这是后续控制台、详情页和历史查询共用的唯一日志模型，先把字段稳定下来，后续 UI 与存储都围绕它演进。
 * 2. Step 4 当前先落地连接元数据与基础协议识别，即使 body/header 还未完全填满，也能保证模型层不再反复重构。
 */
public class VpnTrafficLogModel {
    public String logId;
    public String protocol;
    public String host;
    public String url;
    public String method;
    public String statusCode;
    public long timestamp;
    public long startTime;
    public long endTime;
    public long costTimeMs;
    public int requestBytes;
    public int responseBytes;
    public boolean encrypted;
    public String parseStatus;
    public Map<String, String> requestHeaders;
    public String requestBody;
    public Map<String, String> responseHeaders;
    public String responseBody;
    public String errorMessage;
    public String sourceDescription;
    public String appPackageName;

    @Nullable
    public String buildSummaryText() {
        if (host == null && url == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        builder.append(protocol != null ? protocol : "UNKNOWN")
                .append(" ")
                .append(host != null ? host : url);
        if (statusCode != null && statusCode.length() > 0) {
            builder.append(" [").append(statusCode).append("]");
        }
        builder.append(" ").append(costTimeMs).append("ms");
        return builder.toString();
    }
}
