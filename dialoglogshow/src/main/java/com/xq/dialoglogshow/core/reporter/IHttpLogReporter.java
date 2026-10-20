package com.xq.dialoglogshow.core.reporter;

import androidx.annotation.NonNull;

import com.xq.dialoglogshow.core.model.HttpLogRecord;

/**
 * 开放式通用网络日志上报契约接口。
 * 为什么这样做：
 * 为非 OkHttp 用户或自研网络库提供极简的数据投递标准，用户只需传入标准化的 HttpLogRecord，
 * SDK 内部自动完成并发排队、异步批量写库、LRU 淘汰与界面渲染，完全零负担。
 */
public interface IHttpLogReporter {

    /**
     * 上报单条网络日志。
     *
     * @param record 标准网络日志实体
     */
    void report(@NonNull HttpLogRecord record);
}
