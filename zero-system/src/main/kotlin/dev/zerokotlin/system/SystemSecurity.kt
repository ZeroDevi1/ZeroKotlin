package dev.zerokotlin.system

import dev.zerokotlin.core.CurrentActor
import jakarta.servlet.DispatcherType
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.ImportRuntimeHints
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.server.ResponseStatusException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.HexFormat

class SystemActor(
    val user: UserRow,
    val sessionDigest: String,
    val administrator: Boolean,
    val scopes: Map<String, DataScope>,
) {
    fun requireScope(authority: String): DataScope =
        scopes[authority] ?: throw AccessDeniedException("缺少权限 $authority")
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

object SystemTokens {
    private val random = SecureRandom()
    private val format = Regex("[A-Za-z0-9_-]{43}")

    fun issue(): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))

    fun digest(raw: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.US_ASCII)))

    fun bearer(header: String?): String? {
        if (header == null || !header.startsWith("Bearer ", ignoreCase = true)) return null
        return header.substring(7).takeIf(format::matches)
    }
}

private class SystemBearerFilter(private val sessions: SystemSessionAuthentication) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val raw = SystemTokens.bearer(request.getHeader(HttpHeaders.AUTHORIZATION))
        val actor = raw?.let { sessions.authenticate(SystemTokens.digest(it)) }
        if (actor != null) {
            val context = SecurityContextHolder.createEmptyContext()
            context.authentication = UsernamePasswordAuthenticationToken.authenticated(
                actor,
                null,
                actor.scopes.keys.map(::SimpleGrantedAuthority),
            )
            SecurityContextHolder.setContext(context)
        }
        filterChain.doFilter(request, response)
    }
}

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
@ImportRuntimeHints(SystemRuntimeHints::class)
class SystemSecurity {
    @Bean
    fun currentActor(): CurrentActor = CurrentActor { currentSystemActor().user.id }

    @Bean
    fun systemPasswordEncoder(): PasswordEncoder = BCryptPasswordEncoder(12)

    @Bean
    fun systemSecurityFilterChain(
        http: HttpSecurity,
        sessions: SystemSessionAuthentication,
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
                rules.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
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
            .addFilterBefore(SystemBearerFilter(sessions), AnonymousAuthenticationFilter::class.java)
        return http.build()
    }
}
