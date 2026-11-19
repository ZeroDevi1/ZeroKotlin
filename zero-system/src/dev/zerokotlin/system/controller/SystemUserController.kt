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

@RestController
@RequestMapping("/api/system/users")
class SystemUserController(private val users: SystemUsers) {
    @GetMapping
    @PreAuthorize("hasAuthority('$USER_READ')")
    fun list(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) departmentId: String?,
        @RequestParam(required = false) roleId: String?,
    ): PageResult<UserListEntry> = users.list(PageWindow(page, size), departmentId, roleId)

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('$USER_READ')")
    fun detail(@PathVariable id: String): UserDetails = users.detail(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('$USER_MANAGE')")
    fun create(@RequestBody request: CreateUserRequest): CreatedResource = users.create(request)

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('$USER_UPDATE')")
    fun updateProfile(@PathVariable id: String, @RequestBody request: UserProfileRequest): UserSummary =
        users.updateProfile(id, request)

    @PutMapping("/{id}/account")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$USER_MANAGE')")
    fun updateAccount(@PathVariable id: String, @RequestBody request: UserAccountRequest) =
        users.updateAccount(id, request)
}
