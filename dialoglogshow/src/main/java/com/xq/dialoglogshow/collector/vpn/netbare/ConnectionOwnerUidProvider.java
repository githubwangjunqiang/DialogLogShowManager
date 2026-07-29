package com.xq.dialoglogshow.collector.vpn.netbare;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Process;
import android.system.OsConstants;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.github.megatronking.netbare.NetBareUtils;
import com.github.megatronking.netbare.ip.Protocol;
import com.github.megatronking.netbare.net.Session;
import com.github.megatronking.netbare.net.UidProvider;

import java.net.InetSocketAddress;

/**
 * Android 10+ VPN 连接 UID 提供器。
 * 为什么这样做：Android Q 起无法继续读取 /proc/net，改用系统专门为活跃 VPN 暴露的连接归属 API，才能为多 App 日志提供可靠 UID。
 */
public final class ConnectionOwnerUidProvider implements UidProvider {

    private final ConnectivityManager mConnectivityManager;
    private final String mLocalVpnAddress;

    public ConnectionOwnerUidProvider(@NonNull Context context,
                                      @NonNull String localVpnAddress) {
        mConnectivityManager = ContextCompat.getSystemService(
                context.getApplicationContext(),
                ConnectivityManager.class
        );
        mLocalVpnAddress = localVpnAddress;
    }

    /**
     * 查询连接所属 UID。
     * 为什么这样做：低版本继续交给 NetBare 的 /proc 兜底，当前实现只在 API 29+ 且 TCP/UDP 五元组完整时调用系统能力。
     */
    @Override
    public int uid(Session session) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || mConnectivityManager == null) {
            return UID_UNKNOWN;
        }
        int protocol;
        if (session.protocol == Protocol.TCP) {
            protocol = OsConstants.IPPROTO_TCP;
        } else if (session.protocol == Protocol.UDP) {
            protocol = OsConstants.IPPROTO_UDP;
        } else {
            return UID_UNKNOWN;
        }
        try {
            InetSocketAddress localAddress = new InetSocketAddress(
                    mLocalVpnAddress,
                    NetBareUtils.convertPort(session.localPort)
            );
            InetSocketAddress remoteAddress = new InetSocketAddress(
                    NetBareUtils.convertIp(session.remoteIp),
                    NetBareUtils.convertPort(session.remotePort)
            );
            int uid = mConnectivityManager.getConnectionOwnerUid(
                    protocol,
                    localAddress,
                    remoteAddress
            );
            return uid == Process.INVALID_UID ? UID_UNKNOWN : uid;
        } catch (IllegalArgumentException | SecurityException exception) {
            // 为什么这样做：连接可能已结束或 VPN 尚未成为系统活跃实例，此时归属未知不应中断主抓包链路。
            return UID_UNKNOWN;
        }
    }
}
