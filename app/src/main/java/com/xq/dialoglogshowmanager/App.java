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
import com.xq.dialoglogshow.config.VpnPayloadDecryptor;
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
 * 为什么这样做：演示全新 ShowLog 5.0.0 架构，并在 provideHttpLogs 中模拟丰富真实的网络日志数据。
 *
 * Author: 王俊强 980766134@qq.com
 * Date: 2026/07/22
 */
public class App extends Application {

    private final ArrayList<HttpLogData> mMockHttpLogs = new ArrayList<>();
    private final ArrayList<PushData> mMockPushLogs = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d("App", "onCreate: 初始化 ShowLog 调试控制台与丰富的模拟日志数据");

        // 构造丰富多样的真实模拟网络日志数据
        initMockLogs();

        // 为什么这样做：接入并调用 nativelib-release.aar 提供的 Native C/C++ SO 库与其 API 接口
        try {
            boolean initSuccess = com.xq.app.so.BiZhi.init(this);
            String lepValue = com.xq.app.so.BiZhi.lep();
            Log.d("App", "nativelib-release.aar Native 库初始化结果: " + initSuccess + ", lep标志位: " + lepValue);
        } catch (Throwable t) {
            Log.e("App", "nativelib-release.aar 初始化异常", t);
        }

        // 为什么这样做：Demo App 直接按最终单方案接入，传入 true 后统一启用 VPN 网络日志能力，方便真机验证。
        ShowLog.init(this, new ShowLogProvider() {

            @Override
            public boolean isDebug() {
                return true; // 仅 Debug 环境开启控制台
            }

            @Override
            public boolean isVpnNetworkLogEnabled() {
                // 为什么这样做：Demo 显式开启 SDK 唯一 VPN 采集链路，验证真实宿主接入时无需额外启动 API。
                return true;
            }

            @Nullable
            @Override
            public String getCacheDocPath() {
                File externalCache = getExternalCacheDir();
                return externalCache != null ? externalCache.getAbsolutePath() : null;
            }

            @Override
            public boolean isSortUrlForTime() {
                return false; // 演示父子卡片展开与折叠树状结构
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
                return "极客调试台 (VPN 验证)";
            }

            @Nullable
            @Override
            public VpnPayloadDecryptor getVpnPayloadDecryptor() {
                // 为什么这样做：Demo 默认不注入业务解密逻辑，保持 SDK 在未注册时展示原始报文的真实接入路径。
                return null;
            }

            @Nullable
            @Override
            public ArrayList<HttpLogData> provideHttpLogs(long startTime, long endTime) {
                return mMockHttpLogs;
            }

            @Override
            public void setupCustomTab(@NonNull FrameLayout container, @NonNull Dialog dialog) {
                TextView textView = new TextView(container.getContext());
                textView.setText("自定义设置 Tab 页面\n点击关闭控制台弹窗");
                textView.setTextColor(0xFF06B6D4);
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
                ArrayList<BaseShowData> list = new ArrayList<>();
                BaseShowData d1 = new BaseShowData();
                d1.setItemType(BaseShowData.TYE_ONE);
                d1.setContent("DeviceToken: 9876543210ABCDEF123456789\nAPNsToken: e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
                list.add(d1);
                return list;
            }

            @Nullable
            @Override
            public ArrayList<PushData> providePushLogs() {
                return mMockPushLogs;
            }

            @Nullable
            @Override
            public ArrayList<BaseShowData> provideUserInfo() {
                BaseShowData data = new BaseShowData();
                data.setItemType(BaseShowData.TYE_ONE);
                data.setContent("{\n  \"userId\": 886699,\n  \"userName\": \"SuperGeekUser\",\n  \"role\": \"PREMIUM_VIP\",\n  \"token\": \"Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...\"\n}");
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
                    File file = new File(cacheDir, "app_debug_log.txt");
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                        writer.write("===== 宿主 App 全量日志调试缓存 =====\n");
                        writer.write("[2026-07-22 10:00:01] [INFO] App Application.onCreate() finished.\n");
                        writer.write("[2026-07-22 10:00:05] [DEBUG] ShowLog SDK 5.0.0 initialized successfully.\n");
                        writer.write("[2026-07-22 10:00:12] [WARN] Low memory warning intercepted.\n");
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
                return null;
            }

            @Override
            public boolean onDeleteHttpLog(@NonNull BaseShowData data) {
                // 模拟成功删除指定网络日志项
                if (data.getList() != null && !data.getList().isEmpty()) {
                    mMockHttpLogs.remove(data);
                }
                return true;
            }

            @Override
            public boolean onClearAllHttpLogs() {
                mMockHttpLogs.clear();
                return true;
            }

            @Override
            public void onOpenTextFile(@NonNull String filePath) {
            }

            @Override
            public void onConsoleShow() {
                Log.d("App", "控制台弹窗已打开");
            }

            @Override
            public void onConsoleDismiss() {
                Log.d("App", "控制台弹窗已关闭");
            }
        });
    }

