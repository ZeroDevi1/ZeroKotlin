package dev.zerokotlin.system

import dev.zerokotlin.core.CurrentActor
import dev.zerokotlin.core.security.BearerSessionAuthentication
import dev.zerokotlin.core.security.PublicApiOperation
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.ImportRuntimeHints
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

class SystemActor(
    val user: UserRow,
    val sessionDigest: String,
    val administrator: Boolean,
    val scopes: Map<String, DataScope>,
) {
    fun requireScope(authority: String): DataScope =
        scopes[authority] ?: throw AccessDeniedException("缺少权限 $authority")

    fun requireAdministrator() {
        if (!administrator) throw AccessDeniedException("仅系统管理员可执行此操作")
    }
}

fun currentSystemActor(): SystemActor =
    SecurityContextHolder.getContext().authentication?.principal as? SystemActor
        ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)

@Component
class SystemSessionAuthentication(
    private val users: UserMapper,
    private val permissions: PermissionMapper,
    private val relations: SystemRelationMapper,
) {
    fun authenticate(digest: String): SystemActor? {
        val user = users.findSessionActor(digest, System.currentTimeMillis()) ?: return null
        val administrator = relations.countAdministratorRoles(user.id) != 0L
        val scopes = if (administrator) {
            permissions.selectList(query<PermissionRecord>().eq("enabled", 1)).associate { it.code to DataScope.ALL }
        } else {
            val effective = mutableMapOf<String, DataScope>()
            for (row in relations.findAuthorities(user.id)) {
                val scope = DataScope.valueOf(row.dataScope)
                effective.merge(row.code, scope) { existing, granted -> maxOf(existing, granted) }
            }
            effective
        }
        return SystemActor(user, digest, administrator, scopes)
    }
}

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
