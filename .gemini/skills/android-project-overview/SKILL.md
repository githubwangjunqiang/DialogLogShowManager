---
name: android-project-overview
description: 编写 kotlin/java/xml/compose 代码前，或在规划设计（如 plan 模式、制定计划、草案）时，必须强制加载本技能
---

## 一、默认技术路线

- 语言：Kotlin
- UI：Jetpack Compose，传统 View 仅用于存量页面
- 架构：MVI
- 异步：Coroutines + Flow
- 本地存储：MMKV / Room
- 网络：OkHttp 调用链／图片：统一图片加载封装
- 依赖管理与工程脚本：按项目现有结构接入

---

## 二、强制规范

### 2.1 统一架构
- 新页面默认 **Compose + MVI** 组织
- 优先复用现有基类、状态模型、工具函数，不重复创建冲突基础设施

### 2.2 中文注释
代码必须含**中文注释**，只说明**为什么这样做**，不重复解释代码表面含义。

**强制要求：**
- **类 / object / interface / sealed class** → 必须写 KDoc，说明职责、拆分原因或放在这里的原因
- **公共方法** → 必须写 KDoc；关键私有方法也必须补中文注释
- **关键属性 / 常量 / 配置项** → 必须写中文注释
- **关键逻辑代码行 / 代码块** → 必须写中文注释
- **sealed 子类型** → 必须说明状态含义和使用边界

**以下场景一律视为关键，禁止省略注释：**
- 业务语义明显的字段
- 环境差异处理（debug/release、sourceSet、渠道、开关）
- 兼容旧逻辑、兜底逻辑、空值保护
- 防抖、防重、缓存、时序控制
- magic number、默认值、特殊常量来源
- 成组配置、映射表、初始化参数块

**特别要求：**
- **类注释不能替代属性注释、配置块注释和关键代码行注释**
- 字段名再清楚，只要背后有业务原因、来源约束或维护风险，仍然必须写注释

**禁止：**
- 无意义注释（`// 初始化`、`// 点击事件`、`// 设置数据`）
- 只写“做了什么”，不写“为什么这样做”
- 只在类上写注释，类内关键属性、关键配置和关键逻辑不写注释

### 2.3 资源引用（禁止硬编码）

- **字符串**: 禁止用户可见文本硬编码；必须引用 `@string/xxx`，key 全英文小写 snake_case。
- **颜色**: 禁止 `#FF...` / `Color(0xFF...)`；必须引用主题色或 `@color/xxx`。
- **字号**: 禁止直接写数字 sp；必须引用 `@dimen/sp_xxx` 或 `MaterialTheme.typography`。
- **间距/尺寸**: 禁止直接写 dp；必须引用 `@dimen/dp_xxx` 或 dimenResource/spacing 系统。

**用户指定具体数值时仍须遵守资源规范：**

除非用户明确要求写死或不使用资源，否则用户指定的 `20dp`、`15sp`、颜色等仅代表视觉要求，仍必须使用项目资源：

- 尺寸、间距、字号 → `@dimen/`
- 颜色 → `@color/` 或主题资源
- 用户可见文本 → `@string/`

优先检索并复用已有资源；没有合适资源时，新增符合命名规范的资源。

字符串 key 命名：有英文直接用英文转 snake_case，无英文先翻译再转换；标点去掉，代码中动态拼接。

### 2.4 命名规范
- 类名：PascalCase／函数\变量：camelCase／常量：UPPER_SNAKE_CASE
- 缩进：4 空格／导入：显式导入
- 格式化：统一 ktlint 或 Android Studio 默认（Ctrl+Alt+L）

类名后缀约定：

- `Activity/Fragment/Service` -> `XxxActivity/Fragment/Service` (例: `LoginActivity`)
- `BroadcastReceiver` -> `XxxReceiver` (例: `BootReceiver`)
- `Application` -> `XxxApp` / `XxxApplication`
- `ViewModel` -> `XxxViewModel`
- `Repository` -> `XxxRepository`
- `网络 API` -> `XxxApi`
- `数据模型` -> `XxxBean` (与存量一致)
- `Adapter/自定义 View/Dialog` -> `XxxAdapter/View/Dialog`
- `工具类/管理器` -> `XxxUtils/XxxManager`
- `接口/抽象层` -> 描述性命名或 `XxxContract`
- `基类` -> `BaseXxx`
- `枚举` -> `XxxType/XxxStatus`

