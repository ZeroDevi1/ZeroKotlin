package dev.zerokotlin.core.config

import dev.zerokotlin.core.IpRegionLookup
import dev.zerokotlin.core.RequestAuditSink
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.web.servlet.HandlerExceptionResolver
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/** 装配离线 IP 查询和可选的 MVC 请求审计。 */
@AutoConfiguration
internal class ObservabilityAutoConfiguration {
    /** 使用部署方指定的本地文件，路径缺省时对应地址族无查询结果。 */
    @Bean
    fun ipRegionLookup(
        @Value("\${zero.observability.ip-region.ipv4-path:}") ipv4Path: String,
        @Value("\${zero.observability.ip-region.ipv6-path:}") ipv6Path: String,
    ): IpRegionLookup = IpRegionLookup(ipv4Path, ipv6Path)

    /** 仅在 Servlet 应用有审计接收器且启用功能时安装拦截器。 */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnBean(RequestAuditSink::class)
    fun requestAuditMvcConfigurer(
        sink: RequestAuditSink,
        regions: IpRegionLookup,
        @Value("\${zero.observability.request-audit.enabled:true}") enabled: Boolean,
        @Value("\${zero.observability.request-audit.include-user-agent:false}") includeUserAgent: Boolean,
    ): WebMvcConfigurer = object : WebMvcConfigurer {
        private val audit = RequestAuditInterceptor(sink, regions, includeUserAgent)

        /** 在已匹配的控制器请求上识别审计注解。 */
        override fun addInterceptors(registry: InterceptorRegistry) {
            if (enabled) registry.addInterceptor(audit)
        }

        /** 在 MVC 将异常转换成 HTTP 响应前记录异常类型。 */
        override fun extendHandlerExceptionResolvers(resolvers: MutableList<HandlerExceptionResolver>) {
            if (enabled) {
                resolvers.add(0, HandlerExceptionResolver { request, _, _, exception ->
                    audit.observeException(request, exception)
                    null
                })
            }
        }
    }
}
