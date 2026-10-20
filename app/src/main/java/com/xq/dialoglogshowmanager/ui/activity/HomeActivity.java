package com.xq.dialoglogshowmanager.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.xq.dialoglogshow.ShowLog;
import com.xq.dialoglogshowmanager.App;
import com.xq.dialoglogshowmanager.MainActivity;
import com.xq.dialoglogshowmanager.R;
import com.xq.dialoglogshowmanager.ui.utils.ClickThrottleUtils;
import com.xq.dialoglogshowmanager.ui.utils.StatusBarInsetHelper;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 应用首页（Demo 演示与入口）。
 * 为什么这样做：
 * 1. 核心展示全新的 ShowLog SDK 与 OkHttp 拦截器的极简使用方式；
 * 2. 页面直观提供 GET、POST、404 错误三个真实开源接口测试按钮，方便一键点击并在悬浮球或控制台中即时验证；
 * 3. 业务代码发起网络请求完全按照 OkHttp 常规写法编写，零多余接入成本，全自动拦截。
 */
@Keep
public class HomeActivity extends AppCompatActivity {

    private static final String TAG = "HomeActivity";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        StatusBarInsetHelper.applyTopPaddingForStatusBar(findViewById(R.id.home_root));

        // 1. 全新 OkHttp 实测按钮绑定
        setupOkHttpTestButtons();

