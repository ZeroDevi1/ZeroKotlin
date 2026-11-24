package dev.zerokotlin.ktor

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.HttpSecurityScheme
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.plugins.bodylimit.RequestBodyLimit
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.request.contentType
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.OpenApiDocSource
import io.ktor.server.routing.openapi.registerSecurityScheme
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** EngineMain 按 application.conf 中的模块名称加载此入口。 */
fun Application.module() {
    val config = environment.config
    val serviceName = config.property("app.name").getString()
    val contextPath = config.property("app.contextPath").getString()
    val logger = environment.log
    require(contextPath == "/" || Regex("(?:/[A-Za-z0-9][A-Za-z0-9_-]*)+").matches(contextPath)) {
        "app.contextPath 须为 / 或不带尾部斜杠的访问前缀，例如 /apps/orders"
    }

    val extensions = ExtensionSettings(config)
    val files = configuredFileStorages(config)
    val security = try {
        configuredSecurityState(config)
    } catch (failure: Exception) {
        runCatching { files.close() }.onFailure(failure::addSuppressed)
        throw failure
    }
    val captcha: LoginCaptchaService
    val store = try {
        captcha = LoginCaptchaService(
            config.property("zero.captchaEnabled").getString().toBooleanStrict(),
            config.property("zero.captchaTtlSeconds").getString().toLong(),
            security,
        )
        SystemStore(
            DatabaseSettings(config), config.property("zero.sessionSeconds").getString().toLong(), extensions.modules,
            config.property("zero.sanitizeText").getString().toBooleanStrict(),
        )
    } catch (failure: Exception) {
        runCatching { security.close() }.onFailure(failure::addSuppressed)
        runCatching { files.close() }.onFailure(failure::addSuppressed)
        throw failure
    }
    monitor.subscribe(ApplicationStarted) { started -> if (started === this) startJobWorker(store) }
    monitor.subscribe(ApplicationStopped) { stopped ->
        if (stopped === this) try { store.close() } finally { try { files.close() } finally { security.close() } }
    }

    install(WebSockets) { maxFrameSize = 16L * 1024 }
    install(ContentNegotiation) { json(Json { encodeDefaults = true; ignoreUnknownKeys = true }) }
    install(RequestBodyLimit) {
        bodyLimit { call ->
            if (call.request.contentType().match(ContentType.MultiPart.FormData)) multipartLimit else 128L * 1024
        }
    }
    install(StatusPages) {
        exception<SystemFailure> { call, cause ->
            call.respond(HttpStatusCode.fromValue(cause.status), ApiError("system_error", cause.message.orEmpty()))
        }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ApiError("bad_request", "请求格式无效"))
        }
        exception<PayloadTooLargeException> { call, _ ->
            call.respond(HttpStatusCode.PayloadTooLarge, ApiError("payload_too_large", "请求体过大"))
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

    val docs = config.property("zero.apiDocsEnabled").getString().toBooleanStrict()
    if (docs) registerSecurityScheme("bearer", HttpSecurityScheme(scheme = "bearer"))
    routing {
        route(contextPath.removeSuffix("/")) {
            // 存活探针不查依赖；就绪探针才检查数据库和已配置的 Redis。
            get("health") {
                call.respond(HealthResponse(serviceName, "UP"))
            }
            get("actuator/health") {
                val databaseUp = store.healthy()
                val redisUp = security.redisSnapshot().status != "UNAVAILABLE"
                val up = databaseUp && redisUp
                call.respond(
                    if (up) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
                    mapOf("status" to if (up) "UP" else "DOWN"),
                )
            }
            if (docs) {
                val info = OpenApiInfo(serviceName, "1.0")
                val defaults = OpenApiDoc(info = info, security = listOf(mapOf("bearer" to emptyList())))
                get("v3/api-docs") {
                    val doc = OpenApiDocSource.Routing().read(call.application, defaults)
                    call.respondText(doc.content, doc.contentType)
                }
                get("v3/api-docs.yaml") {
                    val doc = OpenApiDocSource.Routing(ContentType.parse("application/yaml")).read(call.application, defaults)
                    call.respondText(doc.content, doc.contentType)
                }
                swaggerUI("swagger-ui") {
                    this.info = info
                    security { requirement("bearer") }
                    source = OpenApiDocSource.Routing()
                }
                get("swagger-ui/index.html") {
                    call.respondRedirect("${contextPath.removeSuffix("/")}/swagger-ui")
                }
            }
            route("api") {
                systemRoutes(store, captcha, files, security)
                extensionRoutes(store, extensions)
                businessRoutes(store)
            }
        }
    }
}

@Serializable
internal data class ApiError(val code: String, val message: String)

@Serializable
private data class HealthResponse(val name: String, val status: String)
