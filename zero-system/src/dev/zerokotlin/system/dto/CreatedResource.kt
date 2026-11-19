package dev.zerokotlin.system.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 资源创建成功后返回的标识。
 *
 * @property id 新建资源 ID。
 */
@Schema(description = "资源创建成功后返回的标识。")
data class CreatedResource(
    @field:Schema(description = "新建资源 ID。")
    val id: String,
)
