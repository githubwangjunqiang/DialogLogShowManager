package com.xq.dialoglogshow.core.model;

import androidx.annotation.NonNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 接口名（Path）聚合折叠展示模型。
 * 为什么这样做：
 * 当用户开启“接口名分组”模式时，同类接口聚合为一个父级卡片展示，清晰呈现总调用次数、失败率、最新耗时与状态，避免高频接口刷屏。
 */
public final class HttpLogGroupItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 接口路径，如 /api/v1/user/login。
     */
    @NonNull
    public final String path;

    /**
     * 请求方法，如 POST。
     */
    @NonNull
    public final String method;

    /**
     * 所属域名 Host。
     */
    @NonNull
    public final String host;

    /**
     * 该分组下包含的全部实际请求记录列表。
     */
    @NonNull
    public final List<HttpLogRecord> records = new ArrayList<>();

    /**
     * 该接口调用的总次数。
     */
    public int totalCount;

    /**
     * 该接口失败的次数。
     */
    public int failureCount;

    /**
     * 最新一次调用的发起时间戳。
     */
    public long latestTimestamp;

    /**
     * 最新一次调用的耗时（毫秒）。
     */
    public long latestDurationMs;

    /**
     * 最新一次调用的状态码。
     */
    public int latestStatusCode;

    /**
     * 最新一次调用是否业务成功。
     * 为什么这样做：分组摘要需要与日志条目使用同一业务成功口径，失败时才能在列表外层标红提示。
     */
    public boolean latestSuccess;

    /**
     * 在 UI 列表中是否处于展开状态。
     */
    public boolean isExpanded;

    public HttpLogGroupItem(@NonNull String path, @NonNull String method, @NonNull String host) {
        this.path = path;
        this.method = method;
        this.host = host;
    }

    /**
     * 向分组中追加一条请求记录并更新最新摘要统计。
     * 为什么这样做：聚合计算在后台或数据组织阶段一次性完成，避免 RecyclerView 滑动时反复统计导致掉帧。
     */
    public void addRecord(@NonNull HttpLogRecord record) {
        records.add(record);
        totalCount++;
        if (record.isFailed) {
            failureCount++;
        }
        if (record.timestamp >= latestTimestamp) {
            latestTimestamp = record.timestamp;
            latestDurationMs = record.durationMs;
            latestStatusCode = record.statusCode;
            latestSuccess = record.success;
        }
    }
}
