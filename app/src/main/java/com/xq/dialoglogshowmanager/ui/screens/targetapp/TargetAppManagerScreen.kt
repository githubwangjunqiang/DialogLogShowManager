package com.xq.dialoglogshowmanager.ui.screens.targetapp

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xq.dialoglogshowmanager.R
import com.xq.dialoglogshowmanager.ui.entity.CaptureAbilityState
import com.xq.dialoglogshowmanager.ui.entity.CertificateTrustState
import com.xq.dialoglogshowmanager.ui.entity.InstalledAppInfo
import com.xq.dialoglogshowmanager.ui.entity.TargetAppStatusItem
import com.xq.dialoglogshowmanager.ui.utils.rememberThrottleOnClick
import com.xq.dialoglogshowmanager.ui.utils.throttleClick
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

/**
 * 目标 App 管理页。
 * 为什么这样做：页面按“当前白名单、搜索选择、确认生效”顺序组织，用户始终清楚现在拦截谁、下一步要做什么。
 */
@Composable
fun TargetAppManagerScreen(
    viewModel: TargetAppManagerViewModel,
    onOpenTargetApp: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val filteredItems = remember(uiState.statusItems, uiState.searchKeyword) {
        filterStatusItems(uiState.statusItems, uiState.searchKeyword)
    }

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is TargetAppManagerEffect.ShowMessage -> Toast.makeText(
                    context,
                    effect.message,
                    if (effect.isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                ).show()

                is TargetAppManagerEffect.OpenTargetApp -> onOpenTargetApp(effect.packageName)
            }
        }
    }

    TargetAppManagerTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .imePadding()
        ) {
            PageHeader(
                onRefresh = rememberThrottleOnClick {
                    viewModel.handleIntent(TargetAppManagerIntent.RefreshClicked)
                },
                refreshEnabled = uiState.pageStatus != TargetAppManagerPageStatus.Loading
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    horizontal = dimensionResource(R.dimen.dp_20),
                    vertical = dimensionResource(R.dimen.dp_16)
                ),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.dp_16))
            ) {
                item(key = "active_target") {
                    ActiveTargetSection(
                        state = uiState,
                        onOpen = rememberThrottleOnClick {
                            viewModel.handleIntent(TargetAppManagerIntent.OpenActiveClicked)
                        }
                    )
                }
                item(key = "search") {
                    SearchSection(
                        state = uiState,
                        onSearchChanged = { value ->
                            viewModel.handleIntent(TargetAppManagerIntent.SearchChanged(value))
                        }
                    )
                }
                item(key = "selected_detail") {
                    SelectedTargetSection(uiState)
                }
                item(key = "list_header") {
                    AppListHeader(
                        pageStatus = uiState.pageStatus,
                        totalCount = uiState.statusItems.size,
                        filteredCount = filteredItems.size
                    )
                }
                when (uiState.pageStatus) {
                    TargetAppManagerPageStatus.Loading -> item(key = "loading") { LoadingSection() }
                    TargetAppManagerPageStatus.Empty -> item(key = "empty") { EmptySection() }
                    TargetAppManagerPageStatus.Error -> item(key = "error") {
                        ErrorSection(uiState) {
                            viewModel.handleIntent(TargetAppManagerIntent.RefreshClicked)
                        }
                    }

                    TargetAppManagerPageStatus.Content -> {
                        if (filteredItems.isEmpty()) {
                            item(key = "search_empty") { SearchEmptySection() }
                        } else {
                            items(filteredItems, key = { it.appInfo.packageName }) { item ->
                                TargetAppStatusCard(
                                    item = item,
                                    selected = item.appInfo.packageName == uiState.selectedPackageName,
                                    active = item.appInfo.packageName == uiState.activePackageName,
                                    onClick = {
                                        viewModel.handleIntent(TargetAppManagerIntent.AppClicked(item))
                                    }
                                )
                            }
                        }
                    }
                }
            }
            ConfirmTargetBar(
                state = uiState,
                onApply = rememberThrottleOnClick {
                    viewModel.handleIntent(TargetAppManagerIntent.ApplyClicked)
                },
                onOpen = rememberThrottleOnClick {
                    viewModel.handleIntent(TargetAppManagerIntent.OpenSelectedClicked)
                }
            )
        }
    }
}

