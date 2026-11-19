package dev.zerokotlin.system.config

import dev.zerokotlin.core.security.Masked
import dev.zerokotlin.core.security.MaskedStringSerializer
import dev.zerokotlin.system.dto.LoginAuditEntry
import dev.zerokotlin.system.dto.RequestAuditEntry
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** 将审计地址的展示策略应用于 HTTP 序列化，归属地查询始终可读取完整对端地址。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "zero.system.audit", name = ["mask-remote-address"], havingValue = "true")
internal class AuditJsonConfiguration {
    /** 启用审计地址脱敏时，为登录和请求审计注册同一字段规则。 */
    @Bean
    @RegisterReflectionForBinding(AddressMask::class, MaskedStringSerializer::class)
    fun auditAddressMask() = JsonMapperBuilderCustomizer { builder ->
        builder.addMixIn(LoginAuditEntry::class.java, AddressMask::class.java)
        builder.addMixIn(RequestAuditEntry::class.java, AddressMask::class.java)
    }

    /** 与响应 DTO 的同名访问器匹配。 */
    private abstract class AddressMask {
        @get:Masked(prefix = 3)
        abstract val remoteAddress: String
    }
}
