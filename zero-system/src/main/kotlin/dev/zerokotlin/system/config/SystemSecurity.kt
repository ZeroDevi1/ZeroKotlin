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

@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(SystemRuntimeHints::class)
class SystemSecurity {
    @Bean
    fun currentActor(): CurrentActor = CurrentActor { currentSystemActor().user.id }

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

    @Bean
    fun systemLoginOperation() = PublicApiOperation(HttpMethod.POST, "/api/auth/login")
}
