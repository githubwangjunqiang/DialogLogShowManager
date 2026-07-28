package com.xq.dialoglogshow.collector.vpn;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * VPN 运行状态中心
 *
 * 为什么这样做：
 * 1. VPN 权限页、控制台弹窗、后台 Service 都需要共享同一份状态，否则 UI 很容易显示旧值。
 * 2. 这里先做轻量内存态状态收口，后续即使补充观察者或持久化，也能保持调用入口不变。
 */
public class VpnStatusManager {

    /**
     * 为什么这样做：一期只保留有限状态集，避免 UI 与 Service 各自扩展状态导致不可控分支膨胀。
     */
    public enum Status {
        IDLE,
        PREPARE_REQUIRED,
        STARTING,
        RUNNING,
        STOPPED,
        ERROR
    }

    /**
     * VPN 流量快照
     *
     * 为什么这样做：Step 3 先用最小统计快照验证读包是否生效，比直接等到完整协议解析再看 UI 更容易定位链路问题。
     */
    public static class TrafficSnapshot {
        public final long packetCount;
        public final long tcpPacketCount;
        public final long udpPacketCount;
        public final int activeSessionCount;
        public final String lastSessionSummary;
        public final long updateTimeMs;

        public TrafficSnapshot(long packetCount,
                               long tcpPacketCount,
                               long udpPacketCount,
                               int activeSessionCount,
                               @Nullable String lastSessionSummary,
                               long updateTimeMs) {
            this.packetCount = packetCount;
            this.tcpPacketCount = tcpPacketCount;
            this.udpPacketCount = udpPacketCount;
            this.activeSessionCount = activeSessionCount;
            this.lastSessionSummary = lastSessionSummary;
            this.updateTimeMs = updateTimeMs;
        }
    }

    private static volatile VpnStatusManager sInstance;
    /**
     * 为什么这样做：控制台与后台服务跨线程更新状态时需要无锁安全广播，使用 CopyOnWriteArrayList 可以在调试场景下用更低心智成本换取稳定性。
     */
    private final CopyOnWriteArrayList<StatusListener> mStatusListeners = new CopyOnWriteArrayList<>();
    private volatile Status mCurrentStatus = Status.IDLE;
    private volatile String mErrorMessage;
    private volatile TrafficSnapshot mTrafficSnapshot = new TrafficSnapshot(0L, 0L, 0L, 0, null, 0L);

    private VpnStatusManager() {
    }

    @NonNull
    public static VpnStatusManager getInstance() {
        if (sInstance == null) {
            synchronized (VpnStatusManager.class) {
                if (sInstance == null) {
                    sInstance = new VpnStatusManager();
                }
            }
        }
        return sInstance;
    }

    /**
     * 更新当前状态
     */
    public void updateStatus(@NonNull Status status, @Nullable String errorMessage) {
        mCurrentStatus = status;
        mErrorMessage = errorMessage;
        for (StatusListener listener : mStatusListeners) {
            if (listener != null) {
                listener.onVpnStatusChanged(status, errorMessage);
            }
        }
    }

    /**
     * 获取当前状态
     */
    @NonNull
    public Status getCurrentStatus() {
        return mCurrentStatus;
    }

    /**
     * 获取当前错误说明
     */
    @Nullable
    public String getErrorMessage() {
        return mErrorMessage;
    }

    /**
     * 更新流量统计快照
     */
    public void updateTrafficSnapshot(@NonNull TrafficSnapshot trafficSnapshot) {
        mTrafficSnapshot = trafficSnapshot;
        for (StatusListener listener : mStatusListeners) {
            if (listener != null) {
                listener.onVpnTrafficSnapshotChanged(trafficSnapshot);
            }
        }
    }

    /**
     * 获取当前流量统计
     */
    @NonNull
    public TrafficSnapshot getTrafficSnapshot() {
        return mTrafficSnapshot;
    }

    /**
     * 注册状态监听
     */
    public void addStatusListener(@Nullable StatusListener listener) {
        if (listener == null || mStatusListeners.contains(listener)) {
            return;
        }
        mStatusListeners.add(listener);
    }

    /**
     * 取消状态监听
     */
    public void removeStatusListener(@Nullable StatusListener listener) {
        if (listener == null) {
            return;
        }
        mStatusListeners.remove(listener);
    }

    /**
     * VPN 状态监听器
     * 为什么这样做：一期先用最轻量的监听接口把 UI 刷新打通，后续再演进为更完整的观察者体系时不会破坏现有调用链。
     */
    public interface StatusListener {
        void onVpnStatusChanged(@NonNull Status status, @Nullable String errorMessage);

        void onVpnTrafficSnapshotChanged(@NonNull TrafficSnapshot trafficSnapshot);
    }
}
