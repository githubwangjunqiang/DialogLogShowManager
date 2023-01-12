package com.xq.dialoglogshow.utils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Created by Android-小强 on 2023/1/11.
 * mailbox:980766134@qq.com
 * description:
 */
public class DateUtils {
    /**
     * 获取当天0点时间戳
     *
     * @return
     */
    public static long getTimeMorning() {
//        String tz = "GMT+8";
//        TimeZone curTimeZone = TimeZone.getTimeZone(tz);
        //这是可以设置时区的
//        Calendar calendar = Calendar.getInstance(curTimeZone);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }


    public static String format(long time) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(time);
    }

    public static String formatDd(long time) {
        return new SimpleDateFormat("MM-dd", Locale.ENGLISH).format(time);
    }
}
