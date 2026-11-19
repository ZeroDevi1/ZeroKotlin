package dev.zerokotlin.system.controller

import dev.zerokotlin.core.PageResult
import dev.zerokotlin.core.PageWindow
import dev.zerokotlin.system.dto.CreatedResource
import dev.zerokotlin.system.dto.DepartmentNode
import dev.zerokotlin.system.dto.DepartmentRequest
import dev.zerokotlin.system.dto.UserListEntry
import dev.zerokotlin.system.security.DEPARTMENT_WRITE
import dev.zerokotlin.system.security.USER_READ
import dev.zerokotlin.system.service.SystemDepartments
import dev.zerokotlin.system.service.SystemUsers
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
 * 提供部门树、部门成员查询及部门维护接口。
 *
 * @property departments 维护部门层级和部门资料。
 * @property users 按调用者的数据范围查询部门成员。
 */
@RestController
@RequestMapping("/api/system/departments")
@Tag(name = "部门管理")
class SystemDepartmentController(
    private val departments: SystemDepartments,
    private val users: SystemUsers,
) {
    /**
     * 读取组织架构。
     *
     * @return 从根节点展开的部门树。
     */
    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('system:department:read')")
    @Operation(
        summary = "获取部门树",
        description = "需要 `system:department:read` 权限。返回完整部门树，同级节点按排序值和 ID 升序排列。",
        responses = [ApiResponse(responseCode = "200", description = "部门树", useReturnTypeSchema = true)],
    )
    fun tree(): List<DepartmentNode> = departments.tree()

    /**
     * 分页查询指定部门内当前用户有权查看的成员。
     *
     * @param id 成员直属部门的 ID。
     * @param page 页码，从 1 开始，默认 1。
     * @param size 每页条数，范围为 1～100，默认 20。
     * @param roleId 用于筛选成员的角色 ID，省略时也包含未分配角色的成员。
     * @return 包含成员资料和已分配角色的用户页。
     */
    @GetMapping("/{id}/members")
    @PreAuthorize("hasAuthority('$USER_READ')")
    @Operation(
        summary = "分页查询部门成员",
        description = "应用 `$USER_READ` 权限的行级范围。仅查询指定部门的直属成员。部门不存在时返回空分页。",
        responses = [ApiResponse(responseCode = "200", description = "部门成员分页", useReturnTypeSchema = true)],
    )
    fun members(
        @Parameter(description = "要筛选的部门 ID")
        @PathVariable id: String,
        @Parameter(description = "页码，从 1 开始")
        @RequestParam(defaultValue = "1") page: Int,
        @Parameter(description = "每页条数，范围为 1–100")
        @RequestParam(defaultValue = "20") size: Int,
        @Parameter(description = "按实际关联的角色 ID 筛选，含已停用角色")
        @RequestParam(required = false) roleId: String?,
    ): PageResult<UserListEntry> = users.list(PageWindow(page, size), id, roleId)

    /**
     * 新增一个部门，仅系统管理员可执行。
     *
     * @param request 部门名称、上级部门 ID 和同级排序值。
     * @return HTTP 201 响应体，包含新资源的 ID。
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('$DEPARTMENT_WRITE')")
    @Operation(
        summary = "创建部门",
        description = "需要内置管理员身份和 `$DEPARTMENT_WRITE` 权限。省略 parentId 或传 null 时创建根节点。",
        responses = [
            ApiResponse(responseCode = "201", description = "部门已创建", useReturnTypeSchema = true),
            ApiResponse(responseCode = "400", description = "部门名称或父节点无效", content = [Content()]),
            ApiResponse(responseCode = "403", description = "缺少所需权限或内置管理员身份", content = [Content()]),
        ],
    )
    fun create(@RequestBody request: DepartmentRequest): CreatedResource = departments.create(request)

    /**
     * 修改部门资料或调整上级部门，仅系统管理员可执行，成功时返回 HTTP 204。
     *
     * @param id 待修改的部门 ID。
     * @param request 部门的新名称、上级部门 ID 和同级排序值。
     */
    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$DEPARTMENT_WRITE')")
    @Operation(
        summary = "更新部门",
        description = "需要内置管理员身份和 `$DEPARTMENT_WRITE` 权限。可以调整父部门，父子关系须保持无环。",
        parameters = [Parameter(name = "id", `in` = ParameterIn.PATH, description = "部门 ID")],
        responses = [
            ApiResponse(responseCode = "204", description = "部门已更新", content = [Content()]),
            ApiResponse(responseCode = "400", description = "部门名称或父节点无效", content = [Content()]),
            ApiResponse(responseCode = "403", description = "缺少所需权限或内置管理员身份", content = [Content()]),
            ApiResponse(responseCode = "404", description = "部门不存在", content = [Content()]),
        ],
    )
    fun update(@PathVariable id: String, @RequestBody request: DepartmentRequest) = departments.update(id, request)
}
