package com.xq.dialoglogshow.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * VPN 日志解析状态常量与归一化工具。
 *
 * 为什么这样做：解析状态会在拦截器、兜底记录器和详情页之间多处复用，单独抽成统一定义后，
 * 既能避免魔法字符串散落，又能让“成功 / 部分成功 / 最小元数据 / 失败”这几个阶段含义保持一致。
 */
public final class VpnTrafficLogParseStatus {

    /**
     * 为什么这样做：完整拿到头体、状态码和会话信息时，才应算真正的可读抓包成功。
     */
    public static final String SUCCESS = "SUCCESS";
    /**
     * 为什么这样做：拿到部分请求或响应信息，但正文/状态仍不完整时，不能再当作完整成功展示。
     */
    public static final String PARTIAL = "PARTIAL";
    /**
     * 为什么这样做：当正文解析失败时，至少还要把 url、时间、包名这些最小元数据留下来，保证控制台不空白。
     */
    public static final String MINIMAL = "MINIMAL";
    /**
     * 为什么这样做：请求连最小元数据都没有补全时，用失败态帮助开发者区分“没抓到”和“只抓到一半”。
     */
    public static final String FAILED = "FAILED";

    private VpnTrafficLogParseStatus() {
        // 为什么这样做：这是纯常量工具类，不需要被实例化。
    }

    /**
     * 归一化解析状态，避免数据库里出现空值。
     */
    @NonNull
    public static String normalize(@Nullable String status) {
        if (status == null || status.trim().isEmpty()) {
            return PARTIAL;
        }
        return status.trim();
    }

    /**
     * 判断是否已经进入可读成功态。
     */
    public static boolean isSuccess(@Nullable String status) {
        return SUCCESS.equalsIgnoreCase(normalize(status));
    }

    /**
     * 判断是否已经至少保住了最小元数据。
     */
    public static boolean isMinimalOrBetter(@Nullable String status) {
        String normalizedStatus = normalize(status);
        return SUCCESS.equalsIgnoreCase(normalizedStatus)
                || PARTIAL.equalsIgnoreCase(normalizedStatus)
                || MINIMAL.equalsIgnoreCase(normalizedStatus);
    }
}
