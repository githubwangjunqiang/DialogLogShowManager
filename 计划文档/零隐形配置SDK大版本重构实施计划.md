# 零隐形配置 SDK 大版本重构实施计划

## 1. 重构宗旨与设计哲学

* **零隐形配置与强提示**：开发者接入时不再需要翻阅任何文档找隐形属性，只要实现统一的 `ShowLogProvider` 接口，IDE 会 100% 强制标红并一键生成所有的配置、外观、数据源及回调函数。
* **彻底摆脱存量包袱**：大版本升级，彻底废弃旧的 `IShowLoadDataCallback` 与 `LogConfigData` 体系，集中收拢为一个契约接口。
* **统一的内部兜底代理模式 (Internal Default Provider Pattern)**：
  在 SDK 内部实现一个私有的默认提供者 `InternalDefaultShowLogProvider implements ShowLogProvider`。所有函数的默认缺省值（如默认缓存目录、线程池、空列表等）统一集中在此类中维护。SDK 消费数据时通过代理装饰器包装开发者传入的 `userProvider`，当方法返回 `null` 时自动触发默认兜底，既收拢了全局默认值定义，又确保 SDK 运行 100% 无空指针异常。

---

## 2. 核心架构设计

### 2.1 对外契约接口：`ShowLogProvider` (全纯接口，无默认函数体)

```java
package com.xq.dialoglogshow.config;

import android.content.Context;
import android.graphics.RectF;
import android.view.View;
import android.widget.FrameLayout;
import android.app.Dialog;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.xq.dialoglogshow.entity.BaseShowData;
import com.xq.dialoglogshow.entity.HttpLogData;
import com.xq.dialoglogshow.entity.PushData;
import java.util.ArrayList;
import java.util.concurrent.Executor;

public interface ShowLogProvider {
    // ==========================================
    // 1. 全局配置
    // ==========================================
    boolean isDebug();
    @Nullable String getCacheDocPath();
    boolean isSortUrlForTime();
    @Nullable Executor getLoadExecutor();

    // ==========================================
    // 2. 悬浮窗与控制台外观 UI
    // ==========================================
    boolean isShowVersionCodeView();
    @Nullable String getShowVersionCodeText();
    @Nullable RectF getDebugViewSize();
    @Nullable View getCustomEntranceView(@NonNull Context context);
    @Nullable String getConsoleTitle();

    // ==========================================
    // 3. 6 大 Tab 数据源提供器
    // ==========================================
    @Nullable ArrayList<HttpLogData> provideHttpLogs(long startTime, long endTime);
    void setupCustomTab(@NonNull FrameLayout container, @NonNull Dialog dialog);
    @Nullable ArrayList<BaseShowData> provideKeyValueData();
    @Nullable ArrayList<PushData> providePushLogs();
    @Nullable ArrayList<BaseShowData> provideUserInfo();
    @Nullable String provideAllCacheDocPath();
    @Nullable ArrayList<BaseShowData> provideRestData();

    // ==========================================
    // 4. 事件与响应回调
    // ==========================================
    boolean onDeleteHttpLog(@NonNull BaseShowData data);
    boolean onClearAllHttpLogs();
    void onOpenTextFile(@NonNull String filePath);
    void onShareFile(@NonNull Context context, @NonNull String filePath);
    void onConsoleShow();
    void onConsoleDismiss();
}
```

### 2.2 内部默认提供者与代理装饰器：`InternalDefaultShowLogProvider`

* **职责**：仅限 SDK 内部使用（包级私有），实现 `ShowLogProvider` 并为所有函数提供全量统一的缺省/兜底逻辑。
* **代理机制**：
  ```java
  public class InternalDefaultShowLogProvider implements ShowLogProvider {
      private final ShowLogProvider delegate;
      private final Context context;

      public InternalDefaultShowLogProvider(Context context, ShowLogProvider userProvider) {
          this.context = context.getApplicationContext();
          this.delegate = userProvider;
      }

      @Override
      public boolean isDebug() {
          return delegate != null && delegate.isDebug();
      }

      @Override
      public String getCacheDocPath() {
          if (delegate != null && delegate.getCacheDocPath() != null) {
              return delegate.getCacheDocPath();
          }
          return context.getCacheDir().getAbsolutePath();
      }

      @Override
      public ArrayList<HttpLogData> provideHttpLogs(long startTime, long endTime) {
          if (delegate == null) return new ArrayList<>();
          ArrayList<HttpLogData> list = delegate.provideHttpLogs(startTime, endTime);
          return list != null ? list : new ArrayList<>();
      }
      
      @Override
      public String getConsoleTitle() {
          if (delegate != null && delegate.getConsoleTitle() != null) {
              return delegate.getConsoleTitle();
          }
          return "Geek Console";
      }
      
      // ... 其余所有方法均在内部代理中实现全量安全防空与全局统一默认值
  }
  ```

