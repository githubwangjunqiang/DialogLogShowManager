# 阶段 1：纯 Java 运行时代理网络日志自动收集 - 开发落地计划书（收口与落地开发版）

> **项目名称**：DialogLogShowManager - 自动网络日志收集模块  
> **实施版本**：阶段 1 (Phase 1) - 纯 Java 运行时代理收口开发版  
> **核心目标**：零代码侵入、网络框架无关、自动捕获全 App 的 **HTTP & HTTPS** 网络日志，双模式（手动+自动）兼容，并在调试弹窗中新增专属 Tab 与全新 UI 体验联动展示。

---

## 1. 产品与 SDK 接入设计（双模式兼容）

### 1.1 双模式并行策略
为保证向上兼容性与灵活性，SDK **完全保留现有的开发者手动传入模式**，同时新增 **纯 Java 运行时代理自动拦截模式**：

| 模式类型 | 说明 | 日志来源 | UI 控件归属 |
| :--- | :--- | :--- | :--- |
| **手动传入模式 (保留)** | 开发者在拦截器/业务代码中手动调用接口传入 | `provider.provideHttpLogs()` | 弹窗 Tab 1："手动HTTP" |
| **自动拦截模式 (新增)** | SDK 在 Java 运行时底层自动拦截 HTTP/HTTPS 流量 | `JavaHttpLogCollector` 自动抓取 | 弹窗 新 Tab："自动HTTP" |

---

### 1.2 SDK 初始化接口适配 (`ShowLog.java`)

保持原有 `init` 接口不变，新增带有 `autoCollectNetworkLog` 布尔开关的重载方法：

```java
public class ShowLog {

    /**
     * 1. 传统初始化方式（完全兼容老代码，默认不开启全局自动拦截）
     */
    public static void init(@NonNull Application application, @NonNull ShowLogProvider provider) {
        init(application, provider, false);
    }

    /**
     * 2. 双模式初始化方式（支持开启 Java 运行时自动网络拦截）
     *
     * @param application             宿主 Application
     * @param provider                开发者 Provider 实现
     * @param autoCollectNetworkLog   true: 开启 Java 运行时网络日志自动拦截；false: 仅使用开发者手动传入
     */
    public static void init(@NonNull Application application, 
                            @NonNull ShowLogProvider provider, 
                            boolean autoCollectNetworkLog) {
        ShowLogManager.getInstance().init(application, provider);
        
        // 开启底层自动拦截器
        if (autoCollectNetworkLog) {
            JavaHttpLogCollector.getInstance().start(application);
        }
    }
}
```

---

## 2. 调试弹窗 UI 改造与专属新设计 (`DialogShowLog.java`)

在调试弹框主页 (`DialogShowLog`) 的导航 Tab 区域（`app_dialog_httplog_tablayout`）添加专用的 **“自动HTTP”** Tab，并对自动拦截日志进行 4 大专属 UI/UX 体验升级。

### 2.1 布局与 Tab 映射规划

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                                   DialogShowLog 调试主页                                │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│ Tab 导航栏:                                                                             │
│ [手动HTTP]  [★ 自动HTTP]  [Key-Value]  [Push消息]  [Rest数据]  [设置]                    │
│   (Tab 1)    (新增 Tab)     (Tab 2)     (Tab 3)    (Tab 4)     (Setting)                 │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│ [ 🔍 搜索关键字... ]                    [ ☑ 仅看失败 (4xx/5xx) ]  <-- 新增 Filter Bar    │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│ 列表项 (Card View)：                                                                     │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐ │
│ │ [POST] 200 OK   https://api.example.com/v1/user/info                                │ │
│ │ 耗时: 128ms  │  大小: 1.2KB  │  协议: HTTPS/1.1                [ 📋 复制 cURL ]       │ │
│ └─────────────────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

---

### 2.2 自动 HTTP 日志专属 UI 4 大设计亮点

#### 🌟 亮点 1：Method & 状态码彩色 Badge 标签 (视觉强化)
- **请求 Method 徽章**：
  - `GET`：深蓝色 Badge
  - `POST`：翠绿色 Badge
  - `PUT / PATCH`：橙黄色 Badge
  - `DELETE`：品红色 Badge
- **状态码彩色警示**：
  - `2xx (200 OK)`：绿色文字与标签
  - `3xx (重定向)`：蓝色标签
  - `4xx / 5xx / 异常网络`：**醒目的红色高亮警示**，卡片背景微红提示。

