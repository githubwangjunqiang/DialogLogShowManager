package com.xq.dialoglogshow.utils;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import com.xq.dialoglogshow.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by Android-小强 on 2023/1/11.
 * mailbox:980766134@qq.com
 * description: SDK 统一分享工具。
 * 为什么这样做：文件 URI、临时授权和跨应用兼容性容易被宿主重复实现错误，因此统一由 SDK 安全处理。
 */
public final class ShareUtils {

    /**
     * 为什么这样做：分享缓存只用于跨应用短期读取，保留一天可兼顾接收端延迟读取与缓存空间回收。
     */
    private static final long SHARE_CACHE_MAX_AGE_MILLIS = 24L * 60L * 60L * 1000L;

    /**
     * 为什么这样做：文件复制必须确保在后台执行，不能依赖宿主可能传入的主线程 Executor；
     * 核心线程空闲后自动回收，避免调试面板长时间不用分享时仍占用线程资源。
     */
    private static final ThreadPoolExecutor FILE_SHARE_EXECUTOR;

    /**
     * 为什么这样做：复用主线程 Handler 可减少每次分享创建调度对象，同时集中保证 UI 操作回到主线程。
     */
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    static {
        FILE_SHARE_EXECUTOR = new ThreadPoolExecutor(
                1,
                1,
                30L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>()
        );
        FILE_SHARE_EXECUTOR.allowCoreThreadTimeOut(true);
    }

    private ShareUtils() {
        // 为什么这样做：工具类不保存实例状态，禁止创建对象可避免误用。
    }

    /**
     * 分享文本
     */
    public static void shareText(Context context, String shareText) {
        Intent apply = new Intent(Intent.ACTION_SEND);
        apply.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        apply.putExtra(Intent.EXTRA_TEXT, shareText);
        apply.setType("text/*");
        try {
            Intent chooser = Intent.createChooser(apply, "share-test");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 分享指定日志文件。
     * 为什么这样做：先复制到 SDK 可控缓存目录，再通过 FileProvider 分享，可兼容宿主返回任意合法日志路径，
     * 同时避免对外暴露宿主原始目录。
     *
     * @param context  用于启动系统分享面板的上下文
     * @param filePath 待分享日志文件的绝对路径
     */
    public static void shareFile(@NonNull Context context, @NonNull String filePath) {
        Context appContext = context.getApplicationContext();
        File sourceFile = new File(filePath);
        if (!sourceFile.isFile() || !sourceFile.canRead()) {
            Toast.makeText(appContext, R.string.show_sdk_share_file_not_found, Toast.LENGTH_SHORT).show();
            return;
        }

        FILE_SHARE_EXECUTOR.execute(() -> {
            File sharedFile = null;
            File candidateFile = null;
            try {
                File shareDirectory = new File(appContext.getCacheDir(), "dialoglogshow/share");
                ensureDirectory(shareDirectory);
                clearExpiredFiles(shareDirectory);
                candidateFile = new File(
                        shareDirectory,
                        System.currentTimeMillis() + "_" + sanitizeFileName(sourceFile.getName())
                );
                copyFile(sourceFile, candidateFile);
                // 为什么这样做：只有完整复制成功后才交给分享面板，避免异常时把半截日志误当成成功结果。
                sharedFile = candidateFile;
            } catch (Exception exception) {
                exception.printStackTrace();
                if (candidateFile != null && candidateFile.exists()) {
                    candidateFile.delete();
                }
            }

            File result = sharedFile;
            MAIN_HANDLER.post(() -> {
                if (result == null || !result.isFile()) {
                    Toast.makeText(appContext, R.string.show_sdk_share_file_failed, Toast.LENGTH_SHORT).show();
                    return;
                }
                openShareChooser(appContext, result);
            });
        });
    }

    /**
     * 创建分享目录并校验结果，避免 mkdirs 静默失败后继续写文件。
     */
    private static void ensureDirectory(@NonNull File directory) throws IOException {
        if ((!directory.exists() && !directory.mkdirs()) || !directory.isDirectory()) {
            throw new IOException("Unable to create SDK share cache directory");
        }
    }

    /**
     * 清理过期缓存，避免长期调试期间反复分享导致缓存无限增长。
     */
    private static void clearExpiredFiles(@NonNull File directory) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        long expiredBefore = System.currentTimeMillis() - SHARE_CACHE_MAX_AGE_MILLIS;
        for (File file : files) {
            if (file.isFile() && file.lastModified() < expiredBefore) {
                // 为什么这样做：删除失败不应阻断本次分享，系统缓存目录后续仍可由系统统一回收。
                file.delete();
            }
        }
    }

    /**
     * 清理文件名中的路径字符，避免异常名称改变 SDK 预期的缓存落点。
     */
    @NonNull
    private static String sanitizeFileName(@NonNull String fileName) {
        String sanitized = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (sanitized.isEmpty()) {
            return "show_log.txt";
        }
        // 为什么这样做：给时间戳前缀预留文件系统长度空间，避免极长宿主文件名导致缓存创建失败。
        return sanitized.length() > 60 ? sanitized.substring(sanitized.length() - 60) : sanitized;
    }

    /**
     * 使用流复制以兼容 minSdk 21，并通过 try-with-resources 保证异常路径也能释放文件句柄。
     */
    private static void copyFile(@NonNull File source, @NonNull File target) throws IOException {
        byte[] buffer = new byte[8192];
        try (FileInputStream inputStream = new FileInputStream(source);
             FileOutputStream outputStream = new FileOutputStream(target)) {
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.flush();
        }
    }

    /**
     * 启动系统分享面板，并只授予接收应用临时读取权限。
     */
    private static void openShareChooser(@NonNull Context context, @NonNull File sharedFile) {
        try {
            String authority = context.getPackageName() + ".dialoglogshow.fileprovider";
            Uri uri = FileProvider.getUriForFile(context, authority, sharedFile);
            String mimeType = URLConnection.guessContentTypeFromName(sharedFile.getName());
            if (mimeType == null) {
                mimeType = "application/octet-stream";
            }

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType(mimeType);
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            // 为什么这样做：部分定制系统只从 ClipData 传递 URI 授权，双通道设置可提升兼容性。
            shareIntent.setClipData(ClipData.newRawUri(sharedFile.getName(), uri));
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Intent chooser = Intent.createChooser(
                    shareIntent,
                    context.getString(R.string.show_sdk_share_log_file)
            );
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);
        } catch (Exception exception) {
            exception.printStackTrace();
            Toast.makeText(context, R.string.show_sdk_share_file_failed, Toast.LENGTH_SHORT).show();
        }
    }
}
