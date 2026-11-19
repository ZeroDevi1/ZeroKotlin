package dev.zerokotlin.system.controller

import dev.zerokotlin.system.dto.CreatePermissionRequest
import dev.zerokotlin.system.dto.PermissionSummary
import dev.zerokotlin.system.dto.UpdatePermissionRequest
import dev.zerokotlin.system.security.PERMISSION_WRITE
import dev.zerokotlin.system.service.SystemPermissionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
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
@Tag(name = "权限点管理")
class SystemPermissionController(private val permissions: SystemPermissionService) {
    /**
     * 读取权限点目录。
     *
     * @return 按编码升序排列的权限点摘要，含启用和停用项。
     */
    @GetMapping
    @PreAuthorize("hasAuthority('system:permission:read')")
    @Operation(
        summary = "查询权限点列表",
        description = "需要 `system:permission:read` 权限。包含启用和停用的权限点，按编码升序排列。",
        responses = [ApiResponse(responseCode = "200", description = "权限点列表", useReturnTypeSchema = true)],
    )
    fun list(): List<PermissionSummary> = permissions.summaries()

    /**
     * 登记一个自定义权限点，仅系统管理员可执行。
     *
     * @param request 唯一的自定义权限编码和显示名称。
     * @return HTTP 201 响应体，包含新权限点的摘要。
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('$PERMISSION_WRITE')")
    @Operation(
        summary = "创建业务权限点",
        description = "需要内置管理员身份和 `$PERMISSION_WRITE` 权限。`system:` 前缀保留给系统维护的权限点。",
        responses = [
            ApiResponse(responseCode = "201", description = "权限点已创建", useReturnTypeSchema = true),
            ApiResponse(responseCode = "400", description = "权限点名称或编码无效，或使用了保留前缀", content = [Content()]),
            ApiResponse(responseCode = "403", description = "缺少所需权限或内置管理员身份", content = [Content()]),
            ApiResponse(responseCode = "409", description = "权限点编码已存在", content = [Content()]),
        ],
    )
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
    @Operation(
        summary = "更新业务权限点",
        description = "需要内置管理员身份和 `$PERMISSION_WRITE` 权限。按编码更新名称和启用状态，`system:` 权限点由系统维护。",
        parameters = [Parameter(name = "code", `in` = ParameterIn.PATH, description = "业务权限点编码")],
        responses = [
            ApiResponse(responseCode = "200", description = "更新后的权限点", useReturnTypeSchema = true),
            ApiResponse(responseCode = "400", description = "请求字段无效或编码使用了保留前缀", content = [Content()]),
            ApiResponse(responseCode = "403", description = "缺少所需权限或内置管理员身份", content = [Content()]),
            ApiResponse(responseCode = "404", description = "权限点不存在", content = [Content()]),
        ],
    )
    fun update(@PathVariable code: String, @RequestBody request: UpdatePermissionRequest): PermissionSummary =
        permissions.update(code, request)
}
