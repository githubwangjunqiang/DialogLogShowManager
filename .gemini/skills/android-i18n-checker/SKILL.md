---
name: android-i18n-checker
description: 用户输入“补齐多语言配置”时使用。
---

# Android 国际化资源检查技能

本技能用于检查 Android 项目中各语言的国际化资源是否完整，并自动补齐缺失的翻译。

**目标：确保所有语言文件包含相同的 key，只追加缺失翻译，不删除已有内容。**

---

## 一、技能用途

当用户说 **“补齐多语言配置”** 时，自动触发本技能。

---

## 二、执行流程

### 步骤 1：确认检查范围（AI交互）

询问用户要检查哪些模块：
- 仅检查 `app` 模块（推荐）
- 检查所有模块
- 指定特定模块

### 步骤 2：AI 探索并定位 res 目录（关键）

**AI 必须先探索项目结构，找到正确的 res 目录位置，再调用脚本。**

示例探索命令：
```bash
find <模块路径> -type d -name "res" | head -5
```

**正确的脚本参数是 res 的父目录**（通常是 `src/main`）：
- 如果找到 `<模块>/src/main/res`，脚本参数应为 `<模块>/src/main`
- 如果找到其他位置的 res，脚本参数应为 res 的直接父目录

### 步骤 3：脚本扫描统计

**根据操作系统选择脚本执行：**

> 以下命令默认在本 skill 目录中执行，因此使用相对路径，避免绑定某个工具的全局安装目录。

- **macOS/Linux/Git Bash/WSL**: `bash ./i18n-check.sh <res父目录路径>`
- **Windows PowerShell**: `powershell ./i18n-check.ps1 -ModulePath <res父目录路径>`

**如果脚本报错，立即停止执行，将错误信息报告给用户，不再继续尝试。**

脚本输出：
- 各语言文件条数统计
- 全量 key 并集数量
- 各语言缺失详情

### 步骤 4：确认翻译参考来源（AI交互）

询问用户补齐时参考哪个语言的文本：
- 用户指定（如 `values-zh-rCN`）
- 不指定则默认 `values`

### 步骤 5：询问是否补齐（AI交互）

向用户展示缺失报告，询问是否补齐翻译。

### 步骤 6：提取缺失条目文本

用户同意后执行提取脚本：

- **macOS/Linux/Git Bash/WSL**: `bash ./i18n-extract.sh <参考文件路径> "key1|key2|key3"`
- **Windows PowerShell**: `powershell ./i18n-extract.ps1 -File <参考文件路径> -Keys "key1|key2|key3"`

**如果脚本报错，立即停止执行，将错误信息报告给用户。**

### 步骤 7：翻译生成并写入（AI执行）

AI 获取脚本输出的文本后：
1. 为每个目标语言生成翻译
2. 写入对应文件末尾（追加，不删除已有内容）

### 步骤 8：汇报结果

汇报补齐结果，询问是否编译检查。

### 步骤 9：编译验证（可选）

用户同意后执行：
```bash
./gradlew assembleDebug --warning-mode all 2>&1 | head -100
```

---

## 三、核心原则

1. **AI 智能探索**：路径探索由 AI 完成，脚本只接收明确的正确路径
2. **报错即停**：任何步骤报错，立即停止，报告错误给用户，不继续尝试
3. **不删除已有翻译**：只追加，绝不删除
4. **保持 XML 格式**：追加时保持正确缩进
5. **特殊字符转义**：注意 `&`, `<`, `>`, `"`, `'`
6. **格式化字符串**：保留 `%s`, `%d`, `%1$s` 等占位符

---

## 四、附属文件

- `i18n-check.sh` / `i18n-extract.sh`: 扫描统计 / 提取脚本 (macOS/Linux/Bash/WSL)
- `i18n-check.ps1` / `i18n-extract.ps1`: 扫描统计 / 提取脚本 (Windows)

---

## 五、示例

**AI 探索 res 目录：**
```bash
find /Users/xxx/Project/app -type d -name "res"
# 输出: /Users/xxx/Project/app/src/main/res
```

**确定脚本参数：**
res 父目录 = `/Users/xxx/Project/app/src/main`

**执行脚本：**
```bash
bash ./i18n-check.sh /Users/xxx/Project/app/src/main
```

**脚本输出示例：**
```
================================
语言文件统计
================================
res/values/strings.xml: 128
res/values-zh-rCN/strings.xml: 135

================================
全量 key 并集
================================
全量基准: 143 strings

================================
各语言缺失详情
================================

values-zh-rCN: 缺失 8 strings
  - feature_new_title
  - feature_new_desc

================================
检查完成
================================
```
