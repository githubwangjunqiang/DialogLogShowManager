<!-- project-init-rules:start -->
### 强制规则

1. **Skill执行卡口**：做事前，先打印命中的 `[Skill 清单]`，并加载全部命中 Skill；
2. **存量项目补救**：接手存量项目先体检底层架构；若网络层未做全局异常拦截（原生 try-catch 泛滥），禁止重构底层代码，改用零侵入补丁统一拦截异常，细节见对应架构 Skill。
<!-- project-init-rules:end -->
