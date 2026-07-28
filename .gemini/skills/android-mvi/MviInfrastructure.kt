/**
 * MVI 基础设施 - ViewModel 基类与状态容器
 * ⚠️ 包名需替换为项目实际包名
 * 例如：com.xxx.app → com.yourcompany.yourapp
 */
package com.xxx.app.base.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * MVI 页面通用状态的密封接口
 * 用于表达页面的整体框架状态，并且每个状态都可以携带自定义参数
 */
sealed interface MviPageStatus {
    /** 内容正常显示 */
    data object Content : MviPageStatus

    /** 全屏加载中 */
    data class FullScreenLoading(val text: String? = null) : MviPageStatus

    /** 弹窗/覆盖式加载（不影响底层UI） */
    data class DialogLoading(val text: String? = null) : MviPageStatus

    /** 错误状态 */
    data class Error(val message: String, val icon: Int? = null, val cause: Throwable? = null) : MviPageStatus

    /** 空状态 */
    data class Empty(val message: String, val icon: Int? = null) : MviPageStatus
}

/**
 * 通用 MVI 页面状态容器 (最终版)
 *
 * @param T 页面专属的数据类型
 * @property status 页面的整体框架状态（加载、错误、内容等）
 * @property data 页面专属的、类型安全的数据
 */
data class MviUiState<T>(
    val status: MviPageStatus = MviPageStatus.Content,
    val data: T
)

/**
 * MVI 架构 ViewModel 基类 (最终版)
 *
 * @param I Intent (用户意图)
 * @param S State (页面专属的纯数据状态，如 UserListData)
 * @param E Effect (一次性副作用)
 */
abstract class MviBaseViewModel<I, S, E>(initialData: S) : ViewModel() {

    /** 页面 UI 状态，包裹在通用容器 MviUiState 中 */
    private val _uiState = MutableStateFlow(MviUiState(data = initialData))
    val uiState: StateFlow<MviUiState<S>> = _uiState.asStateFlow()

    /** 一次性副作用事件通道 */
    private val _effect = Channel<E>(Channel.BUFFERED)
    val effect: Flow<E> = _effect.receiveAsFlow()

    /** 唯一意图入口 */
    abstract fun handleIntent(intent: I)

    /**
     * 在 viewModelScope 中启动协程，自动处理异常
     * @param block 协程执行体
     */
    protected fun launchTryViewModelScope(block: suspend CoroutineScope.() -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showError(e.message ?: "未知错误")
            }
        }
    }

    /**
     * 更新状态的唯一入口，保证状态修改的可控性
     * @param reducer 一个接收当前状态并返回新状态的函数
     */
    protected fun setState(reducer: (MviUiState<S>) -> MviUiState<S>) {
        _uiState.update(reducer)
    }

    /** 发送一次性副作用事件 */
    protected fun sendEffect(effect: E) {
        _effect.trySend(effect)
    }

    // region 状态更新辅助函数

    /** 进入全屏加载状态 */
    protected fun showFullScreenLoading(text: String? = null) {
        setState { it.copy(status = MviPageStatus.FullScreenLoading(text)) }
    }

    /** 进入弹窗加载状态 */
    protected fun showDialogLoading(text: String? = null) {
        setState { it.copy(status = MviPageStatus.DialogLoading(text)) }
    }

    /** 进入错误状态 */
    protected fun showError(message: String, icon: Int? = null, cause: Throwable? = null) {
        setState { it.copy(status = MviPageStatus.Error(message, icon, cause)) }
    }

    /** 进入空状态 */
    protected fun showEmpty(message: String, icon: Int? = null) {
        setState { it.copy(status = MviPageStatus.Empty(message, icon)) }
    }

    /** 恢复到内容显示状态，并更新数据 */
    protected fun showContent(reducer: (S) -> S) {
        setState { it.copy(status = MviPageStatus.Content, data = reducer(it.data)) }
    }

    // endregion
}
