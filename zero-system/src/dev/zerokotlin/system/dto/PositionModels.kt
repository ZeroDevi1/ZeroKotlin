package dev.zerokotlin.system.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 新建岗位的请求。
 *
 * @property code 岗位的唯一编码。
 * @property name 岗位的显示名称。
 * @property sortOrder 岗位列表的排序值，必填，较小值排在前面。
 */
@Schema(description = "新建岗位的请求。")
data class CreatePositionRequest(
    @field:Schema(description = "岗位的唯一编码。")
    val code: String,
    @field:Schema(description = "岗位的显示名称。")
    val name: String,
    @field:Schema(description = "岗位列表的排序值，必填，较小值排在前面。", requiredMode = Schema.RequiredMode.REQUIRED)
    val sortOrder: Int?,
)

/**
 * 修改岗位名称、状态和排序的请求。
 *
 * @property name 更新后的岗位名称。
 * @property enabled 是否启用岗位，必填。
 * @property sortOrder 岗位列表的排序值，必填，较小值排在前面。
 */
@Schema(description = "修改岗位名称、状态和排序的请求。")
data class UpdatePositionRequest(
    @field:Schema(description = "更新后的岗位名称。")
    val name: String,
    @field:Schema(description = "是否启用岗位，必填。", requiredMode = Schema.RequiredMode.REQUIRED)
    val enabled: Boolean?,
    @field:Schema(description = "岗位列表的排序值，必填，较小值排在前面。", requiredMode = Schema.RequiredMode.REQUIRED)
    val sortOrder: Int?,
)

/**
 * 整体替换岗位成员的请求。
 *
 * @property userIds 新的完整成员 ID 集合。空集合清空成员，每个元素须为已存在的用户 ID。
 */
@Schema(description = "整体替换岗位成员的请求。")
data class ReplacePositionMembersRequest(
    @field:Schema(description = "新的完整成员 ID 集合。空集合清空成员，每个元素须为已存在的用户 ID。")
    val userIds: Set<String?>,
)

/**
 * 岗位管理列表中的一条记录。
 *
 * @property id 岗位 ID。
 * @property code 岗位的唯一编码。
 * @property name 岗位的显示名称。
 * @property enabled 岗位是否启用。
 * @property sortOrder 岗位列表的排序值，较小值排在前面。
 */
@Schema(description = "岗位管理列表中的一条记录。")
data class PositionEntry(
    @field:Schema(description = "岗位 ID。")
    val id: String,
    @field:Schema(description = "岗位的唯一编码。")
    val code: String,
    @field:Schema(description = "岗位的显示名称。")
    val name: String,
    @field:Schema(description = "岗位是否启用。")
    val enabled: Boolean,
    @field:Schema(description = "岗位列表的排序值，较小值排在前面。")
    val sortOrder: Int,
)

/**
 * 岗位成员列表中的一名用户。
 *
 * @property userId 成员的用户 ID。
 * @property username 成员的登录名。
 * @property displayName 成员的显示名称。
 */
@Schema(description = "岗位成员列表中的一名用户。")
data class PositionMember(
    @field:Schema(description = "成员的用户 ID。")
    val userId: String,
    @field:Schema(description = "成员的登录名。", example = "admin")
    val username: String,
    @field:Schema(description = "成员的显示名称。", example = "王明")
    val displayName: String,
)
