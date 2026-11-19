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
import dev.zerokotlin.system.service.SystemPositionService
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
 * 提供岗位目录及成员管理接口。
 *
 * @property positions 查询岗位与成员，保存岗位和成员关系。
 */
@RestController
@RequestMapping("/api/system/positions")
@Tag(name = "岗位管理")
class SystemPositionController(private val positions: SystemPositionService) {
    /**
     * 分页读取岗位目录。
     *
     * @param page 页码，从 1 开始，默认 1。
     * @param size 每页条数，范围为 1～100，默认 20。
     * @return 按排序值和 ID 升序排列的岗位页，含停用岗位。
     */
    @GetMapping
    @PreAuthorize("hasAuthority('$POSITION_READ')")
    @Operation(
        summary = "分页查询岗位",
        description = "需要 `$POSITION_READ` 权限。包含已停用的岗位，按排序值和 ID 升序排列。",
        responses = [ApiResponse(responseCode = "200", description = "岗位分页", useReturnTypeSchema = true)],
    )
    fun list(
        @Parameter(description = "页码，从 1 开始")
        @RequestParam(defaultValue = "1") page: Int,
        @Parameter(description = "每页条数，范围为 1–100")
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResult<PositionEntry> = positions.list(PageWindow(page, size))

    /**
     * 新建岗位，仅系统管理员可执行。
     *
     * @param request 唯一的岗位编码、岗位名称和排序值。
     * @return HTTP 201 响应体，包含新资源的 ID。
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('$POSITION_WRITE')")
    @Operation(
        summary = "创建岗位",
        description = "需要内置管理员身份和 `$POSITION_WRITE` 权限。新岗位默认启用，岗位编码全局唯一。",
        responses = [
            ApiResponse(responseCode = "201", description = "岗位已创建", useReturnTypeSchema = true),
            ApiResponse(responseCode = "400", description = "岗位编码或名称无效，或缺少排序值", content = [Content()]),
            ApiResponse(responseCode = "403", description = "缺少所需权限或内置管理员身份", content = [Content()]),
            ApiResponse(responseCode = "409", description = "岗位编码已存在", content = [Content()]),
        ],
    )
    fun create(@RequestBody request: CreatePositionRequest): CreatedResource = positions.create(request)

    /**
     * 修改岗位名称、启用状态和排序，仅系统管理员可执行，成功时返回 HTTP 204。
     *
     * @param id 待修改的岗位 ID。
     * @param request 岗位的新名称、启用状态和排序值。
     */
    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$POSITION_WRITE')")
    @Operation(
        summary = "更新岗位",
        description = "需要内置管理员身份和 `$POSITION_WRITE` 权限。更新岗位名称、启用状态和排序值。",
        parameters = [Parameter(name = "id", `in` = ParameterIn.PATH, description = "岗位 ID")],
        responses = [
            ApiResponse(responseCode = "204", description = "岗位已更新", content = [Content()]),
            ApiResponse(responseCode = "400", description = "岗位名称无效，或缺少启用状态或排序值", content = [Content()]),
            ApiResponse(responseCode = "403", description = "缺少所需权限或内置管理员身份", content = [Content()]),
            ApiResponse(responseCode = "404", description = "岗位不存在", content = [Content()]),
        ],
    )
    fun update(@PathVariable id: String, @RequestBody request: UpdatePositionRequest) = positions.update(id, request)

    /**
     * 读取岗位的完整成员名单，要求岗位读取权限且仅系统管理员可执行。
     *
     * @param id 岗位 ID。
     * @return 按用户名和 ID 升序排列的成员摘要。
     */
    @GetMapping("/{id}/members")
    @PreAuthorize("hasAuthority('$POSITION_READ')")
    @Operation(
        summary = "查询岗位的完整成员名单",
        description = "需要内置管理员身份和 `$POSITION_READ` 权限。包含已停用的账号，按用户名和 ID 升序排列。",
        parameters = [Parameter(name = "id", `in` = ParameterIn.PATH, description = "岗位 ID")],
        responses = [
            ApiResponse(responseCode = "200", description = "岗位成员名单", useReturnTypeSchema = true),
            ApiResponse(responseCode = "403", description = "缺少所需权限或内置管理员身份", content = [Content()]),
            ApiResponse(responseCode = "404", description = "岗位不存在", content = [Content()]),
        ],
    )
    fun members(@PathVariable id: String): List<PositionMember> = positions.members(id)

    /**
     * 整体替换岗位成员，仅系统管理员可执行，成功时返回 HTTP 204。
     *
     * @param id 待调整的岗位 ID。
     * @param request 完整的用户 ID 集合，空集会清空岗位成员。
     */
    @PutMapping("/{id}/members")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$POSITION_WRITE')")
    @Operation(
        summary = "替换岗位的完整成员名单",
        description = "需要内置管理员身份和 `$POSITION_WRITE` 权限。提交完整的用户 ID 集合，空集合表示清空名单。",
        parameters = [Parameter(name = "id", `in` = ParameterIn.PATH, description = "岗位 ID")],
        responses = [
            ApiResponse(responseCode = "204", description = "岗位成员名单已替换", content = [Content()]),
            ApiResponse(responseCode = "400", description = "名单中有无效或不存在的用户 ID", content = [Content()]),
            ApiResponse(responseCode = "403", description = "缺少所需权限或内置管理员身份", content = [Content()]),
            ApiResponse(responseCode = "404", description = "岗位不存在", content = [Content()]),
        ],
    )
    fun replaceMembers(@PathVariable id: String, @RequestBody request: ReplacePositionMembersRequest) =
        positions.replaceMembers(id, request)
}
