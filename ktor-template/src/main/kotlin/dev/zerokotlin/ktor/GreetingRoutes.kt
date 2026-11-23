package dev.zerokotlin.ktor

import io.ktor.http.BadContentTypeFormatException
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.request.contentType
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable

internal fun Route.greetingRoutes() {
    post("hello") {
        val contentType = try {
            call.request.contentType()
        } catch (cause: BadContentTypeFormatException) {
            throw BadRequestException("Content-Type 格式无效", cause)
        }
        if (!contentType.match(ContentType.Application.Json)) {
            throw UnsupportedMediaTypeException(contentType)
        }
        val name = call.receive<GreetingRequest>().name.trim()
        if (name.isEmpty() || name.length > 80) {
            call.respond(HttpStatusCode.BadRequest, ApiError("invalid_name", "name 长度须为 1–80 个字符"))
            return@post
        }
        call.respond(GreetingResponse("你好，$name"))
    }
}

@Serializable
private data class GreetingRequest(val name: String)

@Serializable
private data class GreetingResponse(val message: String)
