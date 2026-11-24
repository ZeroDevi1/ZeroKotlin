package dev.zerokotlin.ktor.system.permission.service

import dev.zerokotlin.ktor.system.permission.dto.CreatePermission
import dev.zerokotlin.ktor.system.permission.dto.PermissionSummary
import dev.zerokotlin.ktor.system.permission.dto.UpdatePermission

/** 权限点管理契约。写入仅允许具备相应权限的系统管理员操作。 */
internal interface PermissionService {
    suspend fun list(token: String): List<PermissionSummary>
    suspend fun create(token: String, request: CreatePermission): PermissionSummary
    suspend fun update(token: String, code: String, request: UpdatePermission): PermissionSummary
}
