# NetBare 本地改动登记

## 使用规则
后续只要修改 `third_party/NetBare-Android/netbare-core` 内的源码、资源、Manifest 或发布配置，都必须在这里补一条记录。

## 当前状态
- 2026-07-28：清理第三方仓库非必要文件，仅保留 `netbare-core` 最小可构建集，并补齐 Maven 发布配置，目的是让 `dialoglogshow` 能通过 POM 传递依赖隐藏底层模块。
