# Compose 屏幕适配规范

## 最小模板

```kotlin
@Composable
fun ResponsiveScaffold(
    modifier: Modifier = Modifier,
    contentMaxWidth: Dp = 600.dp,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    val activity = LocalContext.current as? ComponentActivity
    val windowSizeClass = activity?.let { calculateWindowSizeClass(it) }
    val isCompact = windowSizeClass == null || windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact

    Scaffold(
        modifier = modifier,
        topBar = { if (isCompact) topBar() },
        bottomBar = { if (isCompact) bottomBar() }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = if (isCompact) Alignment.TopStart else Alignment.TopCenter
        ) {
            Box(
                modifier = if (isCompact) Modifier else Modifier.widthIn(max = contentMaxWidth)
            ) {
                content()
            }
        }
    }
}
```