### 2.5 三方库版本
默认使用官方渠道可确认的最新稳定版；无法确认时暂停询问。项目已有稳定版本时优先兼容现状。

### 2.6 @Keep 规则

以下场景必须加 @Keep：
- Activity/Fragment/四大组件 → 类级
- WebView @JavascriptInterface 方法 → 方法级
- Gson/Moshi data class、Retrofit interface、Enum → 类级
- sealed class/interface 子类型 → 每个子类独立（父类不继承）
- ViewModel 自定义 Factory 构造器 → 构造器级（无 Hilt 时）

- 豁免 @Keep：@Composable 函数、纯业务类、注解类

### 2.7 包结构

**包名规则：** 全小写英文，单词间不用分隔符（`appconfig`、`baseui`）

**顶层结构：**

```
${projectPackage}/
├── base/       # 基础设施：基类、HTTP、缓存、工具、自定义 View
├── manager/    # 业务能力：第三方 SDK 封装，按厂商独立子包
└── ui/         # 界面层：所有 UI 相关代码
```

**新增 Compose + MVI 页面：** `ui/screens/{featureName}/` 下按 feature 分包：
`XxxScreen.kt / XxxViewModel.kt / XxxIntent.kt / XxxUiState.kt / XxxEffect.kt / components/`

**存量页面（传统 View + Fragment）位置：**

- `Activity`: `ui/activity/` | `Fragment`: `ui/fragment/`
- `ViewModel`: `ui/vm/` | `Adapter`: `ui/adapter/`
- `Dialog`: `ui/dialog/` | `自定义 View`: `ui/view/`
- `Entity`: `ui/entity/` | `Service`: `ui/service/`
- `Receiver`: `ui/receiver/` | `UI 工具`: `ui/utils/`

### 2.8 资源文件命名

**资源目录：** Vector 图标 → `drawable/`；位图（WebP）→ `drawable-xxhdpi/`；XML 图形（shape/selector/layer-list）→ `drawable/`；存量图片保持原位置不动。

**命名规则：**

- **Vector / 位图图标**: `ic_` 前缀 -> `drawable/` 或 `drawable-xxhdpi/`
- **背景 / 选择器**: `bg_` / `selector_` 前缀 -> `drawable/`
- **layout**: `activity_/fragment_/item_/dialog_/view_/layout_` 前缀 -> `layout/`
- **color**: 语义命名 (如 `text_primary`) -> `values/colors.xml`
- **dimen**: `dp_` / `sp_` 前缀 -> `values/dimens.xml`
- **动画**: `anim_` / `animator_` 前缀 -> `anim/` 或 `animator/`
- **raw**: 英文语义 -> `raw/`

新增图片优先级：Vector drawable → WebP（比 PNG 小 25-35%）→ PNG（WebP 不兼容时）

### 2.9 文件组织
- 一个 `.kt` 文件只含一个顶级类，文件名 = 类名。例外：非公用扩展函数、sealed 子类可同文件。
- Compose 文件内顺序：`@Preview` → 入口 Composable → 子组件（视觉从上到下）→ 辅助函数
- ViewModel 文件内顺序：`handleIntent()` → 业务方法（调用顺序）→ 私有辅助方法
- XML layout 属性顺序：`id → layout_width → layout_height → layout_xxx → 内容属性`

---

## 三、统一命名与实现基准

- ViewModel 基类：`MviBaseViewModel`／状态容器：`MviUiState<T>`／页面状态类型：`MviPageStatus`
- 状态更新入口：`setState { ... }`
- 禁止旧命名：`BaseViewModel` / `UIStatus` / `showLoadingView()` / `showErrorLayout()`
