---
name: android-view-xml-rules
description: 当任务涉及 Android 传统 View、XML layout、TextView、EditText、Button 或页面布局实现时使用。
---

# android-view-xml-rules


## 1. XML

### TextView / MaterialTextView / AppCompatTextView / Button / MaterialButton
- 用途：文本展示或文本按钮
- 默认高度：写 `wrap_content`
- 仅当用户明确要求固定高度时，才允许写死高度
- 固定高度时必须：显式限制行数，并配置 `ellipsize`
- 仅当限制为单行时，必须设置 `ellipsize`
- 多行：允许自然撑高
- 禁止：无理由固定高度；固定高度但不配行数和省略策略

## 2. 通用布局规则

- 图片：宽高强制写死，除非用户明确要求不写死
- 层级：避免过度嵌套
- RTL：优先 `start/end`
- 键盘：输入场景必须考虑软键盘遮挡布局问题
