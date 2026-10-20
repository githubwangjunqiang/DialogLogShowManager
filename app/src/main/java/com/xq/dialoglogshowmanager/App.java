package com.xq.dialoglogshowmanager;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;

import com.xq.dialoglogshow.ShowLog;
import com.xq.dialoglogshow.core.config.ShowLogConfig;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

/**
 * 宿主 Demo Application。
 * 为什么这样做：
 * 1. 演示全新的 ShowLog 极简接入：仅需 ShowLog.init(this) 一行代码即可开启全套网络日志监控；
 * 2. 演示 OkHttp 拦截器的无侵入接入：在构建 OkHttpClient 时直接注入 ShowLog.getOkHttpInterceptor()；
 * 3. 彻底摆脱旧版必须开发者手动实现拦截、手写 SQLite 缓存和分页查询的沉重负担。
 */
public class App extends Application {

    private static final String TAG = "DemoApp";
    private static volatile OkHttpClient sOkHttpClient;

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(TAG, "onCreate: 启动全新的 ShowLog 网络日志监控调试 SDK");

        // 1. 初始化 ShowLog：配置默认保留最新 10000 条日志，自动开启免权限悬浮球
        ShowLog.init(this, new ShowLogConfig.Builder()
                .maxDatabaseLogLimit(10000)
                .build());

        // 1.1 配置全局业务成功裁决拦截器（解耦 HTTP 传输状态码与真实业务成败）
        ShowLog.setBusinessJudger(context -> {
            if (context.getHttpCode() < 200 || context.getHttpCode() >= 300) {
                return new com.xq.dialoglogshow.core.interceptor.HttpLogBusinessJudger.JudgementResult(
                        false, "HTTP " + context.getHttpCode());
            }
            org.json.JSONObject json = context.optResponseBodyAsJson();
            if (json != null) {
                int code = json.optInt("code", 0);
                String msg = json.optString("msg", json.optString("message", "OK"));
                if (code != 0) {
                    return new com.xq.dialoglogshow.core.interceptor.HttpLogBusinessJudger.JudgementResult(
                            false, "业务错误(" + code + "): " + msg);
                }
            }
            return new com.xq.dialoglogshow.core.interceptor.HttpLogBusinessJudger.JudgementResult(true, "OK");
        });

        // 2. 初始化全局共享的 OkHttpClient，并配置 SDK 提供的拦截器
        initOkHttpClient();
    }

    private void initOkHttpClient() {
        sOkHttpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .addInterceptor(ShowLog.getOkHttpInterceptor()) // 核心：一行代码配置拦截器，网络请求全托管
                .build();
    }

    @NonNull
    public static OkHttpClient getOkHttpClient() {
        if (sOkHttpClient == null) {
            sOkHttpClient = new OkHttpClient.Builder()
                    .addInterceptor(ShowLog.getOkHttpInterceptor())
                    .build();
        }
        return sOkHttpClient;
    }
}
