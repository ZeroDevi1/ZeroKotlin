package dev.zerokotlin.ktor.system.permission.dto

import kotlinx.serialization.Serializable

/** 管理台返回的权限点摘要。enabled 参与登录用户实时权限计算。 */
@Serializable
internal data class PermissionSummary(val code: String, val name: String, val enabled: Boolean)

@Serializable
internal data class CreatePermission(val code: String, val name: String)

@Serializable
internal data class UpdatePermission(val name: String, val enabled: Boolean)
