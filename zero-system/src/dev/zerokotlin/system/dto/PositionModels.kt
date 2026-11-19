package dev.zerokotlin.system.dto

/**
 * 新建岗位的请求。
 *
 * @property code 岗位的唯一编码。
 * @property name 岗位的显示名称。
 * @property sortOrder 岗位列表的排序值，必填，较小值排在前面。
 */
data class CreatePositionRequest(val code: String, val name: String, val sortOrder: Int?)

/**
 * 修改岗位名称、状态和排序的请求。
 *
 * @property name 更新后的岗位名称。
 * @property enabled 是否启用岗位，必填。
 * @property sortOrder 岗位列表的排序值，必填，较小值排在前面。
 */
data class UpdatePositionRequest(val name: String, val enabled: Boolean?, val sortOrder: Int?)

/**
 * 整体替换岗位成员的请求。
 *
 * @property userIds 新的完整成员 ID 集合。空集合清空成员，每个元素须为已存在的用户 ID。
 */
data class ReplacePositionMembersRequest(val userIds: Set<String?>)

/**
 * 岗位管理列表中的一条记录。
 *
 * @property id 岗位 ID。
 * @property code 岗位的唯一编码。
 * @property name 岗位的显示名称。
 * @property enabled 岗位是否启用。
 * @property sortOrder 岗位列表的排序值，较小值排在前面。
 */
data class PositionEntry(val id: String, val code: String, val name: String, val enabled: Boolean, val sortOrder: Int)

/**
 * 岗位成员列表中的一名用户。
 *
 * @property userId 成员的用户 ID。
 * @property username 成员的登录名。
 * @property displayName 成员的显示名称。
 */
data class PositionMember(val userId: String, val username: String, val displayName: String)
