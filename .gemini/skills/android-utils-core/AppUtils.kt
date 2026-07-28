@file:Suppress("unused")

package com.xxx.app.base.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer

import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

import com.bumptech.glide.Glide
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

// ==================== 全局 Context 提供者 ====================

/**
 * 全局 Context 提供者
 * 在 Application.onCreate() 中调用 init(this) 初始化
 */
object ContextProvider {

    private lateinit var appContext: Context

    /** 初始化（Application.onCreate 中调用） */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** 获取全局 Application Context */
    fun get(): Context = appContext
}

// ==================== Toast 扩展 ====================

/** 底部 Toast 提示 */
fun String?.show() {
    if (this.isNullOrEmpty()) return
    Toast.makeText(ContextProvider.get(), this, Toast.LENGTH_SHORT).show()
}

/** 居中 Toast 提示 */
fun String?.showCenter(isLengthLong: Boolean = false) {
    if (this.isNullOrEmpty()) return
    val toast = Toast.makeText(
        ContextProvider.get(),
        this,
        if (isLengthLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
    )
    toast.setGravity(Gravity.CENTER, 0, 0)
    toast.show()
}

/** 仅 Debug 模式下的居中 Toast（项目可自行替换为 BuildConfig 判断） */
fun String?.showCenterDebug(isLengthLong: Boolean = false) {
    this.showCenter(isLengthLong)
}

// ==================== JSON 处理（基于 Gson） ====================

/** 全局 Gson 实例 */
private val gson = Gson()

/** 将任意对象序列化为 JSON 字符串 */
fun Any.json(): String = gson.toJson(this)

/** 将 JSON 字符串反序列化为指定类型 */
fun <T> String.fromJson(classOfT: Class<T>): T? {
    return try {
        gson.fromJson(this, classOfT)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * 通过 JSON 序列化实现深拷贝
 * ⚠️ 注意：对包含泛型参数的集合类型（如 List<String>）无法正确深拷贝，
 * 因 this::class.java 不携带泛型信息，请改用 gson.fromJson(json, object : TypeToken<T>(){}.type)
 */
fun <T : Any> T?.copyFromJson(): T? {
    if (this == null) return null
    val json = gson.toJson(this)
    @Suppress("UNCHECKED_CAST")
    return gson.fromJson(json, this::class.java) as? T
}

/** 格式化 JSON 字符串（用于日志打印） */
fun String.formatJson(): String {
    return try {
        val element = JsonParser.parseString(this)
        GsonBuilder().setPrettyPrinting().create().toJson(element)
    } catch (e: Exception) {
        this
    }
}

// ==================== 时间格式化（Long / Int 扩展） ====================

/** 时间戳格式化 */
fun Long.format(format: String? = null): String {
    return SimpleDateFormat(format ?: "yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(this)
}

/** 毫秒值 → "mm:ss" */
fun Long.formatMillisToMmSs(): String {
    if (this < 0) return "00:00"
    val totalSeconds = this / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

/** 毫秒值 → "HH:mm:ss" */
fun Long.formatHMS(): String {
    if (this <= 0) return "00:00:00"
    val h = this / 3600000
    val m = (this % 3600000) / 60000
    val s = (this % 60000) / 1000
    return String.format("%02d:%02d:%02d", h, m, s)
}

/** 毫秒值 → 分钟数 */
fun Long.formatM(): String {
    return "${max(this / 60000, 0)}"
}

/** 毫秒值 → 小时数（保留 1 位小数） */
fun Long.formatH(): String {
    val hours = this.toDouble() / 3600000.0
    return String.format(Locale.ENGLISH, "%.1f", hours)
}

/** 秒 → "HH:mm:ss" */
fun Int.formatS2Time(): String {
    if (this < 0) return "00:00:00"
    val hours = this / 3600
    val minutes = (this % 3600) / 60
    val seconds = this % 60
    return String.format("%02d:%02d:%02d", hours, minutes, seconds)
}

// ==================== 距离格式化 ====================

/** 米数格式化为 km/m */
fun Long.formatKm(showM: Boolean = true): String {
    return try {
        if (showM && this < 1000) {
            "${this} m"
        } else {
            val km = this.toDouble() / 1000.0
            String.format(Locale.ENGLISH, "%.1f km", km)
        }
    } catch (e: Exception) {
        this.toString()
    }
}

// ==================== DP/PX 转换 ====================

/** Float dp → px（Float 返回） */
fun Float.dp2px(): Float {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this,
        Resources.getSystem().displayMetrics
    )
}

/** Float dp → px（Int 返回） */
fun Float.dp2pxInt(): Int = (this.dp2px() + 0.5f).toInt()

/** Int dp → px（Float 返回） */
fun Int.dp2px(): Float = this.toFloat().dp2px()

/** Int dp → px（Int 返回） */
fun Int.dp2pxInt(): Int = this.toFloat().dp2pxInt()

/** Int dp → px（Int 返回，密度计算） */
fun Int.dpToPx(): Int = (this * Resources.getSystem().displayMetrics.density).toInt()

// ==================== 资源获取 ====================

/** 通过资源 ID 获取字符串 */
fun Int.getResString(): String {
    return ContextProvider.get().getString(this)
}

/** 通过资源 ID 获取尺寸值 */
fun Int.getResDimension(): Int {
    return try {
        ContextProvider.get().resources.getDimension(this).toInt()
    } catch (e: Exception) {
        0
    }
}

/** 通过资源 ID 获取颜色值 */
fun Int.getResColor(): Int {
    return ContextCompat.getColor(ContextProvider.get(), this)
}

// ==================== SpannableString 富文本 ====================

/** 字符串分段设置不同字体大小和颜色 */
fun String.spannable(
    index: Int,
    textSizeStartDp: Float,
    textSizeEndDp: Float,
    textColorStart: Int? = null,
    textColorEnd: Int? = null,
): SpannableStringBuilder {
    val builder = SpannableStringBuilder(this)
    builder.setSpan(AbsoluteSizeSpan(textSizeStartDp.toInt(), true), 0, index, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
    builder.setSpan(AbsoluteSizeSpan(textSizeEndDp.toInt(), true), index, this.length, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
    textColorStart?.let {
        builder.setSpan(ForegroundColorSpan(it), 0, index, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
    }
    textColorEnd?.let {
        builder.setSpan(ForegroundColorSpan(it), index, this.length, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
    }
    return builder
}

/** 字符串分段设置不同颜色（字体大小不变） */
fun String.spannableColor(
    index: Int,
    textColorStart: Int? = null,
    textColorEnd: Int? = null,
): SpannableStringBuilder {
    val builder = SpannableStringBuilder(this)
    textColorStart?.let {
        builder.setSpan(ForegroundColorSpan(it), 0, index, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
    }
    textColorEnd?.let {
        builder.setSpan(ForegroundColorSpan(it), index, this.length, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
    }
    return builder
}

// ==================== 剪切板与设备信息 ====================

/** 复制文本到系统剪切板 */
fun String.copyToClipboard(showToast: Boolean = true) {
    val clipboard = ContextProvider.get()
        .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("text", this)
    clipboard.setPrimaryClip(clip)
    if (showToast) "已复制到剪切板".show()
}

/** 获取设备 Android ID */
fun loadAndroidId(): String {
    return Settings.Secure.getString(
        ContextProvider.get().contentResolver,
        Settings.Secure.ANDROID_ID
    )
}

// ==================== 文件工具 ====================

/** 文件操作工具（使用 ContextProvider 获取路径） */
object FileUtils {
    /** 获取应用私有缓存目录下的子目录 */
    fun getCacheDir(subDir: String): File {
        val dir = File(ContextProvider.get().cacheDir, subDir)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** 获取应用私有文件目录下的子目录 */
    fun getFilesDir(subDir: String): File {
        val dir = File(ContextProvider.get().filesDir, subDir)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** 删除文件或目录（递归） */
    fun delete(file: File): Boolean {
        return if (file.isDirectory) file.deleteRecursively() else file.delete()
    }

    /** 计算目录大小（字节） */
    fun calculateSize(dir: File): Long {
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /** 格式化文件大小 */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes} B"
            bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format(Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }
}

// ==================== 点击防抖与权限设置页 ====================

private val clickTimeMap = WeakHashMap<View, Long>()

/** 统一点击防抖 */
fun View.setOnClickListenerThrottle(interval: Long = 500L, block: (View) -> Unit) {
    setOnClickListener { view ->
        val now = SystemClock.elapsedRealtime()
        val last = clickTimeMap[view] ?: 0L
        if (now - last >= interval) {
            clickTimeMap[view] = now
            block(view)
        }
    }
}

/** 400ms 防抖点击 */
fun View.setOnClickListener400(block: (View) -> Unit) {
    setOnClickListenerThrottle(400L, block)
}

/** 500ms 防抖点击 */
fun View.setOnClickListener500(block: (View) -> Unit) {
    setOnClickListenerThrottle(500L, block)
}

/** Compose 场景点击防抖包装 */
fun throttleClick(interval: Long = 500L, block: () -> Unit): () -> Unit {
    @Volatile var lastClickTime = 0L
    return {
        val now = SystemClock.elapsedRealtime()
        if (now - lastClickTime >= interval) {
            lastClickTime = now
            block()
        }
    }
}

/** 跳转应用权限设置页 */
fun Context.openAppSettings() {
    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}

// ==================== 协程 / LiveData / Flow 工具 ====================

/** 非粘性 LiveData */
open class BaseNotStickyLiveData<T>(
    private val onHandlerError: ((e: Exception) -> Unit) = { it.printStackTrace() },
    private val onHandlerErrorTask: ((t: T) -> Unit)? = null
) : MutableLiveData<T>() {
    private val observerProcessedHashCodes = ConcurrentHashMap<Int, Long>()
    private val observerMap = ConcurrentHashMap<Observer<in T>, Observer<in T>>()
    private var lastUpdateTime: Long = 0

    override fun observe(owner: LifecycleOwner, observer: Observer<in T>) {
        val wrappedObserver = observerMap.getOrPut(observer) { createWrappedObserver(observer) }
        super.observe(owner, wrappedObserver)
    }

    override fun removeObserver(observer: Observer<in T>) {
        val wrappedObserver: Observer<in T> = observerMap.remove(observer) ?: observer
        val identityHashCode = System.identityHashCode(wrappedObserver)
        observerProcessedHashCodes.remove(identityHashCode)
        super.removeObserver(wrappedObserver)
    }

    override fun observeForever(observer: Observer<in T>) {
        val wrappedObserver = observerMap.getOrPut(observer) { createWrappedObserver(observer) }
        super.observeForever(wrappedObserver)
    }

    override fun postValue(value: T) {
        lastUpdateTime = SystemClock.elapsedRealtime()
        super.postValue(value)
    }

    override fun setValue(value: T) {
        lastUpdateTime = SystemClock.elapsedRealtime()
        super.setValue(value)
    }

    protected open fun createWrappedObserver(originalObserver: Observer<in T>): Observer<T> {
        val eventually = object : Observer<T> {
            override fun onChanged(value: T) {
                try {
                    val identityHashCode = System.identityHashCode(this)
                    val observerCreateTime = observerProcessedHashCodes[identityHashCode] ?: 0
                    if (lastUpdateTime > observerCreateTime) {
                        originalObserver.onChanged(value)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    onHandlerError.invoke(e)
                    onHandlerErrorTask?.invoke(value)
                }
            }
        }
        val identityHashCode = System.identityHashCode(eventually)
        observerProcessedHashCodes[identityHashCode] = SystemClock.elapsedRealtime()
        return eventually
    }
}

/** 创建非粘性 LiveData */
fun <T> createNonStickyLiveData(): BaseNotStickyLiveData<T> = BaseNotStickyLiveData()

/** 创建非粘性 SharedFlow（replay = 0） */
fun <T> createNonStickyShareFlow(): MutableSharedFlow<T> = MutableSharedFlow(replay = 0)



/** Flow 生命周期感知观察 */
fun <T> Flow<T>.observe(
    owner: LifecycleOwner,
    minState: Lifecycle.State = Lifecycle.State.STARTED,
    collector: suspend (T) -> Unit
) {
    owner.lifecycleScope.launch {
        owner.repeatOnLifecycle(minState) {
            this@observe.collect { collector(it) }
        }
    }
}

/** 统一将第三方回调安全恢复为挂起结果 */
@OptIn(InternalCoroutinesApi::class)
fun <T> CancellableContinuation<T>.tryResumeYourself(t: T) {
    this.tryResume(t)?.also { value ->
        this.completeResume(value)
    }
}

/** 全局协程异常处理器 */
val globalCoroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
    "全局协程异常: ${throwable.message}".loge()
    throwable.loge()
}

/** 项目级全局作用域（按需使用） */
val appScope = CoroutineScope(
    SupervisorJob() +
        Dispatchers.IO +
        globalCoroutineExceptionHandler
)

// ==================== 图片加载 ====================

/** ImageView 图片加载扩展 */
fun ImageView.loadImage(
    url: Any?,
    isCircle: Boolean = false,
    roundedCorners: Int = 0,
    placeholder: Int = 0,
    error: Int = 0,
    skipMemoryCache: Boolean = false,
    diskCacheStrategy: DiskCacheStrategy = DiskCacheStrategy.AUTOMATIC
) {
    if (url == null) return
    var options = RequestOptions()
        .skipMemoryCache(skipMemoryCache)
        .diskCacheStrategy(diskCacheStrategy)
    if (placeholder != 0) options = options.placeholder(placeholder)
    if (error != 0) options = options.error(error)

    val builder = when (url) {
        is Uri -> Glide.with(context).load(url)
        is Int -> Glide.with(context).load(url)
        else -> Glide.with(context).load(url.toString())
    }
    val request = builder.apply(options)
    when {
        isCircle -> request.apply(RequestOptions.bitmapTransform(CircleCrop())).into(this)
        roundedCorners > 0 -> request.transform(CenterCrop(), RoundedCorners(roundedCorners.dpToPx())).into(this)
        else -> request.into(this)
    }
}

/** Compose 通用图片组件（如项目使用 Compose 可直接复用） */
@Composable
fun AppImage(
    model: Any?,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    isCircle: Boolean = false,
    roundedCorners: Int = 0,
    placeholder: Int = 0,
    error: Int = 0,
    contentScale: ContentScale = ContentScale.Crop
) {
    GlideImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier.then(
            when {
                isCircle -> Modifier.clip(CircleShape)
                roundedCorners > 0 -> Modifier.clip(RoundedCornerShape(roundedCorners.dp))
                else -> Modifier
            }
        ),
        contentScale = contentScale
    ) {
        if (placeholder != 0) it.placeholder(placeholder)
        if (error != 0) it.error(error)
        it.diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
    }
}

// ==================== 日志系统 ====================

private const val LOG_TAG = "AppUtils"

/** 日志开关：默认使用 BuildConfig.DEBUG，复制到业务项目后确认 BuildConfig 导入路径正确 */
private val LOG_ENABLED: Boolean = BuildConfig.DEBUG

/** Debug 日志 */
fun String?.logd(tag: String = LOG_TAG) {
    if (this.isNullOrEmpty()) return
    if (LOG_ENABLED) Log.d(tag, this)
    LogManager.log(message = "[$tag] $this", type = "debug")
}

/** Error 日志（字符串） */
fun String?.loge(tag: String = LOG_TAG) {
    if (this.isNullOrEmpty()) return
    if (LOG_ENABLED) Log.e(tag, this)
    LogManager.log(message = "[ERROR][$tag] $this", type = "error")
}

/** Error 日志（异常） */
fun Exception?.loge(tag: String = LOG_TAG) {
    if (this == null) return
    val msg = this.stackTraceToString()
    if (LOG_ENABLED) Log.e(tag, msg)
    LogManager.log(message = "[EXCEPTION][$tag] $msg", type = "error")
}

/** Error 日志（Throwable，用于 CoroutineExceptionHandler 等场景） */
fun Throwable?.loge(tag: String = LOG_TAG) {
    if (this == null) return
    val msg = this.stackTraceToString()
    if (LOG_ENABLED) Log.e(tag, msg)
    LogManager.log(message = "[THROWABLE][$tag] $msg", type = "error")
}

/** HTTP 请求日志 */
fun String?.logHttp(tag: String = "HTTP") {
    if (this.isNullOrEmpty()) return
    if (LOG_ENABLED) splitAndLog(tag)
    LogManager.log(message = "[$tag] $this", type = "http")
}

private fun String.splitAndLog(tag: String) {
    val maxLength = 2000
    if (length <= maxLength) {
        Log.d(tag, this)
        return
    }
    var start = 0
    while (start < length) {
        val end = (start + maxLength).coerceAtMost(length)
        Log.d(tag, substring(start, end))
        start = end
    }
}

/** 异步文件日志管理器 */
object LogManager {
    private data class LogEntry(val message: String, val type: String)
    private val logChannel = Channel<LogEntry>(Channel.UNLIMITED)
    private lateinit var baseLogDir: String
    private var isInitialized = false
    private var writeCount = 0
    private const val MAX_DIR_SIZE = 100L * 1024 * 1024
    private const val CLEAN_CHECK_INTERVAL = 100
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 初始化：Application.onCreate() 中调用 */
    fun init(context: Context, appSpecificBasePath: String) {
        if (isInitialized) return
        baseLogDir = appSpecificBasePath
        isInitialized = true
        scope.launch {
            for (entry in logChannel) {
                writeLogToFile(entry)
            }
        }
    }

    /** 写入日志（非阻塞） */
    fun log(message: String, type: String = "default") {
        if (!isInitialized) return
        logChannel.trySend(LogEntry(message, type))
    }

    private fun writeLogToFile(entry: LogEntry) {
        try {
            val now = Date()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val hourFormat = SimpleDateFormat("HH", Locale.getDefault())
            val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

            val logDir = File(baseLogDir, "${entry.type}/${dateFormat.format(now)}")
            if (!logDir.exists()) logDir.mkdirs()

            val logFile = File(logDir, "${hourFormat.format(now)}.log")
            val logLine = "[${timeFormat.format(now)}] ${entry.message}\n"
            logFile.appendText(logLine)

            writeCount++
            if (writeCount >= CLEAN_CHECK_INTERVAL) {
                writeCount = 0
                checkAndCleanLogDirectory(entry.type)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkAndCleanLogDirectory(type: String) {
        val typeDir = File(baseLogDir, type)
        if (!typeDir.exists()) return
        val totalSize = typeDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        if (totalSize <= MAX_DIR_SIZE) return

        val dateDirs = typeDir.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: return
        for (dir in dateDirs) {
            dir.deleteRecursively()
            if (typeDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } <= MAX_DIR_SIZE * 0.8) break
        }
    }
}

// ==================== 全局异常处理 ====================

/** 全局未捕获异常处理器（崩溃日志通过 FileUtils 写入本地文件，无需 MMKV） */
object CrashHandler : Thread.UncaughtExceptionHandler {

    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private const val CRASH_DIR = "crash_logs"
    private const val MAX_CRASH_COUNT = 50

    /** 初始化（Application.onCreate 调用） */
    fun init() {
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        val crashLog = buildString {
            appendLine("===== CRASH LOG =====")
            appendLine("时间: ${System.currentTimeMillis().format("yyyy-MM-dd HH:mm:ss")}")
            appendLine("线程: ${thread.name}")
            appendLine("异常: ${throwable.stackTraceToString()}")
        }
        crashLog.loge()
        saveCrashToFile(crashLog)
        defaultHandler?.uncaughtException(thread, throwable)
    }

    private fun saveCrashToFile(log: String) {
        try {
            val dir = FileUtils.getFilesDir(CRASH_DIR)
            // 超出上限时删除最旧的文件
            val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: emptyList()
            if (files.size >= MAX_CRASH_COUNT) {
                files.take(files.size - MAX_CRASH_COUNT + 1).forEach { it.delete() }
            }
            File(dir, "${System.currentTimeMillis()}.log").writeText(log)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** 获取所有崩溃记录（最新在前） */
    fun getCrashList(): List<String> {
        return try {
            FileUtils.getFilesDir(CRASH_DIR)
                .listFiles()
                ?.filter { it.isFile && it.name.endsWith(".log") }
                ?.sortedByDescending { it.lastModified() }
                ?.map { it.readText() }
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 清空崩溃记录 */
    fun clearCrashList() {
        try {
            FileUtils.getFilesDir(CRASH_DIR).listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