#### 🌟 亮点 2：元数据“三件套”概览
在未展开卡片时，折叠头部直接展示 3 个关键指标：
`[ 耗时: 128ms ]` | `[ 大小: 1.5KB ]` | `[ 协议: HTTPS/1.1 ]`
- **慢接口警示**：当耗时超过 2000ms 时，耗时标签**自动高亮变红**。

#### 🌟 亮点 3：一键复制 cURL 命令行 (开发者调试神器)
- 拦截器自动提取完整 Request Headers、URL 和 Request Body。
- 在日志详情展开面板与卡片右上角增加 **【📋 复制 cURL】** 按钮。
- 点击后自动合成标准 cURL 命令（例如 `curl -X POST "https://api.com/v1/login" -H "Content-Type: application/json" -d '{"username":"test"}'`）写入剪贴板，方便直接复制到 Postman / Mac 终端调测。

#### 🌟 亮点 4：快捷筛选器 Filter Bar (高效排错)
- 在“自动HTTP” Tab 顶部搜索栏右侧提供 **“仅看失败 (4xx/5xx)”** 的快捷 Switch 开关。
- 开启后瞬间过滤掉所有 2xx 成功请求，帮助开发者在大量自动拦截数据中秒定位报错接口。

---

### 2.3 `DialogShowLog.java` 代码联动逻辑

1. **控件定义**：
   在 `DialogShowLog` 中声明 `private TextView mTabAutoHttp;` 并绑定布局文件中的 `R.id.show_sdk_id_dialog_http_log_tv_auto`。
2. **事件监听**：
   ```java
   // 点击“自动HTTP” Tab
   mTabAutoHttp.setOnClickListener(v -> {
       setting_config.setVisibility(View.GONE);
       mRecyclerView.setVisibility(View.VISIBLE);
       loadDataAutoHttp(); // 异步加载 JavaHttpLogCollector 拦截到的日志
       changeTabState(v);
       mCurrentDate.setVisibility(View.VISIBLE);
       mClearHttpLog.setVisibility(View.VISIBLE);
       mPrevious.setVisibility(View.VISIBLE);
       mNext.setVisibility(View.VISIBLE);
   });
   ```
3. **数据加载**：
   `loadDataAutoHttp()` 通过后台线程获取 `JavaHttpLogCollector.getInstance().getAutoHttpLogs()` 并刷新 `MyAdapter`。

---

## 3. 技术架构与底层拦截器设计

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                          宿主 App 任意 Java 网络框架                              │
│         (HttpURLConnection / OkHttp / Volley / Apache HttpClient / 自研)         │
└───────────────────────────────┬─────────────────┬───────────────────────────────┘
                                │                 │
                       (HTTP 80 明文)        (HTTPS 443 密文)
                                │                 │
                                ▼                 ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                       DialogLogShow - Java 运行时代理层                           │
│                                                                                 │
│  ┌───────────────────────────────┐     ┌─────────────────────────────────────┐  │
│  │   ProxySocketImplFactory      │     │      ProxySSLSocketFactory          │  │
│  │  (代理普通 TCP Socket 流)       │     │   (代理 TLS 握手解密后的明文流)       │  │
│  └───────────────┬───────────────┘     └──────────────────┬──────────────────┘  │
│                  │                                        │                     │
│                  └───────────────────┬────────────────────┘                     │
│                                      │ (原始 HTTP 报文字节流)                   │
│                                      ▼                                          │
│                        ┌───────────────────────────┐                            │
│                        │    RawHttpStreamParser    │                            │
│                        │  (轻量级 HTTP 状态机解析器) │                            │
│                        └─────────────┬─────────────┘                            │
└──────────────────────────────────────┼──────────────────────────────────────────┘
                                       │ 生成 HttpLogData (含 cURL 拼接能力)
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                      JavaHttpLogCollector (自动日志内存池)                       │
│                                      │                                          │
│                                      ▼                                          │
│            DialogShowLog -> 新Tab ("自动HTTP" 带 cURL 复制与失败过滤)            │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. 包结构与新建类列表

包路径：`com.xq.dialoglogshow.collector`

