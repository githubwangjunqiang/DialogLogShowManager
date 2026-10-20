package com.xq.dialoglogshow.core.interceptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.xq.dialoglogshow.ShowLog;
import com.xq.dialoglogshow.core.config.ShowLogConfig;
import com.xq.dialoglogshow.core.model.HttpLogRecord;
import com.xq.dialoglogshow.core.repository.HttpLogRepository;

import java.io.EOFException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.GzipSource;

/**
 * 专用于 OkHttp 客户端的开箱即用网络日志拦截器。
 * 为什么这样做：
 * 1. 采用适配器模式将 OkHttp 的请求与响应无感转换为 SDK 通用的 HttpLogRecord 实体；
 * 2. 使用 Okio 的 Buffer.clone() 克隆缓冲流读取，绝对不提前消费或破坏宿主业务层的数据流；
 * 3. 严格限制最大读取截断长度（默认 256KB），内置纯文本嗅探算法，防止大文件 OOM 与二进制乱码；
 * 4. 严格捕获网络超时、连接拒绝、SSL 校验等 IOException 留痕，并在记录后原样 throw 抛出，严禁吞掉宿主业务的网络异常。
 */
public class ShowLogOkHttpInterceptor implements Interceptor {

    private final int mMaxBodyBytes;
    private final Set<String> mRedactHeaders;

    public ShowLogOkHttpInterceptor() {
        this(ShowLogConfig.defaultConfig());
    }

    public ShowLogOkHttpInterceptor(@NonNull ShowLogConfig config) {
        this.mMaxBodyBytes = config.maxBodyBytes;
        this.mRedactHeaders = config.redactHeaders;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();

        // 为什么这样做：生产包或调试开关关闭时，实现 100% 纯粹的零开销放行（Passthrough），绝不消耗 CPU 与额外内存
        if (!ShowLog.isEnabled()) {
            return chain.proceed(request);
        }

        long startNs = System.nanoTime();
        long requestTime = System.currentTimeMillis();

        HttpUrl httpUrl = request.url();
        String urlString = httpUrl.toString();
        String host = httpUrl.host();
        String path = httpUrl.encodedPath();
        String method = request.method();

        // 1. 提取请求头（含脱敏）
        String requestHeadersStr = formatHeaders(request.headers());

        // 2. 提取请求体
        String requestBodyStr = null;
        long requestSize = 0L;
        RequestBody requestBody = request.body();
        if (requestBody != null) {
            try {
                Buffer buffer = new Buffer();
                requestBody.writeTo(buffer);
                requestSize = buffer.size();
                if (isPlaintext(buffer)) {
                    Charset charset = StandardCharsets.UTF_8;
                    MediaType contentType = requestBody.contentType();
                    if (contentType != null) {
                        charset = contentType.charset(StandardCharsets.UTF_8);
                        if (charset == null) {
                            charset = StandardCharsets.UTF_8;
                        }
                    }
                    requestBodyStr = readBufferSafe(buffer, charset, mMaxBodyBytes);
                } else {
                    requestBodyStr = "[Binary Data: size=" + requestSize + " bytes]";
                }
            } catch (Throwable t) {
                requestBodyStr = "[Failed to read request body: " + t.getMessage() + "]";
            }
        }

        Response response;
        try {
            response = chain.proceed(request);
        } catch (IOException ioException) {
            // 为什么这样做：网络超时、断网、DNS 失败或 SSL 校验失败时，记录异常堆栈并将状态码记为 -1，随后原样重新抛出异常，绝不吞噬。
            long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
            String ioMsg = ioException.getMessage() != null ? ioException.getMessage() : "Network IOException";
            HttpLogRecord failedRecord = new HttpLogRecord.Builder()
                    .url(urlString)
                    .host(host)
                    .path(path)
                    .method(method)
                    .statusCode(-1)
                    .httpCode(-1)
                    .success(false)
                    .message(ioMsg)
                    .requestHeaders(requestHeadersStr)
                    .requestBody(requestBodyStr)
                    .requestSize(requestSize)
                    .durationMs(tookMs)
                    .timestamp(requestTime)
                    .errorMessage(getStackTraceString(ioException))
                    .isFailed(true)
                    .build();

            HttpLogRepository.getInstance().enqueueLog(failedRecord);
            throw ioException;
        }

        long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
        int statusCode = response.code();
        Headers responseHeaders = response.headers();
        String responseHeadersStr = formatHeaders(responseHeaders);

        // 3. 提取响应体
        String responseBodyStr = null;
        String contentTypeStr = null;
        long responseSize = 0L;
        ResponseBody responseBody = response.body();

        if (responseBody != null) {
            MediaType mediaType = responseBody.contentType();
            if (mediaType != null) {
                contentTypeStr = mediaType.toString();
            }
            try {
                BufferedSource source = responseBody.source();
                // 为什么这样做：避免大文件或长流下载时全量缓冲导致 OOM，仅预读最大配置字节量
                long prefetchBytes = mMaxBodyBytes > 0 ? (long) mMaxBodyBytes : 256 * 1024L;
                source.request(prefetchBytes);
                Buffer buffer = source.getBuffer();

                // 处理 gzip 自动解压兼容
                if ("gzip".equalsIgnoreCase(responseHeaders.get("Content-Encoding"))) {
                    try (GzipSource gzippedResponseBody = new GzipSource(buffer.clone())) {
                        buffer = new Buffer();
                        buffer.writeAll(gzippedResponseBody);
                    } catch (Throwable ignored) {
                    }
                }

                responseSize = buffer.size();
                if (isPlaintext(buffer)) {
                    Charset charset = StandardCharsets.UTF_8;
                    if (mediaType != null) {
                        charset = mediaType.charset(StandardCharsets.UTF_8);
                        if (charset == null) {
                            charset = StandardCharsets.UTF_8;
                        }
                    }
                    responseBodyStr = readBufferSafe(buffer.clone(), charset, mMaxBodyBytes);
                } else {
                    responseBodyStr = "[Binary Data: size=" + responseSize + " bytes, type=" + contentTypeStr + "]";
                }
            } catch (Throwable t) {
                responseBodyStr = "[Failed to read response body: " + t.getMessage() + "]";
            }
        }

        boolean isSuccess = (statusCode >= 200 && statusCode < 300);
        String businessMessage = isSuccess ? "OK" : ("HTTP " + statusCode);
        HttpLogBusinessJudger judger = ShowLog.getBusinessJudger();
        if (judger != null) {
            try {
                HttpLogBusinessJudger.RequestContext context = new HttpLogBusinessJudger.RequestContext(
                        urlString,
                        method,
                        statusCode,
                        requestHeadersStr,
                        requestBodyStr,
                        responseHeadersStr,
                        responseBodyStr,
                        tookMs
                );
                HttpLogBusinessJudger.JudgementResult result = judger.judge(context);
                if (result != null) {
                    isSuccess = result.isSuccess();
                    if (result.getMessage() != null && !result.getMessage().isEmpty()) {
                        businessMessage = result.getMessage();
                    }
                }
            } catch (Throwable t) {
                // 业务裁决异常保护
            }
        }

        HttpLogRecord record = new HttpLogRecord.Builder()
                .url(urlString)
                .host(host)
                .path(path)
                .method(method)
                .statusCode(statusCode)
                .httpCode(statusCode)
                .success(isSuccess)
                .message(businessMessage)
                .requestHeaders(requestHeadersStr)
                .requestBody(requestBodyStr)
                .responseHeaders(responseHeadersStr)
                .responseBody(responseBodyStr)
                .contentType(contentTypeStr)
                .requestSize(requestSize)
                .responseSize(responseSize)
                .durationMs(tookMs)
                .timestamp(requestTime)
                .isFailed(!isSuccess)
                .errorMessage(!isSuccess && statusCode >= 400 ? ("HTTP " + statusCode) : null)
                .build();

        HttpLogRepository.getInstance().enqueueLog(record);
        return response;
    }

