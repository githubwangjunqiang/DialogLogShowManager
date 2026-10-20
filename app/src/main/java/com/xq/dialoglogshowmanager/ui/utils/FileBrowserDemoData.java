package com.xq.dialoglogshowmanager.ui.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.content.Context;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 为宿主 Demo 准备可重复浏览、预览和分享的本地样例文件。
 * 为什么独立封装：将 Demo 测试数据的目录、生成和覆盖策略集中管理，避免宿主初始化代码被样例细节淹没。
 */
public final class FileBrowserDemoData {

    private static final String ROOT_DIRECTORY_NAME = "showlog_file_browser_demo";

    private FileBrowserDemoData() {
    }

    /**
     * 返回 SDK 文件浏览使用的应用私有根目录。
     * 为什么放在 filesDir：无需申请共享存储权限，同时 Android 7.0+ 系统分享由 SDK FileProvider 提供安全 URI。
     */
    @NonNull
    public static File getRootDirectory(@NonNull Context context) {
        return new File(context.getFilesDir(), ROOT_DIRECTORY_NAME);
    }

    /**
     * 创建文本、图片、目录和不支持预览的分享样例；已有文件不覆盖。
     */
    public static void createSamplesIfNeeded(@NonNull File rootDirectory) throws IOException {
        if (!rootDirectory.exists() && !rootDirectory.mkdirs()) {
            throw new IOException("无法创建演示根目录: " + rootDirectory);
        }
        if (!rootDirectory.isDirectory()) {
            throw new IOException("演示根路径不是目录: " + rootDirectory);
        }

        writeTextIfMissing(new File(rootDirectory, "readme.txt"),
                "ShowLog 文件浏览演示\n\n点击文本文件查看分页文本预览。\n"
                        + "图片用于验证原生采样解码；PDF 和 BIN 用于验证不支持预览时的系统分享。\n");
        writeTextIfMissing(new File(rootDirectory, "sample.json"),
                "{\n  \"feature\": \"file-browser\",\n  \"preview\": true,\n  \"share\": true\n}\n");
        writeTextIfMissing(new File(rootDirectory, "notes.md"),
                "# 文件浏览测试\n\n- 文本：点击查看\n- 图片：点击查看\n- PDF / BIN：点击后使用右上角分享\n");

        File nestedDirectory = new File(rootDirectory, "nested_folder");
        if (!nestedDirectory.exists() && !nestedDirectory.mkdirs()) {
            throw new IOException("无法创建演示子目录: " + nestedDirectory);
        }
        writeTextIfMissing(new File(nestedDirectory, "inside_folder.txt"),
                "已进入子目录。点击顶部返回按钮回到文件浏览根目录。\n");

        writeBitmapIfMissing(new File(rootDirectory, "sample_image.png"), Bitmap.CompressFormat.PNG);
        writeBitmapIfMissing(new File(rootDirectory, "sample_image.jpg"), Bitmap.CompressFormat.JPEG);
        writeBytesIfMissing(new File(rootDirectory, "sample_document.pdf"), createSamplePdf());
        writeBytesIfMissing(new File(rootDirectory, "sample_binary.bin"),
                new byte[]{0, 1, 2, 3, 0, 16, 32, 64, (byte) 0xff, (byte) 0xfe});
    }

    private static void writeTextIfMissing(@NonNull File file, @NonNull String text) throws IOException {
        writeBytesIfMissing(file, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeBytesIfMissing(@NonNull File file, @NonNull byte[] bytes) throws IOException {
        if (file.exists()) {
            return;
        }
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(bytes);
            output.flush();
        }
    }

    private static void writeBitmapIfMissing(@NonNull File file,
                                             @NonNull Bitmap.CompressFormat format) throws IOException {
        if (file.exists()) {
            return;
        }

        // 为什么控制在 640×480：足以观察预览采样效果，又避免每次 Demo 启动生成过大的测试资产。
        Bitmap bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.rgb(31, 41, 55));
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.rgb(56, 189, 248));
            canvas.drawCircle(180, 210, 110, paint);
            paint.setColor(Color.rgb(251, 191, 36));
            canvas.drawRect(340, 105, 555, 330, paint);
            paint.setColor(Color.WHITE);
            paint.setTextSize(30f);
            canvas.drawText("ShowLog Preview", 48, 425, paint);

            try (FileOutputStream output = new FileOutputStream(file)) {
                if (!bitmap.compress(format, 88, output)) {
                    throw new IOException("图片压缩失败: " + file);
                }
                output.flush();
            }
        } finally {
            bitmap.recycle();
        }
    }

    /** 生成可供外部 PDF 阅读器识别的最小有效 PDF，便于实际验证系统分享后的打开流程。 */
    @NonNull
    private static byte[] createSamplePdf() {
        String pdfContent = "BT /F1 18 Tf 50 140 Td (ShowLog PDF Share Test) Tj ET\n";
        String[] objects = new String[]{
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 400 240] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
                "<< /Length " + pdfContent.getBytes(StandardCharsets.US_ASCII).length
                        + " >>\nstream\n" + pdfContent + "endstream",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"
        };
        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        int[] offsets = new int[objects.length + 1];
        for (int index = 0; index < objects.length; index++) {
            offsets[index + 1] = pdf.toString().getBytes(StandardCharsets.US_ASCII).length;
            pdf.append(index + 1).append(" 0 obj\n")
                    .append(objects[index]).append("\nendobj\n");
        }
        int xrefOffset = pdf.toString().getBytes(StandardCharsets.US_ASCII).length;
        pdf.append("xref\n0 ").append(objects.length + 1).append("\n")
                .append("0000000000 65535 f \n");
        for (int index = 1; index < offsets.length; index++) {
            pdf.append(String.format(java.util.Locale.ROOT, "%010d 00000 n \n", offsets[index]));
        }
        pdf.append("trailer\n<< /Size ").append(objects.length + 1)
                .append(" /Root 1 0 R >>\nstartxref\n").append(xrefOffset).append("\n%%EOF\n");
        return pdf.toString().getBytes(StandardCharsets.US_ASCII);
    }
}