/** 为什么这样做：刷新入口固定在标题栏，用户查完目标 App 后无需滚回列表底部再重新读取状态。 */
@Composable
private fun PageHeader(onRefresh: () -> Unit, refreshEnabled: Boolean) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = dimensionResource(R.dimen.dp_20),
                    top = dimensionResource(R.dimen.dp_16),
                    end = dimensionResource(R.dimen.dp_12),
                    bottom = dimensionResource(R.dimen.dp_12)
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.dp_12))
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.target_app_manager_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.target_app_manager_subtitle),
                    modifier = Modifier.padding(top = dimensionResource(R.dimen.dp_6)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onRefresh, enabled = refreshEnabled) {
                Text(text = stringResource(R.string.target_app_refresh))
            }
        }
    }
}

/** 为什么这样做：白名单必须优先展示“已生效”的目标，避免用户误把暂存选择当成 VPN 当前拦截对象。 */
@Composable
private fun ActiveTargetSection(state: TargetAppManagerUiState, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(dimensionResource(R.dimen.dp_16))) {
            Text(
                text = stringResource(R.string.target_app_active_section_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (state.activeAppInfo == null) {
                Text(
                    text = stringResource(R.string.target_app_active_empty),
                    modifier = Modifier.padding(top = dimensionResource(R.dimen.dp_8)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                AppIdentity(state.activeAppInfo)
                state.activeStatusItem?.let { StatusRow(it) }
                OutlinedButton(
                    onClick = onOpen,
                    modifier = Modifier.padding(top = dimensionResource(R.dimen.dp_12))
                ) {
                    Text(text = stringResource(R.string.target_app_open_app_short))
                }
            }
        }
    }
}

/** 为什么这样做：搜索直接作用于同一份应用列表，用户不用在“弹窗列表”和“页面列表”之间来回跳转。 */
@Composable
private fun SearchSection(
    state: TargetAppManagerUiState,
    onSearchChanged: (String) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.target_app_choose_section_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(R.string.target_app_choose_section_hint),
            modifier = Modifier.padding(top = dimensionResource(R.dimen.dp_6)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = state.searchKeyword,
            onValueChange = onSearchChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = dimensionResource(R.dimen.dp_12)),
            singleLine = true,
            isError = state.inputError != null,
            supportingText = state.inputError?.let { error -> { Text(text = error) } },
            placeholder = { Text(text = stringResource(R.string.target_app_search_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

/** 为什么这样做：选择后立即展示完整状态，用户可以在确认写入白名单前判断证书和抓包预期。 */
@Composable
private fun SelectedTargetSection(state: TargetAppManagerUiState) {
    val appInfo = state.selectedAppInfo ?: return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(dimensionResource(R.dimen.dp_16))) {
            Text(
                text = if (appInfo.packageName == state.activePackageName) {
                    stringResource(R.string.target_app_selected_active_title)
                } else {
                    stringResource(R.string.target_app_selected_pending_title)
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            AppIdentity(appInfo)
            AppTechnicalDetail(appInfo)
            state.selectedStatusItem?.let { statusItem ->
                StatusRow(statusItem)
                Text(
                    text = stringResource(R.string.target_app_status_note_label, statusNoteText(statusItem)),
                    modifier = Modifier.padding(top = dimensionResource(R.dimen.dp_8)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } ?: Text(
                text = stringResource(R.string.capture_app_target_info_unknown),
                modifier = Modifier.padding(top = dimensionResource(R.dimen.dp_8)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 为什么这样做：名称与包名在所有入口保持相同展示顺序，降低用户核对相似应用时的认知成本。 */
@Composable
private fun AppIdentity(appInfo: InstalledAppInfo) {
    Column(modifier = Modifier.padding(top = dimensionResource(R.dimen.dp_8))) {
        Text(
            text = appInfo.appName,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = appInfo.packageName,
            modifier = Modifier.padding(top = dimensionResource(R.dimen.dp_4)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}


/** 为什么这样做：完整详情保留兼容性排查信息，而列表只展示核心状态，兼顾信息密度与查阅效率。 */
@Composable
private fun AppTechnicalDetail(appInfo: InstalledAppInfo) {
    Text(
        text = stringResource(R.string.target_app_target_sdk_label, appInfo.targetSdkVersion),
        modifier = Modifier.padding(top = dimensionResource(R.dimen.dp_8)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        text = stringResource(
            R.string.target_app_system_app_label,
            if (appInfo.systemApp) stringResource(R.string.system_app_yes)
            else stringResource(R.string.system_app_no)
        ),
        modifier = Modifier.padding(top = dimensionResource(R.dimen.dp_4)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 为什么这样做：列表标题显示筛选结果数量，用户输入搜索词后可立即确认是否命中目标。 */
@Composable
private fun AppListHeader(
    pageStatus: TargetAppManagerPageStatus,
    totalCount: Int,
    filteredCount: Int
) {
    val text = when (pageStatus) {
        TargetAppManagerPageStatus.Loading -> stringResource(R.string.target_app_status_loading)
        TargetAppManagerPageStatus.Content -> stringResource(
            R.string.target_app_filtered_count,
            filteredCount,
            totalCount
        )
        TargetAppManagerPageStatus.Empty -> stringResource(R.string.target_app_list_empty)
        TargetAppManagerPageStatus.Error -> stringResource(R.string.target_app_list_title)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground
    )
}

/** 为什么这样做：选中与当前白名单在列表项上直接标识，用户不需要反复回顶部确认两者是否一致。 */
@Composable
private fun TargetAppStatusCard(
    item: TargetAppStatusItem,
    selected: Boolean,
    active: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .throttleClick(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(dimensionResource(R.dimen.dp_16))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.appInfo.appName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (active) {
                    StatusChip(
                        text = stringResource(R.string.target_app_active_badge),
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (selected) {
                    StatusChip(
                        text = stringResource(R.string.target_app_selected_badge),
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            Text(
                text = item.appInfo.packageName,
                modifier = Modifier.padding(top = dimensionResource(R.dimen.dp_4)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            StatusRow(item)
        }
    }
}

/** 为什么这样做：仅保留用户决策抓包时最关键的两个状态，详细 targetSdk 等信息不再挤占列表空间。 */
@Composable
private fun StatusRow(item: TargetAppStatusItem) {
    Row(
        modifier = Modifier.padding(top = dimensionResource(R.dimen.dp_12)),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.dp_8))
    ) {
        StatusChip(trustStateText(item), trustStateColor(item.certificateTrustState))
        StatusChip(captureStateText(item), captureStateColor(item.captureAbilityState))
    }
}

/** 为什么这样做：将状态做成短标签，列表浏览时能快速比较多个应用的抓包条件。 */
@Composable
private fun StatusChip(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(color = color, shape = MaterialTheme.shapes.small) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = dimensionResource(R.dimen.dp_8),
                vertical = dimensionResource(R.dimen.dp_4)
            ),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

/** 为什么这样做：确认按钮固定在底部，用户在长列表中选中 App 后不用再滚回顶部完成最关键操作。 */
@Composable
private fun ConfirmTargetBar(
    state: TargetAppManagerUiState,
    onApply: () -> Unit,
    onOpen: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = dimensionResource(R.dimen.dp_4)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensionResource(R.dimen.dp_12)),
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.dp_12)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onOpen,
                enabled = state.selectedPackageName != null || state.activePackageName != null,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.target_app_open_app_short),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Button(
                onClick = onApply,
                enabled = state.pageStatus != TargetAppManagerPageStatus.Loading && state.selectedPackageName != null,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = if (state.selectedPackageName == state.activePackageName) {
                        stringResource(R.string.target_app_applied)
                    } else {
                        stringResource(R.string.target_app_apply)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** 为什么这样做：加载期间保留内容区反馈，避免用户误以为应用列表被清空。 */
@Composable
private fun LoadingSection() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimensionResource(R.dimen.dp_32)),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

/** 为什么这样做：设备没有可查询启动应用时明确说明原因，用户不会把空状态误解为 VPN 不可用。 */
@Composable
private fun EmptySection() {
    Text(
        text = stringResource(R.string.target_app_list_empty),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 为什么这样做：搜索没有命中不是加载失败，使用独立文案帮助用户改用完整包名检索。 */
@Composable
private fun SearchEmptySection() {
    Text(
        text = stringResource(R.string.target_app_search_empty),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 为什么这样做：读取失败保留一键重试，防止临时 PackageManager 异常导致用户必须退出重进页面。 */
@Composable
private fun ErrorSection(state: TargetAppManagerUiState, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = state.errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
        TextButton(onClick = rememberThrottleOnClick(onClick = onRetry)) {
            Text(text = stringResource(R.string.target_app_refresh))
        }
    }
}

/** 为什么这样做：本地过滤支持名称与包名，既适合普通用户搜应用名，也保留开发调试时按 applicationId 精确查找。 */
private fun filterStatusItems(
    items: List<TargetAppStatusItem>,
    keyword: String
): List<TargetAppStatusItem> {
    val normalizedKeyword = keyword.trim()
    if (normalizedKeyword.isEmpty()) return items
    return items.filter { item ->
        item.appInfo.appName.contains(normalizedKeyword, ignoreCase = true) ||
            item.appInfo.packageName.contains(normalizedKeyword, ignoreCase = true)
    }
}

/** 为什么这样做：证书状态只由分析器的枚举驱动，UI 不重复推导，确保白名单卡和列表卡含义一致。 */
@Composable
private fun trustStateText(item: TargetAppStatusItem): String = when (item.certificateTrustState) {
    CertificateTrustState.TRUSTED -> stringResource(R.string.certificate_trust_trusted)
    CertificateTrustState.NOT_TRUSTED -> stringResource(R.string.certificate_trust_not_trusted)
    CertificateTrustState.NOT_REQUIRED -> stringResource(R.string.certificate_trust_not_required)
    CertificateTrustState.UNKNOWN -> stringResource(R.string.certificate_trust_unknown)
}

/** 为什么这样做：抓包能力同样只展示分析器结论，避免界面根据局部字段做出不一致判断。 */
@Composable
private fun captureStateText(item: TargetAppStatusItem): String = when (item.captureAbilityState) {
    CaptureAbilityState.FULLY_CAPTURED -> stringResource(R.string.capture_ability_full)
    CaptureAbilityState.PARTIALLY_CAPTURED -> stringResource(R.string.capture_ability_partial)
    CaptureAbilityState.NOT_CAPTURED -> stringResource(R.string.capture_ability_blocked)
    CaptureAbilityState.UNKNOWN -> stringResource(R.string.capture_ability_unknown)
}

/** 为什么这样做：详情给出一句关键解释，帮助用户理解“未知”是还未产生样本，而不等同于不能拦截。 */
@Composable
private fun statusNoteText(item: TargetAppStatusItem): String = when {
    !item.hasTrafficSample -> stringResource(R.string.capture_note_unknown)
    item.certificateTrustState == CertificateTrustState.TRUSTED -> stringResource(R.string.capture_note_trusted)
    item.certificateTrustState == CertificateTrustState.NOT_TRUSTED -> stringResource(R.string.capture_note_not_trusted)
    else -> stringResource(R.string.capture_note_not_required)
}

/** 为什么这样做：状态颜色沿用 Demo 的语义色，成功、限制和未知在深浅主题下保持一致识别。 */
@Composable
private fun trustStateColor(state: CertificateTrustState) = when (state) {
    CertificateTrustState.TRUSTED -> MaterialTheme.colorScheme.primary
    CertificateTrustState.NOT_TRUSTED -> MaterialTheme.colorScheme.error
    CertificateTrustState.NOT_REQUIRED -> MaterialTheme.colorScheme.tertiary
    CertificateTrustState.UNKNOWN -> MaterialTheme.colorScheme.outline
}

/** 为什么这样做：抓包能力颜色与证书状态颜色分离，避免“已部分抓到”被误读成完全失败。 */
@Composable
private fun captureStateColor(state: CaptureAbilityState) = when (state) {
    CaptureAbilityState.FULLY_CAPTURED -> MaterialTheme.colorScheme.primary
    CaptureAbilityState.PARTIALLY_CAPTURED -> MaterialTheme.colorScheme.secondary
    CaptureAbilityState.NOT_CAPTURED -> MaterialTheme.colorScheme.error
    CaptureAbilityState.UNKNOWN -> MaterialTheme.colorScheme.outline
}

/** 为什么这样做：页面使用项目已有语义色，不另引入主题基础设施影响其他 Demo 页面。 */
@Composable
private fun TargetAppManagerTheme(content: @Composable () -> Unit) {
    val darkColors = darkColorScheme(
        primary = colorResource(R.color.demo_button_primary),
        secondary = colorResource(R.color.demo_button_secondary),
        background = colorResource(R.color.demo_background),
        surface = colorResource(R.color.demo_surface),
        surfaceVariant = colorResource(R.color.demo_surface_alt),
        onBackground = colorResource(R.color.demo_text_primary),
        onSurface = colorResource(R.color.demo_text_primary),
        onSurfaceVariant = colorResource(R.color.demo_text_secondary),
        error = colorResource(R.color.demo_status_danger),
        tertiary = colorResource(R.color.demo_status_success)
    )
    val lightColors = lightColorScheme(
        primary = colorResource(R.color.demo_button_primary),
        secondary = colorResource(R.color.demo_button_secondary),
        background = colorResource(R.color.demo_background),
        surface = colorResource(R.color.demo_surface),
        surfaceVariant = colorResource(R.color.demo_surface_alt),
        onBackground = colorResource(R.color.demo_text_primary),
        onSurface = colorResource(R.color.demo_text_primary),
        onSurfaceVariant = colorResource(R.color.demo_text_secondary),
        error = colorResource(R.color.demo_status_danger),
        tertiary = colorResource(R.color.demo_status_success)
    )
    MaterialTheme(colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) darkColors else lightColors, content = content)
}
