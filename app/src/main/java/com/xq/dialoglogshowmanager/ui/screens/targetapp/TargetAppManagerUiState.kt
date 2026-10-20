package com.xq.dialoglogshowmanager.ui.screens.targetapp

import com.xq.dialoglogshowmanager.ui.entity.InstalledAppInfo
import com.xq.dialoglogshowmanager.ui.entity.TargetAppStatusItem

/**
 * 目标 App 管理页的完整渲染状态。
 * 为什么这样做：将“已生效的白名单”和“用户正在挑选的目标”分开保存，用户浏览列表时不会意外改动 VPN 配置。
 */
data class TargetAppManagerUiState(
    /** 为什么这样做：安装列表读取涉及 PackageManager 和日志库，显式状态能避免旧数据与加载提示混杂。 */
    val pageStatus: TargetAppManagerPageStatus = TargetAppManagerPageStatus.Loading,
    /** 为什么这样做：页面同时需要应用元信息和抓包状态，保留原始列表方便刷新后重建详情。 */
    val installedApps: List<InstalledAppInfo> = emptyList(),
    /** 为什么这样做：列表直接按状态项渲染，避免 Compose 层再按包名做多次关联查询。 */
    val statusItems: List<TargetAppStatusItem> = emptyList(),
    /** 为什么这样做：搜索词只影响列表可见范围，不能作为已确认白名单的依据。 */
    val searchKeyword: String = "",
    /** 为什么这样做：用户点选后先进入待确认态，只有确认按钮才会真正切换 VPN 白名单。 */
    val selectedPackageName: String? = null,
    /** 为什么这样做：详情卡要即时展示用户当前点选的 App，不依赖列表是否正好可见。 */
    val selectedAppInfo: InstalledAppInfo? = null,
    /** 为什么这样做：详情中的证书与抓包状态来自同一轮日志分析，必须和 App 元信息保持一致。 */
    val selectedStatusItem: TargetAppStatusItem? = null,
    /** 为什么这样做：这是 SDK 当前真正使用的目标，用于明确区分“已生效”与“待确认”。 */
    val activePackageName: String? = null,
    /** 为什么这样做：白名单卡展示已生效 App 的完整信息，即使它不在桌面应用列表中也能正常显示。 */
    val activeAppInfo: InstalledAppInfo? = null,
    /** 为什么这样做：白名单状态同样要基于实时日志展示，避免用户误把历史详情当成当前结论。 */
    val activeStatusItem: TargetAppStatusItem? = null,
    /** 为什么这样做：手动输入完整包名仍是兼容无桌面入口应用的兜底能力，需要单独给出校验反馈。 */
    val inputError: String? = null,
    /** 为什么这样做：加载失败需要在内容区提供可恢复的错误状态，而不是静默显示空列表。 */
    val errorMessage: String? = null
)

/**
 * 页面内容状态。
 * 为什么这样做：Compose 根据单一状态分支渲染，避免加载、空列表和错误提示互相覆盖。
 */
sealed interface TargetAppManagerPageStatus {
    /** 为什么这样做：首次读取应用与刷新期间禁止提交，防止用户基于不完整列表确认错误目标。 */
    object Loading : TargetAppManagerPageStatus

    /** 为什么这样做：已取得可供选择的应用列表。 */
    object Content : TargetAppManagerPageStatus

    /** 为什么这样做：设备没有可见桌面应用时应告知用户，而不是显示无意义的空白区域。 */
    object Empty : TargetAppManagerPageStatus

    /** 为什么这样做：保留失败信息和重试入口，避免一次 PackageManager 异常使页面不可恢复。 */
    object Error : TargetAppManagerPageStatus
}
