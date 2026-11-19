package dev.zerokotlin.system.config

import dev.zerokotlin.core.config.STOMP_ENDPOINT
import dev.zerokotlin.core.security.PublicApiOperation
import dev.zerokotlin.core.security.StompAccessPolicy
import dev.zerokotlin.system.security.MESSAGE_READ
import dev.zerokotlin.system.security.SystemActor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod

/** 订阅实时群发消息的完整目的地。 */
internal const val REALTIME_BROADCAST_DESTINATION = "/topic/realtime/broadcast"

/** 服务端私发时使用的队列，客户端通过 `/user` 前缀订阅自己的队列。 */
internal const val REALTIME_PRIVATE_QUEUE = "/queue/realtime/private"

/** 为系统实时消息登记 WebSocket 握手路径及受控订阅。 */
@Configuration(proxyBeanMethods = false)
class SystemRealtimeConfiguration {
    /** 浏览器先发起 GET 握手，在接下来的 STOMP CONNECT 帧中提交 Bearer 令牌。 */
    @Bean
    fun realtimeHandshakeOperation() = PublicApiOperation(HttpMethod.GET, STOMP_ENDPOINT)

    /** 绑定系统会话的用户 ID，仅允许读取实时群发和自己的私发队列。 */
    @Bean
    fun realtimeStompPolicy() = StompAccessPolicy(
        MESSAGE_READ,
        setOf(REALTIME_BROADCAST_DESTINATION, "/user$REALTIME_PRIVATE_QUEUE"),
    ) { (it.principal as SystemActor).user.id }
}
