package dev.zerokotlin.core.config

import dev.zerokotlin.core.security.BearerSessionAuthentication
import dev.zerokotlin.core.security.BearerStompAccess
import dev.zerokotlin.core.security.StompAccessPolicy
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Lazy
import org.springframework.messaging.Message
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.MessageBuilder
import org.springframework.scheduling.TaskScheduler
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler

/** 提供给业务模块登记握手放行规则的 WebSocket 路径。 */
const val STOMP_ENDPOINT = "/api/realtime/ws"

/** 在业务模块提供 Bearer 会话认证及订阅策略时装配同源 STOMP 消息代理。 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean(BearerSessionAuthentication::class, StompAccessPolicy::class)
@EnableWebSocketMessageBroker
internal class StompBrokerAutoConfiguration(
    sessions: BearerSessionAuthentication,
    policy: StompAccessPolicy,
    @Lazy @Qualifier("messageBrokerTaskScheduler") private val scheduler: TaskScheduler,
) : WebSocketMessageBrokerConfigurer {
    private val access = BearerStompAccess(sessions, policy)

    /** 注册同源握手路径，并统一返回协议错误提示以免回显客户端帧内容。 */
    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint(STOMP_ENDPOINT)
        registry.setPreserveReceiveOrder(true)
        registry.setErrorHandler(object : StompSubProtocolErrorHandler() {
            override fun handleClientMessageProcessingError(
                clientMessage: Message<ByteArray>?,
                ex: Throwable,
            ): Message<ByteArray> {
                val headers = StompHeaderAccessor.create(StompCommand.ERROR)
                headers.message = "实时连接或订阅被拒绝"
                return MessageBuilder.createMessage(ByteArray(0), headers.messageHeaders)
            }
        })
    }

    /** 限制首帧等待时间和客户端 STOMP 帧的大小。 */
    override fun configureWebSocketTransport(registry: WebSocketTransportRegistration) {
        registry.setTimeToFirstMessage(10_000).setMessageSizeLimit(8 * 1024)
    }

    /** 使用进程内代理分发主题和用户队列，并协商双向心跳。 */
    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.enableSimpleBroker("/topic", "/queue")
            .setTaskScheduler(scheduler)
            .setHeartbeatValue(longArrayOf(15_000, 15_000))
    }

    /** 校验连接身份和订阅地址。 */
    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(access)
    }

    /** 投递前复核接收端的当前权限。 */
    override fun configureClientOutboundChannel(registration: ChannelRegistration) {
        registration.interceptors(access.outbound)
    }
}
