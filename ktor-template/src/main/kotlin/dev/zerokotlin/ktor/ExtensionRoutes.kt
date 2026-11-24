package dev.zerokotlin.ktor

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.apache.kafka.common.KafkaException
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Serializable data class KafkaPosition(val partition: Int, val offset: Long)
@Serializable data class KafkaMessage(val partition: Int, val offset: Long, val key: String?, val value: String?)
@Serializable private data class KafkaPublish(val key: String? = null, val value: String)
@Serializable private data class SearchDocument(val title: String, val content: String)
private val searchId = Regex("[a-zA-Z0-9_-]{1,120}")
private val indexName = Regex("[a-z0-9][a-z0-9._-]{0,254}")

internal class ExtensionSettings(config: ApplicationConfig) {
    private val extension = config.config("extensions")
    private fun string(key: String) = extension.property(key).getString()
    val search = if (string("searchEnabled").toBooleanStrict()) SearchConnection(
        string("searchUrl"), string("searchIndex"), extension.propertyOrNull("searchApiKey")?.getString(),
    ) else null
    val kafka = if (string("kafkaEnabled").toBooleanStrict()) KafkaMessages(
        KafkaProperties(
            string("kafkaBootstrapServers").split(',').map(String::trim),
            string("kafkaTopic"), string("kafkaGroupId"), Duration.ofSeconds(string("kafkaTimeoutSeconds").toLong()),
            extension.config("kafkaClientProperties").toMap().mapValues { it.value.toString() },
        ),
    ) else null
    val modules = buildSet {
        if (search != null) add("search")
        if (kafka != null) add("kafka")
        extension.propertyOrNull("businessModules")?.getString()?.split(',')?.map(String::trim)
            ?.filter(String::isNotEmpty)?.forEach { business ->
                require(business.matches(Regex("[a-z][a-z0-9_]{0,31}")) && business !in setOf("search", "kafka")) {
                    "业务模块名须为非保留的小写标识符"
                }
                add(business)
            }
    }
}

internal class SearchConnection(url: String, private val index: String, private val key: String?) {
    private val base = URI(url.trimEnd('/'))
    private val timeout = Duration.ofSeconds(8)
    private val client = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build()

    init {
        require(base.scheme in setOf("http", "https") && base.host != null && base.rawUserInfo == null &&
            base.rawQuery == null && base.rawFragment == null
        ) { "搜索服务地址须为 HTTP(S) URL" }
        require(indexName.matches(index) && index !in setOf(".", "..")) { "搜索索引名称无效" }
    }

    suspend fun request(method: String, path: String, payload: String): String = withContext(Dispatchers.IO) {
        val request = HttpRequest.newBuilder(URI(base.toString() + "/$index/$path")).timeout(timeout)
            .header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(payload))
        if (!key.isNullOrBlank()) request.header("Authorization", "ApiKey $key")
        val response = try {
            client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream())
        } catch (_: IOException) {
            throw SystemFailure(502, "搜索服务暂不可用")
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw SystemFailure(502, "搜索请求中断")
        }
        response.body().use { body ->
            if (response.statusCode() !in 200..299) throw SystemFailure(502, "搜索服务 HTTP ${response.statusCode()}")
            val limit = 4 * 1024 * 1024
            val bytes = body.readNBytes(limit + 1)
            if (bytes.size > limit) throw SystemFailure(502, "搜索服务响应过大")
            val value = bytes.toString(Charsets.UTF_8)
            try { Json.parseToJsonElement(value) } catch (_: IllegalArgumentException) {
                throw SystemFailure(502, "搜索服务返回了无效 JSON")
            }
            value
        }
    }
}

internal fun Route.extensionRoutes(store: SystemStore, settings: ExtensionSettings) {
    route("extensions") {
        val search = settings.search
        if (search != null) route("search/documents") {
            put("{id}") {
                val token = call.bearer()
                store.read(token, "extensions:search:write") { _, _ -> Unit }
                val id = call.id().takeIf(searchId::matches) ?: invalid("文档 ID 无效")
                val document = call.receive<SearchDocument>()
                if (document.title.isBlank() || document.title.length > 200 || document.content.length > 10_000) invalid("标题或正文长度无效")
                val payload = search.request("PUT", "_doc/$id?refresh=wait_for", Json.encodeToString(document))
                call.respondText(payload, ContentType.Application.Json)
            }
            get {
                val token = call.bearer()
                store.read(token, "extensions:search:read") { _, _ -> Unit }
                val query = call.request.queryParameters["q"]?.takeIf { it.isNotBlank() && it.length <= 200 } ?: invalid("搜索词长度无效")
                val request = buildJsonObject {
                    put("size", 20)
                    put("query", buildJsonObject {
                        put("multi_match", buildJsonObject {
                            put("query", query)
                            put("fields", buildJsonArray {
                                add(kotlinx.serialization.json.JsonPrimitive("title"))
                                add(kotlinx.serialization.json.JsonPrimitive("content"))
                            })
                        })
                    })
                }
                call.respondText(search.request("POST", "_search?allow_partial_search_results=false", request.toString()), ContentType.Application.Json)
            }
        }
        val kafka = settings.kafka
        if (kafka != null) route("kafka") {
            post("messages") {
                val token = call.bearer()
                store.read(token, "extensions:kafka:publish") { _, _ -> Unit }
                val input = call.receive<KafkaPublish>()
                if ((input.key?.length ?: 0) > 200 || input.value.isBlank() || input.value.length > 10_000) invalid("Kafka 消息长度无效")
                try { call.respond(withContext(Dispatchers.IO) { kafka.publish(input.key, input.value) }) }
                catch (failure: KafkaException) {
                    call.respond(HttpStatusCode.BadGateway, ApiError("KAFKA_REQUEST_FAILED", kafkaFailureDetail(failure)))
                }
            }
            post("consume") {
                val token = call.bearer()
                store.read(token, "extensions:kafka:consume") { _, _ -> Unit }
                try { call.respond(withContext(Dispatchers.IO) { kafka.consume() }) }
                catch (failure: KafkaException) {
                    call.respond(HttpStatusCode.BadGateway, ApiError("KAFKA_REQUEST_FAILED", kafkaFailureDetail(failure)))
                }
            }
        }
    }
}
