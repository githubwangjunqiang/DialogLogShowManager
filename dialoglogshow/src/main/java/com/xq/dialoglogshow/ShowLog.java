package com.xq.dialoglogshow;

import android.app.Application;

import androidx.annotation.NonNull;

import com.xq.dialoglogshow.config.ShowLogProvider;
import com.xq.dialoglogshow.manager.ShowLogManager;

/**
 * Description: 极简 SDK 顶层门面类
 * 为什么这样做：提供极简的 ShowLog.init(...) 单行静态入口，方便开发者在 Application 中初始化，
 * 避免记复杂的内部 Manager 实例方法。
 *
 * Author: 王俊强 980766134@qq.com
 * Date: 2026/07/22
 */
public class ShowLog {

    /**
     * SDK 传统初始化入口（保持 100% 向上兼容）
     *
     * @param application 宿主 Application
     * @param provider    开发者实现的 ShowLogProvider 契约接口实例
     */
    public static void init(@NonNull Application application, @NonNull ShowLogProvider provider) {
        init(application, provider, false);
    }

    /**
     * SDK 双模式初始化入口
     * 为什么这样做：支持开发者通过布尔开关一键开启纯 Java 运行时的网络日志无感自动拦截，无需改动现有网络框架代码。
     *
     * @param application           宿主 Application
     * @param provider              开发者实现的 ShowLogProvider 契约接口实例
     * @param autoCollectNetworkLog true: 开启纯 Java 运行时网络日志自动拦截；false: 仅使用开发者手动传入
     */
    public static void init(@NonNull Application application, @NonNull ShowLogProvider provider, boolean autoCollectNetworkLog) {
        ShowLogManager.getInstance().init(application, provider);
        if (autoCollectNetworkLog) {
            com.xq.dialoglogshow.collector.JavaHttpLogCollector.getInstance().start(application);
        }
    }

    /**
     * 获取 SDK 内部代理包装后的 Provider（绝对安全防空）
     *
     * @return InternalDefaultShowLogProvider
     */
    @NonNull
    public static ShowLogProvider getProvider() {
        return ShowLogManager.getInstance().getProvider();
    }
}
