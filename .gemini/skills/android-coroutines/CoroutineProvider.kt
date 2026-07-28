/**
 * 全局协程提供者
 * ⚠️ 包名需替换为项目实际包名（com.xxx.app → 实际包名）
 * 为非生命周期组件（工具类、单例管理器、Repository）提供协程作用域
 *
 * 使用场景：
 * - 工具类中需要执行异步操作（如 Toast、文件操作）
 * - 单例管理器需要后台任务（如缓存管理、日志上传）
 * - Repository 层（非 ViewModel 内）需要协程
 * - 全局监听器需要异步处理
 *
 * 禁止场景：
 * - Activity/Fragment → 使用 lifecycleScope
 * - ViewModel → 使用 viewModelScope
 * - Fragment 视图 → 使用 viewLifecycleOwner.lifecycleScope
 */

package com.xxx.app.base.coroutines

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 协程提供者单例
 * 提供全局可用的协程作用域（UI / IO / CPU）
 */
object CoroutineProvider {

    private const val TAG = "CoroutineProvider"

    /**
     * 全局协程异常处理器
     * 捕获未处理的协程异常，防止协程直接崩溃退出
     */
    private val errorHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "协程未捕获异常: ${throwable.message}", throwable)
        // 可接入 CrashHandler 记录
        // CrashHandler.recordException(throwable)
    }

    /** UI 作用域（主线程） */
    val uiScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main + errorHandler)

    /** IO 作用域（后台线程） */
    val ioScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + errorHandler)

    /** CPU 作用域（计算线程） */
    val cpuScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + errorHandler)

    /**
     * 取消所有全局协程
     * 通常仅在特定调试或重置场景下使用
     */
    fun cancelAll() {
        uiScope.cancel("CoroutineProvider cancelled")
        ioScope.cancel("CoroutineProvider cancelled")
        cpuScope.cancel("CoroutineProvider cancelled")
        Log.d(TAG, "所有全局协程已取消")
    }

    /**
     * 创建新的作用域（用于特定任务组）
     * @param dispatcher 调度器，默认 IO
     */
    fun createScope(dispatcher: CoroutineDispatcher = Dispatchers.IO): CoroutineScope {
        return CoroutineScope(SupervisorJob() + dispatcher + errorHandler)
    }

    /**
     * 安全启动 UI 协程
     * 自动包裹 try-catch，防止异常传播
     */
    fun launchUI(block: suspend CoroutineScope.() -> Unit): Job {
        return uiScope.launch {
            try {
                block()
            } catch (e: Exception) {
                Log.e(TAG, "UI 协程异常: ${e.message}", e)
            }
        }
    }

    /**
     * 安全启动 IO 协程
     * 自动包裹 try-catch，防止异常传播
     */
    fun launchIO(block: suspend CoroutineScope.() -> Unit): Job {
        return ioScope.launch {
            try {
                block()
            } catch (e: Exception) {
                Log.e(TAG, "IO 协程异常: ${e.message}", e)
            }
        }
    }

    /**
     * 安全启动 CPU 协程
     * 自动包裹 try-catch，防止异常传播
     */
    fun launchCPU(block: suspend CoroutineScope.() -> Unit): Job {
        return cpuScope.launch {
            try {
                block()
            } catch (e: Exception) {
                Log.e(TAG, "CPU 协程异常: ${e.message}", e)
            }
        }
    }
}
