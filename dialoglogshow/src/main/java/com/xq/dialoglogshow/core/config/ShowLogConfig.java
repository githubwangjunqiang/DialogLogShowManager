package com.xq.dialoglogshow.core.config;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 全局 SDK 运行配置。
 * 为什么这样做：
 * 采用参数化配置与安全范围保护，既让开发者能自由调整数据库容量上限（默认 10000 条）和脱敏规则，
 * 又能防止极端参数引发 OOM 或磁盘耗尽。
 */
public final class ShowLogConfig {

    public static final int DEFAULT_MAX_DATABASE_LIMIT = 10000;
    public static final int MIN_DATABASE_LIMIT = 100;
    public static final int MAX_DATABASE_LIMIT = 50000;
    public static final int DEFAULT_MAX_BODY_BYTES = 256 * 1024; // 256KB 截断保护

    /**
     * SQLite 数据库保留网络日志的最大条数，默认 10000 条。
     */
    public final int maxDatabaseLogLimit;

    /**
     * 单个请求体/响应体允许读取和缓存的最大字节数，超出部分安全截断。
     */
    public final int maxBodyBytes;

    /**
     * 敏感请求头集合，拦截器会自动对其打码脱敏。
     */
    @NonNull
    public final Set<String> redactHeaders;

    /**
     * 是否启用网络日志监控与调试功能。
     * 为什么这样做：
     * 生产包可配置为 false（如传入 BuildConfig.DEBUG），此时 SDK 内部拦截器直接直通放行，
     * 绝不执行 Buffer 克隆、正则嗅探与后台写库，悬浮球与控制台完全静默，零开销零侵入。
     */
    public final boolean enabled;

    /**
     * 是否在 App 界面上显示悬浮球入口。
     */
    public final boolean showFloatingView;

    private ShowLogConfig(Builder builder) {
        // 为什么这样做：对数据库上限执行区间钳制，杜绝负数、0 或极端百万级配置引起闪退或卡死。
        this.maxDatabaseLogLimit = Math.max(MIN_DATABASE_LIMIT, Math.min(builder.maxDatabaseLogLimit, MAX_DATABASE_LIMIT));
        this.maxBodyBytes = Math.max(1024, builder.maxBodyBytes);
        this.redactHeaders = Collections.unmodifiableSet(new HashSet<>(builder.redactHeaders));
        this.enabled = builder.enabled;
        this.showFloatingView = builder.showFloatingView;
    }

    @NonNull
    public static ShowLogConfig defaultConfig() {
        return new Builder().build();
    }

    public static final class Builder {
        private int maxDatabaseLogLimit = DEFAULT_MAX_DATABASE_LIMIT;
        private int maxBodyBytes = DEFAULT_MAX_BODY_BYTES;
        private final Set<String> redactHeaders = new HashSet<>();
        private boolean enabled = true;
        private boolean showFloatingView = true;

        public Builder() {
            // 为什么这样做：默认对主流认证和身份凭证字段进行脱敏保护，防止调试日志泄露敏感 Token。
            redactHeaders.add("Authorization");
            redactHeaders.add("Cookie");
            redactHeaders.add("Set-Cookie");
            redactHeaders.add("token");
            redactHeaders.add("access_token");
        }

        /**
         * 设置数据库最多保留的历史网络日志条数（默认 10000 条，允许范围 100 ~ 50000）。
         */
        public Builder maxDatabaseLogLimit(int limit) {
            this.maxDatabaseLogLimit = limit;
            return this;
        }

        /**
         * 设置报文读取最大截断字节数（默认 256KB）。
         */
        public Builder maxBodyBytes(int bytes) {
            this.maxBodyBytes = bytes;
            return this;
        }

        /**
         * 添加需要脱敏的敏感 Header 键名。
         */
        public Builder addRedactHeader(@NonNull String headerName) {
            this.redactHeaders.add(headerName);
            return this;
        }

        /**
         * 设置是否启用 SDK（默认 true）。生产包建议设置为 false（如传入 BuildConfig.DEBUG），
         * 关闭后拦截器将 100% 直通放行，不产生任何性能影响。
         */
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /**
         * 是否自动在页面上显示调试悬浮球入口（默认 true）。
         */
        public Builder showFloatingView(boolean show) {
            this.showFloatingView = show;
            return this;
        }

        @NonNull
        public ShowLogConfig build() {
            return new ShowLogConfig(this);
        }
    }
}
