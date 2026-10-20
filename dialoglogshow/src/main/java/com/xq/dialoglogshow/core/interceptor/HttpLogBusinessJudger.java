package com.xq.dialoglogshow.core.interceptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.util.Map;

/**
 * 业务结果与成功判定拦截器接口。
 * 为什么这样做：
 * 在 OkHttp 自动拦截场景下，默认只根据 HTTP 2xx 判定成功与否。
 * 业务通过配置此拦截器，可以全权根据自定义报文格式（如业务 code 是否为 0）动态裁决最终的成功/失败状态与展示文本。
 */
@FunctionalInterface
public interface HttpLogBusinessJudger {

    /**
     * 判定当前请求结果。
     *
     * @param context 包含当前接口全部信息的上下文快照（只读）
     * @return 业务判定结果：包含 isSuccess 与展示用 message
     */
    @NonNull
    JudgementResult judge(@NonNull RequestContext context);

    /**
     * 接口上下文只读数据快照。
     */
    class RequestContext {
        public final String url;
        public final String host;
        public final String path;
        public final String method;
        public final int httpCode;
        public final long durationMs;
        public final Map<String, String> requestHeaders;
        public final Map<String, String> responseHeaders;
        public final String requestBody;
        public final String responseBody;
        public final Throwable error;

        public RequestContext(String url, String method, int httpCode,
                              String requestHeadersStr, String requestBody,
                              String responseHeadersStr, String responseBody,
                              long durationMs) {
            this(url, extractHost(url), extractPath(url), method, httpCode, durationMs,
                    null, null, requestBody, responseBody, null);
        }

        public RequestContext(String url, String host, String path, String method, int httpCode, long durationMs,
                              Map<String, String> requestHeaders, Map<String, String> responseHeaders,
                              String requestBody, String responseBody, Throwable error) {
            this.url = url != null ? url : "";
            this.host = host != null ? host : "";
            this.path = path != null ? path : "";
            this.method = method != null ? method : "GET";
            this.httpCode = httpCode;
            this.durationMs = durationMs;
            this.requestHeaders = requestHeaders;
            this.responseHeaders = responseHeaders;
            this.requestBody = requestBody;
            this.responseBody = responseBody;
            this.error = error;
        }

        private static String extractHost(String url) {
            if (url == null) return "";
            try {
                java.net.URI uri = java.net.URI.create(url);
                return uri.getHost() != null ? uri.getHost() : "";
            } catch (Throwable t) {
                return "";
            }
        }

        private static String extractPath(String url) {
            if (url == null) return "";
            try {
                java.net.URI uri = java.net.URI.create(url);
                return uri.getPath() != null ? uri.getPath() : "";
            } catch (Throwable t) {
                return url;
            }
        }

        public String getUrl() {
            return url;
        }

        public String getHost() {
            return host;
        }

        public String getPath() {
            return path;
        }

        public String getMethod() {
            return method;
        }

        public int getHttpCode() {
            return httpCode;
        }

        public long getDurationMs() {
            return durationMs;
        }

        @Nullable
        public Map<String, String> getRequestHeaders() {
            return requestHeaders;
        }

        @Nullable
        public Map<String, String> getResponseHeaders() {
            return responseHeaders;
        }

        @Nullable
        public String getRequestBody() {
            return requestBody;
        }

        @Nullable
        public String getResponseBody() {
            return responseBody;
        }

        @Nullable
        public Throwable getError() {
            return error;
        }

        /**
         * 便捷辅助方法：尝试将响应体解析为 JSONObject（解析失败或为空时返回 null）。
         */
        @Nullable
        public JSONObject optResponseBodyAsJson() {
            if (responseBody == null || responseBody.trim().isEmpty()) {
                return null;
            }
            try {
                return new JSONObject(responseBody);
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    /**
     * 业务裁决结果。
     */
    class JudgementResult {
        public final boolean success;
        public final String message;

        public JudgementResult(boolean success, @Nullable String message) {
            this.success = success;
            this.message = message != null ? message : (success ? "业务请求成功" : "业务请求失败");
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public static JudgementResult success() {
            return new JudgementResult(true, "OK");
        }

        public static JudgementResult success(String message) {
            return new JudgementResult(true, message);
        }

        public static JudgementResult failure(String message) {
            return new JudgementResult(false, message);
        }
    }
}
