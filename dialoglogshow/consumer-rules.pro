# ShowLog AAR 自带的消费者混淆规则：宿主无需再手动配置任何 ShowLog keep 规则。
# 通过 build.gradle 的 consumerProguardFiles 打包进 AAR（产物内为 proguard.txt），
# 由 Android Gradle Plugin 在宿主开启 R8/ProGuard 时自动合并生效。
-keep class com.xq.dialoglogshow.** { *; }
-dontnote com.xq.dialoglogshow.**
