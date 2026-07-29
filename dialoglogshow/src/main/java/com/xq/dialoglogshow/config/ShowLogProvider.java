package com.xq.dialoglogshow.config;

import android.app.Dialog;
import android.content.Context;
import android.graphics.RectF;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.xq.dialoglogshow.entity.BaseShowData;
import com.xq.dialoglogshow.entity.HttpLogData;
import com.xq.dialoglogshow.entity.PushData;

import java.util.ArrayList;
import java.util.concurrent.Executor;

/**
 * Description: 全局 SDK 接入唯一契约接口。
 * 为什么这样做：采用全纯接口声明（无默认函数体），开发者实现该接口时 IDE 会 100% 强制标红并一键生成全量函数，
 * 开发者可一目了然地看到所有配置、悬浮窗 UI、数据源及回调函数，彻底消除隐形配置查文档的痛苦。
 *
 * Author: 王俊强 980766134@qq.com
 * Date: 2026/07/22
 */
public interface ShowLogProvider {

    // =========================================================================
    // 1. 全局基础配置 (Basic Configurations)
    // =========================================================================

    /**
     * 是否启用调试控制台
     * 为什么这样做：在 Release 生产环境中返回 false 可彻底禁用悬浮窗与面板，保证安全性。
     *
     * @return true 开启控制台，false 关闭
     */
    boolean isDebug();

    /**
     * 是否启用 VPN 网络日志能力。
     * 为什么这样做：把能力开关纳入统一 Provider 契约，可让唯一的双参数初始化入口保持稳定，同时强制宿主明确选择开启或关闭。
     *
     * @return true 启用 VPN 网络日志；false 不启用
     */
    boolean isVpnNetworkLogEnabled();

    /**
     * 缓存日志文件的根目录路径
     *
     * @return 目录绝对路径 (为 null 则使用 SDK 预设应用缓存目录)
     */
    @Nullable
    String getCacheDocPath();

    /**
     * 接口日志在列表视图中是否按时间倒序排列
     *
     * @return true 按时间倒序，false 按默认顺序
     */
    boolean isSortUrlForTime();

    /**
     * 后台读取与解析日志的异步线程池
     *
     * @return 线程池 Executor (为 null 则使用 SDK 默认单线程池)
     */
    @Nullable
    Executor getLoadExecutor();

    // =========================================================================
    // 2. 悬浮窗与控制台外观 UI (UI & Entrance Configs)
    // =========================================================================

    /**
     * 是否在悬浮窗上直接展示版本号
     *
     * @return true 展示，false 不展示
     */
    boolean isShowVersionCodeView();

    /**
     * 悬浮窗显示的自定义版本号文字
     *
     * @return 版本号文本 (如 "v1.0.4"，不需要展示传 null)
     */
    @Nullable
    String getShowVersionCodeText();

    /**
     * 悬浮窗默认尺寸与屏幕初始坐标
     *
     * @return 尺寸坐标 RectF (使用 SDK 预设尺寸传 null)
     */
    @Nullable
    RectF getDebugViewSize();

    /**
     * 自定义悬浮窗入口 View
     * 为什么这样做：当开发者需要完全自定义悬浮球视觉样式时提供注入点，非 null 时优先级最高。
     *
     * @param context 上下文
     * @return 自定义 View (使用默认悬浮球传 null)
     */
    @Nullable
    View getCustomEntranceView(@NonNull Context context);

    /**
     * 调试弹窗顶部标题文本
     *
     * @return 标题文本 (为 null 则默认显示 "Geek Console")
     */
    @Nullable
    String getConsoleTitle();

    /**
     * VPN 业务报文解密器
     * 为什么这样做：如果宿主对请求体/响应体又做了 AES 或自定义业务加密，SDK 本身无法猜测业务算法；
     * 这里给宿主一个统一出口按需提供解密器，不提供则继续展示原始报文。
     *
     * @return 业务报文解密器；不需要解密传 null
     */
    @Nullable
    VpnPayloadDecryptor getVpnPayloadDecryptor();

    // =========================================================================
    // 3. 6 大 Tab 数据源提供器 (Data Sources for 6 Tabs)
    // =========================================================================

    /**
     * Tab 1【接口数据】：根据时间范围拉取 HTTP 日志列表
     *
     * @param startTime 开始时间戳
     * @param endTime   结束时间戳
     * @return HTTP 日志列表 (无数据传 null 或空列表)
     */
    @Nullable
    ArrayList<HttpLogData> provideHttpLogs(long startTime, long endTime);

    /**
     * Tab 2【自定义设置】：提供开发者自定义配置页面的控制注入
     *
     * @param container 自定义 View 挂载容器
     * @param dialog    当前控制台 Dialog 实例
     */
    void setupCustomTab(@NonNull FrameLayout container, @NonNull Dialog dialog);

    /**
     * Tab 3【本地数据】：提供本地 KV/MMKV/SP 诊断数据列表
     *
     * @return 本地诊断数据列表 (无数据传 null)
     */
    @Nullable
    ArrayList<BaseShowData> provideKeyValueData();

    /**
     * Tab 4【Push数据】：提供消息推送 Push 日志列表
     *
     * @return 推送日志列表 (无数据传 null)
     */
    @Nullable
    ArrayList<PushData> providePushLogs();

    /**
     * Tab 5【个人信息】：提供用户登录信息、Token、环境诊断数据列表
     *
     * @return 个人/账号诊断数据列表 (无数据传 null)
     */
    @Nullable
    ArrayList<BaseShowData> provideUserInfo();

    /**
     * Tab 6【缓存文件夹】：提供全量日志导出的根文件路径
     *
     * @return 缓存文件夹路径 (默认传 null 使用 getCacheDocPath)
     */
    @Nullable
    String provideAllCacheDocPath();

    /**
     * Tab 7【其他信息】：在应用预制诊断信息（包名/版本/签名/时间等）下方追加的扩展数据
     *
     * @return 追加的诊断数据列表 (无数据传 null)
     */
    @Nullable
    ArrayList<BaseShowData> provideRestData();

    // =========================================================================
    // 4. 事件与响应回调 (Events & Handlers)
    // =========================================================================

    /**
     * 用户点击“删除”单条接口日志的回调
     *
     * @param data 要删除的日志数据
     * @return true 删除成功，false 删除失败或无需处理
     */
    boolean onDeleteHttpLog(@NonNull BaseShowData data);

    /**
     * 用户点击“清空数据”按钮的回调
     *
     * @return true 清空成功，false 清空失败或无需处理
     */
    boolean onClearAllHttpLogs();

    /**
     * 用户点击查看文本文件时的回调
     *
     * @param filePath 文本文件路径 (空实现则默认使用 SDK 内置 ReaderTxtDialog 打开)
     */
    void onOpenTextFile(@NonNull String filePath);

    /**
     * 控制台弹窗打开显示回调
     */
    void onConsoleShow();

    /**
     * 控制台弹窗关闭隐藏回调
     */
    void onConsoleDismiss();
}
