package com.xq.dialoglogshow.core.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 设备与应用系统全景信息聚合实体。
 * 为什么这样做：
 * 1. 采用结构化不可变模型设计，分为应用画像、操作系统、硬件屏幕、性能资源四大核心维度；
 * 2. 扁平化提供条目列表，便于适配器统一进行 Tab 分类切换与关键词模糊搜索过滤；
 * 3. 严格实现 Serializable，支持组件间安全传递。
 */
public final class AppDeviceInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String CATEGORY_APP = "应用画像";
    public static final String CATEGORY_OS = "操作系统";
    public static final String CATEGORY_HARDWARE = "硬件屏幕";
    public static final String CATEGORY_PERFORMANCE = "运行性能";

    /**
     * 单个展示属性条目。
     */
    public static final class InfoItem implements Serializable {
        private static final long serialVersionUID = 1L;

        @NonNull
        public final String category;
        @NonNull
        public final String key;
        @NonNull
        public final String value;
        public final boolean isImportant;

        public InfoItem(@NonNull String category, @NonNull String key, @NonNull String value, boolean isImportant) {
            this.category = category;
            this.key = key;
            this.value = value;
            this.isImportant = isImportant;
        }

        public InfoItem(@NonNull String category, @NonNull String key, @NonNull String value) {
            this(category, key, value, false);
        }
    }

    @NonNull
    public final List<InfoItem> allItems;

    public AppDeviceInfo(@NonNull List<InfoItem> items) {
        this.allItems = Collections.unmodifiableList(new ArrayList<>(items));
    }

    /**
     * 获取指定分类下的全部条目。
     */
    @NonNull
    public List<InfoItem> getItemsByCategory(@Nullable String category) {
        if (category == null || category.isEmpty() || "全部".equals(category)) {
            return allItems;
        }
        List<InfoItem> result = new ArrayList<>();
        for (InfoItem item : allItems) {
            if (category.equals(item.category)) {
                result.add(item);
            }
        }
        return result;
    }
}
