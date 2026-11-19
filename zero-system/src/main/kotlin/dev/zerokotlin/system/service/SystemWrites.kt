package dev.zerokotlin.system.service

import dev.zerokotlin.core.OperationAudit
import dev.zerokotlin.system.entity.OperationAuditRecord
import dev.zerokotlin.system.mapper.OperationAuditMapper
import dev.zerokotlin.system.mapper.SystemRelationMapper
import dev.zerokotlin.system.security.SystemActor
import dev.zerokotlin.system.security.SystemSessionAuthentication
import dev.zerokotlin.system.security.currentSystemActor
import dev.zerokotlin.system.support.newId
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Component
@Transactional(propagation = Propagation.MANDATORY)
class SystemWrites(
    private val relations: SystemRelationMapper,
    private val sessions: SystemSessionAuthentication,
    private val audit: OperationAuditMapper,
) {
    // 所有系统配置写入都在事务内先取得此锁，保证关系替换、树校验与审计一起提交。
    fun lock() {
        check(relations.lockSystemWrites() == 1) { "缺少系统写入锁记录" }
    }

    fun lockAndAuthenticate(): SystemActor {
        lock()
        return sessions.authenticate(currentSystemActor().sessionDigest)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
    }

    fun authorize(authority: String, administratorOnly: Boolean = false): SystemActor {
        val actor = lockAndAuthenticate()
        actor.requireScope(authority)
        if (administratorOnly) actor.requireAdministrator()
        return actor
    }

    fun record(actor: SystemActor, action: String, targetId: String) {
        audit.insert(OperationAuditRecord(newId(), actor.user.id, action, targetId, System.currentTimeMillis()))
    }
}

@Component
class SystemOperationAudit(private val writes: SystemWrites) : OperationAudit {
    override fun record(permission: String, targetId: String) {
        val actor = writes.authorize(permission)
        writes.record(actor, permission, targetId)
    }
}
