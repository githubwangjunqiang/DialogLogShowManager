package com.xq.dialoglogshow.collector.vpn.netbare;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.megatronking.netbare.http.HttpIndexedInterceptor;
import com.github.megatronking.netbare.http.HttpInterceptorFactory;
import com.github.megatronking.netbare.http.HttpRequest;
import com.github.megatronking.netbare.http.HttpRequestChain;
import com.github.megatronking.netbare.http.HttpResponse;
import com.github.megatronking.netbare.http.HttpResponseChain;
import com.xq.dialoglogshow.collector.vpn.repository.VpnTrafficLogRepository;
import com.xq.dialoglogshow.manager.ShowLogManager;
import com.xq.dialoglogshow.model.VpnTrafficLogModel;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * NetBare HTTP 拦截桥接器
 *
 * 为什么这样做：
 * 1. SDK 现有 UI 与持久化已经围绕 `VpnTrafficLogModel` 组织，因此最稳妥的接入方式不是重写一套展示层，而是把 NetBare 拦截结果直接桥接回现有 Repository。
 * 2. 这里先优先保证“请求/响应元数据 + 有限正文”能稳定落库，避免在 VPN 主链路里做无限制内存缓存导致调试工具反向拖垮宿主 App。
 * 3. 真实线上流量大量使用 chunked/gzip，如果不在这里统一还原，控制台看到的响应体就会是乱码，无法满足“开发者只开开关就能看完整明文”的目标。
 */
public class ShowLogNetBareHttpInterceptor extends HttpIndexedInterceptor {

    private static final int MAX_CAPTURE_BODY_BYTES = 50 * 1024;
    /**
     * 为什么这样做：二进制内容即使不能还原成正文，也需要给调试者一个稳定的可读片段，避免直接把压缩流/图片字节当成乱码灌进详情页。
     */
    private static final int HEX_PREVIEW_BYTE_COUNT = 96;
    /**
     * 为什么这样做：NetBare 的 ContainerHttpInterceptor 会在 request finished 后移除一次 session，
     * 响应阶段重新进入时不保证还是同一个扩展拦截器实例，因此请求/响应聚合状态不能放在实例字段里。
     */
    private static final ConcurrentHashMap<String, PendingLog> GLOBAL_PENDING_LOGS = new ConcurrentHashMap<>();

    @NonNull
    public static HttpInterceptorFactory createFactory() {
        return ShowLogNetBareHttpInterceptor::new;
    }

    @Override
    public void intercept(@NonNull HttpRequestChain chain, @NonNull ByteBuffer buffer, int index) throws IOException {
        HttpRequest request = chain.request();
        PendingLog pendingLog = obtainPendingLog(request);
        pendingLog.requestBytes += buffer.remaining();
        pendingLog.method = request.method() != null ? request.method().name() : null;
        pendingLog.protocol = request.isHttps() ? "HTTPS" : "HTTP";
        pendingLog.host = request.host();
        pendingLog.url = request.url();
        pendingLog.encrypted = request.isHttps();
        pendingLog.startTime = pendingLog.startTime == 0L ? request.time() : pendingLog.startTime;
        pendingLog.timestamp = pendingLog.startTime;
        pendingLog.requestHeaders = flattenHeaders(request.requestHeaders());
        appendBodyIfNeed(pendingLog.requestBodyBytes, buffer, index == 0 ? request.requestBodyOffset() : 0);
        chain.process(buffer);
    }

    @Override
    public void intercept(@NonNull HttpResponseChain chain, @NonNull ByteBuffer buffer, int index) throws IOException {
        HttpResponse response = chain.response();
        PendingLog pendingLog = obtainPendingLog(response);
        pendingLog.responseBytes += buffer.remaining();
        pendingLog.statusCode = String.valueOf(response.code());
        pendingLog.protocol = response.isHttps() ? "HTTPS" : "HTTP";
        pendingLog.host = response.host();
        pendingLog.url = response.url();
        pendingLog.method = response.method() != null ? response.method().name() : pendingLog.method;
        pendingLog.encrypted = response.isHttps();
        pendingLog.responseHeaders = flattenHeaders(response.responseHeaders());
        pendingLog.endTime = Math.max(pendingLog.endTime, response.time());
        appendBodyIfNeed(pendingLog.responseBodyBytes, buffer, index == 0 ? response.responseBodyOffset() : 0);
        // 为什么这样做：无论 HTTP 还是 HTTPS，只要这里已经拿到真实响应头/体，就说明当前会话已经进入可展示的明文阶段。
        pendingLog.parseStatus = resolveParseStatus(pendingLog);
        pendingLog.errorMessage = resolveErrorMessage(pendingLog);
        // 为什么这样做：HTTP keep-alive / 长连接下，response finished 回调不一定会在调试窗口期内及时到来，
        // 收到响应分片后就持续 upsert，才能保证控制台和历史记录尽快可见。
        VpnTrafficLogRepository.getInstance().appendLog(pendingLog.toModel());
        chain.process(buffer);
    }

