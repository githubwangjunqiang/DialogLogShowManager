package com.xq.dialoglogshow.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

/**
 * Description:
 * Author:王俊强 980766134@qq.com
 * Date:2025/12/22
 */
public class AppUtils {


    /**
     * 获取App版本码
     *
     * @param context     上下文
     * @param packageName 包名
     * @return App版本码
     */
    public static int getAppVersionCode(Context context, String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return -1;
        }
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(packageName, 0);
            return pi == null ? -1 : pi.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return -1;
        }
    }


    /**
     * 字符串中间拆分（奇数长度时前半部分更长）
     * @param str 要拆分的原始字符串
     * @return Pair 拆分后的前后两部分（first：前半部分，second：后半部分）
     */
    public static Pair<String, String> splitStringCenter(String str) {
        // 对应Kotlin的空字符串判断，兼容str为null的情况（比Kotlin更严谨）
        if (TextUtils.isEmpty(str)) {
            return new Pair<>("", "");
        }
        // 核心逻辑：与Kotlin完全一致，(长度 + 1) / 2 实现奇数长度前半部分更长
        int middleIndex = (str.length() + 1) / 2;
        // 对应Kotlin的substring方法，Java substring(int beginIndex, int endIndex) 语义完全一致
        String firstPart = str.substring(0, middleIndex);
        // 对应Kotlin的substring(startIndex)，Java同样支持该重载方法
        String secondPart = str.substring(middleIndex);
        // Java中使用androidx的Pair（或java.util.AbstractMap.SimpleEntry，二选一）
        return new Pair<>(firstPart, secondPart);
    }
}
