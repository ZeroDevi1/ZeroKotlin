package dev.zerokotlin.system.controller

import dev.zerokotlin.core.PageResult
import dev.zerokotlin.core.PageWindow
import dev.zerokotlin.system.dto.LoginAuditEntry
import dev.zerokotlin.system.dto.OperationAuditEntry
import dev.zerokotlin.system.service.SystemAudits
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/system/audits")
@PreAuthorize("hasAuthority('system:audit:read')")
class SystemAuditController(private val audits: SystemAudits) {
    @GetMapping("/logins")
    fun logins(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) successful: Boolean?,
    ): PageResult<LoginAuditEntry> = audits.loginPage(PageWindow(page, size), successful)

    @GetMapping("/operations")
    fun operations(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) actorId: String?,
        @RequestParam(required = false) action: String?,
    ): PageResult<OperationAuditEntry> = audits.operationPage(PageWindow(page, size), actorId, action)
}
