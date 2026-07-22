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
            String message = "SDK Debug Status: " + isDebug + ", Title: " + title;
            Log.d(TAG, message);
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
        });
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