    @Override
    public void onRequestFinished(@NonNull HttpRequest request) {
        PendingLog pendingLog = obtainPendingLog(request);
        pendingLog.endTime = Math.max(pendingLog.endTime, request.time());
        pendingLog.parseStatus = resolveParseStatus(pendingLog);
        pendingLog.errorMessage = resolveErrorMessage(pendingLog);
        // 为什么这样做：有些失败请求可能根本等不到响应阶段，这里先把请求侧快照落库，避免完全丢失。
        VpnTrafficLogRepository.getInstance().appendLog(pendingLog.toModel());
    }

    @Override
    public void onResponseFinished(@NonNull HttpResponse response) {
        String requestId = response.id();
        PendingLog pendingLog = GLOBAL_PENDING_LOGS.remove(requestId);
        if (pendingLog == null) {
            return;
        }
        pendingLog.endTime = Math.max(pendingLog.endTime, response.time());
        pendingLog.parseStatus = resolveParseStatus(pendingLog);
        pendingLog.errorMessage = resolveErrorMessage(pendingLog);
        VpnTrafficLogRepository.getInstance().appendLog(pendingLog.toModel());
    }

    @NonNull
    private String resolveParseStatus(@NonNull PendingLog pendingLog) {
        if (!pendingLog.encrypted) {
            return "SUCCESS";
        }
        // 为什么这样做：HTTPS 是否成功明文抓取，不能只看“是否加密”，而要看是否真正拿到了可解析的请求/响应头体。
        boolean hasRequestDetail = pendingLog.requestHeaders != null
                && !pendingLog.requestHeaders.isEmpty()
                && pendingLog.method != null
                && !"CONNECT".equalsIgnoreCase(pendingLog.method);
        boolean hasRequestBody = pendingLog.requestBodyBytes.size() > 0;
        boolean hasResponseDetail = pendingLog.responseHeaders != null && !pendingLog.responseHeaders.isEmpty();
        boolean hasResponseBody = pendingLog.responseBodyBytes.size() > 0;
        boolean hasStatusCode = pendingLog.statusCode != null && pendingLog.statusCode.length() > 0;
        return (hasRequestDetail || hasRequestBody || hasResponseDetail || hasResponseBody || hasStatusCode) ? "SUCCESS" : "PARTIAL";
    }

    private String resolveErrorMessage(@NonNull PendingLog pendingLog) {
        if ("SUCCESS".equals(resolveParseStatus(pendingLog))) {
            return null;
        }
        if (!pendingLog.encrypted) {
            return pendingLog.errorMessage;
        }
        return "HTTPS 明文抓取失败，请确认已安装并信任调试证书，且宿主应用允许信任用户证书";
    }

    @NonNull
    private PendingLog obtainPendingLog(@NonNull HttpRequest request) {
        String requestId = request.id();
        PendingLog pendingLog = GLOBAL_PENDING_LOGS.get(requestId);
        if (pendingLog != null) {
            return pendingLog;
        }
        PendingLog newLog = new PendingLog();
        newLog.logId = UUID.randomUUID().toString();
        newLog.requestId = requestId;
        newLog.appPackageName = ShowLogManager.getInstance().getApplication().getPackageName();
        newLog.sourceDescription = "VPN";
        PendingLog oldLog = GLOBAL_PENDING_LOGS.putIfAbsent(requestId, newLog);
        return oldLog != null ? oldLog : newLog;
    }

    @NonNull
    private PendingLog obtainPendingLog(@NonNull HttpResponse response) {
        String requestId = response.id();
        PendingLog pendingLog = GLOBAL_PENDING_LOGS.get(requestId);
        if (pendingLog != null) {
            return pendingLog;
        }
        PendingLog newLog = new PendingLog();
        newLog.logId = UUID.randomUUID().toString();
        newLog.requestId = requestId;
        newLog.appPackageName = ShowLogManager.getInstance().getApplication().getPackageName();
        newLog.sourceDescription = "VPN";
        PendingLog oldLog = GLOBAL_PENDING_LOGS.putIfAbsent(requestId, newLog);
        return oldLog != null ? oldLog : newLog;
    }

