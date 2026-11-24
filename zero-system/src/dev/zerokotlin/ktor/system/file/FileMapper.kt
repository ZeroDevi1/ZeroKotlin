package dev.zerokotlin.ktor.system.file

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper
import com.baomidou.mybatisplus.core.mapper.BaseMapper

/** 数据库记录包含对象位置；HTTP DTO 只暴露已约定的元数据。 */
@TableName("sys_file")
internal class FileRecord {
    @field:TableId(type = IdType.INPUT)
    var id: String? = null
    @field:TableField("file_name")
    var fileName: String? = null
    @field:TableField("biz_type")
    var bizType: String? = null
    @field:TableField("content_type")
    var contentType: String? = null
    @field:TableField("size_bytes")
    var sizeBytes: Long? = null
    @field:TableField("storage_backend")
    var storageBackend: String? = null
    @field:TableField("storage_location_id")
    var storageLocationId: String? = null
    var state: String? = null
    @field:TableField("uploaded_by")
    var uploadedBy: String? = null
    @field:TableField("created_at")
    var createdAt: Long? = null

    fun summary() = StoredFile(
        checkNotNull(id), checkNotNull(fileName), bizType, contentType, checkNotNull(sizeBytes),
        checkNotNull(storageBackend), checkNotNull(state), checkNotNull(uploadedBy), checkNotNull(createdAt),
    )
}

internal interface FileMapper : BaseMapper<FileRecord>

/** 从指定状态迁移，供上传、删除和失败补偿共用。 */
internal fun FileMapper.updateState(id: String, state: String, from: String? = null): Int {
    val changes = UpdateWrapper<FileRecord>().eq("id", id).set("state", state)
    if (from != null) changes.eq("state", from)
    return update(null, changes)
}
