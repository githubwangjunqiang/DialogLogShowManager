package com.xq.dialoglogshow.collector.parser;

import android.text.TextUtils;

import com.xq.dialoglogshow.collector.model.AutoHttpLogModel;
import com.xq.dialoglogshow.entity.BaseShowData;
import com.xq.dialoglogshow.entity.HttpLogData;
import com.xq.dialoglogshow.utils.JsonFormat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * 原始 HTTP 报文流解析器与 cURL 命令行合成器
 *
 * 为什么这样做：
 * 1. 代理 Socket 层抓到的是原始字节流，需要通过状态机将请求行、Header、Body、响应状态码解析出来。
 * 2. 增加 GZIP / Deflate 自动解压缩、Chunked 分块传输拆包，以及针对特定 Content-Type 字符集（GBK/UTF-8）解码，彻底解决自动 HTTP 日志乱码问题。
 * 3. 支持自动检测 HTTP/2 Magic 二进制头，避免按 HTTP/1.1 解析乱码引发异常。
 * 4. 增加敏感数据（Token、Authorization、Cookie）脱敏。
 * 5. 提供合成标准 cURL 调试命令的能力，极大提升移动端开发者调试效率。
 *
 * Author: 王俊强 980766134@qq.com
 * Date: 2026/07/23
 */
public class RawHttpStreamParser {

    private static final int MAX_BODY_SHOW_LENGTH = 50 * 1024; // 字符串显示截断 50KB

    /**
     * 将抓取到的请求字节流与响应字节流解析组装为 AutoHttpLogModel 独立数据模型
     *
     * @param requestBytes  发出的请求字节数组
     * @param responseBytes 接收到的响应字节数组
     * @param costTimeMs    请求耗时 (毫秒)
     * @param isHttps       是否为 HTTPS
     * @return 结构化的 AutoHttpLogModel 独立数据模型
     */
    public static AutoHttpLogModel parse(byte[] requestBytes, byte[] responseBytes, long costTimeMs, boolean isHttps) {
        boolean hasReq = requestBytes != null && requestBytes.length > 0;
        boolean hasRes = responseBytes != null && responseBytes.length > 0;
        if (!hasReq && !hasRes) {
            return null;
        }

        String requestStr = hasReq ? new String(requestBytes, StandardCharsets.UTF_8) : "";

        // 默认匹配规则
        String method = "GET";
        String url = "Unknown URL";
        String host = "";
        Map<String, String> requestHeaders = new HashMap<>();
        String requestBody = "";

        // HTTP/1.1 与 HTTP/2 解析
        boolean isHttp2 = requestStr.startsWith("PRI * HTTP/2.0");
        if (isHttp2) {
            url = (isHttps ? "https://" : "http://") + "HTTP/2 Encapsulated Request";
            method = "HTTP/2";
        } else {
            String[] reqLines = requestStr.split("\r\n");
            if (reqLines.length > 0) {
                String firstLine = reqLines[0];
                String[] firstLineParts = firstLine.split(" ");
                if (firstLineParts.length >= 2) {
                    method = firstLineParts[0].toUpperCase();
                    url = firstLineParts[1];
                }
            }

            int headerEndIndex = requestStr.indexOf("\r\n\r\n");
            if (headerEndIndex != -1) {
                String headerPart = requestStr.substring(0, headerEndIndex);
                String[] headerLines = headerPart.split("\r\n");
                for (int i = 1; i < headerLines.length; i++) {
                    int colonIndex = headerLines[i].indexOf(":");
                    if (colonIndex != -1) {
                        String key = headerLines[i].substring(0, colonIndex).trim();
                        String value = headerLines[i].substring(colonIndex + 1).trim();
                        if ("Host".equalsIgnoreCase(key)) {
                            host = value;
                        }
                        requestHeaders.put(key, sanitizeHeader(key, value));
                    }
                }
                requestBody = requestStr.substring(headerEndIndex + 4);
            }

            boolean isValidMethod = "GET".equals(method) || "POST".equals(method) || "PUT".equals(method)
                    || "DELETE".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method) || "PATCH".equals(method);
            if (!isValidMethod) {
                method = "GET";
            }

            if (!isValidUrl(url)) {
                if (!TextUtils.isEmpty(host)) {
                    url = (isHttps ? "https://" : "http://") + host + "/";
                } else {
                    url = (isHttps ? "https://" : "http://") + "Network Request";
                }
            } else if (!url.startsWith("http://") && !url.startsWith("https://")) {
                if (!TextUtils.isEmpty(host)) {
                    url = (isHttps ? "https://" : "http://") + host + (url.startsWith("/") ? url : "/" + url);
                }
            }
        }

