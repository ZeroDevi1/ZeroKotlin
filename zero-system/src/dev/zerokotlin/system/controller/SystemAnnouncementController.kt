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

/**
 * 提供公告管理接口，读写操作分别校验公告读取和写入权限。
 *
 * @property announcements 负责查询公告及保存其生命周期变更。
 */
@RestController
@RequestMapping("/api/system/announcements")
class SystemAnnouncementController(private val announcements: SystemAnnouncements) {
    /**
     * 分页查询各状态的公告。
     *
     * @param page 页码，从 1 开始，默认 1。
     * @param size 每页条数，范围为 1～100，默认 20。
     * @return 按创建时间倒序排列的公告页。
     */
    @GetMapping
    @PreAuthorize("hasAuthority('$ANNOUNCEMENT_READ')")
    fun list(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResult<AnnouncementEntry> = announcements.list(PageWindow(page, size))

    /**
     * 新建草稿公告。
     *
     * @param request 草稿标题和正文。
     * @return HTTP 201 响应体，包含新资源的 ID。
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('$ANNOUNCEMENT_WRITE')")
    fun create(@RequestBody request: AnnouncementRequest): CreatedResource = announcements.create(request)

    /**
     * 修改草稿的标题和正文，成功时返回 HTTP 204。
     *
     * @param id 待修改的公告 ID。
     * @param request 草稿的新标题和正文。
     */
    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$ANNOUNCEMENT_WRITE')")
    fun update(@PathVariable id: String, @RequestBody request: AnnouncementRequest) = announcements.update(id, request)

    /**
     * 发布草稿公告，成功时返回 HTTP 204。
     *
     * @param id 待发布的草稿公告 ID。
     */
    @PostMapping("/{id}/publish")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$ANNOUNCEMENT_WRITE')")
    fun publish(@PathVariable id: String) = announcements.publish(id)

    /**
     * 撤回已发布的公告，成功时返回 HTTP 204。
     *
     * @param id 待撤回的公告 ID。
     */
    @PostMapping("/{id}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$ANNOUNCEMENT_WRITE')")
    fun revoke(@PathVariable id: String) = announcements.revoke(id)
}
