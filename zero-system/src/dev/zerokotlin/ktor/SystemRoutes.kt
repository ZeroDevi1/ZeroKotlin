package dev.zerokotlin.ktor

import dev.zerokotlin.core.SystemFailure
import dev.zerokotlin.core.security.LoginCaptchaService
import dev.zerokotlin.core.security.SecurityState
import dev.zerokotlin.core.security.digest
import dev.zerokotlin.core.storage.FileStorages
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

internal fun Route.systemRoutes(store: SystemStore, captcha: LoginCaptchaService, files: FileStorages, security: SecurityState) {
    route("auth") {
        get("captcha") {
            call.noStore()
            call.enforceRate(security, "auth.captcha", 20)
            val challenge = captcha.issue()
            if (challenge == null) call.respond(HttpStatusCode.NoContent)
            else call.respond(challenge)
        }
        post("login") {
            call.noStore()
            call.enforceRate(security, "auth.login", 30)
            val request = call.receive<LoginRequest>()
            val result = store.login(request, call.request.origin.remoteHost, captcha)
            if (result == null) call.respond(HttpStatusCode.Unauthorized)
            else call.respond(result)
        }
        post("logout") {
            store.logout(call.bearer())
            call.respond(HttpStatusCode.NoContent)
        }
        route("me") {
            get {
                val raw = call.bearer()
                call.respond(store.current(raw))
            }
            get("menus") {
                call.respond(store.menus(call.bearer()))
            }
            get("profile") {
                call.respond(store.profile(call.bearer()))
            }
            put("profile") {
                val raw = call.bearer()
                call.respond(store.updateProfile(raw, call.receive<ProfileRequest>()))
            }
            put("password") {
                val raw = call.bearer()
                call.enforceRate(security, "auth.password", 10)
                store.changePassword(raw, call.receive<PasswordRequest>())
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
    administrationRoutes(store, files, security)
    realtimeRoutes(store)
    route("system/permissions") {
        get { call.respond(store.permissions(call.bearer())) }
        post {
            val raw = call.bearer()
            val request = call.receive<CreatePermission>()
            call.respond(HttpStatusCode.Created, store.savePermission(raw, request.code, request.name, enabled = true, create = true))
        }
        put("{code}") {
            val raw = call.bearer()
            val code = call.parameters["code"] ?: throw SystemFailure(400, "缺少权限编码")
            val request = call.receive<UpdatePermission>()
            call.respond(store.savePermission(raw, code, request.name, request.enabled, create = false))
        }
    }
    route("inbox") {
        get {
            val raw = call.bearer()
            val params = call.request.queryParameters
            val page = params["page"]?.let { it.toIntOrNull() ?: 0 } ?: 1
            val size = params["size"]?.let { it.toIntOrNull() ?: 0 } ?: 20
            if (page < 1 || size !in 1..100) throw SystemFailure(400, "page 必须大于等于 1，size 必须介于 1 和 100 之间")
            val unread = params["unreadOnly"]?.let {
                it.toBooleanStrictOrNull() ?: throw SystemFailure(400, "unreadOnly 必须为布尔值")
            } ?: false
            call.respond(store.inbox(raw, page, size, unread))
        }
        put("read-all") {
            store.markRead(call.bearer(), null)
            call.respond(HttpStatusCode.NoContent)
        }
        put("{id}/read") {
            val raw = call.bearer()
            val id = call.parameters["id"] ?: throw SystemFailure(404, "公告不存在")
            store.markRead(raw, id)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun ApplicationCall.noStore() = response.header(HttpHeaders.CacheControl, "no-store")

internal fun ApplicationCall.bearer(): String {
    noStore()
    val header = request.headers.getAll(HttpHeaders.Authorization)?.singleOrNull()
    if (header == null || !header.startsWith("Bearer ", ignoreCase = true)) throw SystemFailure(401, "登录已失效")
    return header.substring(7)
}

private suspend fun ApplicationCall.enforceRate(security: SecurityState, key: String, limit: Int) {
    val retry = security.checkRate("$key:${digest(request.origin.remoteHost.take(128))}", limit)
    if (retry > 0) {
        response.header(HttpHeaders.RetryAfter, ((retry + 999) / 1_000).toString())
        throw SystemFailure(429, "请求过于频繁，请稍后重试")
    }
}
