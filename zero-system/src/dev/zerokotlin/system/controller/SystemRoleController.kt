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
@Tag(name = "角色管理")
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
    @Operation(
        summary = "分页查询角色",
        description = "需要 `system:role:read` 权限。可按启用状态筛选。",
        responses = [ApiResponse(responseCode = "200", description = "角色分页", useReturnTypeSchema = true)],
    )
    fun list(
        @Parameter(description = "页码，从 1 开始")
        @RequestParam(defaultValue = "1") page: Int,
        @Parameter(description = "每页条数，范围为 1–100")
        @RequestParam(defaultValue = "20") size: Int,
        @Parameter(description = "为 true 时筛选启用角色，为 false 时筛选停用角色，省略时包含两类。")
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
    @Operation(
        summary = "查询角色详情及权限点",
        description = "需要 `system:role:read` 权限。内置管理员角色的权限点为系统中全部已启用的权限点。",
        parameters = [Parameter(name = "id", `in` = ParameterIn.PATH, description = "角色 ID")],
        responses = [
            ApiResponse(responseCode = "200", description = "角色详情和权限编码列表", useReturnTypeSchema = true),
            ApiResponse(responseCode = "404", description = "角色不存在", content = [Content()]),
        ],
    )
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
    @Operation(
        summary = "创建业务角色",
        description = "需要内置管理员身份和 `$ROLE_WRITE` 权限。可设置数据范围并关联已有权限点，内置管理员专用权限由系统管理。",
        responses = [
            ApiResponse(responseCode = "201", description = "角色已创建", useReturnTypeSchema = true),
            ApiResponse(responseCode = "400", description = "角色字段或权限点集合无效", content = [Content()]),
            ApiResponse(responseCode = "403", description = "缺少所需权限或内置管理员身份", content = [Content()]),
            ApiResponse(responseCode = "409", description = "角色编码已存在", content = [Content()]),
        ],
    )
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
    @Operation(
        summary = "更新业务角色及权限点",
        description = "需要内置管理员身份和 `$ROLE_WRITE` 权限。提交完整的权限编码集合以替换现有关联。内置管理员角色由系统维护。",
        parameters = [Parameter(name = "id", `in` = ParameterIn.PATH, description = "角色 ID")],
        responses = [
            ApiResponse(responseCode = "204", description = "角色已更新", content = [Content()]),
            ApiResponse(responseCode = "400", description = "请求字段或权限点集合无效，或目标是内置管理员角色", content = [Content()]),
            ApiResponse(responseCode = "403", description = "缺少所需权限或内置管理员身份", content = [Content()]),
            ApiResponse(responseCode = "404", description = "角色不存在", content = [Content()]),
        ],
    )
    fun update(@PathVariable id: String, @RequestBody request: UpdateRoleRequest) = roles.update(id, request)
}
