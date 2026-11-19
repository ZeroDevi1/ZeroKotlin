package dev.zerokotlin.system.controller

import dev.zerokotlin.core.PageResult
import dev.zerokotlin.core.PageWindow
import dev.zerokotlin.system.dto.InboxEntry
import dev.zerokotlin.system.service.SystemInbox
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 提供当前已认证用户的公告收件箱接口。
 *
 * @property inbox 查询已发布公告并维护各用户的已读时间。
 */
@RestController
@RequestMapping("/api/inbox")
class SystemInboxController(private val inbox: SystemInbox) {
    /**
     * 分页读取当前用户收件箱中的已发布公告。
     *
     * @param page 页码，从 1 开始，默认 1。
     * @param size 每页条数，范围为 1～100，默认 20。
     * @param unreadOnly 为 true 时只返回当前用户尚未读过的公告，默认 false。
     * @return 按发布时间倒序排列的公告页，各条目带当前用户的已读时间。
     */
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "false") unreadOnly: Boolean,
    ): PageResult<InboxEntry> = inbox.list(PageWindow(page, size), unreadOnly)

    /**
     * 将公告标记为当前用户已读，重复提交同样返回 HTTP 204。
     *
     * @param id 当前仍处于已发布状态的公告 ID。
     */
    @PutMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markRead(@PathVariable id: String) = inbox.markRead(id)
}