        // Response 解析
        String statusCode = "200 OK";
        String responseBody = "";
        Map<String, String> responseHeaders = new HashMap<>();

        if (hasRes) {
            int resHeaderEndIndex = findHeaderEndIndex(responseBytes);
            if (resHeaderEndIndex != -1) {
                String resHeaderPart = new String(responseBytes, 0, resHeaderEndIndex, StandardCharsets.UTF_8);
                String[] resHeaderLines = resHeaderPart.split("\r\n");
                if (resHeaderLines.length > 0) {
                    statusCode = resHeaderLines[0];
                }
                for (int i = 1; i < resHeaderLines.length; i++) {
                    int colonIndex = resHeaderLines[i].indexOf(":");
                    if (colonIndex != -1) {
                        String key = resHeaderLines[i].substring(0, colonIndex).trim();
                        String value = resHeaderLines[i].substring(colonIndex + 1).trim();
                        responseHeaders.put(key.toLowerCase(), value);
                    }
                }

                int bodyStart = resHeaderEndIndex + 4;
                int bodyLength = responseBytes.length - bodyStart;
                if (bodyLength > 0) {
                    byte[] rawBodyBytes = new byte[bodyLength];
                    System.arraycopy(responseBytes, bodyStart, rawBodyBytes, 0, bodyLength);

                    if ("chunked".equalsIgnoreCase(responseHeaders.get("transfer-encoding"))) {
                        rawBodyBytes = parseChunkedBody(rawBodyBytes);
                    }

                    String contentEncoding = responseHeaders.get("content-encoding");
                    byte[] decompressedBytes = decompressIfNeeded(rawBodyBytes, contentEncoding);

                    Charset charset = parseCharsetFromContentType(responseHeaders.get("content-type"));
                    responseBody = new String(decompressedBytes, charset);
                }
            } else {
                responseBody = new String(responseBytes, StandardCharsets.UTF_8);
            }
        }

        if (responseBody.length() > MAX_BODY_SHOW_LENGTH) {
            responseBody = responseBody.substring(0, MAX_BODY_SHOW_LENGTH) + "\n...[Content Truncated]";
        }
        responseBody = JsonFormat.format(responseBody);

        // 组装全新的 AutoHttpLogModel 独立模型
        AutoHttpLogModel model = new AutoHttpLogModel();
        model.setUrl(url);
        model.setMethod(method);
        model.setStatusCode(statusCode);
        model.setHttps(isHttps);
        model.setCostTimeMs(costTimeMs);
        model.setReqSize(requestBytes != null ? requestBytes.length : 0);
        model.setResSize(responseBytes != null ? responseBytes.length : 0);
        model.setRequestHeaders(requestHeaders);
        model.setRequestBody(requestBody);
        model.setResponseHeaders(responseHeaders);
        model.setResponseBody(responseBody);
        model.setCurlCommand(generateCurlCommand(method, url, requestHeaders, requestBody));

