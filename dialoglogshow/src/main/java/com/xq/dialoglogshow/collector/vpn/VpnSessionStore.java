package com.xq.dialoglogshow.collector.vpn;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/**
 * VPN 会话偏好存储
 *
 * 为什么这样做：
 * 1. SDK 需要记住“用户上一次是否明确开启过 VPN”，这样下次应用冷启动时才能自动恢复，而不是每次都重新要求人工点击。
 * 2. 这里用 SharedPreferences 而不是更重的数据库，是因为只存极少量布尔状态，读写路径短、依赖少、最适合 Application 启动阶段快速判断。
 */
public final class VpnSessionStore {

    private static final String PREF_NAME = "showlog_vpn_session";
    /**
     * 为什么这样做：这个标记只表示“用户是否希望 SDK 继续自动保持 VPN 开启”，
     * 不直接等同于当前运行态，避免进程被系统杀掉时把临时中断误判成用户主动关闭。
     */
    private static final String KEY_USER_ENABLED = "key_user_enabled";
    /**
     * 为什么这样做：自动恢复需要最小重试间隔与失败冷却窗口，否则当系统或 ROM 持续拒绝拉起时会形成高频抖动。
     */
    private static final String KEY_LAST_RESTORE_ATTEMPT_TIME = "key_last_restore_attempt_time";
    private static final String KEY_RESTORE_FAIL_COUNT = "key_restore_fail_count";

    private VpnSessionStore() {
    }

    @NonNull
    private static SharedPreferences getPreferences(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 记录用户是否希望自动维持 VPN 开启
     */
    public static void setUserEnabled(@NonNull Context context, boolean enabled) {
        getPreferences(context).edit().putBoolean(KEY_USER_ENABLED, enabled).apply();
    }

    /**
     * 读取用户是否曾明确开启过 VPN
     */
    public static boolean isUserEnabled(@NonNull Context context) {
        return getPreferences(context).getBoolean(KEY_USER_ENABLED, false);
    }

    /**
     * 记录最近一次自动恢复尝试时间
     */
    public static void setLastRestoreAttemptTime(@NonNull Context context, long timeMs) {
        getPreferences(context).edit().putLong(KEY_LAST_RESTORE_ATTEMPT_TIME, timeMs).apply();
    }

    /**
     * 读取最近一次自动恢复尝试时间
     */
    public static long getLastRestoreAttemptTime(@NonNull Context context) {
        return getPreferences(context).getLong(KEY_LAST_RESTORE_ATTEMPT_TIME, 0L);
    }

    /**
     * 记录连续恢复失败次数
     */
    public static void setRestoreFailCount(@NonNull Context context, int failCount) {
        getPreferences(context).edit().putInt(KEY_RESTORE_FAIL_COUNT, Math.max(0, failCount)).apply();
    }

    /**
     * 读取连续恢复失败次数
     */
    public static int getRestoreFailCount(@NonNull Context context) {
        return getPreferences(context).getInt(KEY_RESTORE_FAIL_COUNT, 0);
    }

    /**
     * 恢复成功或用户主动关闭后清空失败节流状态
     */
    public static void resetRestoreThrottle(@NonNull Context context) {
        getPreferences(context).edit()
                .putLong(KEY_LAST_RESTORE_ATTEMPT_TIME, 0L)
                .putInt(KEY_RESTORE_FAIL_COUNT, 0)
                .apply();
    }
}
