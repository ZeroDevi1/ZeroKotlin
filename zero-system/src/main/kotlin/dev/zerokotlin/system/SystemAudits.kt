package dev.zerokotlin.system

import com.baomidou.mybatisplus.extension.plugins.pagination.Page
import dev.zerokotlin.core.PageResult
import dev.zerokotlin.core.PageWindow
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class LoginAuditEntry(
    val id: String,
    val userId: String?,
    val username: String?,
    val successful: Boolean,
    val remoteAddress: String,
    val occurredAt: Long,
)

data class OperationAuditEntry(
    val id: String,
    val actorId: String,
    val action: String,
    val targetId: String,
    val occurredAt: Long,
)

@Service
class SystemAudits(
    private val logins: LoginAuditMapper,
    private val operations: OperationAuditMapper,
) {
    @Transactional(readOnly = true)
    fun loginPage(window: PageWindow, successful: Boolean?): PageResult<LoginAuditEntry> {
        val results = logins.selectPage(
            Page<LoginAuditRecord>(window.page.toLong(), window.size.toLong()),
            query<LoginAuditRecord>().eq(successful != null, "successful", successful?.flag())
                .orderByDesc("occurred_at", "id"),
        )

        val entries = results.records.map {
            LoginAuditEntry(it.id, it.userId, it.username, it.successful == 1, it.remoteAddress, it.occurredAt)
        }
        return window.result(entries, results.total)
    }

    @Transactional(readOnly = true)
    fun operationPage(window: PageWindow, actorId: String?, action: String?): PageResult<OperationAuditEntry> {
        val results = operations.selectPage(
            Page<OperationAuditRecord>(window.page.toLong(), window.size.toLong()),
            query<OperationAuditRecord>().eq(actorId != null, "actor_id", actorId).eq(action != null, "action", action)
                .orderByDesc("occurred_at", "id"),
        )

        val entries = results.records.map {
            OperationAuditEntry(it.id, it.actorId, it.action, it.targetId, it.occurredAt)
        }
        return window.result(entries, results.total)
    }
}

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
