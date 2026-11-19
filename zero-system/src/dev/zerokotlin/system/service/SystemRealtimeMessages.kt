package dev.zerokotlin.system.service

import dev.zerokotlin.system.config.REALTIME_BROADCAST_DESTINATION
import dev.zerokotlin.system.config.REALTIME_PRIVATE_QUEUE
import dev.zerokotlin.system.dto.REALTIME_BODY_MAX_LENGTH
import dev.zerokotlin.system.dto.RealtimeBroadcastRequest
import dev.zerokotlin.system.dto.RealtimeMessage
import dev.zerokotlin.system.dto.RealtimePresence
import dev.zerokotlin.system.dto.RealtimePrivateRequest
import dev.zerokotlin.system.dto.UserQuery
import dev.zerokotlin.system.mapper.UserMapper
import dev.zerokotlin.system.security.MESSAGE_BROADCAST
import dev.zerokotlin.system.security.MESSAGE_PRIVATE
import dev.zerokotlin.system.security.SystemActor
import dev.zerokotlin.system.support.checkedText
import dev.zerokotlin.system.support.missing
import dev.zerokotlin.system.support.newId
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.messaging.simp.user.SimpUserRegistry
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * 校验实时发送请求，在审计事务提交后将消息交给本实例的代理。
 *
 * @property broker 分发群发消息和用户队列消息。
 * @property registry 维护已建立连接的用户及其会话。
 * @property users 查找私发权限范围内的目标账号。
 * @property writes 在审计事务中重新校验权限及记录操作。
 * @param transactionManager 管理发送授权和审计事务。
 */
@Service
class SystemRealtimeMessages(
    private val broker: SimpMessagingTemplate,
    private val registry: SimpUserRegistry,
    private val users: UserMapper,
    private val writes: SystemWrites,
    transactionManager: PlatformTransactionManager,
) {
    private val transactions = TransactionTemplate(transactionManager)

    /** 返回当前实例已建立 STOMP 连接的不同用户数。 */
    fun online() = RealtimePresence(registry.userCount)

    /**
     * 校验群发权限和正文，向已订阅群发主题的用户提交消息。
     *
     * @param request 待群发的正文。
     */
    fun broadcast(request: RealtimeBroadcastRequest) {
        val body = checkedText(request.body, "body", REALTIME_BODY_MAX_LENGTH)
        val message = requireNotNull(transactions.execute {
            val actor = writes.authorize(MESSAGE_BROADCAST)
            writes.record(actor, MESSAGE_BROADCAST, "broadcast")
            createMessage(actor, body)
        })
        broker.convertAndSend(REALTIME_BROADCAST_DESTINATION, message)
    }

    /**
     * 校验私发权限和接收者范围，向目标用户的订阅会话提交消息。
     *
     * @param request 目标用户 ID 和待发送的正文。
     */
    fun sendPrivate(request: RealtimePrivateRequest) {
        val body = checkedText(request.body, "body", REALTIME_BODY_MAX_LENGTH)
        val recipientId = checkedText(request.recipientId, "recipientId", 36)
        val (recipient, message) = requireNotNull(transactions.execute {
            val actor = writes.authorize(MESSAGE_PRIVATE)
            val user = users.findVisible(UserQuery(actor, MESSAGE_PRIVATE), recipientId)
                ?.takeIf { it.enabled == 1 } ?: missing("可接收消息的用户")
            writes.record(actor, MESSAGE_PRIVATE, user.id)
            user.id to createMessage(actor, body)
        })
        broker.convertAndSendToUser(recipient, REALTIME_PRIVATE_QUEUE, message)
    }

    /** 根据认证主体创建出站消息，发送者信息由系统会话提供。 */
    private fun createMessage(actor: SystemActor, body: String) =
        RealtimeMessage(newId(), actor.user.id, actor.user.displayName, body, System.currentTimeMillis())
}
