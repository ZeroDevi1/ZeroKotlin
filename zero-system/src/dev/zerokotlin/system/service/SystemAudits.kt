package dev.zerokotlin.system.service

import com.baomidou.mybatisplus.extension.plugins.pagination.Page
import dev.zerokotlin.core.PageResult
import dev.zerokotlin.core.PageWindow
import dev.zerokotlin.system.dto.LoginAuditEntry
import dev.zerokotlin.system.dto.OperationAuditEntry
import dev.zerokotlin.system.entity.LoginAuditRecord
import dev.zerokotlin.system.entity.OperationAuditRecord
import dev.zerokotlin.system.mapper.LoginAuditMapper
import dev.zerokotlin.system.mapper.OperationAuditMapper
import dev.zerokotlin.system.support.flag
import dev.zerokotlin.system.support.query
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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
