package com.xq.dialoglogshowmanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;

import com.xq.dialoglogshow.collector.vpn.VpnController;

/**
 * adb 自动联调广播入口
 *
 * 为什么这样做：
 * 1. 某些机型在前台 Activity 复用时不会稳定触发新的联调参数，因此需要一个不依赖界面生命周期的调试入口。
 * 2. 通过 adb broadcast 可以直接触发“装证书 / 启 VPN”等动作，便于无人值守联调和持续回归验证。
 */
public class DebugAutomationReceiver extends BroadcastReceiver {

    public static final String ACTION_AUTOMATION = "com.xq.dialoglogshowmanager.action.DEBUG_AUTOMATION";
    public static final String EXTRA_START_VPN = "start_vpn";
    public static final String EXTRA_INSTALL_CERT = "install_cert";

    private static final String TAG = "DebugAutomation";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!BuildConfig.DEBUG) {
            // 为什么这样做：adb 自动联调入口只允许存在于 debug 场景，避免正式包暴露可被外部触发的调试动作。
            return;
        }
        if (intent == null || !ACTION_AUTOMATION.equals(intent.getAction())) {
            return;
        }
        Context appContext = context.getApplicationContext();
        boolean startVpn = intent.getBooleanExtra(EXTRA_START_VPN, false);
        boolean installCert = intent.getBooleanExtra(EXTRA_INSTALL_CERT, false);
        Log.d(TAG, "收到 adb 广播联调参数: startVpn=" + startVpn + ", installCert=" + installCert);
        if (installCert) {
            installCertificate(appContext);
        }
        if (startVpn) {
            Log.d(TAG, "广播触发 VPN 启动");
            VpnController.getInstance().startVpn(appContext);
        }
    }

    /**
     * 为什么这样做：证书安装链路涉及“是否已装 / 文件是否已生成 / 是否真正发起安装”三种状态，必须明确打日志，否则真机联调时很难判断卡在哪一步。
     */
    private void installCertificate(@NonNull Context context) {
        boolean installed = VpnController.getInstance().isCertificateInstalled(context);
        Log.d(TAG, "准备安装证书: installed=" + installed);
        // 为什么这样做：部分 ROM 下证书安装页必须依赖 Activity 上下文才能稳定拉起，
        // 广播里直接用 applicationContext 调系统安装会出现“日志显示已调用，但界面不弹”的假成功。
        Intent installIntent = new Intent(context, MainActivity.class);
        installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        installIntent.putExtra("auto_install_cert", true);
        context.startActivity(installIntent);
        Log.d(TAG, "已转发到 MainActivity 执行证书安装");
    }
}
