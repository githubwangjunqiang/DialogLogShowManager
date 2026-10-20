package com.xq.dialoglogshowmanager.ui.screens.targetapp

import android.app.Application
import android.text.TextUtils
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xq.dialoglogshow.ShowLog
import com.xq.dialoglogshowmanager.R
import com.xq.dialoglogshowmanager.ui.entity.InstalledAppInfo
import com.xq.dialoglogshowmanager.ui.entity.TargetAppStatusItem
import com.xq.dialoglogshowmanager.ui.utils.AppCaptureStatusAnalyzer
import com.xq.dialoglogshowmanager.ui.utils.InstalledAppLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections

/**
 * 目标 App 管理页状态协调器。
 * 为什么这样做：将“选择目标”和“确认写入 VPN 白名单”拆开，页面旋转或刷新时也不会把未确认选择误写进 SDK。
 */
class TargetAppManagerViewModel(application: Application) : AndroidViewModel(application) {

    /** 为什么这样做：StateFlow 能让 Compose 在配置变化后从完整页面状态恢复，而非依赖旧 View 引用。 */
    private val _uiState = MutableStateFlow(TargetAppManagerUiState())
    val uiState: StateFlow<TargetAppManagerUiState> = _uiState.asStateFlow()

    /** 为什么这样做：一次性提示与系统跳转不属于可恢复页面状态，防止重组时重复触发。 */
    private val _effect = MutableSharedFlow<TargetAppManagerEffect>(extraBufferCapacity = EFFECT_BUFFER_CAPACITY)
    val effect: SharedFlow<TargetAppManagerEffect> = _effect.asSharedFlow()

    /** 为什么这样做：连续返回前台或连续点刷新时只保留一轮读取，避免多次 PackageManager 查询抢占主线程回调。 */
    private var refreshJob: Job? = null

    /** 为什么这样做：白名单更新期间禁止重复提交，避免 VPN 重载顺序被快速点击打乱。 */
    private var applyJob: Job? = null

    /**
     * 统一处理页面事件。
     * 为什么这样做：所有交互入口在这里收敛，便于确认只有 ApplyClicked 会修改 SDK 白名单。
     */
    fun handleIntent(intent: TargetAppManagerIntent) {
        when (intent) {
            TargetAppManagerIntent.PageStarted,
            TargetAppManagerIntent.RefreshClicked -> refreshInstalledApps()
            TargetAppManagerIntent.ApplyClicked -> applyCaptureApp()
            TargetAppManagerIntent.OpenActiveClicked -> openTargetApp(_uiState.value.activePackageName)
            TargetAppManagerIntent.OpenSelectedClicked -> openTargetApp(
                _uiState.value.selectedPackageName ?: _uiState.value.activePackageName
            )
            is TargetAppManagerIntent.SearchChanged -> updateSearchKeyword(intent.value)
            is TargetAppManagerIntent.AppClicked -> selectItem(intent.item)
        }
    }

    /**
     * 更新列表搜索词。
     * 为什么这样做：仅刷新 UI 的过滤条件，避免每输入一个字符就重新读取已安装应用。
     */
    private fun updateSearchKeyword(value: String) {
        _uiState.value = _uiState.value.copy(searchKeyword = value, inputError = null)
    }

    /**
     * 暂存用户点选的应用。
     * 为什么这样做：点选只更新详情与底部确认状态，让用户有机会先判断证书和抓包能力再生效。
     */
    private fun selectItem(item: TargetAppStatusItem) {
        _uiState.value = _uiState.value.copy(
            selectedPackageName = item.appInfo.packageName,
            selectedAppInfo = item.appInfo,
            selectedStatusItem = item,
            inputError = null
        )
    }