        return model;
    }

    /**
     * 在字节数组中查找 \r\n\r\n (0x0D 0x0A 0x0D 0x0A) 的起始索引
     */
    private static int findHeaderEndIndex(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return -1;
        }
        for (int i = 0; i <= bytes.length - 4; i++) {
            if (bytes[i] == '\r' && bytes[i + 1] == '\n' && bytes[i + 2] == '\r' && bytes[i + 3] == '\n') {
                return i;
            }
        }
        return -1;
    }

    /**
     * 处理 GZIP 或 Deflate 解压
     */
    private static byte[] decompressIfNeeded(byte[] bodyBytes, String contentEncoding) {
        if (bodyBytes == null || bodyBytes.length == 0) {
            return new byte[0];
        }

        boolean isGzip = (contentEncoding != null && contentEncoding.toLowerCase().contains("gzip"))
                || (bodyBytes.length >= 2 && bodyBytes[0] == (byte) 0x1f && bodyBytes[1] == (byte) 0x8b);
        boolean isDeflate = contentEncoding != null && contentEncoding.toLowerCase().contains("deflate");

        if (!isGzip && !isDeflate) {
            return bodyBytes;
        }

        try (InputStream in = isGzip ? new GZIPInputStream(new ByteArrayInputStream(bodyBytes))
                : new InflaterInputStream(new ByteArrayInputStream(bodyBytes));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            return out.toByteArray();
        } catch (Throwable t) {
            // 解压失败时降级返回原始字节
            return bodyBytes;
        }
    }

    /**
     * Chunked 分块传输拆包
     */
    private static byte[] parseChunkedBody(byte[] bodyBytes) {
        if (bodyBytes == null || bodyBytes.length == 0) {
            return new byte[0];
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            int pos = 0;
            while (pos < bodyBytes.length) {
                // 查找 CRLF
                int crlf = -1;
                for (int i = pos; i < bodyBytes.length - 1; i++) {
                    if (bodyBytes[i] == '\r' && bodyBytes[i + 1] == '\n') {
                        crlf = i;
                        break;
                    }
                }
                if (crlf == -1) break;

                String chunkSizeStr = new String(bodyBytes, pos, crlf - pos, StandardCharsets.UTF_8).trim();
                // 过滤可能存在的 chunk extension
                int extensionIndex = chunkSizeStr.indexOf(';');
                if (extensionIndex != -1) {
                    chunkSizeStr = chunkSizeStr.substring(0, extensionIndex).trim();
                }

                int chunkSize;
                try {
                    chunkSize = Integer.parseInt(chunkSizeStr, 16);
                } catch (NumberFormatException e) {
                    break;
                }
                if (chunkSize == 0) break;

                int dataStart = crlf + 2;
                if (dataStart + chunkSize <= bodyBytes.length) {
                    out.write(bodyBytes, dataStart, chunkSize);
                } else {
                    out.write(bodyBytes, dataStart, bodyBytes.length - dataStart);
                }
                pos = dataStart + chunkSize + 2; // 跳过数据和尾随的 CRLF
            }
            return out.toByteArray();
        } catch (Throwable t) {
            return bodyBytes;
        }
    }

    /**
     * 从 Content-Type 头解析 Charset，缺省返回 UTF-8
     */
    private static Charset parseCharsetFromContentType(String contentType) {
        if (contentType != null) {
            String lower = contentType.toLowerCase();
            int charsetIndex = lower.indexOf("charset=");
            if (charsetIndex != -1) {
                String charsetName = contentType.substring(charsetIndex + 8).trim();
                int semicolon = charsetName.indexOf(';');
                if (semicolon != -1) {
                    charsetName = charsetName.substring(0, semicolon).trim();
                }
                charsetName = charsetName.replace("\"", "").replace("'", "");
                try {
                    return Charset.forName(charsetName);
                } catch (Throwable ignored) {
                }
            }
        }
        return StandardCharsets.UTF_8;
    }

    /**
     * 生成标准 cURL 调试命令行
     */
    public static String generateCurlCommand(String method, String url, Map<String, String> headers, String body) {
        StringBuilder curl = new StringBuilder("curl -X ").append(method).append(" \"").append(url).append("\"");
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                curl.append(" -H \"").append(entry.getKey()).append(": ").append(entry.getValue()).append("\"");
            }
        }
        if (!TextUtils.isEmpty(body)) {
            curl.append(" -d '").append(body.replace("'", "\\'")).append("'");
        }
        return curl.toString();
    }

    /**
     * 敏感 Header 脱敏处理
     */
    private static String sanitizeHeader(String key, String value) {
        String lowerKey = key.toLowerCase();
        if (lowerKey.contains("authorization") || lowerKey.contains("cookie") || lowerKey.contains("token")) {
            if (value.length() > 10) {
                return value.substring(0, 6) + "*****" + value.substring(value.length() - 4);
            }
            return "*****";
        }
        return value;
    }

    /**
     * 校验 URL 字符串合法性，防止非 ASCII / 二进制杂质乱码
     */
    private static boolean isValidUrl(String url) {
        if (TextUtils.isEmpty(url)) return false;
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c <= 32 || c >= 127) {
                return false;
            }
        }
        return true;
    }
}

