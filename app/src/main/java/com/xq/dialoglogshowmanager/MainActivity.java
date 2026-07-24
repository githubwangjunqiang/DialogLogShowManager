package com.xq.dialoglogshowmanager;

import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.xq.dialoglogshow.ShowLog;
import com.xq.dialoglogshow.dialog.DialogShowLog;

/**
 * Description: SDK Demo 宿主主页面
 * 为什么这样做：演示全新的 ShowLog SDK v5.0.0 手动打开控制台弹窗与查询 Provider 状态的规范代码，
 * 并应用点击事件防抖机制（默认 500ms），保障界面交互流畅稳定。
 *
 * Author: 王俊强 980766134@qq.com
 * Date: 2026/07/22
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final long THROTTLE_INTERVAL_MS = 500L;
    private long lastClickTime = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnOpenConsole = findViewById(R.id.btn_open_console);
        Button btnCheckStatus = findViewById(R.id.btn_check_status);
        Button btnTestGet = findViewById(R.id.btn_test_get);
        Button btnTestPost = findViewById(R.id.btn_test_post);
        Button btnTestError = findViewById(R.id.btn_test_error);

        // 手动唤起调试控制台 (防抖 500ms)
        btnOpenConsole.setOnClickListener(v -> {
            if (isFastDoubleClick()) {
                return;
            }
            // 为什么这样做：除了通过悬浮球点击唤起外，允许宿主应用在设置页或隐藏菜单中手动弹出控制台
            new DialogShowLog(MainActivity.this).show();
        });

        // 检查 SDK 当前运行状态 (防抖 500ms)
        btnCheckStatus.setOnClickListener(v -> {
            if (isFastDoubleClick()) {
                return;
            }
            boolean isDebug = ShowLog.getProvider().isDebug();
            String title = ShowLog.getProvider().getConsoleTitle();

            // 为什么这样做：在界面触发按钮时测试调用 nativelib-release.aar 导出的 BiZhi 本地 native 接口
            boolean nativeInit = false;
            String nativeLep = "";
            try {
                nativeInit = com.xq.app.so.BiZhi.init(getApplicationContext());
                nativeLep = com.xq.app.so.BiZhi.lep();
            } catch (Throwable t) {
                Log.e(TAG, "BiZhi Native API 调用异常", t);
            }

            String message = "SDK Debug: " + isDebug + ", NativeInit: " + nativeInit + ", Lep: " + nativeLep;
            Log.d(TAG, message);
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
        });

        // 测试 1：测试发起 HTTP GET 请求 (防抖 500ms)
        btnTestGet.setOnClickListener(v -> {
            if (isFastDoubleClick()) {
                return;
            }
            Toast.makeText(MainActivity.this, "正在发起 HTTP GET 请求...", Toast.LENGTH_SHORT).show();
            // 为什么这样做：开启子线程发送真实 HTTP 网络请求，验证代理 SocketImpl 是否能无感自动捕获 HTTP 80 端口日志
            new Thread(() -> {
                sendHttpRequest("http://httpbin.org/get", "GET", null);
            }).start();
        });

        // 测试 2：测试发起 HTTPS POST 请求 (防抖 500ms)
        btnTestPost.setOnClickListener(v -> {
            if (isFastDoubleClick()) {
                return;
            }
            Toast.makeText(MainActivity.this, "正在发起 HTTPS POST 请求...", Toast.LENGTH_SHORT).show();
            // 为什么这样做：开启子线程发送真实 HTTPS 网络请求，验证代理 SSLSocketFactory 是否能抓取 TLS 443 端口明文
            new Thread(() -> {
                sendHttpRequest("https://httpbin.org/post", "POST", "{\"client\":\"AndroidApp\",\"version\":\"5.0.0\"}");
            }).start();
        });

        // 测试 3：测试发起 404 异常请求 (防抖 500ms)
        btnTestError.setOnClickListener(v -> {
            if (isFastDoubleClick()) {
                return;
            }
            Toast.makeText(MainActivity.this, "正在发起 404 错误请求...", Toast.LENGTH_SHORT).show();
            // 为什么这样做：验证状态码为 404 错误时，日志能够自动收集并可以在 UI 上通过“仅看失败”快速筛选
            new Thread(() -> {
                sendHttpRequest("https://httpbin.org/status/404", "GET", null);
            }).start();
        });
    }

    /**
     * 底层通用网络请求发送器 (基于 HttpURLConnection)
     * 为什么这样做：模拟最原生的 Java 网络通信，不依赖特定三方库即可测试 SDK 底层拦截能力。
     */
    private void sendHttpRequest(String urlStr, String method, String body) {
        java.net.HttpURLConnection conn = null;
        try {
            java.net.URL url = new java.net.URL(urlStr);
            conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "DialogLogShowManager-Demo/5.0.0");
            conn.setRequestProperty("Accept", "application/json");

            if ("POST".equalsIgnoreCase(method) && body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                try (java.io.OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }

            int responseCode = conn.getResponseCode();
            Log.d(TAG, "Request " + urlStr + " finished with code: " + responseCode);

            // 为什么这样做：必须读取响应输入流，模拟真实 App 网络通信，确保触发 CopyInputStream 数据镜像复制与底层 Socket close 提交
            java.io.InputStream is = responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (is != null) {
                byte[] buf = new byte[1024];
                while (is.read(buf) != -1) {
                    // 读取完整 Response Body 触发旁路复制
                }
                is.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Request failed: " + urlStr, e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 校验点击防抖，500毫秒内连续点击无效
     * 为什么这样做：遵循项目全局强制规范，防止反复重复拉起 Dialog 导致 Window 溢出。
     */
    private boolean isFastDoubleClick() {
        long currentTime = SystemClock.elapsedRealtime();
        if (currentTime - lastClickTime < THROTTLE_INTERVAL_MS) {
            return true;
        }
        lastClickTime = currentTime;
        return false;
    }
}