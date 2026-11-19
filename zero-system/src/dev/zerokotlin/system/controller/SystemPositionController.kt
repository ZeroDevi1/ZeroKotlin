package dev.zerokotlin.system.controller

import dev.zerokotlin.core.PageResult
import dev.zerokotlin.core.PageWindow
import dev.zerokotlin.system.dto.CreatePositionRequest
import dev.zerokotlin.system.dto.CreatedResource
import dev.zerokotlin.system.dto.PositionEntry
import dev.zerokotlin.system.dto.PositionMember
import dev.zerokotlin.system.dto.ReplacePositionMembersRequest
import dev.zerokotlin.system.dto.UpdatePositionRequest
import dev.zerokotlin.system.security.POSITION_READ
import dev.zerokotlin.system.security.POSITION_WRITE
import dev.zerokotlin.system.service.SystemPositions
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
@RequestMapping("/api/system/positions")
class SystemPositionController(private val positions: SystemPositions) {
    @GetMapping
    @PreAuthorize("hasAuthority('$POSITION_READ')")
    fun list(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResult<PositionEntry> = positions.list(PageWindow(page, size))

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('$POSITION_WRITE')")
    fun create(@RequestBody request: CreatePositionRequest): CreatedResource = positions.create(request)

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$POSITION_WRITE')")
    fun update(@PathVariable id: String, @RequestBody request: UpdatePositionRequest) = positions.update(id, request)

    @GetMapping("/{id}/members")
    @PreAuthorize("hasAuthority('$POSITION_READ')")
    fun members(@PathVariable id: String): List<PositionMember> = positions.members(id)

    @PutMapping("/{id}/members")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$POSITION_WRITE')")
    fun replaceMembers(@PathVariable id: String, @RequestBody request: ReplacePositionMembersRequest) =
        positions.replaceMembers(id, request)
}