    /**
     * 读取安装列表和对应的实时抓包状态。
     * 为什么这样做：PackageManager 与数据库查询都可能阻塞，必须在 IO 线程完成后再一次性更新页面状态。
     */
    private fun refreshInstalledApps() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                pageStatus = TargetAppManagerPageStatus.Loading,
                errorMessage = null,
                inputError = null
            )
            try {
                val previous = _uiState.value
                val result = withContext(Dispatchers.IO) {
                    val context = getApplication<Application>()
                    val apps = InstalledAppLoader.load(context)
                    val items = AppCaptureStatusAnalyzer.buildStatusItems(context, apps)
                    val configuredPackage = resolveCurrentCaptureTargetPackage()
                    // 为什么这样做：SDK 当前配置是白名单的唯一真相，用户在其他入口重置后不能保留页面中的旧目标。
                    val activePackage = configuredPackage
                    // 为什么这样做：若用户此前没有暂存新选择，白名单从其他入口变化后详情也必须跟随最新已生效目标。
                    val selectedPackage = if (previous.selectedPackageName == previous.activePackageName) {
                        activePackage
                    } else {
                        previous.selectedPackageName ?: activePackage
                    }
                    val activeInfo = loadAppInfo(context, apps, activePackage)
                    val selectedInfo = loadAppInfo(context, apps, selectedPackage)
                    LoadedApps(
                        apps = apps,
                        items = items,
                        activePackageName = activePackage,
                        activeAppInfo = activeInfo,
                        selectedPackageName = selectedPackage,
                        selectedAppInfo = selectedInfo
                    )
                }
                applyLoadedApps(result)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: RuntimeException) {
                val reason = exception.message ?: exception.javaClass.simpleName
                _uiState.value = _uiState.value.copy(
                    pageStatus = TargetAppManagerPageStatus.Error,
                    errorMessage = getApplication<Application>().getString(
                        R.string.target_app_list_failed,
                        reason
                    )
                )
            }
        }
    }

    /**
     * 将同一次分析得到的数据同步到选择卡和白名单卡。
     * 为什么这样做：两个区域共用同一份状态快照，防止刷新后显示互相矛盾的证书结论。
     */
    private fun applyLoadedApps(result: LoadedApps) {
        val previous = _uiState.value
        val activeStatusItem = result.items.firstOrNull {
            it.appInfo.packageName == result.activePackageName
        }
        val selectedStatusItem = result.items.firstOrNull {
            it.appInfo.packageName == result.selectedPackageName
        }
        _uiState.value = previous.copy(
            pageStatus = if (result.items.isEmpty()) {
                TargetAppManagerPageStatus.Empty
            } else {
                TargetAppManagerPageStatus.Content
            },
            installedApps = result.apps,
            statusItems = result.items,
            selectedPackageName = result.selectedPackageName,
            selectedAppInfo = result.selectedAppInfo,
            selectedStatusItem = selectedStatusItem,
            activePackageName = result.activePackageName,
            activeAppInfo = result.activeAppInfo,
            activeStatusItem = activeStatusItem,
            errorMessage = null
        )
    }

    /**
     * 确认切换 VPN 白名单。
     * 为什么这样做：SDK 当前只支持单目标抓包，只有这里调用 setVpnCapturePackageNames，避免搜索、浏览列表改变拦截范围。
     */
    private fun applyCaptureApp() {
        if (applyJob?.isActive == true) return
        applyJob = viewModelScope.launch {
            val context = getApplication<Application>()
            val packageName = _uiState.value.selectedPackageName
            if (packageName.isNullOrEmpty()) {
                _uiState.value = _uiState.value.copy(
                    inputError = context.getString(R.string.target_app_select_before_apply)
                )
                return@launch
            }
            try {
                ShowLog.setVpnCapturePackageNames(Collections.singleton(packageName))
                val selectedItem = _uiState.value.statusItems.firstOrNull {
                    it.appInfo.packageName == packageName
                }
                val selectedInfo = selectedItem?.appInfo ?: withContext(Dispatchers.IO) {
                    InstalledAppLoader.loadSingle(context, packageName)
                }
                _uiState.value = _uiState.value.copy(
                    selectedPackageName = packageName,
                    selectedAppInfo = selectedInfo,
                    selectedStatusItem = selectedItem,
                    activePackageName = packageName,
                    activeAppInfo = selectedInfo,
                    activeStatusItem = selectedItem,
                    inputError = null
                )
                _effect.tryEmit(
                    TargetAppManagerEffect.ShowMessage(
                        context.getString(R.string.capture_app_apply_success, formatCurrentPackages()),
                        false
                    )
                )
            } catch (exception: RuntimeException) {
                val reason = exception.message ?: exception.javaClass.simpleName
                _effect.tryEmit(
                    TargetAppManagerEffect.ShowMessage(
                        context.getString(R.string.capture_app_apply_failed, reason),
                        true
                    )
                )
            }
        }
    }

    /**
     * 按调用入口打开指定目标。
     * 为什么这样做：白名单卡与待确认操作分别传入明确包名，避免用户浏览其他应用时打开错误目标。
     */
    private fun openTargetApp(packageName: String?) {
        if (packageName.isNullOrEmpty()) {
            _effect.tryEmit(
                TargetAppManagerEffect.ShowMessage(
                    getApplication<Application>().getString(R.string.target_app_open_target_app_empty),
                    false
                )
            )
            return
        }
        _effect.tryEmit(TargetAppManagerEffect.OpenTargetApp(packageName))
    }

    /**
     * 读取 SDK 已生效的非宿主包名。
     * 为什么这样做：SDK 会自动包含宿主自身，管理页只应把外部 App 作为用户可操作的抓包目标展示。
     */
    private fun resolveCurrentCaptureTargetPackage(): String? {
        val packages = ShowLog.getVpnCapturePackageNames()
        val hostPackage = getApplication<Application>().packageName
        return packages.firstOrNull { it != hostPackage }
    }

    /**
     * 在桌面列表未包含目标时补全应用信息。
     * 为什么这样做：没有启动入口的 App 仍可能被手动配置到白名单，白名单卡不能因此退化成只显示包名。
     */
    private fun loadAppInfo(
        context: Application,
        apps: List<InstalledAppInfo>,
        packageName: String?
    ): InstalledAppInfo? {
        if (packageName.isNullOrEmpty()) return null
        return apps.firstOrNull { it.packageName == packageName }
            ?: InstalledAppLoader.loadSingle(context, packageName)
    }

    /** 为什么这样做：提示中需要展示 SDK 实际持久化后的集合，而不是假设用户选择一定写入成功。 */
    private fun formatCurrentPackages(): String = TextUtils.join(", ", ShowLog.getVpnCapturePackageNames())

    /**
     * 单次刷新结果。
     * 为什么这样做：后台线程先组合完数据，再回主线程提交，避免半成品状态造成白名单卡和列表状态不一致。
     */
    private data class LoadedApps(
        val apps: List<InstalledAppInfo>,
        val items: List<TargetAppStatusItem>,
        val activePackageName: String?,
        val activeAppInfo: InstalledAppInfo?,
        val selectedPackageName: String?,
        val selectedAppInfo: InstalledAppInfo?
    )

    private companion object {
        /** 为什么这样做：一次性事件只需容纳少量连续操作，限制容量避免后台异常时无限积压。 */
        const val EFFECT_BUFFER_CAPACITY = 8
    }
}
