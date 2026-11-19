package dev.zerokotlin.system.controller

import dev.zerokotlin.system.dto.CreatePermissionRequest
import dev.zerokotlin.system.dto.PermissionSummary
import dev.zerokotlin.system.dto.UpdatePermissionRequest
import dev.zerokotlin.system.security.PERMISSION_WRITE
import dev.zerokotlin.system.service.SystemPermissions
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 提供权限点目录查询和自定义权限点维护接口。
 *
 * @property permissions 读取权限目录并校验权限编码的可编辑性。
 */
@RestController
@RequestMapping("/api/system/permissions")
class SystemPermissionController(private val permissions: SystemPermissions) {
    /**
     * 读取权限点目录。
     *
     * @return 按编码升序排列的权限点摘要，含启用和停用项。
     */
    @GetMapping
    @PreAuthorize("hasAuthority('system:permission:read')")
    fun list(): List<PermissionSummary> = permissions.list()

    /**
     * 登记一个自定义权限点，仅系统管理员可执行。
     *
     * @param request 唯一的自定义权限编码和显示名称。
     * @return HTTP 201 响应体，包含新权限点的摘要。
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('$PERMISSION_WRITE')")
    fun create(@RequestBody request: CreatePermissionRequest): PermissionSummary = permissions.create(request)

    /**
     * 修改自定义权限点，仅系统管理员可执行。
     *
     * @param code 待修改的自定义权限编码。
     * @param request 权限点的新名称和启用状态。
     * @return 更新后的权限点摘要。
     */
    @PutMapping("/{code}")
    @PreAuthorize("hasAuthority('$PERMISSION_WRITE')")
    fun update(@PathVariable code: String, @RequestBody request: UpdatePermissionRequest): PermissionSummary =
        permissions.update(code, request)
}
