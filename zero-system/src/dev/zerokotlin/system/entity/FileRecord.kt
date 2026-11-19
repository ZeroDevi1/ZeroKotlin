package dev.zerokotlin.system.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName

/**
 * `sys_file` 中的文件元数据和用于恢复存储操作的状态。
 *
 * @property id 数据库主键，同时作为内容存储的对象键。
 * @property fileName 客户端原始文件名的末段，用于展示和下载。
 * @property bizType 可选业务分类编码。
 * @property contentType 客户端声明的媒体类型摘要。
 * @property sizeBytes 上传内容的字节数。
 * @property storageBackend 写入对象所用的存储后端类型。
 * @property storageLocationId 用于定位原存储位置的配置指纹。
 * @property state [FileState] 的枚举名。
 * @property uploadedBy 发起上传的用户 ID。
 * @property createdAt 上传登记时间，单位为 Unix 毫秒。
 */
@TableName("sys_file")
class FileRecord(
    @field:TableId(type = IdType.INPUT)
    var id: String = "",
    var fileName: String = "",
    var bizType: String? = null,
    var contentType: String? = null,
    var sizeBytes: Long = 0,
    var storageBackend: String = "",
    var storageLocationId: String = "",
    var state: String = FileState.UPLOADING.name,
    var uploadedBy: String = "",
    var createdAt: Long = 0,
)

/** 文件元数据和对象内容之间的持久化状态。 */
enum class FileState {
    /** 已登记上传意图，内容尚未发布。 */
    UPLOADING,

    /** 内容已写入存储并提交元数据，可下载。 */
    AVAILABLE,

    /** 已禁止新下载，正在删除或等待重试清理。 */
    DELETING,
}
