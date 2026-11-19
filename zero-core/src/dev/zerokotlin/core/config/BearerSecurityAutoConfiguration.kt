package dev.zerokotlin.core.config

import dev.zerokotlin.core.security.BearerSessionAuthentication
import dev.zerokotlin.core.security.BearerTokens
import dev.zerokotlin.core.security.PublicApiOperation
import jakarta.servlet.DispatcherType
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository
import org.springframework.web.filter.OncePerRequestFilter

/** Servlet 应用提供会话认证实现时，注册无状态的 Bearer 安全过滤链并启用方法级鉴权。 */
@AutoConfiguration(before = [ManagementWebSecurityAutoConfiguration::class, SecurityAutoConfiguration::class])
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean(BearerSessionAuthentication::class)
@EnableWebSecurity
@EnableMethodSecurity
internal class BearerSecurityAutoConfiguration {
    /**
     * 放行声明为公开的操作、错误分派，以及健康检查和 API 文档的指定 GET 请求。
     * 其余请求须通过认证，缺少认证时返回 HTTP 401 和 `WWW-Authenticate: Bearer`。
     *
     * @param http Spring Security 的 Servlet 安全配置构建器。
     * @param sessions 负责用令牌摘要认证会话的组件。
     * @param publicOperations 允许匿名访问的 HTTP 方法和路径声明。
     * @return 在匿名身份过滤器前解析 Bearer 令牌的无状态过滤链。
     */
    @Bean
    fun systemSecurityFilterChain(
        http: HttpSecurity,
        sessions: BearerSessionAuthentication,
        publicOperations: ObjectProvider<PublicApiOperation>,
    ): SecurityFilterChain {
        val requestContexts = RequestAttributeSecurityContextRepository()
        http
            .csrf { it.disable() }
            .requestCache { it.disable() }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .logout { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .securityContext { it.securityContextRepository(requestContexts) }
            .exceptionHandling { settings ->
                settings.authenticationEntryPoint { _, response, _ ->
                    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                    response.status = HttpServletResponse.SC_UNAUTHORIZED
                }
            }
            .authorizeHttpRequests { rules ->
                rules.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                publicOperations.stream().forEach { operation ->
                    rules.requestMatchers(operation.method, operation.path).permitAll()
                }
                rules.requestMatchers(
                    HttpMethod.GET,
                    "/actuator/health",
                    "/actuator/health/liveness",
                    "/actuator/health/readiness",
                    "/v3/api-docs",
                    "/v3/api-docs/**",
                    "/v3/api-docs.yaml",
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                ).permitAll()
                rules.anyRequest().authenticated()
            }
            .addFilterBefore(BearerFilter(sessions, requestContexts), AnonymousAuthenticationFilter::class.java)
        return http.build()
    }
}

/**
 * 将格式有效的 Bearer 令牌转为摘要后认证会话。
 *
 * @property sessions 接收令牌摘要并返回会话认证结果的组件。
 * @property requestContexts 在同一请求的异步派发阶段恢复身份，生命周期止于当前请求。
 */
private class BearerFilter(
    private val sessions: BearerSessionAuthentication,
    private val requestContexts: RequestAttributeSecurityContextRepository,
) : OncePerRequestFilter() {
    /**
     * 认证成功时将身份写入新的安全上下文。请求头缺失、格式不符或会话认证返回空值时，
     * 继续交由后续过滤器处理。
     *
     * @param request 提供 `Authorization` 请求头的 Servlet 请求。
     * @param response 传给后续过滤器的 Servlet 响应。
     * @param filterChain 负责执行后续过滤器及访问规则的过滤链。
     */
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val raw = BearerTokens.fromAuthorization(request.getHeader(HttpHeaders.AUTHORIZATION))
        val authentication = raw?.let { sessions.authenticate(BearerTokens.digest(it)) }
        if (authentication != null) {
            val context = SecurityContextHolder.createEmptyContext()
            context.authentication = authentication
            SecurityContextHolder.setContext(context)
            requestContexts.saveContext(context, request, response)
        }
        filterChain.doFilter(request, response)
    }
}
