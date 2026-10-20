package com.xq.dialoglogshowmanager.ui.screens.targetapp

/**
 * 目标 App 管理页的一次性效果。
 * 为什么这样做：Toast 和外部 Activity 跳转不能随 Compose 重组重复执行，因此与持久 UI 状态分离。
 */
sealed interface TargetAppManagerEffect {
    /** 为什么这样做：白名单写入结果属于瞬时反馈，不应在页面重建后再次弹出。 */
    data class ShowMessage(val message: String, val isLong: Boolean) : TargetAppManagerEffect

    /** 为什么这样做：启动其他 App 是宿主 Activity 的系统职责，ViewModel 只提供安全的包名参数。 */
    data class OpenTargetApp(val packageName: String) : TargetAppManagerEffect
}
