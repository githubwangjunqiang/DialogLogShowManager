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

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.net.VpnService;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.github.megatronking.netbare.gateway.DefaultVirtualGatewayFactory;
import com.github.megatronking.netbare.gateway.VirtualGatewayFactory;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * NetBare is a single instance, we can use this class to config and manage the NetBare service.
 * The NetBare service is an implement class of {@link VpnService}, before starting this service,
 * should call {@link #prepare()} to check the vpn state.
 *
 * Start and stop the NetBare service:
 * <pre>
 * <code>
 *     NetBare.get().start(config);
 *     NetBare.get().stop();
 * </code>
 * </pre>
 *
 * @author Megatron King
 * @since 2018-10-07 09:28
 */
public final class NetBare {

    private static class Holder {

        private static final NetBare INSTANCE = new NetBare();

    }

    private final Set<NetBareListener> mListeners;

    private Application mApp;
    private NetBareConfig mNetBareConfig;
    /**
     * 为什么这样做：显式保存实际 Service 类型后，启动和停止都不再依赖包内隐式 Intent，可兼容 Android 高版本组件启动限制。
     */
    private Class<? extends NetBareService> mServiceClass;

    private boolean mAlive;

    public static NetBare get() {
        return Holder.INSTANCE;
    }

    private NetBare() {
        mListeners = new LinkedHashSet<>();
    }

    /**
     * Attach an application instance to NetBare. We recommend you to call this method in your
     * {@link Application} class.
     *
     * @param application The application instance.
     * @param debug Should print logs in console.
     * @return The single instance of NetBare.
     */
    public NetBare attachApplication(@NonNull Application application, boolean debug) {
        mApp = application;
        NetBareLog.setDebug(debug);
        return this;
    }

    /**
     * Attach an application and the concrete NetBare service class.
     * 为什么这样做：保留旧重载保证已有调用方兼容，新重载让 SDK 可以使用显式 Intent 安全管理服务。
     *
     * @param application The application instance.
     * @param debug Should print logs in console.
     * @param serviceClass The concrete NetBare service class.
     * @return The single instance of NetBare.
     */
    public NetBare attachApplication(@NonNull Application application,
                                     boolean debug,
                                     @NonNull Class<? extends NetBareService> serviceClass) {
        attachApplication(application, debug);
        mServiceClass = serviceClass;
        return this;
    }

    /**
     * Prepare to establish a VPN connection. This method returns {@code null} if the VPN
     * application is already prepared or if the user has previously consented to the VPN
     * application. Otherwise, it returns an {@link Intent} to a system activity. The application
     * should launch the activity using {@link Activity#startActivityForResult} to get itself
     * prepared.
     *
     * @return The intent to call using {@link Activity#startActivityForResult}.
     */
    public Intent prepare() {
        return VpnService.prepare(mApp);
    }

    /**
     * Start the NetBare service with your specific configuration. If the service is started,
     * {@link NetBareListener#onServiceStarted()} will be invoked.
     *
     * @param config The configuration for NetBare service.
     */
    public void start(@NonNull NetBareConfig config) {
        if (config.mtu <= 0) {
            throw new RuntimeException("Must set mtu in NetBareConfig");
        }
        if (config.address == null) {
            throw new RuntimeException("Must set address in NetBareConfig");
        }
        mNetBareConfig = config;
        Intent intent = createServiceIntent(NetBareService.ACTION_START);
        ContextCompat.startForegroundService(mApp, intent);
    }

    /**
     * Stop the NetBare service. If the service is started,
     * {@link NetBareListener#onServiceStopped()} will be invoked.
     */
    public void stop() {
        Intent intent = createServiceIntent(NetBareService.ACTION_STOP);
        if (mServiceClass != null) {
            // 为什么这样做：停止已运行服务不应再走后台 startService，否则 Android 8+ 可能把“停止动作”本身当成非法后台启动。
            mApp.stopService(intent);
            return;
        }
        mApp.startService(intent);
    }

    /**
     * Whether the NetBare service is alive or not.
     *
     * @return True if the service is alive, false otherwise.
     */
    public boolean isActive() {
        return mAlive;
    }

    /**
     * Register a callback to be invoked when the service state changes.
     *
     * @param listener The callback to register.
     */
    public void registerNetBareListener(NetBareListener listener) {
        mListeners.add(listener);
    }

    /**
     * Remove a previously installed service state callback.
     *
     * @param listener The callback to remove.
     */
    public void unregisterNetBareListener(NetBareListener listener) {
        mListeners.remove(listener);
    }

    /* package */ NetBareConfig getConfig() {
        return mNetBareConfig;
    }

    /* package */ VirtualGatewayFactory getGatewayFactory() {
        // Make sure the virtual gateway not be null.
        return mNetBareConfig.gatewayFactory == null ? DefaultVirtualGatewayFactory.create() :
                mNetBareConfig.gatewayFactory;
    }

    /* package */ void notifyServiceStarted() {
        mAlive = true;
        for (NetBareListener listener : new LinkedHashSet<>(mListeners)) {
            listener.onServiceStarted();
        }
    }

    /* package */ void notifyServiceStopped() {
        mAlive = false;
        for (NetBareListener listener : new LinkedHashSet<>(mListeners)) {
            listener.onServiceStopped();
        }
    }

    /* package */ void notifyServiceStartFailed(Throwable throwable) {
        mAlive = false;
        for (NetBareListener listener : new LinkedHashSet<>(mListeners)) {
            listener.onServiceStartFailed(throwable);
        }
    }

    /**
     * 构建服务 Intent。
     * 为什么这样做：新接入优先使用显式组件，旧调用方未提供 Service Class 时仍保留原行为，避免破坏 NetBare 兼容性。
     */
    private Intent createServiceIntent(@NonNull String action) {
        Intent intent = mServiceClass != null
                ? new Intent(mApp, mServiceClass)
                : new Intent(action).setPackage(mApp.getPackageName());
        intent.setAction(action);
        return intent;
    }

}
