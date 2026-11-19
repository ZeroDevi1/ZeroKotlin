package dev.zerokotlin.system.controller

import dev.zerokotlin.core.PageResult
import dev.zerokotlin.core.PageWindow
import dev.zerokotlin.system.dto.CreateRoleRequest
import dev.zerokotlin.system.dto.CreatedResource
import dev.zerokotlin.system.dto.RoleDetails
import dev.zerokotlin.system.dto.RoleSummary
import dev.zerokotlin.system.dto.UpdateRoleRequest
import dev.zerokotlin.system.security.ROLE_WRITE
import dev.zerokotlin.system.service.SystemRoles
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 提供角色查询和角色权限分配接口。
 *
 * @property roles 读取角色及其权限点，保存业务角色配置。
 */
@RestController
@RequestMapping("/api/system/roles")
class SystemRoleController(private val roles: SystemRoles) {
    /**
     * 分页读取角色目录。
     *
     * @param page 页码，从 1 开始，默认 1。
     * @param size 每页条数，范围为 1～100，默认 20。
     * @param enabled 指定要查询的启用状态，省略时查询所有状态。
     * @return 按编码和 ID 升序排列的角色摘要页。
     */
    @GetMapping
    @PreAuthorize("hasAuthority('system:role:read')")
    fun list(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) enabled: Boolean?,
    ): PageResult<RoleSummary> = roles.list(PageWindow(page, size), enabled)

    /**
     * 查询角色及其权限点。
     *
     * @param id 角色 ID。
     * @return 角色摘要和权限编码列表。
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:read')")
    fun detail(@PathVariable id: String): RoleDetails = roles.detail(id)

    /**
     * 新建业务角色，仅系统管理员可执行。
     *
     * @param request 角色编码、名称、数据范围和初始权限集合。
     * @return HTTP 201 响应体，包含新资源的 ID。
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('$ROLE_WRITE')")
    fun create(@RequestBody request: CreateRoleRequest): CreatedResource = roles.create(request)

    /**
     * 更新业务角色并整体替换其权限，仅系统管理员可执行，成功时返回 HTTP 204。
     *
     * @param id 待修改的业务角色 ID。
     * @param request 角色的新名称、数据范围、启用状态和完整权限集合。
     */
    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$ROLE_WRITE')")
    fun update(@PathVariable id: String, @RequestBody request: UpdateRoleRequest) = roles.update(id, request)
}
