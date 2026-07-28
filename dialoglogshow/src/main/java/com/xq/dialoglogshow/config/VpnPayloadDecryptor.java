package com.xq.dialoglogshow.config;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;

/**
 * VPN 业务报文解密器
 *
 * 为什么这样做：
 * 1. HTTPS 只能帮 SDK 拿到传输层明文，但如果宿主业务自己又在 body 里做了 AES/自定义加密，SDK 仍然只能看到业务密文。
 * 2. 解密规则完全属于业务域知识，必须交给宿主可选注入；SDK 只负责在展示时调用，不把“必须实现解密器”强加给所有接入方。
 */
public interface VpnPayloadDecryptor {

    /**
     * 解密请求体
     *
     * @param request 解密输入上下文
     * @return 解密后的请求体；返回 null 表示保持原始请求体展示
     */
    @Nullable
    String decryptRequestBody(@NonNull DecryptRequest request);

    /**
     * 解密响应体
     *
     * @param request 解密输入上下文
     * @return 解密后的响应体；返回 null 表示保持原始响应体展示
     */
    @Nullable
    String decryptResponseBody(@NonNull DecryptRequest request);

    /**
     * 单次解密所需的上下文
     * 为什么这样做：很多业务加密是否命中、用哪套 key、是否需要先 Base64 再 AES，往往都依赖 URL/Method/Header 等上下文，不能只把 body 裸传给宿主。
     */
    final class DecryptRequest {
        public final String protocol;
        public final String host;
        public final String url;
        public final String method;
        public final String statusCode;
        public final boolean encryptedByVpn;
        public final String parseStatus;
        public final Map<String, String> requestHeaders;
        public final Map<String, String> responseHeaders;
        public final String body;

        public DecryptRequest(@Nullable String protocol,
                              @Nullable String host,
                              @Nullable String url,
                              @Nullable String method,
                              @Nullable String statusCode,
                              boolean encryptedByVpn,
                              @Nullable String parseStatus,
                              @Nullable Map<String, String> requestHeaders,
                              @Nullable Map<String, String> responseHeaders,
                              @Nullable String body) {
            this.protocol = protocol;
            this.host = host;
            this.url = url;
            this.method = method;
            this.statusCode = statusCode;
            this.encryptedByVpn = encryptedByVpn;
            this.parseStatus = parseStatus;
            this.requestHeaders = requestHeaders;
            this.responseHeaders = responseHeaders;
            this.body = body;
        }
    }
}
