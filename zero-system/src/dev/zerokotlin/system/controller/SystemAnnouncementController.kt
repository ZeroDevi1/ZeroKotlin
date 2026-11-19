package dev.zerokotlin.system.controller

import dev.zerokotlin.core.PageResult
import dev.zerokotlin.core.PageWindow
import dev.zerokotlin.system.dto.AnnouncementEntry
import dev.zerokotlin.system.dto.AnnouncementRequest
import dev.zerokotlin.system.dto.CreatedResource
import dev.zerokotlin.system.security.ANNOUNCEMENT_READ
import dev.zerokotlin.system.security.ANNOUNCEMENT_WRITE
import dev.zerokotlin.system.service.SystemAnnouncements
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
@RequestMapping("/api/system/announcements")
class SystemAnnouncementController(private val announcements: SystemAnnouncements) {
    @GetMapping
    @PreAuthorize("hasAuthority('$ANNOUNCEMENT_READ')")
    fun list(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResult<AnnouncementEntry> = announcements.list(PageWindow(page, size))

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('$ANNOUNCEMENT_WRITE')")
    fun create(@RequestBody request: AnnouncementRequest): CreatedResource = announcements.create(request)

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$ANNOUNCEMENT_WRITE')")
    fun update(@PathVariable id: String, @RequestBody request: AnnouncementRequest) = announcements.update(id, request)

    @PostMapping("/{id}/publish")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$ANNOUNCEMENT_WRITE')")
    fun publish(@PathVariable id: String) = announcements.publish(id)

    @PostMapping("/{id}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$ANNOUNCEMENT_WRITE')")
    fun revoke(@PathVariable id: String) = announcements.revoke(id)
}
