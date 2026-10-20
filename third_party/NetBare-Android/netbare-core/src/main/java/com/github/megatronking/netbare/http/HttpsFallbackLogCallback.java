package com.github.megatronking.netbare.http;

import androidx.annotation.NonNull;

/**
 * HTTPS 无证书兜底日志回调。
 *
 * 为什么这样做：NetBare 核心库本身不应该依赖宿主业务仓库，但在证书未安装时，
 * 核心层仍然需要把“这里已经拦到 HTTPS 连接”这件事通知给宿主，由宿主决定写库、
 * 打点还是仅展示提示。
 */
public interface HttpsFallbackLogCallback {

    /**
     * HTTPS 明文解析不可用时回调一次。
     *
     * 为什么这样做：这里只有连接结束态最稳定，宿主可以在这里把最小元数据落库，
     * 避免每个分片都重复写入同一条日志。
     */
    void onHttpsRequestFinished(@NonNull HttpRequest request);
}
