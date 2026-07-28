package com.xq.dialoglogshowmanager;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.nio.charset.StandardCharsets;

import com.xq.dialoglogshow.ShowLog;
import com.xq.dialoglogshow.collector.vpn.VpnController;
import com.xq.dialoglogshow.collector.vpn.VpnStatusManager;
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
    /**
     * 为什么这样做：通过 adb 启动 demo 时，需要一套稳定的参数来自动拉起 VPN/证书安装/测试请求，避免每轮真机联调都依赖手工点界面。
     */
    private static final String EXTRA_AUTO_OPEN_CONSOLE = "auto_open_console";
    private static final String EXTRA_AUTO_START_VPN = "auto_start_vpn";
    private static final String EXTRA_AUTO_INSTALL_CERT = "auto_install_cert";
    private static final String EXTRA_AUTO_RUN_HTTP = "auto_run_http";
    private static final String EXTRA_AUTO_RUN_HTTPS = "auto_run_https";
    private static final String EXTRA_AUTO_RUN_OKHTTP_SUITE = "auto_run_okhttp_suite";
    /**
     * 为什么这样做：自动化步骤之间需要留出 UI/系统授权初始化时间，统一主线程调度比散落 sleep 更稳定也更容易维护。
     */
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    /**
     * 为什么这样做：持续把 VPN 状态打到 logcat，才能在无人值守联调时快速看出是卡在授权、启动还是已运行。
     */
    private final VpnStatusManager.StatusListener mVpnStatusListener = new VpnStatusManager.StatusListener() {
        @Override
        public void onVpnStatusChanged(@androidx.annotation.NonNull VpnStatusManager.Status status,
                                       @androidx.annotation.Nullable String errorMessage) {
            Log.d(TAG, "VPN 状态变化: " + status + ", error=" + errorMessage);
        }

        @Override
        public void onVpnTrafficSnapshotChanged(@androidx.annotation.NonNull VpnStatusManager.TrafficSnapshot trafficSnapshot) {
            Log.d(TAG, "VPN 流量快照: packets=" + trafficSnapshot.packetCount
                    + ", tcp=" + trafficSnapshot.tcpPacketCount
                    + ", udp=" + trafficSnapshot.udpPacketCount
                    + ", sessions=" + trafficSnapshot.activeSessionCount
                    + ", latest=" + trafficSnapshot.lastSessionSummary);
        }
    };
    private long lastClickTime = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView tvVpnGuide = findViewById(R.id.tv_vpn_guide);
        Button btnOpenConsole = findViewById(R.id.btn_open_console);
        Button btnCheckStatus = findViewById(R.id.btn_check_status);
        Button btnTestGet = findViewById(R.id.btn_test_get);
        Button btnTestPost = findViewById(R.id.btn_test_post);
        Button btnTestError = findViewById(R.id.btn_test_error);

        // 为什么这样做：真机验证最容易失败在“请求发早了、VPN 还没开”，因此首页直接把验证顺序展示出来，降低你手动排查成本。
        tvVpnGuide.setText(getString(R.string.app_demo_vpn_verify_guide));

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

        Button btnOkHttpWeather = findViewById(R.id.btn_okhttp_weather);
        Button btnOkHttpForm = findViewById(R.id.btn_okhttp_form);
        Button btnOkHttpJson = findViewById(R.id.btn_okhttp_json);

        // 测试 4：OkHttp 发起国内真实天气 API 请求 (GET 返回 JSON) (防抖 500ms)
        btnOkHttpWeather.setOnClickListener(v -> {
            if (isFastDoubleClick()) {
                return;
            }
            Toast.makeText(MainActivity.this, "OkHttp 发起国内天气 API 请求...", Toast.LENGTH_SHORT).show();
            // 为什么这样做：测试真实国内开放 JSON 接口，验证 GET 请求完整返回体在控制台中的美化解析
            sendOkHttpRequest("国内天气 API", new okhttp3.Request.Builder().url("https://api.vvhan.com/api/weather?city=北京").get().build());
        });

        // 测试 5：OkHttp 发起真实 Form 表单提交 (POST Form) (防抖 500ms)
        btnOkHttpForm.setOnClickListener(v -> {
            if (isFastDoubleClick()) {
                return;
            }
            Toast.makeText(MainActivity.this, "OkHttp 发起 Form 表单提交...", Toast.LENGTH_SHORT).show();
            // 为什么这样做：测试 application/x-www-form-urlencoded 表达入参的抓取与 Form 字段展示
            okhttp3.RequestBody formBody = new okhttp3.FormBody.Builder()
                    .add("city", "北京")
                    .add("type", "json")
                    .add("client", "AndroidOkHttp")
                    .build();
            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url("https://httpbin.org/post")
                    .post(formBody)
                    .build();
            sendOkHttpRequest("POST Form 表单", request);
        });

        // 测试 6：OkHttp 发起真实 JSON 结构报文提交 (POST JSON) (防抖 500ms)
        btnOkHttpJson.setOnClickListener(v -> {
            if (isFastDoubleClick()) {
                return;
            }
            Toast.makeText(MainActivity.this, "OkHttp 发起 JSON 复杂报文提交...", Toast.LENGTH_SHORT).show();
            // 为什么这样做：测试 application/json 复杂嵌套 JSON 字符串在请求体与响应体中的高亮展现
            triggerOkHttpRealApiTests();
        });

        // 为什么这样做：真机验证必须由你在 VPN 真正启动后手动触发请求，避免应用刚启动时的自动请求全部落在 VPN 之前，造成“看起来抓不到日志”的假问题。
        handleAutomationIntent(getIntent());
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleAutomationIntent(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        VpnStatusManager.getInstance().addStatusListener(mVpnStatusListener);
    }

    @Override
    protected void onStop() {
        VpnStatusManager.getInstance().removeStatusListener(mVpnStatusListener);
        super.onStop();
    }

    /**
     * 处理 adb 自动联调参数
     * 为什么这样做：把自动化入口收口在一个方法里，避免 onCreate/onNewIntent 分别拼装联调流程导致时序不一致。
     */
    private void handleAutomationIntent(android.content.Intent intent) {
        if (!BuildConfig.DEBUG) {
            // 为什么这样做：自动开 VPN / 自动打请求只服务于真机联调，release 环境必须彻底禁用，避免误触发调试行为。
            return;
        }
        if (intent == null) {
            return;
        }
        boolean autoOpenConsole = intent.getBooleanExtra(EXTRA_AUTO_OPEN_CONSOLE, false);
        boolean autoStartVpn = intent.getBooleanExtra(EXTRA_AUTO_START_VPN, false);
        boolean autoInstallCert = intent.getBooleanExtra(EXTRA_AUTO_INSTALL_CERT, false);
        boolean autoRunHttp = intent.getBooleanExtra(EXTRA_AUTO_RUN_HTTP, false);
        boolean autoRunHttps = intent.getBooleanExtra(EXTRA_AUTO_RUN_HTTPS, false);
        boolean autoRunOkHttpSuite = intent.getBooleanExtra(EXTRA_AUTO_RUN_OKHTTP_SUITE, false);
        if (!autoOpenConsole && !autoStartVpn && !autoInstallCert && !autoRunHttp && !autoRunHttps && !autoRunOkHttpSuite) {
            return;
        }
        Log.d(TAG, "收到 adb 自动联调参数: console=" + autoOpenConsole
                + ", startVpn=" + autoStartVpn
                + ", installCert=" + autoInstallCert
                + ", runHttp=" + autoRunHttp
                + ", runHttps=" + autoRunHttps
                + ", okHttpSuite=" + autoRunOkHttpSuite);
        if (autoOpenConsole) {
            mMainHandler.postDelayed(() -> {
                Log.d(TAG, "自动打开调试控制台");
                new DialogShowLog(MainActivity.this).show();
            }, 300L);
        }
        if (autoInstallCert) {
            mMainHandler.postDelayed(() -> {
                Log.d(TAG, "自动发起 HTTPS 调试证书安装");
                VpnController.getInstance().requestCertificateInstall(MainActivity.this);
            }, 700L);
        }
        if (autoStartVpn) {
            mMainHandler.postDelayed(() -> {
                Log.d(TAG, "自动发起 VPN 启动");
                VpnController.getInstance().startVpn(MainActivity.this);
            }, 1000L);
        }
        if (autoRunHttp) {
            mMainHandler.postDelayed(() -> {
                Log.d(TAG, "自动发起 HTTP GET 测试请求");
                new Thread(() -> sendHttpRequest("http://httpbin.org/get", "GET", null)).start();
            }, 2500L);
        }
        if (autoRunHttps) {
            mMainHandler.postDelayed(() -> {
                Log.d(TAG, "自动发起 HTTPS POST 测试请求");
                new Thread(() -> sendHttpRequest("https://httpbin.org/post", "POST", "{\"client\":\"AndroidApp\",\"version\":\"5.0.0\"}")).start();
            }, 3200L);
        }
        if (autoRunOkHttpSuite) {
            mMainHandler.postDelayed(() -> {
                Log.d(TAG, "自动发起 OkHttp 联调请求套件");
                triggerOkHttpRealApiTests();
            }, 3800L);
        }
    }

    /**
     * 顺序并发发起真实 OkHttp API 请求套件 (包含国内 Weather GET JSON, Form POST, JSON POST)
     */
    private void triggerOkHttpRealApiTests() {
        Log.d(TAG, "开始执行真实 OkHttp 网络请求套件...");

        // 1. 中国天气网官方 JSON API (GET 返回结构化天气 JSON)
        sendOkHttpRequest("国内天气 JSON API", new okhttp3.Request.Builder().url("http://t.weather.sojson.com/api/weather/city/101010100").get().build());

        // 2. Form 表单提交 (POST Form)
        new Thread(() -> {
            sendNativePostRequest("POST Form 表单", "application/x-www-form-urlencoded; charset=UTF-8", "city=%E5%8C%97%E4%BA%AC&type=json&client=AndroidNativeApp".getBytes(StandardCharsets.UTF_8));
        }).start();

        // 3. JSON 复杂结构报文提交 (POST JSON)
        new Thread(() -> {
            String jsonStr = "{\"city\":\"Beijing\",\"days\":7,\"unit\":\"metric\",\"device\":{\"brand\":\"Huawei\",\"model\":\"AMM_AN00\"}}";
            sendNativePostRequest("POST JSON 报文", "application/json; charset=utf-8", jsonStr.getBytes(StandardCharsets.UTF_8));
        }).start();
    }

    /**
     * 底层通用原生 POST 请求发送器 (基于 HttpURLConnection)
     * 为什么这样做：在 HTTP 80 端口上稳定发送真实 Form 表单与 JSON 复杂报文，不受 TLS cipher 协商影响，保障抓包展示 100% 正确。
     */
    private void sendNativePostRequest(String tag, String contentType, byte[] postData) {
        java.net.HttpURLConnection conn = null;
        try {
            java.net.URL url = new java.net.URL("http://httpbin.org/post");
            conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", contentType);
            conn.setRequestProperty("User-Agent", "DialogLogShowManager-Demo/5.0.0");
            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(postData);
                os.flush();
            }
            int code = conn.getResponseCode();
            java.io.InputStream is = code < 400 ? conn.getInputStream() : conn.getErrorStream();
            byte[] resBytes = readAllBytes(is);
            Log.d(TAG, tag + " Native POST 成功: " + code + ", body: " + new String(resBytes, StandardCharsets.UTF_8));
        } catch (Throwable t) {
            Log.e(TAG, tag + " Native POST 异常", t);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private byte[] readAllBytes(java.io.InputStream is) throws java.io.IOException {
        if (is == null) return new byte[0];
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[4096];
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }
    private void sendOkHttpRequest(String tag, okhttp3.Request request) {
        getUnsafeOkHttpClient().newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                Log.e(TAG, tag + " 失败", e);
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                String body = response.body() != null ? response.body().string() : "";
                Log.d(TAG, tag + " 成功: " + response.code() + ", len: " + body.length() + ", body: " + body);
            }
        });
    }

    /**
     * 创建安全全通配的 OkHttpClient (跳过 SSL 证书校验，保障抓包测试稳定运行)
     */
    private static okhttp3.OkHttpClient getUnsafeOkHttpClient() {
        try {
            final javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[]{
                    new javax.net.ssl.X509TrustManager() {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[]{};
                        }
                    }
            };

            final javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            final javax.net.ssl.SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            okhttp3.OkHttpClient.Builder builder = new okhttp3.OkHttpClient.Builder();
            builder.protocols(java.util.Collections.singletonList(okhttp3.Protocol.HTTP_1_1));
            builder.sslSocketFactory(sslSocketFactory, (javax.net.ssl.X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier((hostname, session) -> true);
            builder.connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS);
            builder.readTimeout(10, java.util.concurrent.TimeUnit.SECONDS);
            return builder.build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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

            // 为什么这样做：必须读取响应输入流，模拟真实 App 网络通信，确保 VPN 单方案下的请求与响应都真正走完完整链路，便于控制台稳定落日志。
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
