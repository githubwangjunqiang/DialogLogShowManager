package com.xq.dialoglogshowmanager.ui.utils;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.xq.dialoglogshowmanager.R;

/**
 * 状态栏安全区适配工具。
 * 为什么这样做：当前页面都取消了 ActionBar，顶部标题如果不补系统窗口内边距，视觉上会贴到状态栏，看起来太紧。
 */
public final class StatusBarInsetHelper {

    private StatusBarInsetHelper() {
        // 为什么这样做：该工具类只做一次性窗口内边距处理，不应该被实例化。
    }

    /**
     * 给根布局补状态栏高度，保证标题与状态栏之间的间距在不同机型上一致。
     * 为什么这样做：直接写死 top margin 会在刘海屏、不同状态栏高度和横竖屏切换时失真。
     */
    public static void applyTopPaddingForStatusBar(@NonNull View rootView) {
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (view, insets) -> {
            int originalLeft = getOriginalPadding(view, 0);
            int originalTop = getOriginalPadding(view, 1);
            int originalRight = getOriginalPadding(view, 2);
            int originalBottom = getOriginalPadding(view, 3);
            int statusBarInsetTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            view.setPadding(
                    originalLeft,
                    originalTop + statusBarInsetTop,
                    originalRight,
                    originalBottom
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(rootView);
    }

    /**
     * 为什么这样做：首次回调前要把布局 XML 的原始 padding 记录下来，后续重复分发 insets 时才能避免累加。
     */
    private static int getOriginalPadding(@NonNull View view, int index) {
        int[] originalPadding = (int[]) view.getTag(R.id.tag_original_padding);
        if (originalPadding == null) {
            originalPadding = new int[]{
                    view.getPaddingLeft(),
                    view.getPaddingTop(),
                    view.getPaddingRight(),
                    view.getPaddingBottom()
            };
            view.setTag(R.id.tag_original_padding, originalPadding);
        }
        return originalPadding[index];
    }
}