    /**
     * 构造丰富多样的真实模拟网络日志数据集
     */
    private void initMockLogs() {
        long now = System.currentTimeMillis();

        // 1. 用户资料请求 (GET - 200 OK)
        String userProfileContent = "=== Request Header ===\n" +
                "GET /api/v2/user/profile HTTP/1.1\n" +
                "Host: api.geek.com\n" +
                "Authorization: Bearer eyJhbGciOiJIUzI1Ni... \n" +
                "User-Agent: GeekApp/5.0.0 (Android 12; Pixel 6)\n\n" +
                "=== Response Header ===\n" +
                "HTTP/1.1 200 OK\n" +
                "Content-Type: application/json; charset=utf-8\n" +
                "Cache-Control: no-cache\n\n" +
                "=== Response Body ===\n" +
                "{\n" +
                "  \"code\": 200,\n" +
                "  \"message\": \"success\",\n" +
                "  \"data\": {\n" +
                "    \"userId\": 886699,\n" +
                "    \"nickname\": \"极客狂热者\",\n" +
                "    \"avatar\": \"https://cdn.geek.com/avatars/886699.png\",\n" +
                "    \"gender\": 1,\n" +
                "    \"vipLevel\": 5,\n" +
                "    \"permissions\": [\"READ_LOG\", \"EXEC_CMD\", \"SHARE_DATA\"]\n" +
                "  }\n" +
                "}";
        mMockHttpLogs.add(new HttpLogData("https://api.geek.com/api/v2/user/profile", now - 3600000, userProfileContent, "200 OK", null, false, 0));

        // 2. 提交购物车订单 (POST - 200 OK)
        String orderPostContent = "=== Request Header ===\n" +
                "POST /api/v1/trade/order/create HTTP/1.1\n" +
                "Host: api.geek.com\n" +
                "Content-Type: application/json; charset=utf-8\n\n" +
                "=== Request Body ===\n" +
                "{\n" +
                "  \"cartIds\": [1001, 1002, 1005],\n" +
                "  \"couponId\": \"CPN_2026_JULY_88\",\n" +
                "  \"payType\": \"WECHAT_PAY\",\n" +
                "  \"addressId\": 5501\n" +
                "}\n\n" +
                "=== Response Body ===\n" +
                "{\n" +
                "  \"code\": 200,\n" +
                "  \"message\": \"订单创建成功\",\n" +
                "  \"data\": {\n" +
                "    \"orderNo\": \"ORD2026072217430099182\",\n" +
                "    \"totalAmount\": 299.00,\n" +
                "    \"payAmount\": 249.00,\n" +
                "    \"expireSeconds\": 900\n" +
                "  }\n" +
                "}";
        mMockHttpLogs.add(new HttpLogData("https://api.geek.com/api/v1/trade/order/create", now - 1800000, orderPostContent, "200 OK", null, false, 0));

        // 3. 弃用路由/资源未找到报错 (GET - 404 Not Found)
        String notFoundContent = "=== Request Header ===\n" +
                "GET /api/v1/legacy/config/v0 HTTP/1.1\n" +
                "Host: api.geek.com\n\n" +
                "=== Response Header ===\n" +
                "HTTP/1.1 404 Not Found\n" +
                "Content-Type: application/json\n\n" +
                "=== Response Body ===\n" +
                "{\n" +
                "  \"code\": 404,\n" +
                "  \"error\": \"ResourceNotFound\",\n" +
                "  \"message\": \"The requested endpoint /api/v1/legacy/config/v0 was not found on this server.\"\n" +
                "}";
        mMockHttpLogs.add(new HttpLogData("https://api.geek.com/api/v1/legacy/config/v0", now - 900000, notFoundContent, "404 Not Found", null, false, 0));

        // 4. 服务器支付服务内部崩塌异常 (POST - 500 Internal Server Error)
        String serverErrorContent = "=== Request Header ===\n" +
                "POST /api/v1/payment/checkout HTTP/1.1\n" +
                "Host: pay.geek.com\n\n" +
                "=== Request Body ===\n" +
                "{\n" +
                "  \"orderNo\": \"ORD2026072217430099182\",\n" +
                "  \"amount\": 249.00\n" +
                "}\n\n" +
                "=== Response Body ===\n" +
                "{\n" +
                "  \"code\": 50001,\n" +
                "  \"error\": \"PaymentGatewayTimeout\",\n" +
                "  \"message\": \"Third-party payment channel timeout after 5000ms. Transaction rolled back.\"\n" +
                "}";
        mMockHttpLogs.add(new HttpLogData("https://pay.geek.com/api/v1/payment/checkout", now - 300000, serverErrorContent, "500 Internal Server Error", null, false, 0));

        // 5. 抓取离线埋点数据 (PUT - 200 OK)
        String analyticsContent = "=== Request Header ===\n" +
                "PUT /api/v3/analytics/batch HTTP/1.1\n" +
                "Host: stat.geek.com\n\n" +
                "=== Request Body ===\n" +
                "{\n" +
                "  \"events\": [\n" +
                "    {\"name\": \"page_view\", \"page\": \"MainActivity\", \"timestamp\": " + now + "},\n" +
                "    {\"name\": \"click_btn\", \"target\": \"open_console\", \"timestamp\": " + (now + 10) + "}\n" +
                "  ]\n" +
                "}\n\n" +
                "=== Response Body ===\n" +
                "{\n" +
                "  \"code\": 200,\n" +
                "  \"uploaded\": 2\n" +
                "}";
        mMockHttpLogs.add(new HttpLogData("https://stat.geek.com/api/v3/analytics/batch", now - 60000, analyticsContent, "200 OK", null, false, 0));

        // 6. 推送消息模拟日志
        mMockPushLogs.add(new PushData(now - 1200000, "系统通知：新版本 v5.0.0 已上线\n全新零隐形配置 SDK 5.0.0 震撼发布，欢迎升级体验！"));
        mMockPushLogs.add(new PushData(now - 600000, "营销活动：极客季限时特惠\n恭喜您获得极客专享 88 元无门槛优惠券！"));
    }
}