---

## 3. 全量覆盖核对表

| 序号 | 细分配置/数据源/事件 | 覆盖的 `ShowLogProvider` 方法 | 缺省默认处理 (`InternalDefaultShowLogProvider`) |
| :--- | :--- | :--- | :--- |
| 1 | 调试控制台总开关 | `isDebug()` | 开发者返回的 boolean |
| 2 | 缓存文件根目录 | `getCacheDocPath()` | `context.getCacheDir().getAbsolutePath()` |
| 3 | 接口按时间倒序 | `isSortUrlForTime()` | `false` |
| 4 | 后台线程池 | `getLoadExecutor()` | `Executors.newSingleThreadExecutor()` |
| 5 | 悬浮窗版本号开关 | `isShowVersionCodeView()` | `false` |
| 6 | 悬浮窗版本号文本 | `getShowVersionCodeText()` | App 的 `versionName` |
| 7 | 悬浮窗尺寸与位置 | `getDebugViewSize()` | `new RectF(0, 0, 60dp, 60dp)` |
| 8 | 自定义悬浮窗 View | `getCustomEntranceView(context)`| `null`（使用默认悬浮球 View） |
| 9 | 控制台标题 | `getConsoleTitle()` | `"Geek Console"` |
| 10 | 接口日志列表 (Tab 1)| `provideHttpLogs(start, end)` | `new ArrayList<>()` |
| 11 | 自定义设置页 (Tab 2)| `setupCustomTab(container, dialog)`| 空实现 |
| 12 | 本地 Key-Value (Tab 3)| `provideKeyValueData()` | `new ArrayList<>()` |
| 13 | Push 消息日志 (Tab 4)| `providePushLogs()` | `new ArrayList<>()` |
| 14 | 个人诊断信息 (Tab 5)| `provideUserInfo()` | `new ArrayList<>()` |
| 15 | 缓存日志文件 (Tab 6)| `provideAllCacheDocPath()` | `getCacheDocPath()` |
| 16 | 扩展系统信息 (Tab 7)| `provideRestData()` | `new ArrayList<>()` |
| 17 | 单条日志删除回调 | `onDeleteHttpLog(data)` | `false` |
| 18 | 全部日志清空回调 | `onClearAllHttpLogs()` | `false` |
| 19 | 打开文本日志文件 | `onOpenTextFile(filePath)` | 默认打开 SDK 内置 `ReaderTxtDialog` |
| 20 | 分享日志文件回调 | `onShareFile(context, filePath)`| 默认启动 SDK `FileShareActivity` 分享 |
| 21 | 控制台打开回调 | `onConsoleShow()` | 空实现 |
| 22 | 控制台关闭回调 | `onConsoleDismiss()` | 空实现 |

---

## 4. 分步落地实施路线图

* **[ ] Step 1: 创建核心契约接口**：新建 `com.xq.dialoglogshow.config.ShowLogProvider` 纯接口。
* **[ ] Step 2: 实现内部兜底代理类**：新建 `com.xq.dialoglogshow.config.InternalDefaultShowLogProvider` 包级私有类。
* **[ ] Step 3: 重构门面入口类**：重构 `ShowLogManager`（提供 `ShowLog` 门面别名），提供 `init(Application, ShowLogProvider)` 入口。
* **[ ] Step 4: 全量替换弹窗与 View 数据调用链**：将 `DialogShowLog`、`DialogShowFileCache` : `DialogShowLogInfo`、`ReaderTxtDialog`、`MyAdapter` 等内部所有数据读取点切至 `ShowLogProvider` 代理。
* **[ ] Step 5: 物理删除存量旧类与全量验证**：删除 `IShowLoadDataCallback` 和 `LogConfigData`，更新测试 App，执行 `./gradlew assembleDebug` 全量编译验证。
