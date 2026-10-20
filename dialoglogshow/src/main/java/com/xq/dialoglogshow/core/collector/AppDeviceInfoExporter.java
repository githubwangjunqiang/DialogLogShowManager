package com.xq.dialoglogshow.core.collector;

import androidx.annotation.NonNull;

import com.xq.dialoglogshow.core.model.AppDeviceInfo;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 诊断报告 Markdown 导出格式化器。
 * 为什么这样做：
 * 结构化生成规范的 Markdown 文本，方便测试与研发一键复制并直接粘贴到 Bug 提报单中。
 */
public final class AppDeviceInfoExporter {

    private AppDeviceInfoExporter() {
    }

    @NonNull
    public static String exportToMarkdown(@NonNull AppDeviceInfo info) {
        StringBuilder sb = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

        sb.append("# 📱 应用与设备系统全景诊断报告\n");
        sb.append("> **生成时间**：").append(sdf.format(new Date())).append("\n\n");

        appendCategorySection(sb, "📦 1. 应用画像 (App Profile)", info.getItemsByCategory(AppDeviceInfo.CATEGORY_APP));
        appendCategorySection(sb, "🤖 2. 操作系统 (OS & Runtime)", info.getItemsByCategory(AppDeviceInfo.CATEGORY_OS));
        appendCategorySection(sb, "💻 3. 硬件与显示 (Hardware & Display)", info.getItemsByCategory(AppDeviceInfo.CATEGORY_HARDWARE));
        appendCategorySection(sb, "⚡ 4. 运行性能 (Runtime Performance)", info.getItemsByCategory(AppDeviceInfo.CATEGORY_PERFORMANCE));

        sb.append("---\n");
        sb.append("*Generated automatically by ShowLog SDK.*");
        return sb.toString();
    }

    private static void appendCategorySection(@NonNull StringBuilder sb, @NonNull String title, @NonNull List<AppDeviceInfo.InfoItem> items) {
        if (items.isEmpty()) return;
        sb.append("### ").append(title).append("\n\n");
        sb.append("| 属性名称 | 参数值 |\n");
        sb.append("| :--- | :--- |\n");
        for (AppDeviceInfo.InfoItem item : items) {
            String safeValue = item.value.replace("|", "\\|").replace("\n", " ");
            sb.append("| **").append(item.key).append("** | `").append(safeValue).append("` |\n");
        }
        sb.append("\n");
    }
}
