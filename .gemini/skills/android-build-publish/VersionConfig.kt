/**
 * 版本配置管理工具
 * ⚠️ 文件路径需替换为项目实际路径
 * 基于 version.properties 文件管理版本号、版本名、蒲公英分发 Key
 *
 * 使用前提：项目根目录存在 version.properties 文件；若不存在会自动创建默认值
 */
import org.gradle.api.Project
import java.io.File
import java.util.Properties
object VersionConfig {

    /** 加载 version.properties 文件引用 */
    private fun loadFile(project: Project): File {
        return File(project.rootDir, "version.properties")
    }

    /** 加载属性，文件不存在时自动创建并写入默认值 */
    private fun loadProperties(project: Project): Properties {
        val propsFile = loadFile(project)
        val props = Properties()
        if (propsFile.exists()) {
            propsFile.inputStream().use { props.load(it) }
        } else {
            // 初始化默认值
            props.setProperty("versionCode", "1")
            props.setProperty("versionName", "1.0.0")
            props.setProperty("PGYER_API_KEY", "")
            props.setProperty("PGYER_APP_KEY", "")
            saveProperties(project, props)
        }
        return props
    }

    /** 持久化属性到文件 */
    private fun saveProperties(project: Project, props: Properties) {
        val propsFile = loadFile(project)
        propsFile.outputStream().use { props.store(it, null) }
    }

    /**
     * 获取版本号并自增
     * 每次调用 versionCode +1 并写回文件，适用于构建时自动递增
     */
    fun getNextVersionCode(project: Project): Int {
        val props = loadProperties(project)
        val currentVersion = props.getProperty("versionCode").toIntOrNull() ?: 1
        val newVersion = currentVersion + 1
        props.setProperty("versionCode", newVersion.toString())
        saveProperties(project, props)
        println("✅自动增加版本号写入成功 versionCode=$newVersion")
        return newVersion
    }

    /** 获取语义化版本名（如 "1.0.0"） */
    fun getVersionName(project: Project): String {
        val props = loadProperties(project)
        return props.getProperty("versionName") ?: "1.0.0"
    }

    /** 获取蒲公英 API Key */
    fun getPGyerApiKey(project: Project): String {
        val props = loadProperties(project)
        return props.getProperty("PGYER_API_KEY") ?: ""
    }

    /** 获取蒲公英 App Key */
    fun getPGyerAppKey(project: Project): String {
        val props = loadProperties(project)
        return props.getProperty("PGYER_APP_KEY") ?: ""
    }
}
