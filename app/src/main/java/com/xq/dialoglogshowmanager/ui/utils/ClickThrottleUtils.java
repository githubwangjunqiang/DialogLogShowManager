package com.xq.dialoglogshowmanager.ui.utils;

import androidx.annotation.NonNull;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 点击防抖工具。
 * 为什么这样做：目标页和首页都会有多个入口按钮，统一按 key 防抖可以避免重复点击把同一条链路连发多次。
 */
public final class ClickThrottleUtils {

    private static final long DEFAULT_INTERVAL_MS = 350L;
    private static final ConcurrentHashMap<String, Long> LAST_CLICK_TIME_MAP = new ConcurrentHashMap<>();

    private ClickThrottleUtils() {
        // 为什么这样做：工具类只提供静态能力，禁止实例化可以避免误用。
    }

    /**
     * 判断某个业务入口是否被快速重复点击。
     * 为什么这样做：按业务 key 而不是按 View 实例防抖，可以让跨页面入口共享同一节流逻辑。
     */
    public static boolean isFastClick(@NonNull String key) {
        long currentTime = android.os.SystemClock.elapsedRealtime();
        Long lastClickTime = LAST_CLICK_TIME_MAP.get(key);
        if (lastClickTime != null && currentTime - lastClickTime < DEFAULT_INTERVAL_MS) {
            return true;
        }
        LAST_CLICK_TIME_MAP.put(key, currentTime);
        return false;
    }

    /**
     * 判断某个 View 是否被快速重复点击。
     */
    public static boolean isFastClick(@NonNull android.view.View view) {
        return isFastClick(String.valueOf(view.getId()));
    }
}
