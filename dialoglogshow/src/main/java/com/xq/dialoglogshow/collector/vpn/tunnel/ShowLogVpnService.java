package com.xq.dialoglogshow.collector.vpn.tunnel;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.github.megatronking.netbare.NetBareService;
import com.xq.dialoglogshow.R;

/**
 * ShowLog VPN 前台服务
 *
 * 为什么这样做：
 * 1. 当前项目已经决定只保留一种 VPN 正式实现，因此这里不再维护自研 TUN 读包线程，而是直接复用 NetBare 已验证的 VPN 服务主链路。
 * 2. 继续保留原有类名可以最大限度减少外部调用链与 Manifest 变更范围，让 SDK 对开发者仍然维持同一个入口语义。
 */
public class ShowLogVpnService extends NetBareService {

    private static final String NOTIFICATION_CHANNEL_ID = "showlog_vpn_channel";
    private static final int NOTIFICATION_ID = 10086;

    @Override
    protected int notificationId() {
        return NOTIFICATION_ID;
    }

    @NonNull
    @Override
    protected Notification createNotification() {
        createNotificationChannelIfNeed();
        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.baseline_insert_drive_file_24_doc)
                .setContentTitle(getString(R.string.show_sdk_vpn_notification_title))
                .setContentText(getString(R.string.show_sdk_vpn_notification_text))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void createNotificationChannelIfNeed() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager == null) {
            return;
        }
        NotificationChannel existingChannel = notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID);
        if (existingChannel != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.show_sdk_vpn_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        notificationManager.createNotificationChannel(channel);
    }
}
