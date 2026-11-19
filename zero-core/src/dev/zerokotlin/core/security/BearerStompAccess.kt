package dev.zerokotlin.core.security

import java.security.Principal
import java.util.concurrent.ConcurrentHashMap
import org.springframework.http.HttpHeaders
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.MessageHandler
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.messaging.simp.SimpMessageType
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.ExecutorChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.Authentication

/**
 * 业务模块为 STOMP 连接声明的订阅策略。
 *
 * @property authority 建连、订阅和接收消息所需的权限编码。
 * @property destinations 客户端可以订阅的完整地址集合。
 * @property userId 将认证结果转换为稳定用户 ID，作为 Spring 用户目的地的路由键。
 */
class StompAccessPolicy(
    val authority: String,
    val destinations: Set<String>,
    val userId: (Authentication) -> String,
)

/**
 * 使用 STOMP CONNECT 帧的 Bearer 凭据绑定身份，并按当前会话和权限控制消息流。
 *
 * @property sessions 使用令牌摘要重新加载有效会话和权限。
 * @property policy 业务提供的订阅白名单和身份映射。
 */
internal class BearerStompAccess(
    private val sessions: BearerSessionAuthentication,
    private val policy: StompAccessPolicy,
) : ChannelInterceptor {
    private val connected = ConcurrentHashMap<String, StompPrincipal>()

    /** 代理下发的消息按接收端会话 ID 查找身份，避免使用发送方的身份判定接收权限。 */
    val outbound: ExecutorChannelInterceptor = object : ExecutorChannelInterceptor {
        override fun beforeHandle(message: Message<*>, channel: MessageChannel, handler: MessageHandler): Message<*>? {
            if (SimpMessageHeaderAccessor.getMessageType(message.headers) != SimpMessageType.MESSAGE) return message
            val sessionId = SimpMessageHeaderAccessor.getSessionId(message.headers) ?: return null
            val principal = connected[sessionId] ?: return null
            return message.takeIf { isAuthorized(principal) }
        }
    }

    /** 只允许认证连接、白名单订阅和连接维护帧。消息发布使用单独授权的 HTTP 接口。 */
    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*> {
        val frame = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java) ?: denied()
        val sessionId = frame.sessionId ?: denied()
        when (frame.command) {
            StompCommand.CONNECT, StompCommand.STOMP -> connect(frame, sessionId)
            StompCommand.SUBSCRIBE -> {
                val principal = connected[sessionId] ?: denied()
                val destination = frame.destination ?: denied()
                if (destination !in policy.destinations || !isAuthorized(principal)) denied()
            }
            StompCommand.DISCONNECT -> connected.remove(sessionId)
            StompCommand.UNSUBSCRIBE -> if (!connected.containsKey(sessionId)) denied()
            null -> if (frame.messageType != SimpMessageType.HEARTBEAT || !connected.containsKey(sessionId)) denied()
            else -> denied()
        }
        return message
    }

    /** 取出并移除原始凭据，后续连接状态只保存令牌摘要及服务端查得的用户 ID。 */
    private fun connect(frame: StompHeaderAccessor, sessionId: String) {
        val raw = BearerTokens.fromAuthorization(frame.removeNativeHeader(HttpHeaders.AUTHORIZATION)?.singleOrNull())
            ?: denied()
        val digest = BearerTokens.digest(raw)
        val authentication = authenticate(digest) ?: denied()
        val principal = StompPrincipal(policy.userId(authentication), digest)
        frame.user = principal
        connected[sessionId] = principal
    }

    private fun isAuthorized(principal: StompPrincipal): Boolean =
        authenticate(principal.digest)?.let(policy.userId) == principal.userId

    private fun authenticate(digest: String): Authentication? =
        sessions.authenticate(digest)?.takeIf { authentication ->
            authentication.isAuthenticated && authentication.authorities.any { it.authority == policy.authority }
        }

    private fun denied(): Nothing = throw AccessDeniedException("实时连接或订阅被拒绝")
}

/** 以服务端认证的用户 ID 作为 STOMP 路由身份。 */
private class StompPrincipal(val userId: String, val digest: String) : Principal {
    override fun getName(): String = userId
}