    @NonNull
    private Map<String, String> flattenHeaders(Map<String, List<String>> headers) {
        Map<String, String> result = new LinkedHashMap<>();
        if (headers == null) {
            return result;
        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            StringBuilder valueBuilder = new StringBuilder();
            for (int i = 0; i < entry.getValue().size(); i++) {
                if (i > 0) {
                    valueBuilder.append("; ");
                }
                valueBuilder.append(entry.getValue().get(i));
            }
            result.put(entry.getKey(), valueBuilder.toString());
        }
        return result;
    }

    private void appendBodyIfNeed(@NonNull ByteArrayOutputStream bodyOutputStream,
                                  @NonNull ByteBuffer buffer,
                                  int bodyOffset) {
        if (bodyOutputStream.size() >= MAX_CAPTURE_BODY_BYTES) {
            return;
        }
        ByteBuffer duplicate = buffer.duplicate();
        int start = Math.max(0, bodyOffset);
        if (start >= duplicate.remaining()) {
            return;
        }
        duplicate.position(duplicate.position() + start);
        int maxReadable = Math.min(duplicate.remaining(), MAX_CAPTURE_BODY_BYTES - bodyOutputStream.size());
        byte[] bytes = new byte[maxReadable];
        duplicate.get(bytes);
        bodyOutputStream.write(bytes, 0, bytes.length);
    }

    /**
     * 为什么这样做：抓包链路面对的是原始传输字节，只有在最终落库前统一做 chunk/gzip/charset 还原，UI 和历史记录才能稳定看到可读明文。
     */
    private static String decodeBody(@NonNull ByteArrayOutputStream rawBodyOutputStream,
                                     Map<String, String> headers) {
        if (rawBodyOutputStream.size() <= 0) {
            return null;
        }
        byte[] normalizedBytes = rawBodyOutputStream.toByteArray();
        try {
            normalizedBytes = stripHttpChunkedBodyIfNeed(normalizedBytes, headers);
            String contentEncoding = resolveContentEncoding(headers);
            if (isUnsupportedContentEncoding(contentEncoding)) {
                // 为什么这样做：`br`/`zstd` 等未知压缩如果直接按文本解码，只会在 UI 中显示成大段乱码；这里宁可明确降级，也不伪装成“正文明文”。
                return buildCompressedBodyPreview(contentEncoding, normalizedBytes);
            }
            normalizedBytes = decodeCompressedBodyIfNeed(normalizedBytes, contentEncoding);
            if (!shouldDecodeAsText(headers, normalizedBytes)) {
                // 为什么这样做：图片、音视频、protobuf、apk 等二进制负载不应该强行转字符串；改成十六进制预览后，开发者至少还能判断内容类型与字节特征。
                return buildBinaryBodyPreview(headers, normalizedBytes);
            }
            return decodeTextBody(headers, normalizedBytes);
        } catch (Throwable decodeError) {
            // 为什么这样做：抓包工具的第一目标是“不要丢日志”，即使文本解码失败，也要保留稳定可读的字节预览，避免只剩乱码或空白。
            return buildBinaryBodyPreview(headers, normalizedBytes);
        }
    }

    private static byte[] stripHttpChunkedBodyIfNeed(byte[] rawBytes, Map<String, String> headers) throws IOException {
        if (!containsIgnoreCase(headers, "Transfer-Encoding", "chunked")) {
            return rawBytes;
        }
        ByteArrayOutputStream chunkBodyOutputStream = new ByteArrayOutputStream();
        int cursor = 0;
        while (cursor < rawBytes.length) {
            int lineEnd = findCrlf(rawBytes, cursor);
            if (lineEnd < 0) {
                break;
            }
            String chunkSizeLine = new String(rawBytes, cursor, lineEnd - cursor, StandardCharsets.US_ASCII).trim();
            if (chunkSizeLine.length() == 0) {
                cursor = lineEnd + 2;
                continue;
            }
            int extensionIndex = chunkSizeLine.indexOf(';');
            if (extensionIndex >= 0) {
                chunkSizeLine = chunkSizeLine.substring(0, extensionIndex).trim();
            }
            int chunkSize = Integer.parseInt(chunkSizeLine, 16);
            cursor = lineEnd + 2;
            if (chunkSize <= 0) {
                break;
            }
            int remaining = rawBytes.length - cursor;
            int readableCount = Math.min(chunkSize, remaining);
            chunkBodyOutputStream.write(rawBytes, cursor, readableCount);
            cursor += readableCount;
            if (cursor + 1 < rawBytes.length && rawBytes[cursor] == '\r' && rawBytes[cursor + 1] == '\n') {
                cursor += 2;
            }
        }
        return chunkBodyOutputStream.size() > 0 ? chunkBodyOutputStream.toByteArray() : rawBytes;
    }

