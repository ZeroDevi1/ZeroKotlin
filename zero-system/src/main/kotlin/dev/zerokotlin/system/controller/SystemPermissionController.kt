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

@RestController
@RequestMapping("/api/system/permissions")
class SystemPermissionController(private val permissions: SystemPermissions) {
    @GetMapping
    @PreAuthorize("hasAuthority('system:permission:read')")
    fun list(): List<PermissionSummary> = permissions.list()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('$PERMISSION_WRITE')")
    fun create(@RequestBody request: CreatePermissionRequest): PermissionSummary = permissions.create(request)

    @PutMapping("/{code}")
    @PreAuthorize("hasAuthority('$PERMISSION_WRITE')")
    fun update(@PathVariable code: String, @RequestBody request: UpdatePermissionRequest): PermissionSummary =
        permissions.update(code, request)
}