    /**
     * 格式化 Headers 并对配置中的敏感字段进行脱敏保护。
     */
    @NonNull
    private String formatHeaders(@NonNull Headers headers) {
        if (headers.size() == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < headers.size(); i++) {
            String name = headers.name(i);
            String value = headers.value(i);
            if (isRedactHeader(name)) {
                value = "****** [REDACTED]";
            }
            builder.append(name).append(": ").append(value).append("\n");
        }
        return builder.toString();
    }

    private boolean isRedactHeader(@NonNull String headerName) {
        if (mRedactHeaders == null || mRedactHeaders.isEmpty()) {
            return false;
        }
        for (String redact : mRedactHeaders) {
            if (redact.equalsIgnoreCase(headerName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 安全读取 Buffer 内容并受限截断。
     */
    @NonNull
    private String readBufferSafe(@NonNull Buffer buffer, @NonNull Charset charset, int maxBytes) {
        long totalSize = buffer.size();
        try {
            if (totalSize <= maxBytes) {
                return buffer.readString(charset);
            }
            // 超出部分截断并提示
            String truncated = buffer.readString(maxBytes, charset);
            return truncated + "\n\n... [Content truncated: Total " + totalSize + " bytes, preserved first " + maxBytes + " bytes] ...";
        } catch (Throwable t) {
            return "[Error reading buffer: " + t.getMessage() + "]";
        }
    }

    /**
     * 嗅探数据流前 64 字节是否属于纯文本字符，防止将二进制流误判为文本强转乱码。
     */
    private static boolean isPlaintext(@NonNull Buffer buffer) {
        try {
            Buffer prefix = new Buffer();
            long byteCount = buffer.size() < 64 ? buffer.size() : 64;
            buffer.copyTo(prefix, 0, byteCount);
            for (int i = 0; i < 16; i++) {
                if (prefix.exhausted()) {
                    break;
                }
                int codePoint = prefix.readUtf8CodePoint();
                if (Character.isISOControl(codePoint) && !Character.isWhitespace(codePoint)) {
                    return false;
                }
            }
            return true;
        } catch (EOFException e) {
            return false;
        }
    }

    @NonNull
    private static String getStackTraceString(@NonNull Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }
}
