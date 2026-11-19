package dev.zerokotlin.system.dto

import io.swagger.v3.oas.annotations.media.Schema

/** 文件管理页可见的元数据。 */
@Schema(description = "文件元数据。")
data class FileEntry(
    @field:Schema(description = "文件 ID。", format = "uuid")
    val id: String,
    @field:Schema(description = "原始文件名的末段。", example = "部门资料.pdf")
    val fileName: String,
    @field:Schema(description = "业务分类编码。", example = "documents")
    val bizType: String?,
    @field:Schema(description = "客户端声明的媒体类型。", example = "application/pdf")
    val contentType: String?,
    @field:Schema(description = "字节数。", minimum = "0")
    val sizeBytes: Long,
    @field:Schema(description = "存储后端。", allowableValues = ["LOCAL", "MINIO", "ALIYUN"])
    val storageBackend: String,
    @field:Schema(description = "文件状态。", allowableValues = ["UPLOADING", "AVAILABLE", "DELETING"])
    val state: String,
    @field:Schema(description = "上传者的用户 ID。")
    val uploadedBy: String,
    @field:Schema(description = "上传登记时间，单位为 Unix 毫秒。")
    val createdAt: Long,
)