```
com.xq.dialoglogshow.collector
├── JavaHttpLogCollector.java        // 总控单例：自动日志内存池、开启/停止、查询与清空
├── parser
│   └── RawHttpStreamParser.java     // 报文解析器：字节流 -> HttpLogData (支持 HTTP/1.1 与 HTTP/2 降级、cURL合成)
├── stream
│   ├── CopyInputStream.java         // 旁路镜像流：InputStream 读操作镜像备份
│   └── CopyOutputStream.java        // 旁路镜像流：OutputStream 写操作镜像备份
└── proxy
    ├── ProxySocketImpl.java         // HTTP 代理 SocketImpl
    ├── ProxySocketImplFactory.java  // HTTP 代理 SocketImpl 工厂
    ├── ProxySSLSocket.java          // HTTPS 代理 SSLSocket
    └── ProxySSLSocketFactory.java   // HTTPS 代理 SSLSocketFactory
```

---

## 5. 详细模块设计与 Task 拆解列表

### Task 0: 内存存储与数据模型扩展 (`JavaHttpLogCollector`)
* **作用**：维护自动拦截到的网络日志列表 `CopyOnWriteArrayList<HttpLogData> mAutoLogList`。
* **主要方法**：
  - `addAutoLog(HttpLogData log)`：线程安全地追加自动拦截日志（内置 200 条限制，先进先出，单条 Body 限制 50KB 截断）。
  - `getAutoHttpLogs()`：供 `DialogShowLog` 异步读取。
  - `clearAutoHttpLogs()`：支持在 UI 上单独清空“自动HTTP”日志。

### Task 1: 旁路镜像流模块 (`CopyInputStream` / `CopyOutputStream`)
* **作用**：包装原有的 `InputStream` / `OutputStream`，在宿主读写网络数据流时，零干扰地复制一份数据到内存缓存中（限制单流最大 500KB 镜像）。

### Task 2: 报文解析引擎与 cURL 合成器 (`RawHttpStreamParser`)
* **作用**：将复制到的原始 HTTP 报文字节流转化为标准 `HttpLogData` 对象：
  - 提取请求 Method、URL、Header、Status Code、Cost Time。
  - 提取 Body（支持 JSON 自动检测与格式化，支持 HTTP/1.1 Chunked 传输协议拆包）。
  - **新增 cURL 表达式生成方法**：`public static String generateCurlCommand(HttpLogData logData)`。

### Task 3 & 4: HTTP/HTTPS 代理 Socket 挂载 (`ProxySocketImplFactory` & `ProxySSLSocketFactory`)
* **作用**：
  - HTTP：接管普通 TCP Socket 80 端口流。
  - HTTPS：接管 `SSLSocket` 在 **`startHandshake()` 完成后**的 TLS 解密后 443 端口明文流。

### Task 5: 接入 `ShowLog.java` SDK 初始化
* **作用**：在 `ShowLog.init(app, provider, autoCollect)` 中关联 `JavaHttpLogCollector` 启动逻辑。

### Task 6: 调试弹窗 UI 改造 (`DialogShowLog` & `MyAdapter`)
* **作用**：
  - 布局 XML 中添加“自动HTTP” Tab (`R.id.show_sdk_id_dialog_http_log_tv_auto`) 与 仅看失败 Switch 按钮。
  - `DialogShowLog.java` 中新增 `mTabAutoHttp` 绑定与点击监听。
  - 增加 `loadDataAutoHttp()` 异步数据请求与 Filter 筛选逻辑。
  - 适配 `MyAdapter` 支持 Method 彩色 Badge 绘制、耗时标红及 **【📋 复制 cURL】** 点击响应。

---

## 6. 开发 Checklist 与验收标准

- [ ] **Task 0**：完成 `JavaHttpLogCollector` 内存池与数据读写。
- [ ] **Task 1**：完成 `CopyInputStream` / `CopyOutputStream` 旁路流。
- [ ] **Task 2**：完成 `RawHttpStreamParser` 解析器及 cURL 命令合成器。
- [ ] **Task 3**：完成 HTTP `ProxySocketImplFactory` 挂载。
- [ ] **Task 4**：完成 HTTPS `ProxySSLSocketFactory` 挂载（强制等待握手完成）。
- [ ] **Task 5**：扩展 `ShowLog.init(...)` 具备双模式初始化能力。
- [ ] **Task 6**：完成 `DialogShowLog` UI 弹窗新增 **“自动HTTP” Tab**、cURL 一键复制及仅看失败筛选。

---

## 7. 全维度风险审查报告与方案补充 (Critical Audit & Fixes)

*(保持不变，包含适配性、安全脱敏、TLS 握手等待、HTTP/2 降级、OOM 内存硬上限防爆与无权限开销保障)*
