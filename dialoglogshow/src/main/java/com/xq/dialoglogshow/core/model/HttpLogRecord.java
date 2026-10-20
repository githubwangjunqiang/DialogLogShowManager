package com.xq.dialoglogshow.core.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 全新 HTTP/HTTPS 网络日志不可变实体。
 * 为什么这样做：
 * 1. 采用不可变数据模型（所有关键字段为 final），可确保高并发网络请求与后台异步写库、UI 线程渲染之间绝对线程安全，杜绝数据竞态。
 * 2. 覆盖接口诊断所需的全维度数据（URL、Host、Path、Method、状态码、Headers、Body、耗时、时间戳、异常信息及尺寸），不再混杂非网络杂质。
 * 3. 引入业务 success 字段与 message 字段，业务成功与否与 HTTP 传输状态码解耦，满足真实业务裁决标准。
 */
public final class HttpLogRecord implements Serializable {

    private static final long serialVersionUID = 2L;

    /**
     * 数据库自增主键 ID；在尚未落盘的内存缓冲队列中为唯一的负数临时 ID。
     */
    public final long id;

    /**
     * 唯一跟踪 ID，用于内存缓冲与数据库去重合并。
     */
    @NonNull
    public final String traceId;

    /**
     * 完整请求 URL。
     */
    @NonNull
    public final String url;

    /**
     * 主机域名，如 api.example.com，用于建立索引和快速检索。
     */
    @NonNull
    public final String host;

    /**
     * 路径部分，如 /api/v1/user/login，用于接口名折叠分组。
     */
    @NonNull
    public final String path;

    /**
     * 请求方式，如 GET, POST, PUT, DELETE。
     */
    @NonNull
    public final String method;

    /**
     * HTTP 响应状态码，如 200, 404, 500；若请求在网络传输层发生异常未完成，则固定为 -1。
     */
    public final int statusCode;

    /**
     * HTTP 传输层状态码别名（与 statusCode 对等）。
     */
    public final int httpCode;

    /**
     * 本次接口调用在业务上是否算作成功（控制台以此字段为准决定红绿着色与过滤）。
     */
    public final boolean success;

    /**
     * 接口返回的业务提示信息、错误说明或状态描述。
     */
    @NonNull
    public final String message;

    /**
     * 请求头格式化文本。
     */
    @Nullable
    public final String requestHeaders;

    /**
     * 请求体（已经过安全截断和脱敏）。
     */
    @Nullable
    public final String requestBody;

    /**
     * 响应头格式化文本。
     */
    @Nullable
    public final String responseHeaders;

    /**
     * 响应体（已经过自动解压、安全截断与格式化）。
     */
    @Nullable
    public final String responseBody;

    /**
     * 报文 MIME 类型，如 application/json, text/plain。
     */
    @Nullable
    public final String contentType;

    /**
     * 请求体字节数。
     */
    public final long requestSize;

    /**
     * 响应体字节数。
     */
    public final long responseSize;

    /**
     * 完整耗时（毫秒）。
     */
    public final long durationMs;

    /**
     * 请求发起时间戳（毫秒），用于默认当天筛选及时间排序。
     */
    public final long timestamp;

    /**
     * 异常错误信息与堆栈摘要；成功请求为 null。
     */
    @Nullable
    public final String errorMessage;

    /**
     * 是否失败（兼容旧字段，逻辑恒等于 !success）。
     */
    public final boolean isFailed;

    private HttpLogRecord(Builder builder) {
        this.id = builder.id;
        this.traceId = builder.traceId != null ? builder.traceId : String.valueOf(System.nanoTime());
        this.url = builder.url != null ? builder.url : "";
        this.host = builder.host != null ? builder.host : "";
        this.path = builder.path != null ? builder.path : "";
        this.method = builder.method != null ? builder.method.toUpperCase() : "GET";
        this.statusCode = builder.statusCode;
        this.httpCode = builder.httpCode != 0 ? builder.httpCode : builder.statusCode;
        this.success = builder.success;
        this.message = builder.message != null ? builder.message : (builder.success ? "OK" : "FAILED");
        this.requestHeaders = builder.requestHeaders;
        this.requestBody = builder.requestBody;
        this.responseHeaders = builder.responseHeaders;
        this.responseBody = builder.responseBody;
        this.contentType = builder.contentType;
        this.requestSize = builder.requestSize;
        this.responseSize = builder.responseSize;
        this.durationMs = builder.durationMs;
        this.timestamp = builder.timestamp > 0L ? builder.timestamp : System.currentTimeMillis();
        this.errorMessage = builder.errorMessage;
        this.isFailed = !builder.success;
    }

    /**
     * 根据当前记录创建带新数据库自增 ID 的副本。
     */
    @NonNull
    public HttpLogRecord withId(long newId) {
        return new Builder(this).id(newId).build();
    }

