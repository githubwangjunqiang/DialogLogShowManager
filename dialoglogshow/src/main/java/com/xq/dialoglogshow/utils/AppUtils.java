package com.xq.dialoglogshow.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import com.xq.dialoglogshow.R;
import com.xq.dialoglogshow.entity.BaseShowData;

/**
 * Description:
 * Author:王俊强 980766134@qq.com
 * Date:2025/12/22
 */
public class AppUtils {


    /**
     * 获取App版本码
     *
     * @param context     上下文
     * @param packageName 包名
     * @return App版本码
     */
    public static int getAppVersionCode(Context context, String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return -1;
        }
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(packageName, 0);
            return pi == null ? -1 : pi.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return -1;
        }
    }


    /**
     * 字符串中间拆分（奇数长度时前半部分更长）
     * @param str 要拆分的原始字符串
     * @return Pair 拆分后的前后两部分（first：前半部分，second：后半部分）
     */
    public static Pair<String, String> splitStringCenter(String str) {
        // 对应Kotlin的空字符串判断，兼容str为null的情况（比Kotlin更严谨）
        if (TextUtils.isEmpty(str)) {
            return new Pair<>("", "");
        }
        // 核心逻辑：与Kotlin完全一致，(长度 + 1) / 2 实现奇数长度前半部分更长
        int middleIndex = (str.length() + 1) / 2;
        // 对应Kotlin的substring方法，Java substring(int beginIndex, int endIndex) 语义完全一致
        String firstPart = str.substring(0, middleIndex);
        // 对应Kotlin的substring(startIndex)，Java同样支持该重载方法
        String secondPart = str.substring(middleIndex);
        // Java中使用androidx的Pair（或java.util.AbstractMap.SimpleEntry，二选一）
        return new Pair<>(firstPart, secondPart);
    }

    /**
     * 预制获取 App 及系统的基础诊断信息
     * 为什么这样做：在“其他信息”Tab 中提供自动集成的包名、版本、安装更新时间与安装来源等关键诊断数据，
     * 便于开发者排查不同环境或安装通道下的应用运行状态，无需每次手动编码传入。
     *
     * @param context 应用上下文
     * @return 预制的诊断数据列表
     */
    @NonNull
    public static java.util.ArrayList<BaseShowData> getPresetAppInformation(Context context) {
        java.util.ArrayList<BaseShowData> list = new java.util.ArrayList<>();
        if (context == null) {
            return list;
        }
        Context appContext = context.getApplicationContext();
        PackageManager pm = appContext.getPackageManager();
        String packageName = appContext.getPackageName();

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
        StringBuilder sb = new StringBuilder();

        // -------------------------------------------------------------
        // 1. APK 及其编译基础信息
        // -------------------------------------------------------------
        sb.append("===== APK & 编译信息 =====\n");
        sb.append(appContext.getString(R.string.show_sdk_app_package_name)).append(": ").append(packageName).append("\n");

        try {
            PackageInfo pi = pm.getPackageInfo(packageName, 0);
            if (pi != null) {
                // 版本名称
                String versionName = pi.versionName == null ? appContext.getString(R.string.show_sdk_unknown) : pi.versionName;
                sb.append(appContext.getString(R.string.show_sdk_app_version_name)).append(": ").append(versionName).append("\n");

                // 版本号
                long versionCode;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    versionCode = pi.getLongVersionCode();
                } else {
                    versionCode = pi.versionCode;
                }
                sb.append(appContext.getString(R.string.show_sdk_app_version_code)).append(": ").append(versionCode).append("\n");

                // Target SDK & Min SDK
                if (pi.applicationInfo != null) {
                    sb.append(appContext.getString(R.string.show_sdk_app_target_sdk)).append(": ").append(pi.applicationInfo.targetSdkVersion).append("\n");
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        sb.append(appContext.getString(R.string.show_sdk_app_min_sdk)).append(": ").append(pi.applicationInfo.minSdkVersion).append("\n");
                    }
                    boolean isDebug = (pi.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
                    sb.append(appContext.getString(R.string.show_sdk_app_build_type)).append(": ").append(isDebug ? "Debug (可调试)" : "Release (正式版本)").append("\n");
                }

                // APK 内置 Native 库 CPU 架构
                sb.append(appContext.getString(R.string.show_sdk_apk_cpu_abi)).append(": ").append(getApkSupportedAbis(appContext)).append("\n");

                // 安装与更新时间
                sb.append(appContext.getString(R.string.show_sdk_app_install_time)).append(": ")
                        .append(sdf.format(new java.util.Date(pi.firstInstallTime))).append("\n");
                sb.append(appContext.getString(R.string.show_sdk_app_update_time)).append(": ")
                        .append(sdf.format(new java.util.Date(pi.lastUpdateTime))).append("\n");
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        // 安装来源
        String installer = null;
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                android.content.pm.InstallSourceInfo info = pm.getInstallSourceInfo(packageName);
                if (info != null) {
                    installer = info.getInstallingPackageName();
                }
            } else {
                installer = pm.getInstallerPackageName(packageName);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (TextUtils.isEmpty(installer)) {
            installer = "系统/直接安装 (Direct Install)";
        }
        sb.append(appContext.getString(R.string.show_sdk_app_install_source)).append(": ").append(installer).append("\n");

        // -------------------------------------------------------------
        // 2. 手机 & 硬件信息
        // -------------------------------------------------------------
        sb.append("\n===== 手机 & 硬件信息 =====\n");
        sb.append(appContext.getString(R.string.show_sdk_app_device_model)).append(": ")
                .append(android.os.Build.MANUFACTURER).append(" ").append(android.os.Build.MODEL)
                .append(" (").append(android.os.Build.BRAND).append(")\n");

        // CPU 架构 (ABI)
        sb.append(appContext.getString(R.string.show_sdk_device_cpu_abi)).append(": ")
                .append(java.util.Arrays.toString(android.os.Build.SUPPORTED_ABIS)).append("\n");

        // 屏幕分辨率与 DPI
        android.util.DisplayMetrics dm = appContext.getResources().getDisplayMetrics();
        sb.append(appContext.getString(R.string.show_sdk_device_screen)).append(": ")
                .append(dm.widthPixels).append("x").append(dm.heightPixels)
                .append(" (").append(dm.densityDpi).append(" dpi)\n");

        // 内存 RAM & 堆大小
        long maxMemoryMb = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        android.app.ActivityManager am = (android.app.ActivityManager) appContext.getSystemService(Context.ACTIVITY_SERVICE);
        long totalRamMb = 0;
        if (am != null) {
            android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            totalRamMb = mi.totalMem / (1024 * 1024);
        }
        sb.append(appContext.getString(R.string.show_sdk_device_ram)).append(": ")
                .append("Max Heap: ").append(maxMemoryMb).append("MB / System RAM: ").append(totalRamMb).append("MB\n");

        // -------------------------------------------------------------
        // 3. Android 系统 & 环境信息
        // -------------------------------------------------------------
        sb.append("\n===== 系统 & 环境信息 =====\n");
        sb.append(appContext.getString(R.string.show_sdk_app_system_version)).append(": ")
                .append("Android ").append(android.os.Build.VERSION.RELEASE)
                .append(" (API ").append(android.os.Build.VERSION.SDK_INT).append(")\n");

        sb.append(appContext.getString(R.string.show_sdk_device_language)).append(": ")
                .append(java.util.Locale.getDefault().toString())
                .append(" / ").append(java.util.TimeZone.getDefault().getID()).append("\n");

        // 扩展网络连接诊断 (拆分为蜂窝移动与无线 Wi-Fi 2 套独立维度)
        sb.append(appContext.getString(R.string.show_sdk_network_type)).append(": ")
                .append(getNetworkTypeInfo(appContext)).append("\n");

        sb.append(appContext.getString(R.string.show_sdk_cellular_operator)).append(": ")
                .append(getCellularOperatorInfo(appContext)).append("\n");

        sb.append(appContext.getString(R.string.show_sdk_wifi_info)).append(": ")
                .append(getWifiConnectionInfo(appContext)).append("\n");

        // 原始 3 维地区维度暴露（方便开发者校验可靠性）
        sb.append(appContext.getString(R.string.show_sdk_network_iso)).append(": ")
                .append(getRawNetworkCountryIso(appContext)).append("\n");

        sb.append(appContext.getString(R.string.show_sdk_sim_iso)).append(": ")
                .append(getRawSimCountryIso(appContext)).append("\n");

        sb.append(appContext.getString(R.string.show_sdk_system_locale_region)).append(": ")
                .append(getRawSystemLocaleRegion()).append("\n");

        sb.append(appContext.getString(R.string.show_sdk_network_is_china)).append(": ")
                .append(checkIsChinaNetwork(appContext)).append("\n");

        // -------------------------------------------------------------
        // 4. APK 签名指纹 (MD5 / SHA1 / SHA256)
        // -------------------------------------------------------------
        sb.append("\n===== 签名指纹 (Cert Fingerprints) =====\n");
        android.content.pm.Signature[] signatures = getAppSignatures(appContext);
        String md5 = getSignatureHash(signatures, "MD5");
        String sha1 = getSignatureHash(signatures, "SHA-1");
        String sha256 = getSignatureHash(signatures, "SHA-256");

        sb.append(appContext.getString(R.string.show_sdk_app_sign_md5)).append(":\n").append(md5).append("\n");
        sb.append(appContext.getString(R.string.show_sdk_app_sign_sha1)).append(":\n").append(sha1).append("\n");
        sb.append(appContext.getString(R.string.show_sdk_app_sign_sha256)).append(":\n").append(sha256);

        // 合并为一个单一 Item 添加到列表
        list.add(createItemData(sb.toString()));
        return list;
    }

    /**
     * 获取 APK 的签名 (兼容 API 28+ SigningInfo 与旧版 GET_SIGNATURES)
     */
    @Nullable
    private static android.content.pm.Signature[] getAppSignatures(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            String packageName = context.getPackageName();
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                PackageInfo pi = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES);
                if (pi != null && pi.signingInfo != null) {
                    if (pi.signingInfo.hasMultipleSigners()) {
                        return pi.signingInfo.getApkContentsSigners();
                    } else {
                        return pi.signingInfo.getSigningCertificateHistory();
                    }
                }
            } else {
                PackageInfo pi = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
                if (pi != null) {
                    return pi.signatures;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 计算签名的特定算法哈希值 (MD5 / SHA1 / SHA256)
     * 为什么这样做：在排查第三方 SDK 鉴权失败时，快速直观比对签名指纹是刚需，
     * 自动格式化为大写带冒号分隔的标准十六进制指纹字符串。
     */
    private static String getSignatureHash(android.content.pm.Signature[] signatures, String algorithm) {
        if (signatures == null || signatures.length == 0) {
            return "未知/获取失败";
        }
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance(algorithm);
            md.update(signatures[0].toByteArray());
            byte[] digest = md.digest();

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < digest.length; i++) {
                if (i > 0) {
                    sb.append(":");
                }
                String hex = Integer.toHexString(0xFF & digest[i]).toUpperCase(java.util.Locale.getDefault());
                if (hex.length() == 1) {
                    sb.append("0");
                }
                sb.append(hex);
            }
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "计算失败";
        }
    }

    /**
     * 动态解压分析当前 APK 内置包含哪些 CPU 架构 (.so)
     * 为什么这样做：直观帮助开发者诊断当前安装包是否打入了正确的 Native 指令集（如 arm64-v8a）。
     */
    private static String getApkSupportedAbis(Context context) {
        try {
            String apkPath = context.getPackageCodePath();
            java.util.Set<String> abiSet = new java.util.TreeSet<>();
            try (java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(apkPath)) {
                java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    java.util.zip.ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (name.startsWith("lib/") && !entry.isDirectory()) {
                        String[] parts = name.split("/");
                        if (parts.length >= 2) {
                            abiSet.add(parts[1]);
                        }
                    }
                }
            }
            if (!abiSet.isEmpty()) {
                return abiSet.toString();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "纯 Java/Kotlin (无 Native .so 库)";
    }

    /**
     * 获取当前 APK 进程实际运行加载的 CPU ABI 指令集
     * 为什么这样做：区分 64 位与 32 位运行进程，排查 Native 崩溃极度关键。
     */
    private static String getCurrentProcessAbi() {
        String osArch = System.getProperty("os.arch");
        boolean is64Bit = false;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            is64Bit = android.os.Process.is64Bit();
        } else {
            is64Bit = osArch != null && osArch.contains("64");
        }
        return (osArch != null ? osArch : "未知") + " (" + (is64Bit ? "64 位进程" : "32 位进程") + ")";
    }

    /**
     * 获取当前网络连接模式 (Wi-Fi / 蜂窝移动 / 以太网)
     * 为什么这样做：采用现代 NetworkCapabilities + 经典 NetworkInfo + WifiManager 3层防空降级方案，
     * 并友好捕获未授权 SecurityException，解决无权限或代理模式下显示为“未知”的问题。
     */
    private static String getNetworkTypeInfo(Context context) {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return "未知/ConnectivityManager为空";

            boolean isVpn = false;
            // 1. 尝试使用 Android 6.0+ 现代 NetworkCapabilities 接口
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                android.net.Network activeNetwork = cm.getActiveNetwork();
                if (activeNetwork != null) {
                    android.net.NetworkCapabilities capabilities = cm.getNetworkCapabilities(activeNetwork);
                    if (capabilities != null) {
                        isVpn = capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN);
                        if (capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) {
                            return "Wi-Fi (无线网络)" + (isVpn ? " [代理/VPN模式]" : "");
                        } else if (capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)) {
                            return "蜂窝移动网络 (Cellular)" + (isVpn ? " [代理/VPN模式]" : "");
                        } else if (capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)) {
                            return "以太网 (Ethernet)";
                        } else if (isVpn) {
                            return "VPN / 代理网络 (Proxy/VPN)";
                        }
                    }
                }
            }

            // 2. 降级方案：使用经典 ActiveNetworkInfo
            try {
                android.net.NetworkInfo activeNetworkInfo = cm.getActiveNetworkInfo();
                if (activeNetworkInfo != null && activeNetworkInfo.isConnected()) {
                    int type = activeNetworkInfo.getType();
                    if (type == android.net.ConnectivityManager.TYPE_WIFI) {
                        return "Wi-Fi (无线网络)";
                    } else if (type == android.net.ConnectivityManager.TYPE_MOBILE) {
                        return "蜂窝移动网络 (" + activeNetworkInfo.getSubtypeName() + ")";
                    }
                    return activeNetworkInfo.getTypeName() + " (" + activeNetworkInfo.getSubtypeName() + ")";
                }
            } catch (Exception ignored) {
            }

            // 3. 兜底方案：尝试通过 WifiManager 校验无线网络连接状态
            try {
                android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null && wm.isWifiEnabled()) {
                    android.net.wifi.WifiInfo wifiInfo = wm.getConnectionInfo();
                    if (wifiInfo != null && wifiInfo.getNetworkId() != -1) {
                        return "Wi-Fi (无线网络 - 已连接)";
                    }
                }
            } catch (Exception ignored) {
            }

            return "无网络连接 (Offline)";
        } catch (SecurityException se) {
            return "未授权访问 (缺少 ACCESS_NETWORK_STATE 权限)";
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "未知/无网络连接";
    }

    /**
     * 获取蜂窝移动网络运营商名称及 MCC-MNC 代号
     */
    private static String getCellularOperatorInfo(Context context) {
        try {
            android.telephony.TelephonyManager tm = (android.telephony.TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                String operatorName = tm.getNetworkOperatorName();
                String operatorCode = tm.getNetworkOperator();
                if (!TextUtils.isEmpty(operatorName)) {
                    if (!TextUtils.isEmpty(operatorCode)) {
                        return operatorName + " (" + operatorCode + ")";
                    }
                    return operatorName;
                }
                String simName = tm.getSimOperatorName();
                if (!TextUtils.isEmpty(simName)) {
                    return simName + " (SIM卡)";
                }
                if (!TextUtils.isEmpty(operatorCode)) {
                    return "MCC-MNC: " + operatorCode;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "未插卡/无蜂窝移动信号";
    }

    /**
     * 获取无线 Wi-Fi 热点 SSID 名称及内网 IP 地址
     * 为什么这样做：从 Android 8.1+ 开始，获取具体 SSID 名称需要宿主在运行时动态申请 ACCESS_FINE_LOCATION 定位权限；
     * 优先通过 ConnectivityManager 校验 Wi-Fi 通道，确保在没有动态定位权限时也能 100% 正确识别到已连接 Wi-Fi 及内网 IP，绝不误判。
     */
    private static String getWifiConnectionInfo(Context context) {
        try {
            Context appContext = context.getApplicationContext();
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            boolean isConnectedToWifi = false;

            // 1. 优先通过 ConnectivityManager 校验 Wi-Fi 通道 (不依赖动态定位权限，100% 准确)
            if (cm != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    android.net.Network activeNetwork = cm.getActiveNetwork();
                    if (activeNetwork != null) {
                        android.net.NetworkCapabilities capabilities = cm.getNetworkCapabilities(activeNetwork);
                        if (capabilities != null && capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) {
                            isConnectedToWifi = true;
                        }
                    }
                } else {
                    android.net.NetworkInfo networkInfo = cm.getNetworkInfo(android.net.ConnectivityManager.TYPE_WIFI);
                    if (networkInfo != null && networkInfo.isConnected()) {
                        isConnectedToWifi = true;
                    }
                }
            }

            // 2. 辅助校验 WifiManager 状态
            android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
            android.net.wifi.WifiInfo wifiInfo = null;
            if (wm != null && wm.isWifiEnabled()) {
                wifiInfo = wm.getConnectionInfo();
                if (wifiInfo != null && wifiInfo.getNetworkId() != -1) {
                    isConnectedToWifi = true;
                }
            }

            if (isConnectedToWifi) {
                String ssid = null;
                String ipStr = null;

                if (wifiInfo != null) {
                    ssid = wifiInfo.getSSID();
                    if (ssid != null && ssid.startsWith("\"") && ssid.endsWith("\"")) {
                        ssid = ssid.substring(1, ssid.length() - 1);
                    }
                    int ipAddress = wifiInfo.getIpAddress();
                    if (ipAddress != 0) {
                        ipStr = String.format(java.util.Locale.getDefault(), "%d.%d.%d.%d",
                                (ipAddress & 0xff), (ipAddress >> 8 & 0xff),
                                (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));
                    }
                }

                // 格式化未获得动态定位权限时的 SSID 提示
                if (TextUtils.isEmpty(ssid) || "<unknown ssid>".equalsIgnoreCase(ssid) || "0x".equalsIgnoreCase(ssid)) {
                    ssid = "已连接 Wi-Fi (需定位动态权限显示SSID名称)";
                }

                StringBuilder sb = new StringBuilder("SSID: ").append(ssid);
                if (!TextUtils.isEmpty(ipStr) && !"0.0.0.0".equals(ipStr)) {
                    sb.append(" (IP: ").append(ipStr).append(")");
                }
                return sb.toString();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "未连接 Wi-Fi";
    }

    /**
     * 获取基站网络广播的国家 ISO 代码
     */
    private static String getRawNetworkCountryIso(Context context) {
        try {
            android.telephony.TelephonyManager tm = (android.telephony.TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                String networkCountry = tm.getNetworkCountryIso();
                if (!TextUtils.isEmpty(networkCountry)) {
                    return networkCountry.toUpperCase(java.util.Locale.getDefault());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "未获取/无基站信号";
    }

    /**
     * 获取物理 SIM 卡发行的国家 ISO 代码
     */
    private static String getRawSimCountryIso(Context context) {
        try {
            android.telephony.TelephonyManager tm = (android.telephony.TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                String simCountry = tm.getSimCountryIso();
                if (!TextUtils.isEmpty(simCountry)) {
                    return simCountry.toUpperCase(java.util.Locale.getDefault());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "未插卡/无SIM卡信息";
    }

    /**
     * 获取系统当前语言/地区设置 (Locale Region)
     */
    private static String getRawSystemLocaleRegion() {
        java.util.Locale locale = java.util.Locale.getDefault();
        String country = locale.getCountry();
        if (!TextUtils.isEmpty(country)) {
            return country.toUpperCase(locale) + " (" + locale.toString() + ")";
        }
        return "未知系统地区";
    }

    /**
     * 智能判定是否归属中国网络/地区
     */
    private static String checkIsChinaNetwork(Context context) {
        try {
            String netIso = getRawNetworkCountryIso(context);
            if ("CN".equalsIgnoreCase(netIso)) {
                return "[是] 中国网络 (基站定位: CN)";
            }
            android.telephony.TelephonyManager tm = (android.telephony.TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                String operatorCode = tm.getNetworkOperator();
                if (!TextUtils.isEmpty(operatorCode) && operatorCode.startsWith("460")) {
                    return "[是] 中国网络 (移动网络代码: 460)";
                }
            }
            String simIso = getRawSimCountryIso(context);
            if ("CN".equalsIgnoreCase(simIso)) {
                return "[是] 中国 SIM 卡网络 (SIM ISO: CN)";
            }
            String sysCountry = java.util.Locale.getDefault().getCountry();
            if ("CN".equalsIgnoreCase(sysCountry)) {
                return "[是] 中国地区 (系统 Locale: CN)";
            }
            return "[否] 海外国家/地区 (Net:" + netIso + ", Sim:" + simIso + ", Sys:" + sysCountry + ")";
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "无法感知/默认系统: " + java.util.Locale.getDefault().getCountry();
    }

    /**
     * 构建单条基础展示数据项
     */
    private static BaseShowData createItemData(String content) {
        BaseShowData data = new BaseShowData();
        data.setItemType(BaseShowData.TYE_ONE);
        data.setContent(content);
        return data;
    }
}
