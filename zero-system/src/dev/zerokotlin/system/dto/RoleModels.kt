package dev.zerokotlin.system.dto

import dev.zerokotlin.system.entity.DataScope

data class CreateRoleRequest(
    val code: String,
    val name: String,
    val dataScope: DataScope,
    val permissionCodes: Set<String> = emptySet(),
)

data class UpdateRoleRequest(
    val name: String,
    val dataScope: DataScope,
    val enabled: Boolean?,
    val permissionCodes: Set<String>,
)

data class RoleSummary(
    val id: String,
    val code: String,
    val name: String,
    val dataScope: String,
    val enabled: Boolean,
)

data class RoleDetails(val role: RoleSummary, val permissionCodes: List<String>)

data class CreatePermissionRequest(val code: String, val name: String)

data class UpdatePermissionRequest(val name: String, val enabled: Boolean?)

data class PermissionSummary(val code: String, val name: String, val enabled: Boolean)
