package dev.zerokotlin.system.controller

import dev.zerokotlin.core.PageResult
import dev.zerokotlin.core.PageWindow
import dev.zerokotlin.core.security.ApiRateLimit
import dev.zerokotlin.core.security.RateLimitBy
import dev.zerokotlin.system.dto.InboxEntry
import dev.zerokotlin.system.service.SystemInbox
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
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
@Tag(name = "个人收件箱")
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
    @Operation(
        summary = "分页查询个人收件箱",
        description = "返回当前已发布的公告与当前用户的已读状态，按发布时间倒序排列。时间字段使用 Unix 毫秒。",
        responses = [ApiResponse(responseCode = "200", description = "收件箱分页", useReturnTypeSchema = true)],
    )
    fun list(
        @Parameter(description = "页码，从 1 开始")
        @RequestParam(defaultValue = "1") page: Int,
        @Parameter(description = "每页条数，范围为 1–100")
        @RequestParam(defaultValue = "20") size: Int,
        @Parameter(description = "为 true 时仅返回未读公告，为 false 时包含已读和未读公告。")
        @RequestParam(defaultValue = "false") unreadOnly: Boolean,
    ): PageResult<InboxEntry> = inbox.list(PageWindow(page, size), unreadOnly)

    /**
     * 将公告标记为当前用户已读，重复提交同样返回 HTTP 204。
     *
     * @param id 当前仍处于已发布状态的公告 ID。
     */
    @PutMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "将公告标为已读",
        description = "仅记录当前用户的已读状态。重复调用保留首次已读时间。",
        parameters = [Parameter(name = "id", `in` = ParameterIn.PATH, description = "公告 ID")],
        responses = [
            ApiResponse(responseCode = "204", description = "公告已标为已读", content = [Content()]),
            ApiResponse(responseCode = "404", description = "公告不存在或当前未处于发布状态", content = [Content()]),
        ],
    )
    fun markRead(@PathVariable id: String) = inbox.markRead(id)

    /** 将当前用户收件箱中所有已发布的未读公告标为已读，成功时返回空响应体和 HTTP 204。 */
    @PutMapping("/read-all")
    @ApiRateLimit(key = "inbox.read-all", requests = 20, windowSeconds = 60, by = RateLimitBy.USER)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "一键将收件箱公告标为已读",
        description = "标记当前用户所有页中的已发布未读公告。已读公告保留首次已读时间。",
        responses = [
            ApiResponse(responseCode = "204", description = "已完成标记，包括没有未读公告的情况", content = [Content()]),
            ApiResponse(
                responseCode = "429",
                description = "当前用户超出请求配额",
                headers = [Header(name = "Retry-After", description = "距允许再次请求的秒数。")],
                content = [Content()],
            ),
            ApiResponse(responseCode = "503", description = "安全状态存储暂时不可用"),
        ],
    )
    fun markAllRead() = inbox.markAllRead()
}