    /**
     * 从强制纯抽象契约 HttpLogPayload 转换为不可变实体 HttpLogRecord。
     * 自动处理 JSONObject 的格式化缩进与安全转换。
     */
    @NonNull
    public static HttpLogRecord fromPayload(@NonNull HttpLogPayload payload) {
        String url = payload.getUrl();
        String host = "";
        String path = "";
        try {
            java.net.URI uri = java.net.URI.create(url);
            host = uri.getHost() != null ? uri.getHost() : "";
            path = uri.getPath() != null ? uri.getPath() : "";
        } catch (Throwable ignored) {
            host = "unknown";
            path = url;
        }

        String requestBodyStr = null;
        if (payload.getRequestBody() != null) {
            try {
                requestBodyStr = payload.getRequestBody().toString(2);
            } catch (Throwable ignored) {
                requestBodyStr = payload.getRequestBody().toString();
            }
        }

        String responseBodyStr = null;
        if (payload.getResponseBody() != null) {
            try {
                responseBodyStr = payload.getResponseBody().toString(2);
            } catch (Throwable ignored) {
                responseBodyStr = payload.getResponseBody().toString();
            }
        }

        String reqHeadersStr = null;
        if (payload.getRequestHeaders() != null && !payload.getRequestHeaders().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry : payload.getRequestHeaders().entrySet()) {
                sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
            reqHeadersStr = sb.toString().trim();
        }

        String resHeadersStr = null;
        if (payload.getResponseHeaders() != null && !payload.getResponseHeaders().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry : payload.getResponseHeaders().entrySet()) {
                sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
            resHeadersStr = sb.toString().trim();
        }

        String errorMsg = null;
        if (payload.getThrowable() != null) {
            java.io.StringWriter sw = new java.io.StringWriter();
            java.io.PrintWriter pw = new java.io.PrintWriter(sw);
            payload.getThrowable().printStackTrace(pw);
            errorMsg = sw.toString();
        } else if (!payload.isSuccess()) {
            errorMsg = payload.getMessage();
        }

        long reqSize = requestBodyStr != null ? requestBodyStr.getBytes(StandardCharsets.UTF_8).length : 0L;
        long resSize = responseBodyStr != null ? responseBodyStr.getBytes(StandardCharsets.UTF_8).length : 0L;

        return new Builder()
                .url(url)
                .host(host)
                .path(path)
                .method(payload.getMethod())
                .statusCode(payload.getHttpCode())
                .httpCode(payload.getHttpCode())
                .success(payload.isSuccess())
                .message(payload.getMessage())
                .requestHeaders(reqHeadersStr)
                .requestBody(requestBodyStr)
                .responseHeaders(resHeadersStr)
                .responseBody(responseBodyStr)
                .durationMs(payload.getDurationMs())
                .timestamp(System.currentTimeMillis())
                .requestSize(reqSize)
                .responseSize(resSize)
                .errorMessage(errorMsg)
                .isFailed(!payload.isSuccess())
                .build();
    }

    /**
     * 构造器类，支持链式构建和字段安全校验。
     */
    public static final class Builder {
        private long id = 0L;
        private String traceId;
        private String url = "";
        private String host = "";
        private String path = "";
        private String method = "GET";
        private int statusCode = 200;
        private int httpCode = 200;
        private boolean success = true;
        private String message = "OK";
        private String requestHeaders;
        private String requestBody;
        private String responseHeaders;
        private String responseBody;
        private String contentType;
        private long requestSize = 0L;
        private long responseSize = 0L;
        private long durationMs = 0L;
        private long timestamp = 0L;
        private String errorMessage;
        private boolean isFailed = false;

        public Builder() {
        }

        public Builder(@NonNull HttpLogRecord copy) {
            this.id = copy.id;
            this.traceId = copy.traceId;
            this.url = copy.url;
            this.host = copy.host;
            this.path = copy.path;
            this.method = copy.method;
            this.statusCode = copy.statusCode;
            this.httpCode = copy.httpCode;
            this.success = copy.success;
            this.message = copy.message;
            this.requestHeaders = copy.requestHeaders;
            this.requestBody = copy.requestBody;
            this.responseHeaders = copy.responseHeaders;
            this.responseBody = copy.responseBody;
            this.contentType = copy.contentType;
            this.requestSize = copy.requestSize;
            this.responseSize = copy.responseSize;
            this.durationMs = copy.durationMs;
            this.timestamp = copy.timestamp;
            this.errorMessage = copy.errorMessage;
            this.isFailed = copy.isFailed;
        }

        public Builder id(long id) {
            this.id = id;
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder method(String method) {
            this.method = method;
            return this;
        }

        public Builder statusCode(int statusCode) {
            this.statusCode = statusCode;
            this.httpCode = statusCode;
            return this;
        }

        public Builder httpCode(int httpCode) {
            this.httpCode = httpCode;
            this.statusCode = httpCode;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            this.isFailed = !success;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder requestHeaders(String requestHeaders) {
            this.requestHeaders = requestHeaders;
            return this;
        }

        public Builder requestBody(String requestBody) {
            this.requestBody = requestBody;
            return this;
        }

        public Builder responseHeaders(String responseHeaders) {
            this.responseHeaders = responseHeaders;
            return this;
        }

        public Builder responseBody(String responseBody) {
            this.responseBody = responseBody;
            return this;
        }

        public Builder contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder requestSize(long requestSize) {
            this.requestSize = requestSize;
            return this;
        }

        public Builder responseSize(long responseSize) {
            this.responseSize = responseSize;
            return this;
        }

        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder isFailed(boolean isFailed) {
            this.isFailed = isFailed;
            this.success = !isFailed;
            return this;
        }

        @NonNull
        public HttpLogRecord build() {
            return new HttpLogRecord(this);
        }
    }
}
