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
@RequestMapping("/api/system/departments")
class SystemDepartmentController(
    private val departments: SystemDepartments,
    private val users: SystemUsers,
) {
    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('system:department:read')")
    fun tree(): List<DepartmentNode> = departments.tree()

    @GetMapping("/{id}/members")
    @PreAuthorize("hasAuthority('$USER_READ')")
    fun members(
        @PathVariable id: String,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) roleId: String?,
    ): PageResult<UserListEntry> = users.list(PageWindow(page, size), id, roleId)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('$DEPARTMENT_WRITE')")
    fun create(@RequestBody request: DepartmentRequest): CreatedResource = departments.create(request)

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$DEPARTMENT_WRITE')")
    fun update(@PathVariable id: String, @RequestBody request: DepartmentRequest) = departments.update(id, request)
}
