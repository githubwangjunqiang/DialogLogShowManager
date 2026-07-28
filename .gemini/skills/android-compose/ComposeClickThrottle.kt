/**
 * Compose 点击防抖工具
 * ⚠️ 包名需替换为项目实际包名
 * 例如：com.xxx.app → com.yourcompany.yourapp
 */
package com.xxx.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * 防抖点击 Modifier
 * 默认 500ms 内重复点击只响应第一次
 */
@Composable
fun Modifier.throttleClick(
    throttleTime: Long = 500L,
    onClick: () -> Unit
): Modifier {
    var lastClickTime by remember { mutableLongStateOf(0L) }
    val currentOnClick by rememberUpdatedState(onClick)
    return this.then(
        Modifier.clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick = {
                val now = System.currentTimeMillis()
                if (now - lastClickTime >= throttleTime) {
                    lastClickTime = now
                    currentOnClick()
                }
            }
        )
    )
}

/**
 * 防抖点击回调封装函数
 * 用于 Button 等组件的 onClick 参数
 */
@Composable
fun rememberThrottleOnClick(
    throttleTime: Long = 500L,
    onClick: () -> Unit
): () -> Unit {
    var lastClickTime by remember { mutableLongStateOf(0L) }
    val currentOnClick by rememberUpdatedState(onClick)
    return remember(throttleTime) {
        {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime >= throttleTime) {
                lastClickTime = currentTime
                currentOnClick()
            }
        }
    }
}
