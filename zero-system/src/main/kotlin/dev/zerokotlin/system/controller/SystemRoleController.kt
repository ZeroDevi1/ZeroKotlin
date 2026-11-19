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

@RestController
@RequestMapping("/api/system/roles")
class SystemRoleController(private val roles: SystemRoles) {
    @GetMapping
    @PreAuthorize("hasAuthority('system:role:read')")
    fun list(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) enabled: Boolean?,
    ): PageResult<RoleSummary> = roles.list(PageWindow(page, size), enabled)

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:read')")
    fun detail(@PathVariable id: String): RoleDetails = roles.detail(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('$ROLE_WRITE')")
    fun create(@RequestBody request: CreateRoleRequest): CreatedResource = roles.create(request)

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$ROLE_WRITE')")
    fun update(@PathVariable id: String, @RequestBody request: UpdateRoleRequest) = roles.update(id, request)
}
