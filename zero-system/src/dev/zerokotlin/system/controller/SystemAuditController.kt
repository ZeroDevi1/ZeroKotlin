package dev.zerokotlin.system.controller

import dev.zerokotlin.core.PageResult
import dev.zerokotlin.core.PageWindow
import dev.zerokotlin.system.dto.LoginAuditEntry
import dev.zerokotlin.system.dto.OperationAuditEntry
import dev.zerokotlin.system.service.SystemAudits
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 提供登录和操作审计查询接口，所有端点均要求审计读取权限。
 *
 * @property audits 按审计类别和筛选条件读取记录。
 */
@RestController
@RequestMapping("/api/system/audits")
@PreAuthorize("hasAuthority('system:audit:read')")
@Tag(name = "审计日志")
class SystemAuditController(private val audits: SystemAudits) {
    /**
     * 按登录结果筛选登录尝试。
     *
     * @param page 页码，从 1 开始，默认 1。
     * @param size 每页条数，范围为 1～100，默认 20。
     * @param successful 为 true 时查询成功记录，为 false 时查询失败记录，省略时查询两者。
     * @return 按发生时间倒序排列的登录审计页。
     */
    @GetMapping("/logins")
    @Operation(
        summary = "分页查询登录审计",
        description = "需要 `system:audit:read` 权限。按发生时间倒序排列，时间字段使用 Unix 毫秒。",
        responses = [ApiResponse(responseCode = "200", description = "登录审计分页", useReturnTypeSchema = true)],
    )
    fun logins(
        @Parameter(description = "页码，从 1 开始")
        @RequestParam(defaultValue = "1") page: Int,
        @Parameter(description = "每页条数，范围为 1–100")
        @RequestParam(defaultValue = "20") size: Int,
        @Parameter(description = "按登录结果筛选。true 为成功，false 为失败，省略时包含两类。")
        @RequestParam(required = false) successful: Boolean?,
    ): PageResult<LoginAuditEntry> = audits.loginPage(PageWindow(page, size), successful)

    /**
     * 按操作者和动作筛选操作记录。
     *
     * @param page 页码，从 1 开始，默认 1。
     * @param size 每页条数，范围为 1～100，默认 20。
     * @param actorId 操作者的用户 ID，省略时查询所有操作者。
     * @param action 完整的审计动作标识，省略时查询所有动作。
     * @return 按发生时间倒序排列的操作审计页。
     */
    @GetMapping("/operations")
    @Operation(
        summary = "分页查询操作审计",
        description = "需要 `system:audit:read` 权限。按发生时间倒序排列，时间字段使用 Unix 毫秒。",
        responses = [ApiResponse(responseCode = "200", description = "操作审计分页", useReturnTypeSchema = true)],
    )
    fun operations(
        @Parameter(description = "页码，从 1 开始")
        @RequestParam(defaultValue = "1") page: Int,
        @Parameter(description = "每页条数，范围为 1–100")
        @RequestParam(defaultValue = "20") size: Int,
        @Parameter(description = "精确匹配操作者的用户 ID")
        @RequestParam(required = false) actorId: String?,
        @Parameter(description = "精确匹配操作类型，例如 user.create")
        @RequestParam(required = false) action: String?,
    ): PageResult<OperationAuditEntry> = audits.operationPage(PageWindow(page, size), actorId, action)
}
