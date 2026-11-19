package dev.zerokotlin.core.config

import dev.zerokotlin.core.CurrentActor
import dev.zerokotlin.core.security.ApiRateLimit
import dev.zerokotlin.core.security.LocalSecurityState
import dev.zerokotlin.core.security.PlainTextInput
import dev.zerokotlin.core.security.PlainTextParameterFilter
import dev.zerokotlin.core.security.RateLimitBy
import dev.zerokotlin.core.security.RedisSecurityState
import dev.zerokotlin.core.security.SecurityState
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.security.MessageDigest
import java.util.HexFormat
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.cache.CacheManager
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * 为管理端和业务模块指定可以按纯文本读取的请求参数。
 * @property parameterNames 参与过滤的参数名，默认保留每个接口既有的参数约定。
 * @property excludePaths 豁免过滤的应用内路径，支持 Ant 路径模式。
 */
@ConfigurationProperties("zero.security.xss")
internal data class PlainTextParameterProperties(
    val parameterNames: Set<String> = emptySet(),
    val excludePaths: List<String> = emptyList(),
)

/** 为 Servlet 应用装配纯文本清理和接口限流，复用缓存配置选定的状态后端。 */
@AutoConfiguration(after = [ZeroCacheAutoConfiguration::class])
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(PlainTextParameterProperties::class)
internal class SecurityControlsAutoConfiguration {
    /** 将已配置的查询参数与表单字段统一作为纯文本读取。 */
    @Bean
    @ConditionalOnProperty(prefix = "zero.security.xss", name = ["enabled"], havingValue = "true")
    fun plainTextParameters(config: PlainTextParameterProperties, cleaner: PlainTextInput) =
        FilterRegistrationBean(PlainTextParameterFilter(cleaner, config.parameterNames, config.excludePaths)).apply {
            addUrlPatterns("/api/*")
        }

    /** 提供按字段显式调用的清理器，默认维持原有文本输入行为。 */
    @Bean
    fun plainTextInput(@Value("\${zero.security.xss.enabled:false}") enabled: Boolean) =
        PlainTextInput(enabled)

    /** 同一进程中的限流和验证码复用相同后端；Redis 模式沿用原有连接工厂。 */
    @Bean
    fun securityState(cacheManager: CacheManager, cacheProperties: CacheProperties): SecurityState {
        val redis = (cacheManager as? ManagedCacheManager)?.redis
        if (redis != null) return RedisSecurityState(redis, "${cacheProperties.redis.keyPrefix}security:")
        require(cacheProperties.caffeine.maximumSize > 0) { "zero.cache.caffeine.maximum-size must be positive" }
        return LocalSecurityState(cacheProperties.caffeine.maximumSize)
    }

    /** 在 Bearer 安全过滤链之后、绑定控制器参数之前检查已标注操作的配额。 */
    @Bean
    fun rateLimitConfigurer(
        state: SecurityState,
        actors: ObjectProvider<CurrentActor>,
        @Value("\${zero.security.rate-limit.enabled:true}") enabled: Boolean,
    ): WebMvcConfigurer = object : WebMvcConfigurer {
        override fun addInterceptors(registry: InterceptorRegistry) {
            if (enabled) registry.addInterceptor(ApiRateLimitInterceptor(state, actors))
        }
    }
}

/** 按固定的操作标识和当前可信身份查询配额。 */
private class ApiRateLimitInterceptor(
    private val state: SecurityState,
    private val actors: ObjectProvider<CurrentActor>,
) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val limit = (handler as? HandlerMethod)?.getMethodAnnotation(ApiRateLimit::class.java) ?: return true
        require(limit.key.isNotBlank() && limit.requests > 0 && limit.windowSeconds in 1..86_400)
        val subject = when (limit.by) {
            RateLimitBy.IP -> request.remoteAddr
            RateLimitBy.USER -> actors.getObject().requireUserId()
        }
        val digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(subject.toByteArray()))
        val retryMillis = state.retryAfterMillis("rate:${limit.by}:${limit.key}:$digest", limit.requests, limit.windowSeconds)
        if (retryMillis == 0L) return true

        response.status = HttpStatus.TOO_MANY_REQUESTS.value()
        response.setHeader(HttpHeaders.RETRY_AFTER, ((retryMillis + 999) / 1_000).toString())
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store")
        return false
    }
}
