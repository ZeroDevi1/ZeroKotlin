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
    fun list(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) departmentId: String?,
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
    fun updateAccount(@PathVariable id: String, @RequestBody request: UserAccountRequest) =
        users.updateAccount(id, request)
}
