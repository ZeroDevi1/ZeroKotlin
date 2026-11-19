package dev.zerokotlin.system.controller

import dev.zerokotlin.core.PageResult
import dev.zerokotlin.core.PageWindow
import dev.zerokotlin.system.dto.CreatedResource
import dev.zerokotlin.system.dto.JobEntry
import dev.zerokotlin.system.dto.JobHandlerEntry
import dev.zerokotlin.system.dto.JobRunEntry
import dev.zerokotlin.system.dto.RunJobRequest
import dev.zerokotlin.system.dto.SaveJobRequest
import dev.zerokotlin.system.security.JOB_CREATE
import dev.zerokotlin.system.security.JOB_DELETE
import dev.zerokotlin.system.security.JOB_LOG_READ
import dev.zerokotlin.system.security.JOB_PAUSE
import dev.zerokotlin.system.security.JOB_READ
import dev.zerokotlin.system.security.JOB_RESUME
import dev.zerokotlin.system.security.JOB_RUN
import dev.zerokotlin.system.security.JOB_UPDATE
import dev.zerokotlin.system.service.JobHandlers
import dev.zerokotlin.system.service.SystemJobs
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** 提供 Quartz 任务定义、手动执行与历史读取接口。 */
@RestController
@RequestMapping("/api/system/jobs")
@Tag(name = "定时任务")
class SystemJobController(private val jobs: SystemJobs, private val handlers: JobHandlers) {
    /** 返回任务定义及下一次待派发时间。 */
    @GetMapping
    @PreAuthorize("hasAuthority('$JOB_READ')")
    @Operation(summary = "分页查询定时任务", description = "需要 `$JOB_READ` 权限。")
    fun list(
        @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") page: Int,
        @Parameter(description = "每页条数，范围为 1–100") @RequestParam(defaultValue = "20") size: Int,
    ): PageResult<JobEntry> = jobs.list(PageWindow(page, size))

    /** 返回本地允许名单中的任务实现键。 */
    @GetMapping("/handlers")
    @PreAuthorize("hasAuthority('$JOB_READ')")
    @Operation(summary = "查询可选任务实现", description = "需要 `$JOB_READ` 权限。")
    fun handlers(): List<JobHandlerEntry> = handlers.list()

    /** 创建任务，返回任务 ID。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('$JOB_CREATE')")
    @Operation(summary = "创建定时任务", description = "需要 `$JOB_CREATE` 权限。配置触发器后启用自动执行。")
    @ApiResponse(responseCode = "201", description = "已创建任务", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", description = "任务名称、实现键或触发器配置无效")
    fun create(@RequestBody request: SaveJobRequest): CreatedResource = jobs.create(request)

    /** 更新任务，变更触发器时重算计划。 */
    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$JOB_UPDATE')")
    @Operation(summary = "编辑定时任务", description = "需要 `$JOB_UPDATE` 权限。提交完整定义。")
    @ApiResponse(responseCode = "204", description = "已更新任务")
    @ApiResponse(responseCode = "400", description = "任务名称、实现键或触发器配置无效")
    @ApiResponse(responseCode = "404", description = "任务不存在")
    fun update(@PathVariable id: String, @RequestBody request: SaveJobRequest) = jobs.update(id, request)

    /** 删除任务、取消未执行的手动请求并保留历史。 */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$JOB_DELETE')")
    @Operation(summary = "删除定时任务", description = "需要 `$JOB_DELETE` 权限。已保存的执行历史仍可查询。")
    @ApiResponse(responseCode = "204", description = "已删除任务")
    @ApiResponse(responseCode = "404", description = "任务不存在")
    fun delete(@PathVariable id: String) = jobs.delete(id)

    /** 幂等地暂停自动计划。 */
    @PostMapping("/{id}/pause")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$JOB_PAUSE')")
    @Operation(summary = "暂停自动计划", description = "需要 `$JOB_PAUSE` 权限。暂停期间仍可手动执行。")
    @ApiResponse(responseCode = "204", description = "自动计划已暂停")
    @ApiResponse(responseCode = "404", description = "任务不存在")
    @ApiResponse(responseCode = "409", description = "任务未配置自动触发器")
    fun pause(@PathVariable id: String) = jobs.setPaused(id, true)

    /** 幂等地恢复自动计划，跳过暂停期间的时间点。 */
    @PostMapping("/{id}/resume")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$JOB_RESUME')")
    @Operation(summary = "恢复自动计划", description = "需要 `$JOB_RESUME` 权限。使用原基准计算下一次触发时间。")
    @ApiResponse(responseCode = "204", description = "自动计划已恢复")
    @ApiResponse(responseCode = "404", description = "任务不存在")
    @ApiResponse(responseCode = "409", description = "任务未配置自动触发器")
    fun resume(@PathVariable id: String) = jobs.setPaused(id, false)

    /** 受理一次手动请求，返回对应执行日志 ID。 */
    @PostMapping("/{id}/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('$JOB_RUN')")
    @Operation(
        summary = "立即执行一次定时任务",
        description = "需要 `$JOB_RUN` 权限。响应表示已入队。同一账号可用原 requestId 重试。",
    )
    @ApiResponse(responseCode = "202", description = "手动执行请求已受理", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", description = "请求标识无效")
    @ApiResponse(responseCode = "404", description = "任务不存在")
    @ApiResponse(responseCode = "409", description = "请求标识已被使用")
    fun run(@PathVariable id: String, @RequestBody request: RunJobRequest): CreatedResource = jobs.runNow(id, request)

    /** 分页查询日志，可按任务 ID 筛选。 */
    @GetMapping("/logs")
    @PreAuthorize("hasAuthority('$JOB_LOG_READ')")
    @Operation(summary = "分页查询任务执行日志", description = "需要 `$JOB_LOG_READ` 权限。包含已删除任务的历史。")
    fun logs(
        @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") page: Int,
        @Parameter(description = "每页条数，范围为 1–100") @RequestParam(defaultValue = "20") size: Int,
        @Parameter(description = "精确匹配的任务 ID") @RequestParam(required = false) jobId: String?,
    ): PageResult<JobRunEntry> = jobs.logs(PageWindow(page, size), jobId)
}
