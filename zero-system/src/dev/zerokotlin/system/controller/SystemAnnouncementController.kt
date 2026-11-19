package dev.zerokotlin.system.controller

import dev.zerokotlin.core.PageResult
import dev.zerokotlin.core.PageWindow
import dev.zerokotlin.system.dto.AnnouncementEntry
import dev.zerokotlin.system.dto.AnnouncementRequest
import dev.zerokotlin.system.dto.CreatedResource
import dev.zerokotlin.system.security.ANNOUNCEMENT_READ
import dev.zerokotlin.system.security.ANNOUNCEMENT_WRITE
import dev.zerokotlin.system.service.SystemAnnouncementService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
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
@Tag(name = "公告管理")
class SystemAnnouncementController(private val announcements: SystemAnnouncementService) {
    /**
     * 分页查询各状态的公告。
     *
     * @param page 页码，从 1 开始，默认 1。
     * @param size 每页条数，范围为 1～100，默认 20。
     * @return 按创建时间倒序排列的公告页。
     */
    @GetMapping
    @PreAuthorize("hasAuthority('$ANNOUNCEMENT_READ')")
    @Operation(
        summary = "分页查询公告",
        description = "需要 `$ANNOUNCEMENT_READ` 权限。包含所有状态，按创建时间倒序排列。时间字段使用 Unix 毫秒。",
        responses = [ApiResponse(responseCode = "200", description = "公告分页", useReturnTypeSchema = true)],
    )
    fun list(
        @Parameter(description = "页码，从 1 开始")
        @RequestParam(defaultValue = "1") page: Int,
        @Parameter(description = "每页条数，范围为 1–100")
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
    @Operation(
        summary = "创建公告草稿",
        description = "需要 `$ANNOUNCEMENT_WRITE` 权限。初始状态为 DRAFT。",
        responses = [
            ApiResponse(responseCode = "201", description = "草稿已创建", useReturnTypeSchema = true),
            ApiResponse(responseCode = "400", description = "公告标题或正文无效", content = [Content()]),
        ],
    )
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
    @Operation(
        summary = "更新公告草稿",
        description = "需要 `$ANNOUNCEMENT_WRITE` 权限。仅允许更新 DRAFT 状态的公告。",
        parameters = [Parameter(name = "id", `in` = ParameterIn.PATH, description = "公告 ID")],
        responses = [
            ApiResponse(responseCode = "204", description = "草稿已更新", content = [Content()]),
            ApiResponse(responseCode = "400", description = "公告标题或正文无效", content = [Content()]),
            ApiResponse(responseCode = "404", description = "公告不存在", content = [Content()]),
            ApiResponse(responseCode = "409", description = "公告状态不允许修改", content = [Content()]),
        ],
    )
    fun update(@PathVariable id: String, @RequestBody request: AnnouncementRequest) = announcements.update(id, request)

    /**
     * 发布草稿公告，成功时返回 HTTP 204。
     *
     * @param id 待发布的草稿公告 ID。
     */
    @PostMapping("/{id}/publish")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$ANNOUNCEMENT_WRITE')")
    @Operation(
        summary = "发布公告",
        description = "需要 `$ANNOUNCEMENT_WRITE` 权限。将 DRAFT 公告设为 PUBLISHED，并记下发布时间。",
        parameters = [Parameter(name = "id", `in` = ParameterIn.PATH, description = "公告 ID")],
        responses = [
            ApiResponse(responseCode = "204", description = "公告已发布", content = [Content()]),
            ApiResponse(responseCode = "404", description = "公告不存在", content = [Content()]),
            ApiResponse(responseCode = "409", description = "公告状态不允许发布", content = [Content()]),
        ],
    )
    fun publish(@PathVariable id: String) = announcements.publish(id)

    /**
     * 撤回已发布的公告，成功时返回 HTTP 204。
     *
     * @param id 待撤回的公告 ID。
     */
    @PostMapping("/{id}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$ANNOUNCEMENT_WRITE')")
    @Operation(
        summary = "撤回公告",
        description = "需要 `$ANNOUNCEMENT_WRITE` 权限。将 PUBLISHED 公告设为终态 REVOKED，保留原发布时间。",
        parameters = [Parameter(name = "id", `in` = ParameterIn.PATH, description = "公告 ID")],
        responses = [
            ApiResponse(responseCode = "204", description = "公告已撤回", content = [Content()]),
            ApiResponse(responseCode = "404", description = "公告不存在", content = [Content()]),
            ApiResponse(responseCode = "409", description = "公告状态不允许撤回", content = [Content()]),
        ],
    )
    fun revoke(@PathVariable id: String) = announcements.revoke(id)
}