    private static byte[] decodeCompressedBodyIfNeed(byte[] rawBytes, String contentEncoding) throws IOException {
        if (contentEncoding == null || contentEncoding.trim().isEmpty()) {
            return rawBytes;
        }
        if (containsTokenIgnoreCase(contentEncoding, "gzip")) {
            return readAllBytes(new GZIPInputStream(new ByteArrayInputStream(rawBytes)));
        }
        if (containsTokenIgnoreCase(contentEncoding, "deflate")) {
            return readAllBytes(new InflaterInputStream(new ByteArrayInputStream(rawBytes)));
        }
        return rawBytes;
    }

    @NonNull
    private static String decodeTextBody(Map<String, String> headers, byte[] normalizedBytes) {
        Charset bomCharset = resolveCharsetByBom(normalizedBytes);
        if (bomCharset != null) {
            return new String(stripBomIfNeed(normalizedBytes, bomCharset), bomCharset);
        }
        Charset headerCharset = resolveCharsetFromHeader(headers);
        Charset bestCharset = chooseBestCharset(headers, normalizedBytes, headerCharset);
        String decodedText = new String(normalizedBytes, bestCharset);
        if (headerCharset != null
                && StandardCharsets.ISO_8859_1.equals(headerCharset)
                && isValidUtf8(normalizedBytes)) {
            // 为什么这样做：部分服务端会错误声明 ISO-8859-1，但正文其实是 UTF-8；只要原始字节本身满足 UTF-8，就优先按 UTF-8 展示，避免中文出现 Ã¤Â½ 这类典型乱码。
            String utf8Text = new String(normalizedBytes, StandardCharsets.UTF_8);
            if (calculateTextQualityScore(utf8Text) > calculateTextQualityScore(decodedText)) {
                return utf8Text;
            }
        }
        return decodedText;
    }

    @NonNull
    private static Charset chooseBestCharset(Map<String, String> headers,
                                             byte[] normalizedBytes,
                                             Charset headerCharset) {
        if (headerCharset != null && canDecodeWithoutError(normalizedBytes, headerCharset)) {
            return headerCharset;
        }
        if (isValidUtf8(normalizedBytes)) {
            return StandardCharsets.UTF_8;
        }
        Charset fallbackCharset = isJsonLike(headers)
                ? StandardCharsets.UTF_8
                : Charset.forName("GB18030");
        if (canDecodeWithoutError(normalizedBytes, fallbackCharset)) {
            return fallbackCharset;
        }
        return StandardCharsets.UTF_8;
    }

