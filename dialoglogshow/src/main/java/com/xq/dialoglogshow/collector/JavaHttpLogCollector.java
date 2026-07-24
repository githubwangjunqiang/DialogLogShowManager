package com.xq.dialoglogshow.collector;

import android.app.Application;
import android.util.Log;

import com.xq.dialoglogshow.collector.model.AutoHttpLogModel;
import com.xq.dialoglogshow.collector.proxy.ProxySSLSocketFactory;
import com.xq.dialoglogshow.collector.proxy.ProxySocketImplFactory;
import com.xq.dialoglogshow.entity.HttpLogData;

import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

/**
 * 纯 Java 运行时网络日志自动收集器 (总控类与自动日志内存池)
 *
 * 为什么这样做：
 * 1. 作为自动收集功能的核心单例，负责注册全局 Socket/SSL 代理工厂。
 * 2. 维护线程安全的自动日志列表，内置 MAX_LOG_COUNT (200 条) FIFO 先进先出淘汰机制，防止堆内存溢出。
 * 3. 支持失败请求 (4xx/5xx) 的快速筛选与一键清空能力。
 *
 * Author: 王俊强 980766134@qq.com
 * Date: 2026/07/23
 */
public class JavaHttpLogCollector {

    private static final String TAG = "JavaHttpLogCollector";
    private static final int MAX_LOG_COUNT = 200; // 最多保留 200 条自动日志

    private static volatile JavaHttpLogCollector sInstance;
    private final CopyOnWriteArrayList<AutoHttpLogModel> mAutoLogList = new CopyOnWriteArrayList<>();
    private boolean mIsStarted = false;
    private SSLSocketFactory mOriginalSSLSocketFactory;

    private JavaHttpLogCollector() {
    }

    public static JavaHttpLogCollector getInstance() {
        if (sInstance == null) {
            synchronized (JavaHttpLogCollector.class) {
                if (sInstance == null) {
                    sInstance = new JavaHttpLogCollector();
                }
            }
        }
        return sInstance;
    }

    /**
     * 启动自动收集器，注入全局代理
     */
    public synchronized void start(Application application) {
        if (mIsStarted) {
            return;
        }
        mIsStarted = true;

        // 1. 尝试挂载 HTTP 80 端口 SocketImplFactory (带重复注册异常防护)
        try {
            Socket.setSocketImplFactory(new ProxySocketImplFactory());
        } catch (Throwable t) {
            Log.w(TAG, "SocketImplFactory already initialized by system or other SDKs, fallback to SSL proxy. " + t.getMessage());
        }

        // 2. 挂载 HTTPS 443 端口 SSLSocketFactory
        try {
            SSLSocketFactory defaultFactory = HttpsURLConnection.getDefaultSSLSocketFactory();
            if (!(defaultFactory instanceof ProxySSLSocketFactory)) {
                mOriginalSSLSocketFactory = defaultFactory;
                HttpsURLConnection.setDefaultSSLSocketFactory(new ProxySSLSocketFactory(defaultFactory));
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to set default SSLSocketFactory", t);
        }
    }

    /**
     * 停止自动收集并恢复默认代理
     */
    public synchronized void stop() {
        if (!mIsStarted) {
            return;
        }
        mIsStarted = false;
        if (mOriginalSSLSocketFactory != null) {
            try {
                HttpsURLConnection.setDefaultSSLSocketFactory(mOriginalSSLSocketFactory);
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * 追加由代理捕获到的自动网络日志
     */
    public void addAutoLog(AutoHttpLogModel logModel) {
        if (logModel == null) {
            return;
        }
        Log.d(TAG, "addAutoLog: Successfully captured auto HTTP log -> " + logModel.getUrl());
        // 限制最大容量 200 条，超出淘汰最早的记录
        while (mAutoLogList.size() >= MAX_LOG_COUNT) {
            mAutoLogList.remove(0);
        }
        mAutoLogList.add(0, logModel); // 最新的排前面
    }

    /**
     * 获取自动抓取到的网络日志模型列表
     *
     * @param filterOnlyFailed true: 仅返回状态码为 4xx / 5xx / 错误的日志
     */
    public ArrayList<AutoHttpLogModel> getAutoHttpLogModels(boolean filterOnlyFailed) {
        ArrayList<AutoHttpLogModel> result = new ArrayList<>();
        for (AutoHttpLogModel log : mAutoLogList) {
            if (!filterOnlyFailed) {
                result.add(log);
            } else {
                String code = log.getStatusCode();
                if (code != null && (code.startsWith("4") || code.startsWith("5") || code.contains("Error") || code.contains("Exception"))) {
                    result.add(log);
                }
            }
        }
        return result;
    }

    /**
     * 清空自动抓取到的网络日志
     */
    public void clearAutoHttpLogs() {
        mAutoLogList.clear();
    }

    /**
     * 是否已开启自动收集
     */
    public boolean isStarted() {
        return mIsStarted;
    }
}
