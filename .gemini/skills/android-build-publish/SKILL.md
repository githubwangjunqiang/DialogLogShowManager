---
name: android-build-publish
description: 用户说“补充版本管理和apk命名”时使用；负责版本管理、蒲公英上传和 APK 命名。
---

## 一、触发场景

- 用户说“补充版本管理和apk命名”时使用。
- 目标是补齐项目里的版本配置、APK 命名和自动上传能力。

## 二、强制规则

- 版本配置以项目根目录 `version.properties` 为准。
- `versionCode` 通过 `VersionConfig` 自动递增。
- APK 命名必须包含：项目名、版本名、版本号、时间戳、flavor、buildType。
- `taskName`、`apkPath`、`apkPrefix` 等常量必须按项目实际值替换。
- 新增三方库且用户未指定版本时，先查官方渠道可确认的最新稳定版；若无法确认，则暂停并询问用户。

## 三、参考文件

- `VersionConfig.kt`：读取 `version.properties`、管理版本号与蒲公英密钥。
- `ApkUpLoadUtils.kt`：注册上传任务、查找 APK、上传蒲公英。
- `UploadResponse.kt`：解析蒲公英上传响应。

## 四、接入要点

1. 准备 `version.properties`。
2. 在 `app/build.gradle.kts` 接入 `VersionConfig`。
3. 配置 APK 命名规则。
4. 接入 `ApkUpLoadUtils.registerTaskForDandelion()`。
