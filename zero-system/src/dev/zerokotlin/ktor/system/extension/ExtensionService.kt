package dev.zerokotlin.ktor.system.extension

import dev.zerokotlin.core.kafka.KafkaMessage
import dev.zerokotlin.core.kafka.KafkaPosition
import dev.zerokotlin.ktor.SystemStore
import dev.zerokotlin.ktor.id
import dev.zerokotlin.ktor.invalid
import io.ktor.server.routing.put
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable internal data class KafkaPublish(val key: String? = null, val value: String)
@Serializable internal data class SearchDocument(val title: String, val content: String)
private val searchId = Regex("[a-zA-Z0-9_-]{1,120}")
/** 中间件操作在授权后执行；远端网络请求不持有数据库事务。 */
internal class ExtensionService(private val store: SystemStore, settings: ExtensionSettings) {
    private val search = settings.search
    private val kafka = settings.kafka
    val searchEnabled = search != null
    val kafkaEnabled = kafka != null

    suspend fun index(token: String, rawId: String, document: SearchDocument): String {
        store.read(token, "extensions:search:write") { _, _ -> Unit }
        val id = rawId.takeIf(searchId::matches) ?: invalid("文档 ID 无效")
        if (document.title.isBlank() || document.title.length > 200 || document.content.length > 10_000) invalid("标题或正文长度无效")
        return checkNotNull(search).request("PUT", "_doc/$id?refresh=wait_for", Json.encodeToString(document))
    }

    suspend fun search(token: String, raw: String?): String {
        store.read(token, "extensions:search:read") { _, _ -> Unit }
        val query = raw?.takeIf { it.isNotBlank() && it.length <= 200 } ?: invalid("搜索词长度无效")
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
        return checkNotNull(search).request("POST", "_search?allow_partial_search_results=false", request.toString())
    }

    suspend fun publish(token: String, input: KafkaPublish): KafkaPosition {
        store.read(token, "extensions:kafka:publish") { _, _ -> Unit }
        if ((input.key?.length ?: 0) > 200 || input.value.isBlank() || input.value.length > 10_000) invalid("Kafka 消息长度无效")
        return withContext(Dispatchers.IO) { checkNotNull(kafka).publish(input.key, input.value) }
    }

    suspend fun consume(token: String): List<KafkaMessage> {
        store.read(token, "extensions:kafka:consume") { _, _ -> Unit }
        return withContext(Dispatchers.IO) { checkNotNull(kafka).consume() }
    }
}
