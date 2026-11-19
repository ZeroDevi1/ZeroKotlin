package dev.zerokotlin.system.service

import dev.zerokotlin.core.RequestAuditEvent
import dev.zerokotlin.core.RequestAuditSink
import dev.zerokotlin.system.entity.RequestAuditRecord
import dev.zerokotlin.system.mapper.RequestAuditMapper
import dev.zerokotlin.system.security.SystemActor
import dev.zerokotlin.system.support.newId
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/** 在独立事务中持久化请求审计，并记录当前会话的用户 ID。 */
@Service
class SystemRequestAudits(private val requests: RequestAuditMapper) : RequestAuditSink {
    /** 请求没有系统会话时，操作者 ID 留空。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun save(event: RequestAuditEvent) {
        val actor = SecurityContextHolder.getContext().authentication?.principal as? SystemActor
        requests.insert(
            RequestAuditRecord(
                id = newId(),
                actorId = actor?.user?.id,
                action = event.action,
                httpMethod = event.method,
                routePattern = event.route,
                httpStatus = event.status,
                durationMs = event.durationMs,
                exceptionType = event.exceptionType,
                remoteAddress = event.remoteAddress,
                ipRegion = event.ipRegion,
                userAgent = event.userAgent,
                occurredAt = event.occurredAt,
            ),
        )
    }
}
