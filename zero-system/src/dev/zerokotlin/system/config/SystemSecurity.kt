package dev.zerokotlin.system.config

import dev.zerokotlin.core.CurrentActor
import dev.zerokotlin.core.security.BearerSessionAuthentication
import dev.zerokotlin.core.security.PublicApiOperation
import dev.zerokotlin.system.security.SystemSessionAuthentication
import dev.zerokotlin.system.security.currentSystemActor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.ImportRuntimeHints
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority

/** 将系统会话认证、当前操作者和免认证登录路由接入核心模块，并导入原生镜像运行时提示。 */
@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(SystemRuntimeHints::class)
class SystemSecurity {
    /**
     * 暴露当前操作者查询，供其他模块使用。
     *
     * @return 从当前系统会话主体读取用户 ID 的组件。
     */
    @Bean
    fun currentActor(): CurrentActor = CurrentActor { currentSystemActor().user.id }

    /**
     * 将系统会话主体及其有效权限转换为 Spring Security 认证结果。
     *
     * @param sessions 通过令牌摘要查找有效会话并计算权限的组件。
     * @return Bearer 会话认证适配器，其认证结果携带权限编码，会话无效时结果为 null。
     */
    @Bean
    fun bearerSessionAuthentication(sessions: SystemSessionAuthentication): BearerSessionAuthentication =
        BearerSessionAuthentication { digest ->
            sessions.authenticate(digest)?.let { actor ->
                UsernamePasswordAuthenticationToken.authenticated(
                    actor,
                    null,
                    actor.scopes.keys.map(::SimpleGrantedAuthority),
                )
            }
        }

    /**
     * 登记可在建立会话前调用的登录操作，供安全过滤链和 OpenAPI 配置读取。
     *
     * @return POST /api/auth/login 的公开端点描述。
     */
    @Bean
    fun systemLoginOperation() = PublicApiOperation(HttpMethod.POST, "/api/auth/login")

    /** 允许未登录客户端查询并签发登录验证码。 */
    @Bean
    fun systemCaptchaOperation() = PublicApiOperation(HttpMethod.GET, "/api/auth/captcha")
}
