package dev.zerokotlin.system.controller

import dev.zerokotlin.core.RequestAudit
import dev.zerokotlin.system.dto.MonitorSnapshot
import dev.zerokotlin.system.service.SystemMonitor
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 向具备监控读取权限的用户提供精简的运行指标。 */
@RestController
@RequestMapping("/api/system/monitor")
@Tag(name = "运行监控")
class SystemMonitorController(private val monitor: SystemMonitor) {
    /** 返回一次运行监控采样，并将请求结果记入请求审计。 */
    @GetMapping
    @PreAuthorize("hasAuthority('system:monitor:read')")
    @RequestAudit("monitor.read")
    @Operation(
        summary = "查询运行监控",
        description = "需要 `system:monitor:read` 权限。返回 CPU、内存、JVM、磁盘及 Redis 状态。",
        responses = [
            ApiResponse(responseCode = "200", description = "运行监控快照", useReturnTypeSchema = true),
            ApiResponse(responseCode = "401", description = "需要有效 Bearer 会话"),
            ApiResponse(responseCode = "403", description = "缺少监控读取权限"),
        ],
    )
    fun snapshot(): MonitorSnapshot = monitor.snapshot()
}
