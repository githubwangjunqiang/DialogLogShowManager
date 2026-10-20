package com.xq.dialoglogshow.core.custom;

import android.app.Dialog;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

/**
 * 自定义扩展面板提供者接口。
 * 为什么这样做：
 * SDK 只负责提供承载父布局与当前调试弹框对象，面板内部 UI、数据加载与交互逻辑全部交还业务方；
 * 业务方既可以使用 XML inflate，也可以纯代码构建视图，并可在任意业务时机主动关闭弹框。
 */
public interface CustomPanelProvider {

    /**
     * 在 SDK 提供的承载父布局中填充业务自定义 UI。
     * 为什么这样做：
     * 直接暴露父布局，业务方可以自由决定视图层级、绘制方式与事件处理；
     * 同时暴露弹框对象，业务方可在环境切换、登录失效等场景下主动调用 dismiss() 关闭调试弹框。
     *
     * @param parent SDK 提供的承载父布局，业务方应将自定义视图添加到该容器中
     * @param dialog 当前 ShowLog 调试弹框对象，业务方可调用 dismiss() / cancel() 控制关闭时机
     */
    void onCreateView(@NonNull ViewGroup parent, @NonNull Dialog dialog);

    /**
     * 面板销毁或调试弹框关闭时的清理回调（可选实现）。
     * 为什么这样做：
     * 业务面板可能注册了广播、定时器、网络请求或传感器监听，
     * 统一在此释放可以避免弹框关闭后继续持有 Activity 或业务资源造成泄漏。
     */
    default void onDestroyView() {
    }
}
