---
name: android-i18n-checker
description: 用户输入“补齐多语言配置”时使用。
---

# Android I18n Checker

## 1. 唯一实现

XML 读取、比较和写入统一由 [i18n_tool.py](i18n_tool.py)完成。Shell 与 PowerShell 只是启动包装，禁止再用 grep、sed 或正则解析 XML。

## 2. 检查

```bash
python3 i18n_tool.py check --res-parent app/src/main
```

检查内容：

- `string`、`plurals`、`string-array`缺失项；
- format placeholder 与 XLIFF id 一致性；
- `translatable="false"`排除；
- locale 与 night、land、vNN、swNNNdp 等配置轴分组。

退出码：无问题为 0；发现资源问题为 1；参数、XML 或 I/O 错误为 2。

## 3. 提取与应用

```bash
python3 i18n_tool.py extract \
  --file app/src/main/res/values/strings.xml \
  --keys app_name item_count \
  --output additions.json

python3 i18n_tool.py apply \
  --target app/src/main/res/values-fr/strings.xml \
  --input additions.json
```

翻译时只修改 `additions.json`中每项 `xml`里的文本内容，不得修改 XML 标签类型、资源 `name`、`key`、`source_fingerprint`、`placeholders`或`xliff_ids`。应用时会拒绝重复 key、无效 XML、资源 key 变化及占位符变化。

`apply`必须能够访问 `extract`记录的源文件；移动工程或跨机器使用时，需要重新执行提取，不得手工改写 `source`、`key`或元数据绕过校验。

## 4. 强制规则

- 写入前解析目标文件，写入后重新解析临时文件，再原子替换。
- 不把 `values-night`、`values-land`等目录当成语言。
- 不用文本行数统计资源。
- 不直接覆盖已存在的同名资源。
- 操作前保留 Git diff；脚本失败不得留下半写文件。

## 5. 完成检查

确认 XML 可解析、缺失项与 placeholder 报告准确、目标 qualifier 分组正确、Git diff 只包含预期资源。
