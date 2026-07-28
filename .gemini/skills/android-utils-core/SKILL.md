---
name: android-utils-core
description: 当任务涉及 Android 通用工具封装，如 Toast、JSON、时间、dp/px 转换、日志、图片加载或点击防抖时使用。
---

## 一、适用场景

- 当任务涉及 Android 通用工具层封装，如 Toast、JSON、时间、dp/px 转换、日志、图片加载或点击防抖时使用。
- 优先复用项目现有工具能力；仅在缺少统一工具层时参考本 skill 的默认实现。

## 二、默认实现与落点

- 可参考 `android-utils-core/AppUtils.kt` 作为通用工具层默认实现。
- 默认落点可为 `utils/AppUtils.kt`；如项目已有工具包结构，优先遵循项目现有目录。
- 如果业务项目没有 `utils` 包，则先创建 `utils/`。

---

## 三、功能模块索引

- `ContextProvider`: 全局 Application Context 提供者
- `Toast 扩展`: show() / showCenter() / showCenterDebug()
- `JSON 工具`: Any.json() / String.fromJson() / copyFromJson() / formatJson()
- `时间格式化`: Long.format() / formatMillisToMmSs() / formatHMS() / formatM() / formatH()
- `距离格式化`: Long.formatKm()
- `DP/PX 转换`: dp2px() / dp2pxInt() / dpToPx()
- `资源获取`: getResString() / getResDimension() / getResColor()
- `SpannableString`: spannable() / spannableColor()
- `剪切板 & 设备`: copyToClipboard() / loadAndroidId()
- `文件工具`: FileUtils (getCacheDir / getFilesDir / delete / calculateSize / formatFileSize)
- `点击防抖`: setOnClickListenerThrottle() / throttleClick()
- `权限设置页`: openAppSettings()
- `LiveData / Flow`: BaseNotStickyLiveData / createNonStickyLiveData() / Flow.observe()
- `协程工具`: launchTryViewModelScope() / launchTryViewModelScopeError() / appScope
- `图片加载`: ImageView.loadImage() / AppImage() (Compose)
- `日志系统`: logd() / loge() / logHttp() / LogManager
- `全局异常捕获`: CrashHandler

---

## 四、所需 Gradle 依赖

`AppUtils.kt` 依赖 Gson、Glide、Lifecycle、Coroutines 等库（CrashHandler 已改用 FileUtils，无需 MMKV）。处理依赖时遵循以下策略：

1. **优先复用**：项目中已有的依赖直接使用，不重复添加；
2. **查官方最新稳定版**：项目缺少某个依赖时，实时查询该库的官方最新稳定版本号后再添加，**禁止从历史记录或自身知识库中直接使用版本号**；
3. **找不到时询问用户**：若无法确认官方最新稳定版本，须先告知用户并询问是否需要添加该依赖及使用哪个版本，**不得擅自填写版本号**。

---

## 五、Application 初始化

在业务项目的 `Application.onCreate()` 中调用以下初始化方法：

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 必须第一个初始化
        ContextProvider.init(this)

        // 可选：文件日志（传入日志存放路径）
        LogManager.init(this, getExternalFilesDir("logs")?.absolutePath ?: filesDir.absolutePath)

        // 可选：全局 Crash 捕获（日志写入 filesDir/crash_logs/，无需 MMKV）
        CrashHandler.init()
    }
}
```

---

## 六、落地方式

### 6.1 检查项目结构

先检查业务项目中是否已有：

- `utils` 包
- `AppUtils.kt`

### 6.2 默认处理方式

- 如果已有 `utils` 包，直接复用
- 如果没有 `utils` 包，则创建 `utils/`
- 如果没有 `AppUtils.kt`，则创建
- 如果已有 `AppUtils.kt`，则进入冲突处理

### 6.3 复制与适配

1. 以 `android-utils-core/AppUtils.kt` 为来源复制到业务项目
2. **替换包名**：将第 3 行 `package com.xxx.app.base.utils` 修改为业务项目实际包名（如 `package com.mycompany.myapp.utils`）
3. **确认日志开关**：`BuildConfig.DEBUG` 需要业务项目 `BuildConfig` 已生成，如编译报错可临时改为 `true`（Debug）或 `false`（Release）
4. 仅根据业务项目实际资源名、导入路径做必要调整
5. 不再按函数逐个筛选、拆分或重新组织

---

## 七、冲突处理

如果业务项目中已经存在同名文件或同名函数：

- 先列出冲突清单
- 说明冲突项来自哪里
- 让用户选择以下处理方式：
  - 保留业务项目现有版本
  - 使用本技能版本覆盖
  - 合并后继续

在用户确认前，不得直接覆盖同名实现。

---

## 八、输出要求

完成后应明确告知：

- 新增或更新了哪些文件
- 是否创建了 `utils` 包
- `AppUtils.kt` 是否已复制完成
- 哪些同名冲突被发现
- 用户最终选择了什么处理方式
