package com.xq.dialoglogshowmanager;

import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.graphics.RectF;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.xq.dialoglogshow.ShowLog;
import com.xq.dialoglogshow.config.ShowLogProvider;
import com.xq.dialoglogshow.entity.BaseShowData;
import com.xq.dialoglogshow.entity.HttpLogData;
import com.xq.dialoglogshow.entity.PushData;
import com.xq.dialoglogshow.utils.SizeUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * Description: 宿主 Demo 系统的 Application 初始化
 * 为什么这样做：演示全新的 ShowLog.init(Application, ShowLogProvider) 极简无脑式接入方式，
 * 证明开发者只需实现 ShowLogProvider 纯接口，IDE 就会提示自动生成所有配置函数，零隐形配置。
 *
 * Author: 王俊强 980766134@qq.com
 * Date: 2026/07/22
 */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        Log.d("App", "onCreate: 初始化 ShowLog 调试控制台");

        // 一站式无脑接入 SDK 契约提供者
        ShowLog.init(this, new ShowLogProvider() {

            @Override
            public boolean isDebug() {
                return true; // 仅 Debug 环境开启控制台
            }

            @Nullable
            @Override
            public String getCacheDocPath() {
                File externalCache = getExternalCacheDir();
                return externalCache != null ? externalCache.getAbsolutePath() : null;
            }

            @Override
            public boolean isSortUrlForTime() {
                return true; // 开启接口日志按时间倒序排列
            }

            @Nullable
            @Override
            public Executor getLoadExecutor() {
                return new ScheduledThreadPoolExecutor(1);
            }

            @Override
            public boolean isShowVersionCodeView() {
                return true; // 悬浮窗显示版本号
            }

            @Nullable
            @Override
            public String getShowVersionCodeText() {
                return "v1.2.0-Debug";
            }

            @Nullable
            @Override
            public RectF getDebugViewSize() {
                return new RectF(10,
                        SizeUtils.dpToPx(App.this, 360F),
                        SizeUtils.dpToPx(App.this, 60F),
                        SizeUtils.dpToPx(App.this, 420F));
            }

            @Nullable
            @Override
            public View getCustomEntranceView(@NonNull Context context) {
                return null; // 使用默认悬浮球 View
            }

            @Nullable
            @Override
            public String getConsoleTitle() {
                return "极客调试台 (Geek Console)";
            }

            @Nullable
            @Override
            public ArrayList<HttpLogData> provideHttpLogs(long startTime, long endTime) {
                ArrayList<HttpLogData> httpLogData = new ArrayList<>();
                httpLogData.add(new HttpLogData("https://api.example.com/user/info", startTime, "{\n  \"code\": 200,\n  \"msg\": \"success\"\n}", "200 OK", null, false, 0));
                httpLogData.add(new HttpLogData("https://api.example.com/order/list", startTime, "{\n  \"code\": 200,\n  \"data\": []\n}", "200 OK", null, false, 0));
                return httpLogData;
            }

            @Override
            public void setupCustomTab(@NonNull FrameLayout container, @NonNull Dialog dialog) {
                TextView textView = new TextView(container.getContext());
                textView.setText("自定义设置 Tab 页面");
                textView.setPadding(30, 30, 30, 30);
                textView.setOnClickListener(view -> {
                    Toast.makeText(view.getContext(), "点击了自定义设置", Toast.LENGTH_SHORT).show();
                    dialog.cancel();
                });
                container.addView(textView);
            }

            @Nullable
            @Override
            public ArrayList<BaseShowData> provideKeyValueData() {
                return null;
            }

            @Nullable
            @Override
            public ArrayList<PushData> providePushLogs() {
                return null;
            }

            @Nullable
            @Override
            public ArrayList<BaseShowData> provideUserInfo() {
                BaseShowData data = new BaseShowData();
                data.setContent("{\n  \"userId\": 10086,\n  \"userName\": \"测试用户\",\n  \"role\": \"ADMIN\"\n}");
                ArrayList<BaseShowData> list = new ArrayList<>();
                list.add(data);
                return list;
            }

            @Nullable
            @Override
            public String provideAllCacheDocPath() {
                File cacheDir = getExternalFilesDir("cache");
                if (cacheDir != null && !cacheDir.exists()) {
                    cacheDir.mkdirs();
                }
                if (cacheDir != null) {
                    File file = new File(cacheDir, "test_log.txt");
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                        writer.write("这是一条日志测试缓存文本文件内容！\n第二行数据。");
                        writer.flush();
                        return file.getAbsolutePath();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                return null;
            }

            @Nullable
            @Override
            public ArrayList<BaseShowData> provideRestData() {
                ArrayList<BaseShowData> list = new ArrayList<>();
                BaseShowData data = new BaseShowData();
                data.setContent("环境配置: STAGING-TEST-ENV-01");
                list.add(data);
                return list;
            }

            @Override
            public boolean onDeleteHttpLog(@NonNull BaseShowData data) {
                return true; // 模拟删除成功
            }

            @Override
            public boolean onClearAllHttpLogs() {
                return true; // 模拟清空成功
            }

            @Override
            public void onOpenTextFile(@NonNull String filePath) {
                // 使用 SDK 内置 ReaderTxtDialog
            }

            @Override
            public void onShareFile(@NonNull Context context, @NonNull String filePath) {
                // 使用 SDK 内置 FileShareActivity
            }

            @Override
            public void onConsoleShow() {
                Log.d("App", "控制台弹窗打开了");
            }

            @Override
            public void onConsoleDismiss() {
                Log.d("App", "控制台弹窗关闭了");
            }
        });
    }
}
