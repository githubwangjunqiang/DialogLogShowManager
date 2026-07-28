/**
 * 蒲公英上传接口的响应模型
 * ⚠️ 包名需替换为项目实际包名
 * 被 ApkUpLoadUtils.kt 引用，用于解析蒲公英上传 API 返回的 JSON
 */
package entity

/**
 * 蒲公英上传接口的顶层响应结构
 */
class UploadResponse(
    val code: Int,           // 状态码（0 = 成功）
    val message: String,     // 响应消息
    val data: UploadData?    // 响应数据（上传成功时非空）
)

/**
 * 蒲公英上传成功后返回的构建信息
 */
class UploadData(
    val buildQRCodeURL: String?,     // 二维码下载链接
    val buildShortcutUrl: String?,   // 短链接（拼接 https://pgyer.com/{shortcut}）
    val buildFileName: String?,      // APK 文件名
    val buildName: String?,          // 应用名
    val buildVersion: String?,       // 版本号
    val buildIdentifier: String?     // 唯一构建标识
)