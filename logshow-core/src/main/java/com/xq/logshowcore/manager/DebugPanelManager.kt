package com.xq.logshowcore.manager

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.xq.logshowcore.ui.FloatingEntranceView

/**
 * 调试面板的全局管理类，负责初始化和控制调试入口的显示/隐藏。
 */
class DebugPanelManager private constructor() : Application.ActivityLifecycleCallbacks {

    companion object {
        @Volatile
        private var instance: DebugPanelManager? = null

        @JvmStatic
        fun getInstance(): DebugPanelManager {
            return instance ?: synchronized(this) {
                instance ?: DebugPanelManager().also { instance = it }
            }
        }
    }

    private var application: Application? = null
    private var isShowRequested = false

    /**
     * 初始化调试管理器
     */
    fun init(app: Application) {
        this.application = app
        app.registerActivityLifecycleCallbacks(this)
    }

    /**
     * 显示调试悬浮窗入口
     */
    fun show() {
        isShowRequested = true
        // 暂不处理当前栈顶 Activity 直接弹出的复杂情况，
        // 简单实现在后续 Activity 切换时生效挂载
    }

    /**
     * 隐藏调试悬浮窗入口
     */
    fun hide() {
        isShowRequested = false
        FloatingEntranceView.detach()
    }

    // --- ActivityLifecycleCallbacks 骨架实现 ---

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {}

    override fun onActivityResumed(activity: Activity) {
        if (isShowRequested) {
            // 在页面显示时，挂载我们的悬浮入口
            FloatingEntranceView.attachTo(activity)
        }
    }

    override fun onActivityPaused(activity: Activity) {
        // 页面隐藏时，脱离挂载
        FloatingEntranceView.detach()
    }

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {}
}
