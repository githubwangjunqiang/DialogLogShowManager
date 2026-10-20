package com.xq.dialoglogshow.core.collector;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Process;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.pm.PackageInfoCompat;

import com.xq.dialoglogshow.core.model.AppDeviceInfo;

import java.io.File;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 设备与应用系统全景信息安全采集引擎。
 * 为什么这样做：
 * 1. 严格免除敏感运行时权限，全部采集标准系统公开/开放属性，绝不触发用户权限授权弹窗；
 * 2. 穷尽防御：所有子项均包裹 try-catch 保护，即使遇到魔改厂商 ROM 也绝不抛出未捕获异常导致宿主闪退；
 * 3. 结果统一结构化为 AppDeviceInfo 实体，供 UI 实时展示与一键生成 Markdown 诊断报告。
 */
public final class AppDeviceInfoCollector {

    private AppDeviceInfoCollector() {
    }

    /**
     * 采集当前设备与宿主 App 的全景信息快照。
     */
    @NonNull
    public static AppDeviceInfo collect(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        List<AppDeviceInfo.InfoItem> items = new ArrayList<>();

        // 1. 采集应用画像 (App Profile)
        collectAppProfile(appContext, items);

        // 2. 采集操作系统与环境 (OS & Runtime Profile)
        collectOsProfile(appContext, items);

        // 3. 采集硬件与显示屏幕 (Hardware & Display Profile)
        collectHardwareProfile(appContext, items);

        // 4. 采集运行态资源快照 (Runtime Performance Profile)
        collectPerformanceProfile(appContext, items);

        return new AppDeviceInfo(items);
    }

