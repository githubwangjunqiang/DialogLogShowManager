package com.xq.dialoglogshow;

import android.app.Application;

import androidx.annotation.NonNull;

import com.xq.dialoglogshow.collector.vpn.VpnController;
import com.xq.dialoglogshow.collector.vpn.netbare.ShowLogNetBareManager;
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
     * SDK VPN 初始化入口
     * 为什么这样做：按照最新方案，第三个开关统一收敛为 VPN 自动网络日志能力，宿主只需要声明是否启用即可，
     * 后续控制台会自行处理权限申请、服务启动与状态展示，不再把实现细节暴露给开发者。
     *
     * @param application           宿主 Application
     * @param provider              开发者实现的 ShowLogProvider 契约接口实例
     * @param autoCollectNetworkLog true: 开启 VPN 自动网络日志能力；false: 仅使用开发者手动传入
     */
    public static void init(@NonNull Application application, @NonNull ShowLogProvider provider, boolean autoCollectNetworkLog) {
        // 为什么这样做：ShowLogManager 在注册生命周期回调时需要提前知道 VPN 能力是否开启，否则前后台巡检入口无法挂上。
        ShowLogManager.getInstance().setVpnNetworkLogEnabled(autoCollectNetworkLog);
        ShowLogManager.getInstance().init(application, provider);
        // 为什么这样做：NetBare 证书生成是异步过程，SDK 初始化时尽早附着 Application 并预生成证书，可以减少第一次点击启用时的等待。
        ShowLogNetBareManager.getInstance().init(application, provider.isDebug());
        // 为什么这样做：VPN 首次授权后，后续冷启动应由 SDK 自动恢复抓包能力，避免测试同学忘记手动开启导致关键日志丢失。
        if (autoCollectNetworkLog) {
            VpnController.getInstance().tryAutoRestoreVpn(application);
            return;
        }
        // 为什么这样做：只有在 VPN 已经运行时才主动下发停止指令，避免宿主本来就关闭 VPN 功能时仍在 Application 启动阶段多发一次无意义的 Service 调用。
        if (VpnController.getInstance().isVpnRunning()) {
            VpnController.getInstance().stopVpn(application);
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
