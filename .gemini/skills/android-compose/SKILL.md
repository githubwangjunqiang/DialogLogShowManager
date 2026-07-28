---
name: android-compose
description: 当任务涉及 Jetpack Compose 页面、Composable、导航、主题、布局组件、屏幕适配或交互实现时使用。
---

## 一、导航与主题规则

- 导航必须使用类型安全路由，不得手写字符串路由。
- 页面必须通过 `NavHost` 统一组织。
- 底部导航必须与 `NavHost` 配合使用。
- 深链接必须接入路由系统。
- `ViewModel` 不得直接持有 `navController`。
- 导航通过 `Effect + navController` 由 UI 层执行。
- 页面必须使用 `AppTheme` 包裹入口。
- 所有颜色必须来自 `MaterialTheme.colorScheme`，不得硬编码颜色。
- 页面必须支持深色模式。

## 二、屏幕适配规则

- 页面必须接入 `WindowSizeClass` 判断屏幕档位。
- 大屏页面必须限制内容宽度。
- 大屏页面必须使用居中布局策略。
- 页面根布局必须预留适配入口。

相关实现见 [compose-screen-adaptation.md](compose-screen-adaptation.md)。

## 三、点击防抖规则

- 所有可点击区域必须使用防抖。
- 区域点击用 `Modifier.throttleClick`。
- 按钮点击用 `rememberThrottleOnClick`。

相关实现见 [ComposeClickThrottle.kt](ComposeClickThrottle.kt)。

## 四、性能与数据流规则

- `LazyColumn` 必须提供 `key`。
- 缓存计算：必用 `remember`。
- 过滤重组：必用 `derivedStateOf`。
- 自定义绘制再使用 `drawBehind`，普通背景优先使用 `Modifier.background()`。

## 五、Compose 组件布局规则

### 5.1 Text / BasicText / Button / TextButton / OutlinedButton / ElevatedButton / FilledTonalButton
- 用途：文本展示或文本按钮
- 默认高度：不主动写固定 `height`
- 仅当用户明确要求固定高度时，才允许写死 `height`
- 固定高度时必须：对 `Text` / `BasicText` 显式设置 `maxLines` 与 `overflow = TextOverflow.Ellipsis`；对按钮类组件的内部文案显式设置 `maxLines` 与 `overflow = TextOverflow.Ellipsis`
- 仅当限制为单行时，必须设置 `overflow = TextOverflow.Ellipsis`
- 多行：允许自然撑高
- 禁止：为对齐直接固定文本高度；固定高度但不配 `maxLines` 和 `overflow`

### 5.2 通用 Compose 布局约束
- 避免无意义 `Box`、`Column`
- 输入场景必须考虑软键盘遮挡布局问题；输入页补 `imePadding()` 或等价方案

## 六、页面编写约束

1. 先定义路由和主题。
2. 再接入屏幕适配。
3. 再编写页面结构。
4. 最后做收尾处理。

- 页面只负责布局、状态展示和事件接入；事件处理以 UI 层为主。

## 七、导航路由代码组织规范

### 7.1 Route 定义
- 路由必须使用 `@Serializable` 类型安全路由，禁止字符串路由
- 无参数路由 → `@Serializable data object`（注意：data object，不是普通 object）
- 有参数路由 → `@Serializable data class`
- 禁止将复杂对象（如 User、List）作为路由参数传递，只传 ID，目标页面通过 Repository 加载数据

```kotlin
// ✅ 正确
@Serializable data object Home
@Serializable data class ProductDetail(val productId: String)

// ❌ 禁止：传递复杂对象
@Serializable data class ProductDetail(val product: ProductBean)
```

### 7.2 Route 文件组织

单模块项目的推荐结构：

```
ui/
├── screens/               ← Compose + MVI 页面（按 feature 分包）
│   ├── home/
│   └── product/
├── navigation/            ← 导航定义
│   ├── Route.kt           ← 所有 @Serializable 路由类型定义，按 feature 分组注释
│   └── AppNavHost.kt      ← NavHost 组合入口，composable<Route> 映射
└── theme/
```

### 7.3 NavHost 写法规范

```kotlin
// AppNavHost.kt
@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Home) {
        composable<Home> {
            HomeScreen(onProductClick = { id ->
                navController.navigate(ProductDetail(id))
            })
        }
        composable<ProductDetail> { backStackEntry ->
            val route = backStackEntry.toRoute<ProductDetail>()
            ProductDetailScreen(productId = route.productId)
        }
    }
}
```

- ViewModel 不得持有 navController
- 页面通过 lambda 回调（`onNavigateToXxx`）触发导航，不直接调用 navController
- 导航行为由 UI 层通过 Effect 触发（与 MVI 保持一致）