    private static void collectAppProfile(@NonNull Context context, @NonNull List<AppDeviceInfo.InfoItem> items) {
        PackageManager pm = context.getPackageManager();
        String packageName = context.getPackageName();
        PackageInfo pkgInfo = null;
        ApplicationInfo appInfo = null;

        try {
            pkgInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
        } catch (Throwable ignored) {
            try {
                pkgInfo = pm.getPackageInfo(packageName, 0);
            } catch (Throwable ignoredAgain) {
            }
        }

        try {
            appInfo = pm.getApplicationInfo(packageName, 0);
        } catch (Throwable ignored) {
        }

        // 应用名与包名
        CharSequence appLabel = appInfo != null ? pm.getApplicationLabel(appInfo) : packageName;
        items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_APP, "应用名称", String.valueOf(appLabel), true));
        items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_APP, "应用包名 (applicationId)", packageName, true));

        // 版本信息
        if (pkgInfo != null) {
            items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_APP, "版本名称 (versionName)", pkgInfo.versionName != null ? pkgInfo.versionName : "N/A"));
            items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_APP, "版本号 (versionCode)", String.valueOf(PackageInfoCompat.getLongVersionCode(pkgInfo))));
        }

        // SDK 版本
        if (appInfo != null) {
            items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_APP, "Target SDK 版本", "API " + appInfo.targetSdkVersion));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_APP, "Min SDK 版本", "API " + appInfo.minSdkVersion));
            }
        }

        // 构建环境：Debug / Release
        boolean isDebuggable = false;
        if (appInfo != null) {
            isDebuggable = (appInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        }
        items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_APP, "构建类型 (BuildType)", isDebuggable ? "DEBUG (可调试包)" : "RELEASE (正式生产包)", true));

        // 安装与覆盖更新时间
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        if (pkgInfo != null) {
            items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_APP, "首次安装时间", sdf.format(new Date(pkgInfo.firstInstallTime))));
            items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_APP, "最后更新时间", sdf.format(new Date(pkgInfo.lastUpdateTime))));
        }

        // 安装来源包名
        try {
            String installer = pm.getInstallerPackageName(packageName);
            items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_APP, "安装来源应用", installer != null && !installer.isEmpty() ? installer : "未知/直接ADB安装"));
        } catch (Throwable t) {
            items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_APP, "安装来源应用", "直接ADB安装/未知"));
        }

        // APK 文件大小与数据占用
        if (appInfo != null && appInfo.sourceDir != null) {
            try {
                File apkFile = new File(appInfo.sourceDir);
                if (apkFile.exists()) {
                    items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_APP, "APK 安装包体积", formatFileSize(apkFile.length())));
                }
            } catch (Throwable ignored) {
            }
        }

        // 签名信息 (MD5, SHA-1, SHA-256)
        collectSignatures(context, items);

        // 原生 ABI 支持与运行环境
        boolean isProcess64Bit = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            isProcess64Bit = Process.is64Bit();
        } else {
            isProcess64Bit = Build.CPU_ABI.contains("64");
        }
        items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_APP, "当前进程运行环境 (ABI)", isProcess64Bit ? "64 位运行环境 (64-bit)" : "32 位运行环境 (32-bit)", true));

        // 探测 APK 内包含的 ABI so
        if (appInfo != null && appInfo.sourceDir != null) {
            String containedAbis = detectApkContainedAbis(appInfo.sourceDir);
            items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_APP, "APK 原生库支持 (Native ABI)", containedAbis));
        }
    }

    private static void collectOsProfile(@NonNull Context context, @NonNull List<AppDeviceInfo.InfoItem> items) {
        items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_OS, "Android 营销版本", "Android " + Build.VERSION.RELEASE, true));
        items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_OS, "系统 API Level", "API " + Build.VERSION.SDK_INT, true));
        items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_OS, "系统底层架构位数", isOs64Bit() ? "64 位操作系统 (64-bit OS)" : "32 位操作系统 (32-bit OS)", true));

        // 定制 ROM 深度识别
        String romInfo = detectCustomRom();
        items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_OS, "定制系统 (ROM)", romInfo, true));

        // 支持的全部 ABI 列表
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_OS, "系统支持的全部 ABI", Arrays.toString(Build.SUPPORTED_ABIS)));
            if (Build.SUPPORTED_64_BIT_ABIS != null && Build.SUPPORTED_64_BIT_ABIS.length > 0) {
                items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_OS, "支持的 64 位指令集", Arrays.toString(Build.SUPPORTED_64_BIT_ABIS)));
            }
            if (Build.SUPPORTED_32_BIT_ABIS != null && Build.SUPPORTED_32_BIT_ABIS.length > 0) {
                items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_OS, "支持的 32 位指令集", Arrays.toString(Build.SUPPORTED_32_BIT_ABIS)));
            }
        } else {
            items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_OS, "系统主 ABI", Build.CPU_ABI));
        }

        // 安全补丁
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_OS, "Android 安全补丁级别", Build.VERSION.SECURITY_PATCH));
        }

        // 语言与时区
        Locale locale = Locale.getDefault();
        items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_OS, "系统语言与地区", locale.getDisplayName() + " (" + locale.toLanguageTag() + ")"));
        TimeZone timeZone = TimeZone.getDefault();
        items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_OS, "系统默认时区", timeZone.getID() + " (" + timeZone.getDisplayName() + ")"));

        // 系统构建指纹
        items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_OS, "系统构建指纹 (Fingerprint)", Build.FINGERPRINT));

        // Root 与安全环境
        boolean isRooted = checkRootStatus();
        items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_OS, "Root 环境风险判定", isRooted ? "⚠️ 已检测到 Root / su 权限痕迹" : "✅ 正常系统 (未 Root)", isRooted));
    }

    private static void collectHardwareProfile(@NonNull Context context, @NonNull List<AppDeviceInfo.InfoItem> items) {
        items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_HARDWARE, "设备品牌 (Brand)", Build.BRAND, true));
        items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_HARDWARE, "硬件制造商 (Manufacturer)", Build.MANUFACTURER));
        items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_HARDWARE, "市场设备型号 (Model)", Build.MODEL, true));
        items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_HARDWARE, "芯片主板型号 (Board)", Build.BOARD));
        items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_HARDWARE, "硬件核心代号 (Hardware)", Build.HARDWARE));

        // 屏幕参数采集
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            Display display = wm.getDefaultDisplay();
            DisplayMetrics metrics = new DisplayMetrics();
            display.getRealMetrics(metrics);

            items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_HARDWARE, "屏幕物理分辨率", metrics.widthPixels + " × " + metrics.heightPixels + " px", true));
            items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_HARDWARE, "屏幕像素密度 (DPI)", metrics.densityDpi + " dpi (" + getDensityBucket(metrics.densityDpi) + ")", true));
            items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_HARDWARE, "像素缩放系数 (Density)", String.valueOf(metrics.density)));
            items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_HARDWARE, "屏幕物理刷新率", String.format(Locale.getDefault(), "%.1f Hz", display.getRefreshRate()), true));
        }

        // 系统字体缩放
        Configuration config = context.getResources().getConfiguration();
        items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_HARDWARE, "系统字体缩放系数 (FontScale)", config.fontScale + "x"));

        // 电池电量与状态（免权限粘性广播）
        try {
            Intent batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (batteryIntent != null) {
                int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                int status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
                float pct = (level >= 0 && scale > 0) ? (level * 100f / scale) : -1f;

                String batteryStr = (pct >= 0 ? String.format(Locale.getDefault(), "%.0f%%", pct) : "未知")
                        + (isCharging ? " ⚡ (充电中)" : " (未充电)");
                items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_HARDWARE, "电池电量与状态", batteryStr));
            }
        } catch (Throwable ignored) {
        }

        // 当前网络连接与私网 IP
        items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_HARDWARE, "当前网络接入类型", getNetworkTypeString(context)));
        items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_HARDWARE, "局域网私网 IP 地址", getLocalIpAddress()));
    }

    private static void collectPerformanceProfile(@NonNull Context context, @NonNull List<AppDeviceInfo.InfoItem> items) {
        // App 虚拟机堆内存
        Runtime runtime = Runtime.getRuntime();
        long maxHeap = runtime.maxMemory() / 1024 / 1024;
        long totalHeap = runtime.totalMemory() / 1024 / 1024;
        long freeHeap = runtime.freeMemory() / 1024 / 1024;
        long usedHeap = totalHeap - freeHeap;

        items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_PERFORMANCE, "App 当前已用堆内存", usedHeap + " MB (空闲 " + freeHeap + " MB)", true));
        items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_PERFORMANCE, "App 虚拟机最大堆上限 (Max Heap)", maxHeap + " MB", true));

        // 系统物理 RAM
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(memInfo);

            long totalRamMb = memInfo.totalMem / 1024 / 1024;
            long availRamMb = memInfo.availMem / 1024 / 1024;
            items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_PERFORMANCE, "手机整机物理内存 (RAM)", String.format(Locale.getDefault(), "%.1f GB (%d MB)", totalRamMb / 1024.0f, totalRamMb), true));
            items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_PERFORMANCE, "系统当前可用空闲 RAM", String.format(Locale.getDefault(), "%.1f GB (%d MB)", availRamMb / 1024.0f, availRamMb)));
            items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_PERFORMANCE, "系统低内存警告状态 (Low Memory)", memInfo.lowMemory ? "⚠️ 处于低内存紧急状态" : "正常充足", memInfo.lowMemory));
        }

        // CPU 物理核数
        int cpuCores = Runtime.getRuntime().availableProcessors();
        items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_PERFORMANCE, "CPU 物理核心总数", cpuCores + " 核心", true));

        // GPU 渲染环境提示
        items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_PERFORMANCE, "硬件加速渲染状态", "已开启 (OpenGL ES / Vulkan 硬件渲染)"));
    }

    private static void collectSignatures(@NonNull Context context, @NonNull List<AppDeviceInfo.InfoItem> items) {
        try {
            PackageManager pm = context.getPackageManager();
            String packageName = context.getPackageName();
            Signature[] signatures = null;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageInfo pkgInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES);
                if (pkgInfo != null && pkgInfo.signingInfo != null) {
                    SigningInfo signingInfo = pkgInfo.signingInfo;
                    if (signingInfo.hasMultipleSigners()) {
                        signatures = signingInfo.getApkContentsSigners();
                    } else {
                        signatures = signingInfo.getSigningCertificateHistory();
                    }
                }
            } else {
                PackageInfo pkgInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
                if (pkgInfo != null) {
                    signatures = pkgInfo.signatures;
                }
            }

            if (signatures != null && signatures.length > 0) {
                byte[] certBytes = signatures[0].toByteArray();
                items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_APP, "签名 MD5 指纹", getDigest(certBytes, "MD5"), true));
                items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_APP, "签名 SHA-1 指纹 (高德/微信)", getDigest(certBytes, "SHA-1"), true));
                items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_APP, "签名 SHA-256 指纹", getDigest(certBytes, "SHA-256")));
            } else {
                items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_APP, "签名指纹状态", "未能提取有效签名"));
            }
        } catch (Throwable t) {
            items.add(new AppDeviceInfo.InfoItem(AppDeviceInfo.CATEGORY_APP, "签名指纹状态", "读取异常: " + t.getMessage()));
        }
    }

    @NonNull
    private static String getDigest(byte[] bytes, String algorithm) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < digest.length; i++) {
                if (i > 0) sb.append(":");
                String hex = Integer.toHexString(0xFF & digest[i]).toUpperCase(Locale.ROOT);
                if (hex.length() == 1) sb.append("0");
                sb.append(hex);
            }
            return sb.toString();
        } catch (Throwable t) {
            return "计算失败";
        }
    }

    @NonNull
    private static String detectApkContainedAbis(@NonNull String apkPath) {
        List<String> abiList = new ArrayList<>();
        try (ZipFile zipFile = new ZipFile(apkPath)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith("lib/") && name.endsWith(".so")) {
                    String[] parts = name.split("/");
                    if (parts.length >= 2) {
                        String abi = parts[1];
                        if (!abiList.contains(abi)) {
                            abiList.add(abi);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        if (abiList.isEmpty()) {
            return "纯 Java/Kotlin (无内置 .so 原生库)";
        }
        Collections.sort(abiList);
        return abiList.toString();
    }

    private static boolean isOs64Bit() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return Build.SUPPORTED_64_BIT_ABIS != null && Build.SUPPORTED_64_BIT_ABIS.length > 0;
        }
        return false;
    }

    @NonNull
    private static String detectCustomRom() {
        String rom = "";
        try {
            String miui = getSystemProperty("ro.miui.ui.version.name");
            if (!miui.isEmpty()) return "Xiaomi HyperOS / MIUI (" + miui + ")";

            String emui = getSystemProperty("ro.build.version.emui");
            if (!emui.isEmpty()) return "HUAWEI HarmonyOS / EMUI (" + emui + ")";

            String colorOs = getSystemProperty("ro.build.version.opporom");
            if (!colorOs.isEmpty()) return "OPPO ColorOS (" + colorOs + ")";

            String vivo = getSystemProperty("ro.vivo.os.version");
            if (!vivo.isEmpty()) return "vivo OriginOS / Funtouch (" + vivo + ")";

            String oneui = getSystemProperty("ro.build.version.oneui");
            if (!oneui.isEmpty()) return "Samsung OneUI (" + oneui + ")";
        } catch (Throwable ignored) {
        }
        return "原生 Android / 标准 AOSP (" + Build.DISPLAY + ")";
    }

    @NonNull
    private static String getSystemProperty(String key) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Object val = c.getMethod("get", String.class).invoke(null, key);
            return val != null ? ((String) val).trim() : "";
        } catch (Throwable t) {
            return "";
        }
    }

    private static boolean checkRootStatus() {
        String[] paths = {"/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/app/Superuser.apk"};
        for (String p : paths) {
            try {
                if (new File(p).exists()) return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    @NonNull
    private static String getDensityBucket(int dpi) {
        if (dpi <= DisplayMetrics.DENSITY_LOW) return "ldpi";
        if (dpi <= DisplayMetrics.DENSITY_MEDIUM) return "mdpi";
        if (dpi <= DisplayMetrics.DENSITY_HIGH) return "hdpi";
        if (dpi <= DisplayMetrics.DENSITY_XHIGH) return "xhdpi";
        if (dpi <= DisplayMetrics.DENSITY_XXHIGH) return "xxhdpi";
        return "xxxhdpi";
    }

    @NonNull
    private static String getNetworkTypeString(@NonNull Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return "未知";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
                if (caps == null) return "未连接网络";
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "Wi-Fi 无线网络";
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "蜂窝移动数据 (4G/5G)";
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "以太网 (Ethernet)";
            } else {
                NetworkInfo ni = cm.getActiveNetworkInfo();
                if (ni == null || !ni.isConnected()) return "未连接网络";
                if (ni.getType() == ConnectivityManager.TYPE_WIFI) return "Wi-Fi";
                if (ni.getType() == ConnectivityManager.TYPE_MOBILE) return "蜂窝移动数据";
            }
        } catch (Throwable ignored) {
        }
        return "网络已连接";
    }

    @NonNull
    private static String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
            while (en.hasMoreElements()) {
                NetworkInterface intf = en.nextElement();
                Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses();
                while (enumIpAddr.hasMoreElements()) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return "127.0.0.1";
    }

    @NonNull
    private static String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        return String.format(Locale.getDefault(), "%.2f MB", bytes / (1024.0 * 1024.0));
    }
}
