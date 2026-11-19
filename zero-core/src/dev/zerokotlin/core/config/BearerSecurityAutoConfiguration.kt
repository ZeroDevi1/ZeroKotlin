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
import org.springframework.web.filter.OncePerRequestFilter

@AutoConfiguration(before = [ManagementWebSecurityAutoConfiguration::class, SecurityAutoConfiguration::class])
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean(BearerSessionAuthentication::class)
@EnableWebSecurity
@EnableMethodSecurity
internal class BearerSecurityAutoConfiguration {
    @Bean
    fun systemSecurityFilterChain(
        http: HttpSecurity,
        sessions: BearerSessionAuthentication,
        publicOperations: ObjectProvider<PublicApiOperation>,
    ): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .requestCache { it.disable() }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .logout { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
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
            .addFilterBefore(BearerFilter(sessions), AnonymousAuthenticationFilter::class.java)
        return http.build()
    }
}

private class BearerFilter(private val sessions: BearerSessionAuthentication) : OncePerRequestFilter() {
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
        }
        filterChain.doFilter(request, response)
    }
}
