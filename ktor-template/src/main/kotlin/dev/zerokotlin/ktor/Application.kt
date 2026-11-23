package dev.zerokotlin.ktor

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

/** EngineMain 按 application.conf 中的模块名称加载此入口。 */
fun Application.module() {
    val config = environment.config
    val serviceName = config.property("app.name").getString()
    val contextPath = config.property("app.contextPath").getString()
    val logger = environment.log
    require(contextPath == "/" || Regex("(?:/[A-Za-z0-9][A-Za-z0-9_-]*)+").matches(contextPath)) {
        "app.contextPath 须为 / 或不带尾部斜杠的访问前缀，例如 /apps/orders"
    }

    install(ContentNegotiation) { json() }
    install(StatusPages) {
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ApiError("bad_request", "请求格式无效"))
        }
        exception<UnsupportedMediaTypeException> { call, _ ->
            call.respond(HttpStatusCode.UnsupportedMediaType, ApiError("unsupported_media_type", "请使用 application/json"))
        }
        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(status, ApiError("not_found", "请求路径或资源不存在"))
        }
        exception<Exception> { call, cause ->
            if (cause is CancellationException) throw cause
            logger.error("处理请求失败", cause)
            call.respond(HttpStatusCode.InternalServerError, ApiError("internal_error", "服务暂时无法处理请求"))
        }
    }

    routing {
        route(contextPath.removeSuffix("/")) {
            get("health") {
                call.respond(HealthResponse(serviceName, "UP"))
            }
            route("api") {
                greetingRoutes()
            }
        }
    }
}

@Serializable
internal data class ApiError(val code: String, val message: String)

@Serializable
private data class HealthResponse(val name: String, val status: String)
