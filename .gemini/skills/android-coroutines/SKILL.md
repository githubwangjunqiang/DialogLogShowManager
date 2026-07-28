---
name: android-coroutines
description: 当任务涉及 Kotlin 协程、Flow、suspend、异步任务、线程切换或作用域管理时使用。
---

## 一、协程作用域与 ViewModel 扩展

协程作用域选型遵循生命周期绑定原则：

- 关联界面/视图/ViewModel 生命周期的协程：必须使用生命周期作用域。
- 无需绑定生命周期的全局协程：必须使用 CoroutineProvider。`CoroutineProvider` 的完整实现见 [CoroutineProvider.kt](CoroutineProvider.kt)。
- ViewModel 中启动协程时，优先使用 `launchTryViewModelScope()`。

ViewModel 中安全启动协程的扩展函数可直接复制到项目工具包下的工具类中：

```kotlin
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

fun ViewModel.launchTryViewModelScope(
    block: suspend CoroutineScope.() -> Unit
): Job {
    return viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 按项目需要统一处理异常
        }
    }
}

fun ViewModel.launchTryViewModelScopeError(
    catchBlock: (Exception) -> Unit = {},
    block: suspend CoroutineScope.() -> Unit
): Job {
    return viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            catchBlock(e)
        }
    }
}
```

---

## 二、挂起函数转换规则

当现有异步接口不是 `suspend`，而是回调、监听器或结果回传形式时，必须转换为可取消的挂起函数。

适用场景：
- 回调式 API
- 监听器模式
- 旧式异步接口
- 系统/平台回调
- 任何需要从“回调风格”接入“协程风格”的场景

`tryResumeYourself` 是项目工具包下的唯一扩展实现位置，挂起封装统一通过它恢复结果。

```kotlin
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine

@OptIn(InternalCoroutinesApi::class)
fun <T> CancellableContinuation<T>.tryResumeYourself(value: T) {
    this.tryResume(value)?.also { token ->
        this.completeResume(token)
    }
}

suspend fun getUser(id: String): User? = suspendCancellableCoroutine { continuation ->
    api.getUser(id) { user ->
        continuation.tryResumeYourself(user)
    }
}
```

禁止：
- `suspendCoroutine` + `resumeWith`
- 混用 `tryResumeYourself` 和 `resumeWith`
- 忽略取消检查后继续恢复结果

---

## 三、协程反模式与禁止事项

- 禁止在主线程使用 `runBlocking`。
- 禁止使用 `GlobalScope.launch { ... }`。
- 禁止使用 `Thread { ... }.start()`。
- 协程必须可控，必要时手动取消。
- 禁止使用 `suspendCoroutine` + `resumeWith`；必须使用 `suspendCancellableCoroutine` + `tryResumeYourself` 封装回调。

---

## 四、最小使用示例

```kotlin
CoroutineProvider.launchUI { toast("提示") }
CoroutineProvider.launchIO { downloadFile(url) }
CoroutineProvider.launchCPU { parseJson(text) }

val job = CoroutineProvider.launchIO {
    while (true) {
        ensureActive()
        task()
    }
}
job.cancel()
```
