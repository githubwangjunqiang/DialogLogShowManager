/*  NetBare - An android network capture and injection library.
 *  Copyright (C) 2018-2019 Megatron King
 *  Copyright (C) 2018-2019 GuoShi
 *
 *  NetBare is free software: you can redistribute it and/or modify it under the terms
 *  of the GNU General Public License as published by the Free Software Found-
 *  ation, either version 3 of the License, or (at your option) any later version.
 *
 *  NetBare is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 *  PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with NetBare.
 *  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.megatronking.netbare;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.pm.ServiceInfo;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import androidx.annotation.NonNull;

import com.github.megatronking.netbare.ssl.SSLEngineFactory;

/**
 * Base class for NetBare services.
 * <p>
 * NetBare service is an implement of {@link VpnService}, it establishes a vpn connection to
 * route incoming and outgoing net packets. The NetBare service are forced to display a notification
 * due to intercepting packets raises huge security concerns.
 * </p>
 * <P>
 * The NetBare service is managed by {@link NetBare}, and you can use {@link NetBareListener} to
 * observe the state.
 * </P>
 *
 * @author Megatron King
 * @since 2018-10-08 21:09
 */
public abstract class NetBareService extends VpnService {

    /**
     * Start capturing target app's net packets.
     */
    public static final String ACTION_START =
            "com.github.megatronking.netbare.action.Start";

    /**
     * Stop capturing target app's net packets.
     */
    public static final String ACTION_STOP =
            "com.github.megatronking.netbare.action.Stop";

    /**
     * The identifier for this notification as per
     * {@link NotificationManager#notify(int, Notification)}; must not be 0.
     *
     * @return The identifier
     */
    protected abstract int notificationId();

    /**
     * A {@link Notification} object describing what to show the user. Must not be null.
     *
     * @return The Notification to be displayed.
     */
    @NonNull
    protected abstract Notification createNotification();

    private NetBareThread mNetBareThread;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }
        String action = intent.getAction();
        if (ACTION_START.equals(action)) {
            try {
                // 为什么这样做：前台提升必须先于证书、代理和 VPN 线程初始化，避免耗时初始化触发系统的前台服务启动超时。
                promoteToForeground();
                startNetBare();
            } catch (RuntimeException exception) {
                stopNetBare();
                stopForegroundCompat();
                stopSelf();
                // 为什么这样做：只兜住系统前台服务和配置启动异常，Error 仍交给运行时处理，避免掩盖进程级故障。
                NetBare.get().notifyServiceStartFailed(exception);
            }
        } else if (ACTION_STOP.equals(action)) {
            stopNetBare();
            stopForegroundCompat();
            stopSelf();
        } else {
            stopSelf();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopNetBare();
        stopForegroundCompat();
    }

    private void startNetBare() {
        // Terminate previous service.
        stopNetBare();

        NetBareConfig config = NetBare.get().getConfig();
        if (config == null) {
            throw new IllegalArgumentException("Must start NetBareService with a " +
                    "NetBareConfig");
        }

        NetBareLog.i("Start NetBare service!");
        SSLEngineFactory.updateProviders(config.keyManagerProvider, config.trustManagerProvider);
        mNetBareThread = new NetBareThread(this, config);
        mNetBareThread.start();
    }

    private void stopNetBare() {
        if (mNetBareThread == null) {
            return;
        }
        NetBareLog.i("Stop NetBare service!");
        NetBareThread stoppingThread = mNetBareThread;
        stoppingThread.interrupt();
        try {
            // 为什么这样做：动态改包名时必须等待旧虚拟网卡完成释放，避免新旧线程交叉回调导致状态倒退或描述符泄漏。
            stoppingThread.join(1_000L);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
        mNetBareThread = null;
    }

    /**
     * 把服务提升到与 VPN 语义匹配的前台类型。
     * 为什么这样做：Android 14 起 target 34+ 必须同时声明并传入 systemExempted 类型，旧系统继续使用兼容重载。
     */
    private void promoteToForeground() {
        Notification notification = createNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                    notificationId(),
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
            );
            return;
        }
        startForeground(notificationId(), notification);
    }

    /**
     * 移除前台通知并兼容旧系统。
     * 为什么这样做：新常量能明确表达移除通知，版本分支则避免低版本运行时访问不存在的 API。
     */
    @SuppressWarnings("deprecation")
    private void stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            return;
        }
        stopForeground(true);
    }

}
