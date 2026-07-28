---
name: android-activity-start
description: 当任务涉及 Activity 跳转、Intent、putExtra、启动参数传递或 startActivity 封装时使用。
---

## 一、启动规则

- 新建 Activity 时，优先在 `companion object` 中提供统一的 `startActivity` 方法。
- 启动 Activity 必须使用此封装方法，禁止直接散写 `Intent(...)`。
- 参数通过方法入参和 `putExtra` 传递。
- 参数名使用 `private const val EXTRA_XXX` 统一定义。
- `Intent Flags` 默认模板为 `NEW_TASK | CLEAR_TOP | SINGLE_TOP`，具体是否保留按页面场景裁剪。
- 需返回值时，必须在目标 Activity 的 companion object 中封装 ActivityResultContract，禁止调用方散写启动契约。

---

## 二、最小启动模板

### 2.1 无参数启动

```kotlin
import android.content.Context
import android.content.Intent

class XxxActivity : XxxBaseActivity<XxxViewModel>() {

    companion object {
        fun startActivity(context: Context) {
            context.startActivity(Intent(context, XxxActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
        }
    }
}
```

### 2.2 带参数启动

```kotlin
import android.content.Context
import android.content.Intent

class XxxActivity : XxxBaseActivity<XxxViewModel>() {

    companion object {
        private const val EXTRA_ID = "extra_id"
        private const val EXTRA_NAME = "extra_name"

        fun startActivity(context: Context, id: String, name: String) {
            context.startActivity(
                Intent(context, XxxActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(EXTRA_ID, id)
                    putExtra(EXTRA_NAME, name)
                }
            )
        }
    }

    private fun getIntentData() {
        val id = intent.getStringExtra(EXTRA_ID).orEmpty()
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty()
    }
}
```

### 2.3 带可选参数启动

```kotlin
import android.content.Context
import android.content.Intent

class XxxActivity : XxxBaseActivity<XxxViewModel>() {

    companion object {
        private const val EXTRA_ITEM_ID = "extra_item_id"
        private const val EXTRA_MODE = "extra_mode"

        fun startActivity(
            context: Context,
            itemId: String? = null,
            mode: String = "create"
        ) {
            context.startActivity(
                Intent(context, XxxActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    itemId?.let { putExtra(EXTRA_ITEM_ID, it) }
                    putExtra(EXTRA_MODE, mode)
                }
            )
        }
    }
}
```

---

## 三、使用要求

- Activity 类名、ViewModel 类名、基类名、包名、参数名都要替换为项目实际名称。
- 示例代码只表示写法，不表示固定业务命名。
- 必须按场景显式补充启动需求，禁止滥用 Flags 作为默认模板。

---

## 四、启动后监听返回值

需要监听 Activity 关闭返回值时，优先使用 Activity Result API。

- 启动方只保留 launcher 注册和 `launch(...)` 调用。
- 目标 Activity 统一提供 `registerResultLauncher(...)`。
- 参数使用 `XxxArgs` 承载，结果使用 `XxxResult` 承载。
- 被启动 Activity 使用 `setResult(...)` 回传结果。
- 严禁使用废弃的 `startActivityForResult(...)`。

### 4.1 目标 Activity 的统一封装

```kotlin
import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract

class XxxEditActivity : XxxBaseActivity<XxxViewModel>() {

    companion object {
        private const val EXTRA_ITEM_ID = "extra_item_id"
        private const val EXTRA_MODE = "extra_mode"
        private const val EXTRA_RESULT_ID = "extra_result_id"

        fun registerResultLauncher(
            owner: ComponentActivity,
            callback: (XxxResult) -> Unit
        ): ActivityResultLauncher<XxxArgs> {
            return owner.registerForActivityResult(XxxContract()) { result ->
                callback(result)
            }
        }

        private class XxxContract : ActivityResultContract<XxxArgs, XxxResult>() {
            override fun createIntent(context: android.content.Context, input: XxxArgs): Intent {
                return Intent(context, XxxEditActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    input.itemId?.let { putExtra(EXTRA_ITEM_ID, it) }
                    putExtra(EXTRA_MODE, input.mode)
                }
            }

            override fun parseResult(resultCode: Int, intent: Intent?): XxxResult {
                if (resultCode != Activity.RESULT_OK || intent == null) {
                    return XxxResult.Cancelled
                }
                val resultId = intent.getStringExtra(EXTRA_RESULT_ID).orEmpty()
                return XxxResult.Success(resultId)
            }
        }
    }

    fun finishWithResult(resultId: String) {
        setResult(
            Activity.RESULT_OK,
            Intent().apply {
                putExtra(EXTRA_RESULT_ID, resultId)
            }
        )
        finish()
    }
}

data class XxxArgs(
    val itemId: String? = null,
    val mode: String = "create"
)

sealed interface XxxResult {
    data class Success(val resultId: String) : XxxResult
    data object Cancelled : XxxResult
}
```

### 4.2 启动方

```kotlin
import androidx.activity.result.ActivityResultLauncher

class XxxCallerActivity : XxxBaseActivity<XxxViewModel>() {

    private lateinit var editLauncher: ActivityResultLauncher<XxxArgs>

    override fun initView() {
        editLauncher = XxxEditActivity.registerResultLauncher(this) { result ->
            when (result) {
                is XxxResult.Success -> {
                    val resultId = result.resultId
                }
                XxxResult.Cancelled -> Unit
            }
        }
    }

    private fun openEditPage() {
        editLauncher.launch(
            XxxArgs(
                itemId = "1001",
                mode = "edit"
            )
        )
    }
}
```
