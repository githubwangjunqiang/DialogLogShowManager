package com.xq.logshowcore.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayout
import com.xq.logshowcore.R

/**
 * 调试面板的弹窗架子，后续在这个架子中填充具体的调试面板内容。
 */
class DebugConsoleDialog(context: Context) : BottomSheetDialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_debug_console)
        
        // 设置弹窗占据一定的高度比例
        window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT, 
            (context.resources.displayMetrics.heightPixels * 0.8).toInt()
        )

        initViews()
    }

    private fun initViews() {
        // 绑定关闭按钮
        findViewById<android.view.View>(R.id.iv_close)?.setOnClickListener {
            dismiss()
        }

        // 初始化 Tab 骨架
        val tabLayout = findViewById<TabLayout>(R.id.tab_layout)
        tabLayout?.apply {
            addTab(newTab().setText("网络日志"))
            addTab(newTab().setText("设备信息"))
            addTab(newTab().setText("文件缓存"))
            addTab(newTab().setText("自定义拓展"))
            
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    // TODO: 后续在这里处理 Fragment/View 切换逻辑
                }
                override fun onTabUnselected(tab: TabLayout.Tab?) {}
                override fun onTabReselected(tab: TabLayout.Tab?) {}
            })
        }
    }
}
