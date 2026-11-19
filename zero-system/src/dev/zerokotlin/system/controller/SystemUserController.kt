package dev.zerokotlin.system.controller

import dev.zerokotlin.core.PageResult
import dev.zerokotlin.core.PageWindow
import dev.zerokotlin.system.dto.CreateUserRequest
import dev.zerokotlin.system.dto.CreatedResource
import dev.zerokotlin.system.dto.UserAccountRequest
import dev.zerokotlin.system.dto.UserDetails
import dev.zerokotlin.system.dto.UserListEntry
import dev.zerokotlin.system.dto.UserProfileRequest
import dev.zerokotlin.system.dto.UserSummary
import dev.zerokotlin.system.security.USER_MANAGE
import dev.zerokotlin.system.security.USER_READ
import dev.zerokotlin.system.security.USER_UPDATE
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
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 提供按数据范围查询用户、修改显示名称以及管理账号的接口。
 *
 * @property users 应用用户数据范围规则并维护用户资料、账号状态及角色分配。
 */
@RestController
@RequestMapping("/api/system/users")
@Tag(name = "用户管理")
class SystemUserController(private val users: SystemUsers) {
    /**
     * 分页查询当前用户有权查看的用户。
     *
     * @param page 页码，从 1 开始，默认 1。
     * @param size 每页条数，范围为 1～100，默认 20。
     * @param departmentId 直属部门 ID，省略时不按部门筛选。
     * @param roleId 已分配角色的 ID，省略时不按角色筛选。
     * @return 包含用户资料和已分配角色的用户页。
     */
    @GetMapping
    @PreAuthorize("hasAuthority('$USER_READ')")
    @Operation(
        summary = "分页查询用户及关联角色",
        description = "应用 `$USER_READ` 权限的行级范围，部门和角色筛选在范围内同时生效。每个用户返回其全部关联角色。",
        responses = [ApiResponse(responseCode = "200", description = "用户及关联角色分页", useReturnTypeSchema = true)],
    )
    fun list(
        @Parameter(description = "页码，从 1 开始")
        @RequestParam(defaultValue = "1") page: Int,
        @Parameter(description = "每页条数，范围为 1–100")
        @RequestParam(defaultValue = "20") size: Int,
        @Parameter(description = "仅匹配直接隶属于该部门的用户")
        @RequestParam(required = false) departmentId: String?,
        @Parameter(description = "按实际关联的角色 ID 筛选，含已停用角色")
        @RequestParam(required = false) roleId: String?,
    ): PageResult<UserListEntry> = users.list(PageWindow(page, size), departmentId, roleId)

    /**
     * 读取可见用户的资料及角色分配。
     *
     * @param id 待查询的用户 ID。
     * @return 用户摘要和已分配的角色 ID 列表。
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('$USER_READ')")
    @Operation(
        summary = "查询用户详情及角色 ID",
        description = "应用 `$USER_READ` 权限的行级范围。",
        parameters = [Parameter(name = "id", `in` = ParameterIn.PATH, description = "用户 ID")],
        responses = [
            ApiResponse(responseCode = "200", description = "用户详情和角色 ID 列表", useReturnTypeSchema = true),
            ApiResponse(responseCode = "404", description = "用户不存在或超出读取范围", content = [Content()]),
        ],
    )
    fun detail(@PathVariable id: String): UserDetails = users.detail(id)

    /**
     * 新建账号并分配业务角色，仅系统管理员可执行。
     *
     * @param request 用户名、显示名称、初始密码、可选部门和初始业务角色。
     * @return HTTP 201 响应体，包含新资源的 ID。
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('$USER_MANAGE')")
    @Operation(
        summary = "创建用户账号",
        description = "需要内置管理员身份和 `$USER_MANAGE` 权限。用户名按小写保存，可设置账号所属部门和业务角色。",
        responses = [
            ApiResponse(responseCode = "201", description = "用户已创建", useReturnTypeSchema = true),
            ApiResponse(responseCode = "400", description = "账号字段或关联的部门、角色无效", content = [Content()]),
            ApiResponse(responseCode = "403", description = "缺少所需权限或内置管理员身份", content = [Content()]),
            ApiResponse(responseCode = "409", description = "用户名已存在", content = [Content()]),
        ],
    )
    fun create(@RequestBody request: CreateUserRequest): CreatedResource = users.create(request)

    /**
     * 按资料修改权限的数据范围更新显示名称。
     *
     * @param id 待修改的用户 ID。
     * @param request 新的显示名称。
     * @return 更新后的用户摘要。
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('$USER_UPDATE')")
    @Operation(
        summary = "更新用户显示名",
        description = "应用 `$USER_UPDATE` 权限的行级范围。",
        parameters = [Parameter(name = "id", `in` = ParameterIn.PATH, description = "用户 ID")],
        responses = [
            ApiResponse(responseCode = "200", description = "更新后的用户摘要", useReturnTypeSchema = true),
            ApiResponse(responseCode = "400", description = "显示名无效", content = [Content()]),
            ApiResponse(responseCode = "404", description = "用户不存在或超出更新范围", content = [Content()]),
        ],
    )
    fun updateProfile(@PathVariable id: String, @RequestBody request: UserProfileRequest): UserSummary =
        users.updateProfile(id, request)

    /**
     * 更新账号配置，仅系统管理员可执行，成功时返回 HTTP 204。
     *
     * @param id 待修改的账号 ID。
     * @param request 账号状态、部门、完整角色集合和可选的新密码。
     */
    @PutMapping("/{id}/account")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$USER_MANAGE')")
    @Operation(
        summary = "更新用户账号设置",
        description = "需要内置管理员身份和 `$USER_MANAGE` 权限。更新部门、启用状态并替换完整角色集合，可选重置密码。" +
            "省略 departmentId 或传 null 时取消部门关联。禁用账号或重置密码会撤销该用户的全部会话。" +
            "内置管理员账号必须保持启用并保留 system-admin 角色。",
        parameters = [Parameter(name = "id", `in` = ParameterIn.PATH, description = "用户 ID")],
        responses = [
            ApiResponse(responseCode = "204", description = "账号设置已更新", content = [Content()]),
            ApiResponse(responseCode = "400", description = "账号字段或关联的部门、角色无效，或操作违反内置管理员限制", content = [Content()]),
            ApiResponse(responseCode = "403", description = "缺少所需权限或内置管理员身份", content = [Content()]),
            ApiResponse(responseCode = "404", description = "用户不存在", content = [Content()]),
        ],
    )
    fun updateAccount(@PathVariable id: String, @RequestBody request: UserAccountRequest) =
        users.updateAccount(id, request)
}
