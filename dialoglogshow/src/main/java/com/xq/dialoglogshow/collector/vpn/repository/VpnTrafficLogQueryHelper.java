package com.xq.dialoglogshow.collector.vpn.repository;

import androidx.annotation.NonNull;

import java.util.Calendar;
import java.util.Locale;

/**
 * VPN 日志查询辅助类
 *
 * 为什么这样做：
 * 1. UI 侧的“按天查询”必须严格转换成毫秒范围，单独收口后可以避免各处自己算日期边界出现偏差。
 * 2. 这里先给出一步到位的当日时间范围工具，后续再补分页或更多过滤条件时也能继续复用。
 */
public final class VpnTrafficLogQueryHelper {

    private VpnTrafficLogQueryHelper() {
    }

    @NonNull
    public static TimeRange buildDayRange(long timeInMillis) {
        Calendar calendar = Calendar.getInstance(Locale.getDefault());
        calendar.setTimeInMillis(timeInMillis);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long startTime = calendar.getTimeInMillis();
        calendar.add(Calendar.DAY_OF_YEAR, 1);
        long endTime = calendar.getTimeInMillis();
        return new TimeRange(startTime, endTime);
    }

    public static class TimeRange {
        public final long startTime;
        public final long endTime;

        public TimeRange(long startTime, long endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }
}
