package dev.zerokotlin.system.service

import com.baomidou.mybatisplus.spring.service.IService
import dev.zerokotlin.system.dto.CreatePermissionRequest
import dev.zerokotlin.system.dto.PermissionSummary
import dev.zerokotlin.system.dto.UpdatePermissionRequest
import dev.zerokotlin.system.entity.PermissionRecord

/** 提供权限点目录相关的业务操作。 */
interface SystemPermissionService : IService<PermissionRecord> {
    fun summaries(): List<PermissionSummary>
    fun create(request: CreatePermissionRequest): PermissionSummary
    fun update(code: String, request: UpdatePermissionRequest): PermissionSummary
}
