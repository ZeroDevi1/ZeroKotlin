package dev.zerokotlin.ktor.system.extension

import dev.zerokotlin.core.kafka.kafkaFailureDetail
import dev.zerokotlin.ktor.ApiError
import dev.zerokotlin.ktor.bearer
import dev.zerokotlin.ktor.id
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.put
import org.apache.kafka.common.KafkaException

/** /api/extensions。根据配置挂载搜索和 Kafka 接口。 */
internal fun Route.extensionController(service: ExtensionService) {
    route("extensions") {
        if (service.searchEnabled) route("search/documents") {
            put("{id}") {
                val token = call.bearer()
                val payload = service.index(token, call.id(), call.receive<SearchDocument>())
                call.respondText(payload, ContentType.Application.Json)
            }
            get {
                val payload = service.search(call.bearer(), call.request.queryParameters["q"])
                call.respondText(payload, ContentType.Application.Json)
            }
        }
        if (service.kafkaEnabled) route("kafka") {
            post("messages") {
                val token = call.bearer()
                val input = call.receive<KafkaPublish>()
                try { call.respond(service.publish(token, input)) }
                catch (failure: KafkaException) {
                    call.respond(HttpStatusCode.BadGateway, ApiError("KAFKA_REQUEST_FAILED", kafkaFailureDetail(failure)))
                }
            }
            post("consume") {
                val token = call.bearer()
                try { call.respond(service.consume(token)) }
                catch (failure: KafkaException) {
                    call.respond(HttpStatusCode.BadGateway, ApiError("KAFKA_REQUEST_FAILED", kafkaFailureDetail(failure)))
                }
            }
        }
    }
}
