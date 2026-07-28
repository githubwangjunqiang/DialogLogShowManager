package com.xq.dialoglogshow.storage.entity;

/**
 * VPN 日志数据库实体
 *
 * 为什么这样做：
 * 1. 数据库存储字段与 UI 模型解耦后，后续即使展示层调整，也不会影响表结构与查询逻辑。
 * 2. 这里统一使用基础类型和字符串，避免数据库层直接持有复杂 Map 对象导致序列化边界散落。
 */
public class VpnTrafficLogEntity {
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
    public int encrypted;
    public String parseStatus;
    public String requestHeadersJson;
    public String requestBody;
    public String responseHeadersJson;
    public String responseBody;
    public String errorMessage;
    public String sourceDescription;
    public String appPackageName;
}
