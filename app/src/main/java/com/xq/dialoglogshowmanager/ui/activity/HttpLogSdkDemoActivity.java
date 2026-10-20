package com.xq.dialoglogshowmanager.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.xq.dialoglogshow.ShowLog;
import com.xq.dialoglogshow.core.repository.HttpLogRepository;
import com.xq.dialoglogshowmanager.App;
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
 * ShowLog SDK 独立专属测试页面。
 * 为什么这样做：
 * 1. 彻底与老旧杂乱功能剥离，界面只保留当前新重构 SDK 的测试操作与监控指标；
 * 2. 界面纯粹清晰，大按钮易点击，防止测试时眼花缭乱；
 * 3. 涵盖 GET、POST JSON、404 错误、未知主机/断网异常 4 大典型测试场景，可在页面即时查看结果，并直接呼出控制台或右下角悬浮球。
 */
public class HttpLogSdkDemoActivity extends AppCompatActivity {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private TextView mTvRequestFeedback;
    private TextView mTvSdkStatusIndicator;
    private com.google.android.material.switchmaterial.SwitchMaterial mSwitchToggleSdk;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_http_log_sdk_demo);
        StatusBarInsetHelper.applyTopPaddingForStatusBar(findViewById(R.id.sdk_demo_root));

        initViews();
    }

    private void initViews() {
        mTvRequestFeedback = findViewById(R.id.tv_request_feedback);
        mTvSdkStatusIndicator = findViewById(R.id.tv_sdk_status_indicator);
        mSwitchToggleSdk = findViewById(R.id.switch_toggle_sdk);

        // 0. 全局调试监控开关绑定
        mSwitchToggleSdk.setChecked(ShowLog.isEnabled());
        updateSdkStatusUI(ShowLog.isEnabled());
        mSwitchToggleSdk.setOnCheckedChangeListener((buttonView, isChecked) -> {
            ShowLog.setEnabled(isChecked);
            updateSdkStatusUI(isChecked);
            if (isChecked) {
                Toast.makeText(this, "SDK 调试监控已开启（悬浮球重新呈现）", Toast.LENGTH_SHORT).show();
                updateFeedback("【当前状态：调试已开启】\nOkHttp 拦截器已激活实时监控，新请求将自动记录到数据库，悬浮球已恢复显示。");
            } else {
                Toast.makeText(this, "SDK 生产直通模式已生效（拦截器 100% 零开销放行）", Toast.LENGTH_SHORT).show();
                updateFeedback("【当前状态：生产直通模式】\nShowLog 已进入零侵入熔断状态：\n• OkHttp 拦截器首行直接 proceed 放行，不克隆任何 Buffer；\n• 悬浮球已自动从屏幕卸载；\n• 控制台不响应弹窗，数据库零 I/O 写入。");
            }
        });

        // 注册业务自定义调试面板演示（证明自定义面板与网络日志、设备信息享有同等一级地位）
        ShowLog.registerCustomPanel("业务环境切换", (parent, dialog) -> {
            android.content.Context context = parent.getContext();

            // 业务方自行决定承载父布局内部如何填充：这里用 ScrollView + LinearLayout 构建可滚动面板
            android.widget.ScrollView scrollView = new android.widget.ScrollView(context);
            android.widget.LinearLayout layout = new android.widget.LinearLayout(context);
            layout.setOrientation(android.widget.LinearLayout.VERTICAL);
            layout.setPadding(32, 32, 32, 32);
            scrollView.addView(layout, new android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));
            parent.addView(scrollView);

            TextView tvTitle = new TextView(context);
            tvTitle.setText("🛠️ 宿主 App 业务环境配置面板 (自定义扩展)");
            tvTitle.setTextSize(16);
            tvTitle.setTextColor(0xFFFFFFFF);
            tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            layout.addView(tvTitle);

            TextView tvDesc = new TextView(context);
            tvDesc.setText("这是通过 ShowLog.registerCustomPanel() 注入的业务自定义调试组件。与「网络日志」、「设备信息」完全同级平列。\n\n• 当前环境: DEV 测试联调环境\n• 网关节点: https://gateway-test.internal.io\n• 模拟用户 UID: 9527\n• 模拟 Token: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...");
            tvDesc.setTextSize(13);
            tvDesc.setTextColor(0xFF94A3B8);
            tvDesc.setPadding(0, 24, 0, 24);
            layout.addView(tvDesc);

            MaterialButton btnSwitch = new MaterialButton(context);
            btnSwitch.setText("切换至 PRE 预发环境");
            btnSwitch.setOnClickListener(v -> Toast.makeText(context, "已切换至预发网关节点！", Toast.LENGTH_SHORT).show());
            layout.addView(btnSwitch);

            // 演示业务方通过 SDK 抛出的弹框对象，在适当时机主动关闭调试弹框
            MaterialButton btnClose = new MaterialButton(context);
            btnClose.setText("关闭调试弹框");
            btnClose.setOnClickListener(v -> dialog.dismiss());
            layout.addView(btnClose);
        });

        // 1. 打开全景调试中枢 (Hub) 主按钮
        findViewById(R.id.btn_open_showlog_console).setOnClickListener(v -> {
            if (ClickThrottleUtils.isFastClick(v)) return;
            if (!ShowLog.isEnabled()) {
                Toast.makeText(this, "当前处于生产关闭状态，调试中枢已静默熔断！", Toast.LENGTH_SHORT).show();
                return;
            }
            ShowLog.show(this);
        });

        // 1.1 直达网络日志
        findViewById(R.id.btn_direct_network).setOnClickListener(v -> {
            if (ClickThrottleUtils.isFastClick(v)) return;
            ShowLog.showNetworkLogs(this);
        });

        // 1.2 直达设备系统
        findViewById(R.id.btn_direct_device).setOnClickListener(v -> {
            if (ClickThrottleUtils.isFastClick(v)) return;
            ShowLog.showAppDeviceInfo(this);
        });

        // 1.3 直达自定义扩展
        findViewById(R.id.btn_direct_custom).setOnClickListener(v -> {
            if (ClickThrottleUtils.isFastClick(v)) return;
            ShowLog.showCustomPanel(this);
        });

        // 2. 跳转到旧版入口（备用）
        findViewById(R.id.tv_btn_legacy_nav).setOnClickListener(v -> {
            if (ClickThrottleUtils.isFastClick(v)) return;
            startActivity(new Intent(this, HomeActivity.class));
        });

        // 3. 按钮 1：GET 请求
        findViewById(R.id.btn_test_get).setOnClickListener(v -> {
            if (ClickThrottleUtils.isFastClick(v)) return;
            executeGetRequest();
        });

        // 4. 按钮 2：POST JSON 请求
        findViewById(R.id.btn_test_post).setOnClickListener(v -> {
            if (ClickThrottleUtils.isFastClick(v)) return;
            executePostJsonRequest();
        });

        // 5. 按钮 3：404 错误请求
        findViewById(R.id.btn_test_404).setOnClickListener(v -> {
            if (ClickThrottleUtils.isFastClick(v)) return;
            execute404Request();
        });

        // 6. 按钮 4：网络断开/异常请求
        findViewById(R.id.btn_test_network_error).setOnClickListener(v -> {
            if (ClickThrottleUtils.isFastClick(v)) return;
            executeNetworkErrorRequest();
        });

        // 6.1 按钮 5：纯抽象契约上报成功用例
        findViewById(R.id.btn_test_custom_payload_success).setOnClickListener(v -> {
            if (ClickThrottleUtils.isFastClick(v)) return;
            executeCustomPayloadSuccess();
        });

        // 6.2 按钮 6：纯抽象契约上报业务失败用例
        findViewById(R.id.btn_test_custom_payload_failed).setOnClickListener(v -> {
            if (ClickThrottleUtils.isFastClick(v)) return;
            executeCustomPayloadFailed();
        });

        // 7. 按钮 7：清空网络日志
        findViewById(R.id.btn_clear_all_logs).setOnClickListener(v -> {
            if (ClickThrottleUtils.isFastClick(v)) return;
            HttpLogRepository.getInstance().clearAllAsync(() -> {
                Toast.makeText(this, "所有网络日志已成功清空！", Toast.LENGTH_SHORT).show();
                mTvRequestFeedback.setText("已清空所有网络日志缓存与数据库。");
            });
        });
    }

    private void executeGetRequest() {
        String url = "https://jsonplaceholder.typicode.com/posts/1";
        updateFeedback("正在发起 GET 请求:\n" + url);

        Request request = new Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", "ShowLog-Android-Demo/5.0")
                .header("Accept", "application/json")
                .build();

        long startTime = SystemClock.elapsedRealtime();
        App.getOkHttpClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                long duration = SystemClock.elapsedRealtime() - startTime;
                runOnUiThread(() -> updateFeedback("❌ GET 请求失败 (" + duration + " ms)\n异常原因: " + e.getMessage()
                        + "\n\n💡 请点击右下角悬浮球或上方控制台查看完整堆栈。"));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                long duration = SystemClock.elapsedRealtime() - startTime;
                String body = response.body() != null ? response.body().string() : "";
                runOnUiThread(() -> updateFeedback("✅ GET 请求成功 [HTTP " + response.code() + "] (" + duration + " ms)\n"
                        + "URL: " + url + "\n"
                        + "Body 预览: " + (body.length() > 120 ? body.substring(0, 120) + "..." : body)
                        + "\n\n💡 请点击右下角悬浮球进入控制台查看美化排版与 cURL 命令。"));
            }
        });
    }

    private void executePostJsonRequest() {
        String url = "https://jsonplaceholder.typicode.com/posts";
        String jsonPayload = "{\n  \"title\": \"ShowLog Demo Post\",\n  \"body\": \"This is an automated test request with JSON payload.\",\n  \"userId\": 1024\n}";
        updateFeedback("正在发起 POST JSON 请求:\n" + url);

        RequestBody requestBody = RequestBody.create(jsonPayload, JSON_MEDIA_TYPE);
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .header("Authorization", "Bearer demo-token-123456")
                .header("X-Demo-Client", "DialogLogShow")
                .build();

        long startTime = SystemClock.elapsedRealtime();
        App.getOkHttpClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                long duration = SystemClock.elapsedRealtime() - startTime;
                runOnUiThread(() -> updateFeedback("❌ POST 请求失败 (" + duration + " ms)\n异常原因: " + e.getMessage()));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                long duration = SystemClock.elapsedRealtime() - startTime;
                String body = response.body() != null ? response.body().string() : "";
                runOnUiThread(() -> updateFeedback("✅ POST 请求成功 [HTTP " + response.code() + "] (" + duration + " ms)\n"
                        + "URL: " + url + "\n"
                        + "Body 响应: " + (body.length() > 120 ? body.substring(0, 120) + "..." : body)
                        + "\n\n💡 请求头已自动将 Bearer token 保护，详情见控制台。"));
            }
        });
    }

    private void execute404Request() {
        String url = "https://httpstat.us/404";
        updateFeedback("正在发起 404 错误测试请求:\n" + url);

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        long startTime = SystemClock.elapsedRealtime();
        App.getOkHttpClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                long duration = SystemClock.elapsedRealtime() - startTime;
                runOnUiThread(() -> updateFeedback("❌ 请求网络异常 (" + duration + " ms): " + e.getMessage()));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                long duration = SystemClock.elapsedRealtime() - startTime;
                runOnUiThread(() -> updateFeedback("⚠️ 接收到 404 错误响应 [HTTP " + response.code() + "] (" + duration + " ms)\n"
                        + "URL: " + url + "\n\n"
                        + "💡 控制台已自动将该记录标红，点击控制台顶部的「错误」药丸可一键单选过滤此类记录！"));
            }
        });
    }

    private void executeNetworkErrorRequest() {
        String url = "https://invalid-non-existent-domain-test.xyz/api/test";
        updateFeedback("正在发起断网/DNS 失败异常测试请求:\n" + url);

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        long startTime = SystemClock.elapsedRealtime();
        App.getOkHttpClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                long duration = SystemClock.elapsedRealtime() - startTime;
                runOnUiThread(() -> updateFeedback("💥 预期内的网络异常已成功触发！(" + duration + " ms)\n"
                        + "异常类型: " + e.getClass().getSimpleName() + "\n"
                        + "信息: " + e.getMessage() + "\n\n"
                        + "💡 SDK 拦截器未吞噬异常（已正常通知业务回调），并在控制台中完整记录了异常堆栈！"));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                long duration = SystemClock.elapsedRealtime() - startTime;
                runOnUiThread(() -> updateFeedback("意外成功: " + response.code() + " (" + duration + " ms)"));
            }
        });
    }

    private void executeCustomPayloadSuccess() {
        try {
            org.json.JSONObject reqJson = new org.json.JSONObject();
            reqJson.put("action", "query_user_info");
            reqJson.put("userId", 10086);

            org.json.JSONObject resJson = new org.json.JSONObject();
            resJson.put("code", 0);
            resJson.put("msg", "success");
            resJson.put("nickname", "Antigravity Dev");

            java.util.Map<String, String> reqHeaders = new java.util.HashMap<>();
            reqHeaders.put("Authorization", "Bearer demo_custom_token");
            reqHeaders.put("X-Client-Type", "CustomHttpClient");

            java.util.Map<String, String> resHeaders = new java.util.HashMap<>();
            resHeaders.put("Content-Type", "application/json; charset=utf-8");

            // 契约模式：所有函数均在 IDE (Alt+Enter) 中强制自动实现，开发者直接填空
            ShowLog.report(new com.xq.dialoglogshow.core.model.HttpLogPayload() {
                @NonNull
                @Override
                public String getUrl() {
                    return "https://api.mycustomservice.com/v1/user/info";
                }

                @NonNull
                @Override
                public String getMethod() {
                    return "POST";
                }

                @Override
                public int getHttpCode() {
                    return 200;
                }

                @Nullable
                @Override
                public String getMessage() {
                    return "OK";
                }

                @Override
                public boolean isSuccess() {
                    return true;
                }

                @Override
                public long getDurationMs() {
                    return 192L;
                }

                @Nullable
                @Override
                public java.util.Map<String, String> getRequestHeaders() {
                    return reqHeaders;
                }

                @Nullable
                @Override
                public java.util.Map<String, String> getResponseHeaders() {
                    return resHeaders;
                }

                @Nullable
                @Override
                public org.json.JSONObject getRequestBody() {
                    return reqJson;
                }

                @Nullable
                @Override
                public org.json.JSONObject getResponseBody() {
                    return resJson;
                }

                @Nullable
                @Override
                public Throwable getThrowable() {
                    return null;
                }
            });

            updateFeedback("✅ 已通过强契约接口 HttpLogPayload 上报【业务成功用例】\n"
                    + "URL: https://api.mycustomservice.com/v1/user/info\n"
                    + "HTTP Code: 200, 业务裁决: true (成功)\n"
                    + "💡 已自动格式化缩进 JSONObject 并安全落盘，可在控制台实时查看。");
            Toast.makeText(this, "契约成功用例上报成功！", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            updateFeedback("❌ 契约上报失败: " + t.getMessage());
        }
    }

    private void executeCustomPayloadFailed() {
        try {
            org.json.JSONObject reqJson = new org.json.JSONObject();
            reqJson.put("action", "transfer_points");
            reqJson.put("amount", 999999);

            org.json.JSONObject resJson = new org.json.JSONObject();
            resJson.put("code", 50001);
            resJson.put("msg", "账户可用积分不足，转赠失败");

            ShowLog.report(new com.xq.dialoglogshow.core.model.HttpLogPayload() {
                @NonNull
                @Override
                public String getUrl() {
                    return "https://api.mycustomservice.com/v1/points/transfer";
                }

                @NonNull
                @Override
                public String getMethod() {
                    return "POST";
                }

                @Override
                public int getHttpCode() {
                    return 200; // 网络层传输 200
                }

                @Nullable
                @Override
                public String getMessage() {
                    return "账户可用积分不足(code=50001)"; // 业务错误提示
                }

                @Override
                public boolean isSuccess() {
                    return false; // 业务裁决失败！在控制台标红并可筛选
                }

                @Override
                public long getDurationMs() {
                    return 310L;
                }

                @Nullable
                @Override
                public java.util.Map<String, String> getRequestHeaders() {
                    return null;
                }

                @Nullable
                @Override
                public java.util.Map<String, String> getResponseHeaders() {
                    return null;
                }

                @Nullable
                @Override
                public org.json.JSONObject getRequestBody() {
                    return reqJson;
                }

                @Nullable
                @Override
                public org.json.JSONObject getResponseBody() {
                    return resJson;
                }

                @Nullable
                @Override
                public Throwable getThrowable() {
                    return new IllegalStateException("Business validation failed: insufficient points");
                }
            });

            updateFeedback("⚠️ 已通过强契约接口 HttpLogPayload 上报【业务失败用例】\n"
                    + "URL: https://api.mycustomservice.com/v1/points/transfer\n"
                    + "HTTP Code: 200, 业务裁决: false (业务判定失败)\n"
                    + "业务消息: 账户可用积分不足(code=50001)\n"
                    + "💡 控制台将以此作为失败处理（标红、计入错误 Tab 并展示原因）。");
            Toast.makeText(this, "契约失败用例上报成功！", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            updateFeedback("❌ 契约上报失败: " + t.getMessage());
        }
    }

    private void updateFeedback(@NonNull String message) {
        if (mTvRequestFeedback != null) {
            mTvRequestFeedback.setText(message);
        }
    }

    private void updateSdkStatusUI(boolean isEnabled) {
        if (mTvSdkStatusIndicator != null) {
            if (isEnabled) {
                mTvSdkStatusIndicator.setText(R.string.standalone_status_active);
                mTvSdkStatusIndicator.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.demo_status_success));
            } else {
                mTvSdkStatusIndicator.setText(R.string.standalone_status_disabled);
                mTvSdkStatusIndicator.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.demo_status_warning));
            }
        }
    }
}