        // 2. 既有入口按钮绑定
        setupNavigationButtons();
    }

    private void setupOkHttpTestButtons() {
        MaterialButton btnGet = findViewById(R.id.btn_demo_okhttp_get);
        MaterialButton btnPost = findViewById(R.id.btn_demo_okhttp_post);
        MaterialButton btnError = findViewById(R.id.btn_demo_okhttp_error);

        // 测试 1：GET 开源接口 (天气/结构化 JSON)
        btnGet.setOnClickListener(v -> {
            if (ClickThrottleUtils.isFastClick("demo_btn_okhttp_get")) {
                return;
            }
            sendOkHttpGetTest();
        });

        // 测试 2：POST 开源接口 (带复杂 JSON 报文)
        btnPost.setOnClickListener(v -> {
            if (ClickThrottleUtils.isFastClick("demo_btn_okhttp_post")) {
                return;
            }
            sendOkHttpPostTest();
        });

        // 测试 3：请求 404 错误接口 (验证控制台标红与异常留痕)
        btnError.setOnClickListener(v -> {
            if (ClickThrottleUtils.isFastClick("demo_btn_okhttp_error")) {
                return;
            }
            sendOkHttpErrorTest();
        });
    }

    private void setupNavigationButtons() {
        MaterialButton btnOpenTargetManager = findViewById(R.id.btn_open_target_manager);
        MaterialButton btnOpenNetworkLab = findViewById(R.id.btn_open_network_lab);
        MaterialButton btnOpenConsole = findViewById(R.id.btn_open_console);

        btnOpenTargetManager.setOnClickListener(v -> {
            if (ClickThrottleUtils.isFastClick("home_open_target_app_manager")) {
                return;
            }
            startActivity(new Intent(this, TargetAppManagerActivity.class));
        });

        btnOpenNetworkLab.setOnClickListener(v -> {
            if (ClickThrottleUtils.isFastClick("home_open_network_lab")) {
                return;
            }
            startActivity(new Intent(this, MainActivity.class));
        });

        btnOpenConsole.setOnClickListener(v -> {
            if (ClickThrottleUtils.isFastClick("home_open_console")) {
                return;
            }
            // 为什么这样做：除了右下角免权限悬浮球外，允许直接一键呼出全新的网络日志控制台
            ShowLog.showConsole(this);
        });
    }

    /**
     * 发起 GET 开源接口测试。
     * 为什么这样做：演示常规 GET 请求、Query 参数及 JSON 响应体的自动拦截与解析。
     */
    private void sendOkHttpGetTest() {
        Toast.makeText(this, R.string.demo_request_running, Toast.LENGTH_SHORT).show();
        long startTime = SystemClock.elapsedRealtime();

        Request request = new Request.Builder()
                .url("https://httpbin.org/get?source=android_demo&version=5.0.4&framework=okhttp")
                .header("User-Agent", "DialogLogShowManager-Demo/5.0.4")
                .header("Accept", "application/json")
                .get()
                .build();

        App.getOkHttpClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                long duration = SystemClock.elapsedRealtime() - startTime;
                Log.e(TAG, "GET 请求失败: " + e.getMessage(), e);
                runOnUiThread(() -> Toast.makeText(HomeActivity.this,
                        getString(R.string.demo_request_failed, e.getMessage()), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                long duration = SystemClock.elapsedRealtime() - startTime;
                int code = response.code();
                Log.d(TAG, "GET 请求成功: code=" + code + ", 耗时=" + duration + "ms");
                runOnUiThread(() -> Toast.makeText(HomeActivity.this,
                        getString(R.string.demo_request_success, code, duration), Toast.LENGTH_SHORT).show());
            }
        });
    }

    /**
     * 发起 POST 开源接口测试。
     * 为什么这样做：演示带 Request Headers、复杂 JSON Request Body 报文的自动安全截断、脱敏与高亮展示。
     */
    private void sendOkHttpPostTest() {
        Toast.makeText(this, R.string.demo_request_running, Toast.LENGTH_SHORT).show();
        long startTime = SystemClock.elapsedRealtime();

        String jsonPayload = "{\n" +
                "  \"app\": \"DialogLogShowManager\",\n" +
                "  \"version\": \"5.0.4\",\n" +
                "  \"module\": \"In-App-Interceptor-Mode\",\n" +
                "  \"features\": [\"Auto-Logging\", \"Memory-Hybrid-Read\", \"Path-Grouping\", \"DiffUtil\"],\n" +
                "  \"author\": \"王俊强\",\n" +
                "  \"timestamp\": " + System.currentTimeMillis() + "\n" +
                "}";

        RequestBody body = RequestBody.create(jsonPayload, JSON_MEDIA_TYPE);
        Request request = new Request.Builder()
                .url("https://httpbin.org/post")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer mock_secret_token_12345678") // 验证敏感 Header 自动脱敏打码
                .post(body)
                .build();

        App.getOkHttpClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                long duration = SystemClock.elapsedRealtime() - startTime;
                Log.e(TAG, "POST 请求失败: " + e.getMessage(), e);
                runOnUiThread(() -> Toast.makeText(HomeActivity.this,
                        getString(R.string.demo_request_failed, e.getMessage()), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                long duration = SystemClock.elapsedRealtime() - startTime;
                int code = response.code();
                Log.d(TAG, "POST 请求成功: code=" + code + ", 耗时=" + duration + "ms");
                runOnUiThread(() -> Toast.makeText(HomeActivity.this,
                        getString(R.string.demo_request_success, code, duration), Toast.LENGTH_SHORT).show());
            }
        });
    }

    /**
     * 发起 404 错误接口测试。
     * 为什么这样做：演示服务端非 200 异常状态码在控制台中自动高亮鲜红显示、状态过滤与堆栈记录能力。
     */
    private void sendOkHttpErrorTest() {
        Toast.makeText(this, R.string.demo_request_running, Toast.LENGTH_SHORT).show();
        long startTime = SystemClock.elapsedRealtime();

        Request request = new Request.Builder()
                .url("https://httpbin.org/status/404")
                .get()
                .build();

        App.getOkHttpClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                long duration = SystemClock.elapsedRealtime() - startTime;
                Log.e(TAG, "404 接口调用网络失败: " + e.getMessage(), e);
                runOnUiThread(() -> Toast.makeText(HomeActivity.this,
                        getString(R.string.demo_request_failed, e.getMessage()), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                long duration = SystemClock.elapsedRealtime() - startTime;
                int code = response.code();
                Log.d(TAG, "404 接口调用成功返回错误码: code=" + code + ", 耗时=" + duration + "ms");
                runOnUiThread(() -> Toast.makeText(HomeActivity.this,
                        getString(R.string.demo_request_success, code, duration), Toast.LENGTH_SHORT).show());
            }
        });
    }
}
