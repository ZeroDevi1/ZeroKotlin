package dev.zerokotlin.ktor.system.extension

import dev.zerokotlin.core.SystemFailure
import dev.zerokotlin.core.kafka.KafkaMessages
import dev.zerokotlin.core.kafka.KafkaProperties
import dev.zerokotlin.ktor.businessModules
import io.ktor.server.config.ApplicationConfig
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private val indexName = Regex("[a-z0-9][a-z0-9._-]{0,254}")

/** 可选后端与业务迁移使用同一份启用模块配置。 */
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

/** 限制搜索响应大小并校验 JSON；上游故障转换为固定的网关错误。 */
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
