/**
 * APK 蒲公英上传工具
 * ⚠️ 包名/常量需替换为项目实际值
 * 在 build.gradle.kts 中通过 afterEvaluate 注册上传任务
 *
 * ⚠️ 需替换的常量（见 SKILL.md §四）：
 * - taskName → "assembleXxxRelease"（替换 Xxx 为实际 flavor）
 * - apkPath → "outputs/apk/xxx/release"（替换 xxx）
 * - apkPrefix → "Xxx_"（替换 Xxx）
 *
 * 依赖：UploadResponse.kt（蒲公英响应模型）、VersionConfig.kt（版本配置）
 */
import com.google.gson.Gson
import entity.UploadResponse
import org.gradle.api.GradleException
import org.gradle.api.Project
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * APK 自动上传蒲公英工具
 * 提供蒲公英上传任务的注册
 */
object ApkUpLoadUtils {

    // ======================== ⚠️ 项目相关：以下 3 个常量必须按项目替换 ========================
    /** 当前绑定的构建任务名 ← ⚠️ 替换为 assemble + {Flavor} + {BuildType} */
    private val taskName = "assembleXxxRelease"

    /** APK 输出目录 ← ⚠️ 替换为 outputs/apk/{flavor}/{buildType} */
    private val apkPath = "outputs/apk/xxx/release"

    /** APK 文件名前缀 ← ⚠️ 替换为 "{rootProject.name}_"（与 settings.gradle.kts 一致） */
    private val apkPrefix = "Xxx_"
    // ======================== ⚠️ 项目相关 END ========================

    // ======================== ✅ 以下为通用代码，无需修改 ========================

    /**
     * 注册上传蒲公英任务
     * 任务名：uploadPgyer，分组：upload
     * 通过 finalizedBy 绑定到构建任务之后执行
     *
     * @param mProject 当前 Project
     * @param pgyerapiKey 蒲公英 API Key（从 VersionConfig 获取）
     */
    fun registerTaskForDandelion(mProject: Project, pgyerapiKey: String) {
        val uploadToPgyer = mProject.tasks.register("uploadPgyer") {
            group = "upload"
            description = "自动上传apk到蒲公英"

            doLast {
                val pGyerKey = pgyerapiKey
                if (pGyerKey.isEmpty()) {
                    throw GradleException("蒲公英上传key 未设置，请检查 VersionConfig.PGYER_API_KEY 的值")
                }

                println("执行上传apk到蒲公英:api-key:${pGyerKey}")

                // 1. 查找 APK 文件
                val apkDir = mProject.buildDir.resolve(apkPath)
                val apkFile = apkDir.listFiles()?.find {
                    it.extension == "apk" && it.name.startsWith(apkPrefix)
                }
                println("📦 正在上传 APK 到蒲公英,apk路径：${apkFile}")
                if (apkFile == null) return@doLast

                // 2. 构建 multipart/form-data 请求
                val boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW"
                val lineEnd = "\r\n"
                val twoHyphens = "--"

                val url = URL("https://www.pgyer.com/apiv2/app/upload")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.doInput = true
                conn.useCaches = false
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

                val outputStream = conn.outputStream
                val writer = OutputStreamWriter(outputStream, Charsets.UTF_8)

                /** 写入普通表单字段 */
                fun writeFormField(name: String, value: String) {
                    writer.write("$twoHyphens$boundary$lineEnd")
                    writer.write("Content-Disposition: form-data; name=\"$name\"$lineEnd")
                    writer.write("Content-Type: text/plain; charset=UTF-8$lineEnd")
                    writer.write(lineEnd)
                    writer.write(value)
                    writer.write(lineEnd)
                }

                /** 写入文件字段（4KB 分块读取，避免 OOM） */
                fun writeFileField(name: String, file: File) {
                    writer.write("$twoHyphens$boundary$lineEnd")
                    writer.write("Content-Disposition: form-data; name=\"$name\"; filename=\"${file.name}\"$lineEnd")
                    writer.write("Content-Type: application/vnd.android.package-archive$lineEnd")
                    writer.write("Content-Transfer-Encoding: binary$lineEnd")
                    writer.write(lineEnd)
                    writer.flush()
                    file.inputStream().use { input ->
                        val buffer = ByteArray(4096)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                        }
                    }
                    outputStream.flush()
                    writer.write(lineEnd)
                }

                // 3. 写入请求体
                writeFormField("_api_key", pGyerKey)
                writeFileField("file", apkFile)
                writer.write(twoHyphens + boundary + twoHyphens + lineEnd)
                writer.flush()
                writer.close()
                outputStream.close()

                // 4. 解析响应
                val response = readResponse(conn)
                println("✅ 上传完成，蒲公英返回：\n$response")

                val uploadResponse = Gson().fromJson(response, UploadResponse::class.java)
                if (uploadResponse.code != 0) {
                    println("🚫 原始响应：$response")
                    throw GradleException("❌ 上传失败：${uploadResponse.message}")
                }

                // 5. 打开下载页面
                val shortcut = uploadResponse.data?.buildShortcutUrl?.takeIf { it.isNotBlank() }
                    ?: throw GradleException("❌ 上传成功但未返回短链接，无法自动打开下载页")
                val downloadUrl = "https://pgyer.com/$shortcut"
                println("🚀 上传成功！下载地址：$downloadUrl")

                try {
                    openInBrowser(downloadUrl)
                } catch (e: Exception) {
                    println("⚠️ 无法自动打开浏览器：${e.message}")
                }
            }
        }
        mProject.tasks.matching { it.name.startsWith(taskName) }.configureEach {
            finalizedBy(uploadToPgyer)
        }
    }

    /**
     * 读取接口响应
     * 成功优先读 inputStream，失败优先读 errorStream，避免丢失错误信息
     */
    private fun readResponse(conn: HttpURLConnection): String {
        val stream = try {
            if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        } catch (_: Exception) {
            conn.errorStream ?: conn.inputStream
        } ?: throw GradleException("❌ 蒲公英接口未返回可读取的响应内容")

        return stream.bufferedReader().use { it.readText() }
    }

    /**
     * 打开浏览器查看下载页
     * 使用参数数组，避免 URL 中特殊字符导致命令解析异常
     */
    private fun openInBrowser(downloadUrl: String) {
        val os = System.getProperty("os.name").lowercase()
        when {
            os.contains("windows") -> ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", downloadUrl).start()
            os.contains("mac") -> ProcessBuilder("open", downloadUrl).start()
            os.contains("linux") -> ProcessBuilder("xdg-open", downloadUrl).start()
            else -> throw GradleException("❌ 当前操作系统不支持自动打开浏览器")
        }
    }
}
