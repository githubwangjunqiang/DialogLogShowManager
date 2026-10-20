package com.xq.dialoglogshowmanager.ui.screens.targetapp

import com.xq.dialoglogshowmanager.ui.entity.TargetAppStatusItem

/**
 * 目标 App 管理页的用户意图。
 * 为什么这样做：页面只派发用户动作，白名单写入和异步加载统一由 ViewModel 收口，避免交互分支散落在 Compose 回调中。
 */
sealed interface TargetAppManagerIntent {
    /** 为什么这样做：每次回到前台重新分析日志，证书与抓包状态才不会停留在旧结论。 */
    object PageStarted : TargetAppManagerIntent

    /** 为什么这样做：用户可主动重新读取安装列表和最新抓包状态。 */
    object RefreshClicked : TargetAppManagerIntent

    /** 为什么这样做：确认是唯一会改写 VPN 白名单的动作，防止“点选即生效”造成误操作。 */
    object ApplyClicked : TargetAppManagerIntent

    /** 为什么这样做：白名单卡必须始终打开已生效目标，不能因用户正在浏览另一项而跳错应用。 */
    object OpenActiveClicked : TargetAppManagerIntent

    /** 为什么这样做：底部操作优先打开用户当前点选的应用，便于立即制造待确认目标的抓包流量。 */
    object OpenSelectedClicked : TargetAppManagerIntent

    /** 为什么这样做：搜索只过滤本地已加载列表，不重复触发 PackageManager 查询以保证输入顺畅。 */
    data class SearchChanged(val value: String) : TargetAppManagerIntent

    /** 为什么这样做：列表点击仅更新待确认目标，使用户始终能看清详情后再提交。 */
    data class AppClicked(val item: TargetAppStatusItem) : TargetAppManagerIntent
}
