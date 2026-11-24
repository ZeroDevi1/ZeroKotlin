package dev.zerokotlin.ktor.system.auth

import dev.zerokotlin.core.SystemFailure
import dev.zerokotlin.core.security.SecurityState
import dev.zerokotlin.core.security.digest
import dev.zerokotlin.ktor.bearer
import dev.zerokotlin.ktor.noStore
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/** /api/auth。接收凭据、限制请求速率，并调用会话及个人资料操作。 */
internal fun Route.authController(service: AuthService, security: SecurityState) = route("auth") {
    get("captcha") {
        call.noStore()
        call.enforceRate(security, "auth.captcha", 20)
        val challenge = service.captcha()
        if (challenge == null) call.respond(HttpStatusCode.NoContent)
        else call.respond(challenge)
    }
    post("login") {
        call.noStore()
        call.enforceRate(security, "auth.login", 30)
        val request = call.receive<LoginRequest>()
        val result = service.login(request, call.request.origin.remoteHost)
        if (result == null) call.respond(HttpStatusCode.Unauthorized)
        else call.respond(result)
    }
    post("logout") {
        service.logout(call.bearer())
        call.respond(HttpStatusCode.NoContent)
    }
    route("me") {
        get {
            val raw = call.bearer()
            call.respond(service.current(raw))
        }
        get("menus") {
            call.respond(service.menus(call.bearer()))
        }
        get("profile") {
            call.respond(service.profile(call.bearer()))
        }
        put("profile") {
            val raw = call.bearer()
            call.respond(service.updateProfile(raw, call.receive<ProfileRequest>()))
        }
        put("password") {
            val raw = call.bearer()
            call.enforceRate(security, "auth.password", 10)
            service.changePassword(raw, call.receive<PasswordRequest>())
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private suspend fun ApplicationCall.enforceRate(security: SecurityState, key: String, limit: Int) {
    val retry = security.checkRate("$key:${digest(request.origin.remoteHost.take(128))}", limit)
    if (retry > 0) {
        response.header(HttpHeaders.RetryAfter, ((retry + 999) / 1_000).toString())
        throw SystemFailure(429, "请求过于频繁，请稍后重试")
    }
}
