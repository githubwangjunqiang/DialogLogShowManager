package com.xq.dialoglogshowmanager.ui.utils

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

/**
 * Compose 点击防抖扩展。
 * 为什么这样做：列表 item 和按钮都会触发状态或外部 App 操作，统一节流可以避免重组后重复提交。
 */
fun Modifier.throttleClick(
    intervalMillis: Long = 350L,
    onClick: () -> Unit
): Modifier = composed {
    val throttledClick = rememberThrottleOnClick(intervalMillis, onClick)
    clickable(onClick = throttledClick)
}

/** 使用 remember 保留时间窗口，同时通过 rememberUpdatedState 避免回调捕获旧状态。 */
@Composable
fun rememberThrottleOnClick(
    intervalMillis: Long = 500L,
    onClick: () -> Unit
): () -> Unit {
    val latestOnClick by rememberUpdatedState(onClick)
    var lastClickTime by remember { mutableLongStateOf(0L) }
    return remember(intervalMillis) {
        {
            val currentTime = SystemClock.elapsedRealtime()
            if (currentTime - lastClickTime >= intervalMillis) {
                lastClickTime = currentTime
                latestOnClick()
            }
        }
    }
}
