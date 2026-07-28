---
name: android-mvi
description: 当任务涉及 Android MVI 页面或 ViewModel 实现，以及 Intent、State、Effect、页面状态流转时使用。
---

# MVI 规则规范

---

## 一、MVI 核心规则

- Intent：只接收用户动作
- State/Data：只保存页面数据
- Effect：只处理一次性动作
- ViewModel：只做状态流转和业务协调

---

## 二、页面状态规则

### 2.1 页面状态类型

- `Content`：内容正常显示
- `FullScreenLoading`：全屏加载
- `DialogLoading`：弹窗加载
- `Error`：错误状态
- `Empty`：空状态

### 2.2 页面状态映射

- 页面状态必须通过 `MviPageStatus` 统一表达
- UI 层必须根据 `uiState.status` 切换界面
- Compose 页面使用 `when (uiState.status)` 分发状态
- 传统 View 页面可使用 `StatusViewLayout` 作为状态容器

传统 View 状态容器示例：

```xml
<StatusViewLayout android:id="@+id/statusView" ...>
    <!-- 正常内容放这里 -->
    <RecyclerView ... />
</StatusViewLayout>
```

```kotlin
statusView.showLoading()
statusView.showContent()
statusView.showEmpty(getString(R.string.empty_data)) { retryLoad() }
statusView.showError(getString(R.string.load_failed)) { retryLoad() }
```

完整实现见 [StatusViewLayout.kt](StatusViewLayout.kt)。

基础实现见 [MviInfrastructure.kt](MviInfrastructure.kt)。

---

## 三、状态容器与标准模板

### 3.1 状态容器

```kotlin
data class MviUiState<T>(
    val status: MviPageStatus = MviPageStatus.Content,
    val data: T
)
```

### 3.2 Intent / Data / Effect

```kotlin
sealed interface XxxIntent {
    data object LoadData : XxxIntent
    data object Refresh : XxxIntent
    data class ClickItem(val id: String) : XxxIntent
}

data class XxxData(
    val list: List<XxxItem> = emptyList(),
    val isLoading: Boolean = false
)

sealed interface XxxEffect {
    data class NavigateToDetail(val id: String) : XxxEffect
    data object NavigateBack : XxxEffect
}
```

### 3.3 ViewModel

```kotlin
class XxxViewModel(
    private val repository: XxxRepository
) : MviBaseViewModel<XxxIntent, XxxData, XxxEffect>(XxxData()) {

    override fun handleIntent(intent: XxxIntent) {
        when (intent) {
            XxxIntent.LoadData -> loadData()
            XxxIntent.Refresh -> loadData()
            is XxxIntent.ClickItem -> sendEffect(XxxEffect.NavigateToDetail(intent.id))
        }
    }

    private fun loadData() {
        launchTryViewModelScope {
            showFullScreenLoading()

            repository.getData()
                .onSuccess { list ->
                    showContent { data -> data.copy(list = list, isLoading = false) }
                }
                .onFailure { error ->
                    showError(error.message ?: "加载失败")
                }
        }
    }
}
```

### 3.4 页面层最小接入

- 页面层只负责收集 `uiState`、监听 `effect`、根据状态分发 UI。
- 不在 skill 中定义具体 UI 组件实现。

```kotlin
@Composable
fun XxxPage(viewModel: XxxViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is XxxEffect.NavigateToDetail -> Unit
                XxxEffect.NavigateBack -> Unit
            }
        }
    }

    when (uiState.status) {
        MviPageStatus.Content -> Unit
        is MviPageStatus.FullScreenLoading -> Unit
        is MviPageStatus.DialogLoading -> Unit
        is MviPageStatus.Error -> Unit
        is MviPageStatus.Empty -> Unit
    }
}
```

---

## 四、MVI 命名规范

### 4.1 Intent 命名

```kotlin
sealed interface XxxIntent {
    data object LoadData : XxxIntent          // 纯触发 → data object
    data object Refresh : XxxIntent
    data class ClickItem(val id: String) : XxxIntent   // 带数据 → data class
    data class TextChanged(val query: String) : XxxIntent
}
```

规则：

- **名称**: `XxxIntent`
- **子类型时态**: **动词过去时** (表达"用户已发生的动作")
- **纯触发事件**: 使用 `data object`
- **携带数据**: 使用 `data class`，参数用名词
- **命名示例**: `PageLoaded`, `RefreshSwiped`, `ItemClicked(id)`, `SubmitClicked`, `TextChanged(query)`, `BackPressed`
- **禁止命名**: `OnClick` (模糊), `DoSomething` (不是过去时), `Click` (不完整)

### 4.2 UiState / Data 命名

```kotlin
data class XxxData(
    val list: List<XxxItem> = emptyList(),    // 名词，描述数据
    val isLoading: Boolean = false            // boolean 用 is/has 前缀
)
```

规则：

- **名称**: 与页面状态配对 (`XxxUiState` 含 `status`，`XxxData` 为纯数据)
- **字段命名**: 名词/形容词 (描述"当前数据长什么样")
- **boolean 字段**: 统一用 `is`/`has`/`can` 前缀 (如 `isLoading`, `hasMoreData`, `canSubmit`)
- **默认值**: 所有字段必提供默认值，避免构造遗漏

### 4.3 Effect 命名

```kotlin
sealed interface XxxEffect {
    data class ShowToast(val message: String) : XxxEffect   // 动词原形开头
    data class NavigateTo(val route: String) : XxxEffect
    data object GoBack : XxxEffect
}
```

规则：

- **名称**: `XxxEffect`
- **子类型时态**: **动词原形**开头 (表达"要执行的一次性动作")
- **命名示例**: `ShowToast(message)`, `NavigateTo(route)`, `GoBack`, `OpenGallery`, `LaunchPayment(orderId)`
- **禁止命名**: `OnNavigation` (名词化模糊)

### 4.4 ViewModel 命名

```kotlin
class XxxViewModel(
    private val repository: XxxRepository
) : MviBaseViewModel<XxxIntent, XxxData, XxxEffect>(XxxData()) {

    override fun handleIntent(intent: XxxIntent) {
        // 唯一事件入口，内部通过 when 分发
    }
}
```

规则：

- **类名**: `XxxViewModel` (文件位置与页面同包)
- **入口方法**: 仅 `handleIntent()` 作为外部调用入口
- **内部方法**: 按业务动作命名 (`loadData()`, `submitForm()`, `refreshList()`)
- **init 块**: 不直接调业务方法，统一由 Intent 驱动

### 4.5 包位置约定

```
${projectPackage}/ui/vm/              ← 存量：ViewModel 集中放在 vm 包
${projectPackage}/ui/screens/home/    ← 新增：MVI+Compose 按 feature 分包
```

- **存量传统页面**（Activity/Fragment）：ViewModel 保持现有位置（`ui/vm/`）
- **新增 Compose + MVI 页面**：`Intent`、`UiState`、`Effect`、`ViewModel`、`Screen` 放到 `ui/screens/{featureName}/` 同一包内
