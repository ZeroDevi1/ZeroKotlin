package dev.zerokotlin.ktor.system.realtime

import dev.zerokotlin.core.SystemFailure
import dev.zerokotlin.ktor.SystemStore
import dev.zerokotlin.ktor.invalid
import dev.zerokotlin.ktor.missing
import dev.zerokotlin.ktor.newId
import dev.zerokotlin.ktor.system.auth.CurrentUser
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap

private const val readMessage = "system:message:read"
private const val broadcastMessage = "system:message:broadcast"
private const val privateMessage = "system:message:private"
internal const val broadcastDestination = "/topic/realtime/broadcast"
internal const val privateDestination = "/user/queue/realtime/private"

@Serializable internal data class Presence(val count: Int)
@Serializable internal data class RealtimeMessage(
    val id: String, val senderId: String, val senderName: String, val body: String, val sentAt: Long,
)
internal data class Delivery(val destination: String, val message: RealtimeMessage)

/** 每条连接持有有限缓冲，慢连接满队列时丢弃本次投递。 */
internal class Subscriber(val user: String, val tokenDigest: String) {
    val subscriptions = ConcurrentHashMap<String, String>()
    val outbound = Channel<Delivery>(capacity = 64)
}

/** 会话权限在订阅和投递时复核。发布在审计事务提交后才进入当前进程的队列。 */
internal class RealtimeService(private val store: SystemStore) {
    private val subscribers = ConcurrentHashMap.newKeySet<Subscriber>()

    suspend fun online(token: String): Presence =
        store.read(token, readMessage) { _, _ -> Presence(subscribers.map { it.user }.distinct().size) }

    suspend fun authorizeRead(token: String) {
        store.read(token, readMessage) { _, _ -> Unit }
    }

    suspend fun broadcast(token: String, text: String) {
        val body = messageBody(text)
        val message = store.write(token, broadcastMessage) { _, actor, audit ->
            audit(broadcastMessage, "broadcast")
            actor.message(body)
        }
        subscribers.forEach { it.outbound.trySend(Delivery(broadcastDestination, message)) }
    }

    suspend fun sendPrivate(token: String, rawRecipient: String, text: String) {
        val body = messageBody(text)
        val recipient = rawRecipient.trim().takeIf { it.length in 1..36 } ?: invalid("接收者 ID 无效")
        val message = store.write(token, privateMessage) { db, actor, audit ->
            if (!db.canReceiveMessage(recipient, actor, privateMessage)) missing("可接收消息的用户")
            audit(privateMessage, recipient)
            actor.message(body)
        }
        subscribers.filter { it.user == recipient }.forEach { it.outbound.trySend(Delivery(privateDestination, message)) }
    }

    suspend fun connect(sessionDigest: String): Subscriber? {
        val actor = current(sessionDigest) ?: return null
        return Subscriber(actor.id, sessionDigest).also { subscribers.add(it) }
    }

    suspend fun isAuthorized(subscriber: Subscriber): Boolean =
        current(subscriber.tokenDigest)?.id == subscriber.user

    fun disconnect(subscriber: Subscriber) {
        subscribers.remove(subscriber)
        subscriber.outbound.close()
    }

    private suspend fun current(sessionDigest: String): CurrentUser? = try {
        store.currentDigest(sessionDigest).takeIf { readMessage in it.permissions }
    } catch (failure: SystemFailure) {
        if (failure.status != 401) throw failure
        null
    }
}

private fun CurrentUser.message(body: String) = RealtimeMessage(newId(), id, displayName, body, System.currentTimeMillis())
private fun messageBody(value: String) = value.trim().takeIf { it.length in 1..2_000 } ?: invalid("正文长度须为 1 至 2000 字符")
