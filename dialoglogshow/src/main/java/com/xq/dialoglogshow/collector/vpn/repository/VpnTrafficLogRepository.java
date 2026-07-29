package com.xq.dialoglogshow.collector.vpn.repository;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.xq.dialoglogshow.ShowLog;
import com.xq.dialoglogshow.model.VpnTrafficLogModel;
import com.xq.dialoglogshow.storage.entity.VpnTrafficLogEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * VPN 日志仓库
 *
 * 为什么这样做：
 * 1. 实时日志展示与历史查询共用一套数据源，但一个偏内存快照、一个偏数据库检索，仓库层正好适合把两种读取规则收口。
 * 2. Step 4 先完成“写内存 + 异步落库 + 按天查询”的最小闭环，后续协议解析再变复杂也不需要改调用方式。
 */
public class VpnTrafficLogRepository {

    private static final int MAX_MEMORY_LOG_COUNT = 200;
    private static final long HISTORY_KEEP_DURATION_MS = 7L * 24L * 60L * 60L * 1000L;

    private static volatile VpnTrafficLogRepository sInstance;

    private final CopyOnWriteArrayList<VpnTrafficLogModel> mMemoryLogs = new CopyOnWriteArrayList<>();
    private final ExecutorService mDatabaseExecutor = Executors.newSingleThreadExecutor();
    private final VpnTrafficLogMapper mMapper = new VpnTrafficLogMapper();
    /**
     * 为什么这样做：新增公开异步查询后，查询线程可能和单线程写入队列同时首次访问数据库，volatile 配合锁可避免重复创建 Store。
     */
    private volatile VpnTrafficLogStore mTrafficLogStore;

    private VpnTrafficLogRepository() {
    }

    @NonNull
    public static VpnTrafficLogRepository getInstance() {
        if (sInstance == null) {
            synchronized (VpnTrafficLogRepository.class) {
                if (sInstance == null) {
                    sInstance = new VpnTrafficLogRepository();
                }
            }
        }
        return sInstance;
    }

    private synchronized void ensureStore() {
        if (mTrafficLogStore != null) {
            return;
        }
        Context application = ShowLog.getApplicationInternal();
        if (application == null) {
            throw new IllegalStateException("ShowLog application is null, repository cannot be initialized.");
        }
        mTrafficLogStore = new VpnTrafficLogStore(application);
    }

    /**
     * 追加日志
     */
    public void appendLog(@NonNull VpnTrafficLogModel model) {
        if (model.logId == null || model.logId.length() == 0) {
            return;
        }
        // 为什么这样做：NetBare 在一个会话内会多次回调请求/响应分片，仓库必须按 logId 做覆盖更新，
        // 否则同一条链路会在内存列表里出现多份重复记录，控制台会越来越乱。
        removeMemoryLogById(model.logId);
        while (mMemoryLogs.size() >= MAX_MEMORY_LOG_COUNT) {
            mMemoryLogs.remove(mMemoryLogs.size() - 1);
        }
        mMemoryLogs.add(0, model);
        VpnTrafficLogEntity entity = mMapper.toEntity(model);
        mDatabaseExecutor.execute(() -> {
            ensureStore();
            mTrafficLogStore.insertOrReplace(entity);
        });
    }

    /**
     * 获取实时内存快照
     */
    @NonNull
    public ArrayList<VpnTrafficLogModel> getRealtimeLogs() {
        return new ArrayList<>(mMemoryLogs);
    }

    /**
     * 获取指定 App 的实时内存快照。
     * 为什么这样做：多 App 模式下实时页和历史页必须使用相同包名语义，避免实时列表仍混入其它目标流量。
     */
    @NonNull
    public ArrayList<VpnTrafficLogModel> getRealtimeLogsByApp(
            @NonNull String appPackageName) {
        ArrayList<VpnTrafficLogModel> result = new ArrayList<>();
        for (VpnTrafficLogModel model : mMemoryLogs) {
            if (appPackageName.equals(model.appPackageName)) {
                result.add(model);
            }
        }
        return result;
    }

    /**
     * 按时间范围查询历史日志
     */
    @NonNull
    public ArrayList<VpnTrafficLogModel> queryLogsByRange(long startTime, long endTime) {
        ensureStore();
        List<VpnTrafficLogEntity> entityList = mTrafficLogStore.queryByTimeRange(startTime, endTime);
        ArrayList<VpnTrafficLogModel> modelList = new ArrayList<>();
        for (VpnTrafficLogEntity entity : entityList) {
            modelList.add(mMapper.toModel(entity));
        }
        return modelList;
    }

    /**
     * 按 App 与时间范围查询历史日志。
     */
    @NonNull
    public ArrayList<VpnTrafficLogModel> queryLogsByAppAndRange(
            @NonNull String appPackageName,
            long startTime,
            long endTime) {
        ensureStore();
        List<VpnTrafficLogEntity> entityList =
                mTrafficLogStore.queryByAppAndTimeRange(
                        appPackageName,
                        startTime,
                        endTime
                );
        ArrayList<VpnTrafficLogModel> modelList = new ArrayList<>();
        for (VpnTrafficLogEntity entity : entityList) {
            modelList.add(mMapper.toModel(entity));
        }
        return modelList;
    }

    /**
     * 删除单条日志
     */
    public void deleteLog(@Nullable VpnTrafficLogModel model) {
        if (model == null || model.logId == null) {
            return;
        }
        mMemoryLogs.remove(model);
        mDatabaseExecutor.execute(() -> {
            ensureStore();
            mTrafficLogStore.deleteByLogId(model.logId);
        });
    }

    /**
     * 清空指定日期的日志
     */
    public void clearLogsByDay(long timeInMillis) {
        VpnTrafficLogQueryHelper.TimeRange timeRange = VpnTrafficLogQueryHelper.buildDayRange(timeInMillis);
        clearLogsByRange(timeRange.startTime, timeRange.endTime);
    }

    /**
     * 清空指定时间范围日志
     */
    public void clearLogsByRange(long startTime, long endTime) {
        for (VpnTrafficLogModel model : new ArrayList<>(mMemoryLogs)) {
            if (model.timestamp >= startTime && model.timestamp < endTime) {
                mMemoryLogs.remove(model);
            }
        }
        mDatabaseExecutor.execute(() -> {
            ensureStore();
            mTrafficLogStore.deleteByTimeRange(startTime, endTime);
        });
    }

    /**
     * 清理历史日志
     */
    public void cleanupHistoryIfNeed() {
        long expiredTime = System.currentTimeMillis() - HISTORY_KEEP_DURATION_MS;
        mDatabaseExecutor.execute(() -> {
            ensureStore();
            mTrafficLogStore.deleteBefore(expiredTime);
        });
    }

    /**
     * 重置实时缓存
     */
    public void resetRealtimeCache() {
        mMemoryLogs.clear();
    }

    /**
     * 按日志 id 移除内存旧快照
     */
    private void removeMemoryLogById(@NonNull String logId) {
        for (VpnTrafficLogModel memoryLog : new ArrayList<>(mMemoryLogs)) {
            if (logId.equals(memoryLog.logId)) {
                mMemoryLogs.remove(memoryLog);
            }
        }
    }
}