    @Nullable
    private static Charset resolveCharsetByBom(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            return StandardCharsets.UTF_8;
        }
        if (bytes.length >= 2
                && (bytes[0] & 0xFF) == 0xFE
                && (bytes[1] & 0xFF) == 0xFF) {
            return StandardCharsets.UTF_16BE;
        }
        if (bytes.length >= 2
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xFE) {
            return StandardCharsets.UTF_16LE;
        }
        return null;
    }

    @NonNull
    private static byte[] stripBomIfNeed(byte[] bytes, @NonNull Charset bomCharset) {
        if (StandardCharsets.UTF_8.equals(bomCharset) && bytes.length >= 3) {
            return copyOfRange(bytes, 3, bytes.length);
        }
        if ((StandardCharsets.UTF_16BE.equals(bomCharset) || StandardCharsets.UTF_16LE.equals(bomCharset))
                && bytes.length >= 2) {
            return copyOfRange(bytes, 2, bytes.length);
        }
        return bytes;
    }

    @Nullable
    private static Charset resolveCharsetFromHeader(Map<String, String> headers) {
        String contentType = findHeaderIgnoreCase(headers, "Content-Type");
        if (contentType != null) {
            String[] segments = contentType.split(";");
            for (String segment : segments) {
                String trimmedSegment = segment.trim();
                if (trimmedSegment.toLowerCase().startsWith("charset=")) {
                    String charsetName = trimmedSegment.substring("charset=".length()).trim();
                    try {
                        return Charset.forName(charsetName);
                    } catch (Throwable ignored) {
                        // 为什么这样做：服务端 charset 声明经常不规范，失败时回退 UTF-8 比中断抓包更符合调试工具诉求。
                        return StandardCharsets.UTF_8;
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    private static String resolveContentEncoding(Map<String, String> headers) {
        return findHeaderIgnoreCase(headers, "Content-Encoding");
    }

    private static boolean isUnsupportedContentEncoding(@Nullable String contentEncoding) {
        if (contentEncoding == null || contentEncoding.trim().isEmpty()) {
            return false;
        }
        return !(containsTokenIgnoreCase(contentEncoding, "gzip")
                || containsTokenIgnoreCase(contentEncoding, "deflate")
                || "identity".equalsIgnoreCase(contentEncoding.trim()));
    }

    private static boolean shouldDecodeAsText(Map<String, String> headers, byte[] bytes) {
        if (bytes.length == 0) {
            return false;
        }
        String contentType = findHeaderIgnoreCase(headers, "Content-Type");
        if (contentType != null) {
            String lowerContentType = contentType.toLowerCase();
            if (lowerContentType.startsWith("text/")
                    || lowerContentType.contains("json")
                    || lowerContentType.contains("xml")
                    || lowerContentType.contains("html")
                    || lowerContentType.contains("x-www-form-urlencoded")
                    || lowerContentType.contains("javascript")
                    || lowerContentType.contains("graphql")
                    || lowerContentType.contains("protobuf+json")) {
                return true;
            }
            if (lowerContentType.contains("image/")
                    || lowerContentType.contains("audio/")
                    || lowerContentType.contains("video/")
                    || lowerContentType.contains("octet-stream")
                    || lowerContentType.contains("application/zip")
                    || lowerContentType.contains("application/pdf")
                    || lowerContentType.contains("application/vnd")
                    || lowerContentType.contains("protobuf")) {
                return false;
            }
        }
        return isLikelyUtfText(bytes);
    }

    private static boolean isLikelyUtfText(byte[] bytes) {
        if (bytes.length == 0) {
            return false;
        }
        int zeroByteCount = 0;
        int suspiciousControlCount = 0;
        int printableCount = 0;
        int inspectCount = Math.min(bytes.length, 512);
        for (int i = 0; i < inspectCount; i++) {
            int value = bytes[i] & 0xFF;
            if (value == 0) {
                zeroByteCount++;
                continue;
            }
            if (value == '\n' || value == '\r' || value == '\t') {
                printableCount++;
                continue;
            }
            if (value >= 0x20) {
                printableCount++;
            } else {
                suspiciousControlCount++;
            }
        }
        if (zeroByteCount > 0) {
            return false;
        }
        if (suspiciousControlCount > inspectCount / 10) {
            return false;
        }
        return printableCount >= inspectCount * 0.75f || isValidUtf8(bytes);
    }

    private static boolean isJsonLike(Map<String, String> headers) {
        String contentType = findHeaderIgnoreCase(headers, "Content-Type");
        return contentType != null && contentType.toLowerCase().contains("json");
    }

    private static boolean isValidUtf8(byte[] bytes) {
        return canDecodeWithoutError(bytes, StandardCharsets.UTF_8);
    }

    private static boolean canDecodeWithoutError(byte[] bytes, @NonNull Charset charset) {
        try {
            CharsetDecoder decoder = charset.newDecoder();
            decoder.onMalformedInput(CodingErrorAction.REPORT);
            decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
            decoder.decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int calculateTextQualityScore(@NonNull String text) {
        int score = 0;
        for (int i = 0; i < text.length(); i++) {
            char currentChar = text.charAt(i);
            if (currentChar == '\uFFFD') {
                score -= 6;
                continue;
            }
            if (Character.isISOControl(currentChar) && currentChar != '\n' && currentChar != '\r' && currentChar != '\t') {
                score -= 3;
                continue;
            }
            if (Character.isLetterOrDigit(currentChar)
                    || Character.isWhitespace(currentChar)
                    || isCommonPunctuation(currentChar)
                    || isChineseCharacter(currentChar)) {
                score += 2;
            }
        }
        return score;
    }

    private static boolean isChineseCharacter(char currentChar) {
        return currentChar >= 0x4E00 && currentChar <= 0x9FFF;
    }

    private static boolean isCommonPunctuation(char currentChar) {
        return ",.:;!?@#$%^&*()-_=+[]{}<>/\\'\"`~|，。！？：；（）【】《》、".indexOf(currentChar) >= 0;
    }

    @NonNull
    private static String buildCompressedBodyPreview(@Nullable String contentEncoding, byte[] bytes) {
        return "[encoded body omitted] content-encoding="
                + (contentEncoding == null ? "unknown" : contentEncoding)
                + ", bytes="
                + bytes.length
                + ", hex="
                + buildHexPreview(bytes);
    }

    @NonNull
    private static String buildBinaryBodyPreview(Map<String, String> headers, byte[] bytes) {
        String contentType = findHeaderIgnoreCase(headers, "Content-Type");
        return "[binary body omitted] content-type="
                + (contentType == null ? "unknown" : contentType)
                + ", bytes="
                + bytes.length
                + ", hex="
                + buildHexPreview(bytes);
    }

    @NonNull
    private static String buildHexPreview(byte[] bytes) {
        if (bytes.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int previewCount = Math.min(bytes.length, HEX_PREVIEW_BYTE_COUNT);
        for (int i = 0; i < previewCount; i++) {
            if (i > 0) {
                builder.append(' ');
            }
            String hex = Integer.toHexString(bytes[i] & 0xFF).toUpperCase();
            if (hex.length() < 2) {
                builder.append('0');
            }
            builder.append(hex);
        }
        if (bytes.length > previewCount) {
            builder.append(" ...");
        }
        return builder.toString();
    }

    @NonNull
    private static byte[] copyOfRange(byte[] source, int start, int end) {
        int safeStart = Math.max(0, start);
        int safeEnd = Math.max(safeStart, Math.min(source.length, end));
        byte[] copy = new byte[safeEnd - safeStart];
        System.arraycopy(source, safeStart, copy, 0, copy.length);
        return copy;
    }

    private static int findCrlf(byte[] bytes, int start) {
        for (int index = start; index + 1 < bytes.length; index++) {
            if (bytes[index] == '\r' && bytes[index + 1] == '\n') {
                return index;
            }
        }
        return -1;
    }

    private static byte[] readAllBytes(@NonNull java.io.InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int readCount;
        while ((readCount = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, readCount);
        }
        return outputStream.toByteArray();
    }

    private static boolean containsIgnoreCase(Map<String, String> headers, String key, String expectedValueToken) {
        String value = findHeaderIgnoreCase(headers, key);
        return value != null && containsTokenIgnoreCase(value, expectedValueToken);
    }

    private static String findHeaderIgnoreCase(Map<String, String> headers, String key) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static boolean containsTokenIgnoreCase(String source, String token) {
        return source != null && source.toLowerCase().contains(token.toLowerCase());
    }

    /**
     * 单条 HTTP 会话的聚合中间态
     * 为什么这样做：VPN 拦截拿到的是分片流量，只有把请求和响应阶段的零散信息暂存下来，才能在完成时一次性映射成 SDK 唯一日志模型。
     */
    private static class PendingLog {
        String requestId;
        String logId;
        String protocol;
        String host;
        String url;
        String method;
        String statusCode;
        long timestamp;
        long startTime;
        long endTime;
        int requestBytes;
        int responseBytes;
        boolean encrypted;
        String parseStatus;
        String errorMessage;
        String sourceDescription;
        String appPackageName;
        Map<String, String> requestHeaders;
        Map<String, String> responseHeaders;
        final ByteArrayOutputStream requestBodyBytes = new ByteArrayOutputStream();
        final ByteArrayOutputStream responseBodyBytes = new ByteArrayOutputStream();

        @NonNull
        VpnTrafficLogModel toModel() {
            VpnTrafficLogModel model = new VpnTrafficLogModel();
            model.logId = logId;
            model.protocol = protocol;
            model.host = host;
            model.url = url;
            model.method = method;
            model.statusCode = statusCode;
            model.timestamp = timestamp > 0 ? timestamp : startTime;
            model.startTime = startTime;
            model.endTime = endTime;
            model.costTimeMs = Math.max(0L, endTime - startTime);
            model.requestBytes = requestBytes;
            model.responseBytes = responseBytes;
            model.encrypted = encrypted;
            model.parseStatus = parseStatus != null ? parseStatus : "PARTIAL";
            model.requestHeaders = requestHeaders;
            model.requestBody = decodeBody(requestBodyBytes, requestHeaders);
            model.responseHeaders = responseHeaders;
            model.responseBody = decodeBody(responseBodyBytes, responseHeaders);
            model.errorMessage = errorMessage;
            model.sourceDescription = sourceDescription;
            model.appPackageName = appPackageName;
            return model;
        }
    }
}
