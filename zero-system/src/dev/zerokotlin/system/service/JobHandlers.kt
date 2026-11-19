package dev.zerokotlin.system.service

import dev.zerokotlin.system.dto.JobHandlerEntry
import dev.zerokotlin.system.entity.SessionRecord
import dev.zerokotlin.system.mapper.SessionMapper
import dev.zerokotlin.system.support.invalid
import dev.zerokotlin.system.support.query
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/** 本地任务允许名单。实现只执行短时、可随 JDBC 事务回滚的工作。 */
@Component
class JobHandlers(private val sessions: SessionMapper) {
    private val handlers = mapOf(
        "heartbeat" to ("调度心跳" to { _: Long -> "调度心跳执行成功" }),
        "purge-expired-sessions" to ("清理过期会话" to { now: Long ->
            val removed = sessions.delete(query<SessionRecord>().le("expires_at", now))
            "已清理 ${removed} 条过期会话"
        }),
    )

    /** 返回界面可选的实现键和名称。 */
    fun list(): List<JobHandlerEntry> = handlers.map { (key, handler) -> JobHandlerEntry(key, handler.first) }

    /** 保存前校验实现键必须来自本地允许名单。 */
    fun validate(key: String) {
        if (key !in handlers) invalid("未知的任务实现")
    }

    /** 用保存点隔离任务效果，使失败回滚后外层事务仍可保存执行结果。 */
    @Transactional(propagation = Propagation.NESTED)
    fun execute(key: String, now: Long): String {
        val handler = handlers[key] ?: throw IllegalStateException("本地任务实现已移除")
        return handler.second(now)
    }
}
