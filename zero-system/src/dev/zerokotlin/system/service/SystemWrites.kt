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

/**
 * 为系统写入提供事务级串行化、重新认证、授权和操作审计。调用方必须已开启事务。
 *
 * @property relations 更新数据库内的系统写入锁记录。
 * @property sessions 在取得锁后根据令牌摘要重查当前会话及权限。
 * @property audit 保存操作审计记录，与业务写入一起提交。
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
class SystemWrites(
    private val relations: SystemRelationMapper,
    private val sessions: SystemSessionAuthentication,
    private val audit: OperationAuditMapper,
) {
    /** 更新唯一的系统写入锁记录以取得事务锁，保证关系替换、树校验和审计能在同一事务完成。锁记录缺失时终止操作。 */
    fun lock() {
        check(relations.lockSystemWrites() == 1) { "缺少系统写入锁记录" }
    }

    /**
     * 取得写入锁后重新认证请求中的当前会话。会话已失效时抛出 HTTP 401 异常。
     *
     * @return 按写入时的会话和权限状态重新构建的主体。
     */
    fun lockAndAuthenticate(): SystemActor {
        lock()
        return sessions.authenticate(currentSystemActor().sessionDigest)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
    }

    /**
     * 在写入锁下重新认证并检查权限，授权不通过时抛出访问拒绝异常。
     *
     * @param authority 本次写入要求的权限编码。
     * @param administratorOnly 为 true 时还要求系统管理员身份。
     * @return 已通过写入授权的当前会话主体。
     */
    fun authorize(authority: String, administratorOnly: Boolean = false): SystemActor {
        val actor = lockAndAuthenticate()
        actor.requireScope(authority)
        if (administratorOnly) actor.requireAdministrator()
        return actor
    }

    /**
     * 将操作审计写入当前事务，时间取当前 Unix 毫秒时间戳。
     *
     * @param actor 用于记录操作者用户 ID 的会话主体。
     * @param action 审计动作标识。
     * @param targetId 本次操作涉及的资源标识。
     */
    fun record(actor: SystemActor, action: String, targetId: String) {
        audit.insert(OperationAuditRecord(newId(), actor.user.id, action, targetId, System.currentTimeMillis()))
    }
}

/**
 * 将扩展模块的操作审计接入系统写入事务。
 *
 * @property writes 在调用方事务中串行化并授权操作，保存审计记录。
 */
@Component
class SystemOperationAudit(private val writes: SystemWrites) : OperationAudit {
    /**
     * 重新校验当前会话的权限，再以该权限编码记录操作审计。
     *
     * @param permission 本次操作要求的权限编码，同时作为审计动作标识。
     * @param targetId 本次操作涉及的资源标识。
     */
    override fun record(permission: String, targetId: String) {
        val actor = writes.authorize(permission)
        writes.record(actor, permission, targetId)
    }
}
