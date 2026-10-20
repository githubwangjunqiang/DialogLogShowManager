package com.xq.dialoglogshowmanager.ui.activity

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.annotation.Keep
import androidx.appcompat.app.AppCompatActivity
import com.xq.dialoglogshowmanager.R
import com.xq.dialoglogshowmanager.ui.screens.targetapp.TargetAppManagerIntent
import com.xq.dialoglogshowmanager.ui.screens.targetapp.TargetAppManagerScreen
import com.xq.dialoglogshowmanager.ui.screens.targetapp.TargetAppManagerViewModel

/**
 * 目标 App 管理页宿主。
 * 为什么这样做：Activity 只负责生命周期和系统跳转，列表与表单状态交给 ViewModel，Compose 只负责渲染。
 */
@Keep
class TargetAppManagerActivity : AppCompatActivity() {

    private val viewModel: TargetAppManagerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 为什么这样做：Activity 的 Compose 宿主自动绑定生命周期，页面离开后不会继续持有旧 UI。
        setContent {
            TargetAppManagerScreen(
                viewModel = viewModel,
                onOpenTargetApp = ::openTargetApp
            )
        }
    }

    override fun onStart() {
        super.onStart()
        // 为什么这样做：抓包状态依赖实时日志，回到前台时刷新才能显示最新判断。
        viewModel.handleIntent(TargetAppManagerIntent.PageStarted)
    }

    private fun openTargetApp(packageName: String) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent == null) {
                Toast.makeText(
                    this,
                    R.string.target_app_open_target_app_no_launcher,
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            // 为什么这样做：从管理页打开目标 App 后保留独立任务，用户返回时仍能回到管理页面。
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
            Toast.makeText(
                this,
                R.string.target_app_open_target_app_success,
                Toast.LENGTH_SHORT
            ).show()
        } catch (exception: RuntimeException) {
            val reason = exception.message ?: exception.javaClass.simpleName
            Toast.makeText(
                this,
                getString(R.string.target_app_open_target_app_failed, reason),
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
