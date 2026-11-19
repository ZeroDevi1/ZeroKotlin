package dev.zerokotlin.core.service

import dev.zerokotlin.core.dto.SearchDocument
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.JsonNode

/** 路径中的文档 ID 仅接受 1 至 120 个英文字母、数字、下划线或连字符。 */
internal val elasticsearchDocumentIdPattern = Regex("[a-zA-Z0-9_-]{1,120}")

/**
 * 通过 Elasticsearch HTTP 接口读写指定索引，并查询集群健康状态。
 *
 * @property client 带有 Elasticsearch 基础地址、请求超时和可选 API Key 的 HTTP 客户端。
 * @property index 写入和搜索使用的单个索引名称。
 */
class ElasticsearchDocuments internal constructor(private val client: RestClient, private val index: String) {
    /**
     * 在配置的索引中按 ID 创建或替换文档。使用 `refresh=wait_for` 等待刷新使写入对搜索可见。
     *
     * @param id 满足 [elasticsearchDocumentIdPattern] 的文档 ID。
     * @param document 作为 JSON 请求体发送的标题和正文。
     * @return Elasticsearch 写入响应的 JSON 树。
     * @throws IllegalArgumentException 文档 ID 不符合允许的格式。
     * @throws RestClientException 上游请求失败或响应无正文。
     */
    fun index(id: String, document: SearchDocument): JsonNode {
        require(id.matches(elasticsearchDocumentIdPattern)) { "Invalid search document ID" }
        return client.put()
            .uri("/{index}/_doc/{id}?refresh=wait_for", index, id)
            .contentType(MediaType.APPLICATION_JSON)
            .body(document)
            .retrieve()
            .jsonBody()
    }

    /**
     * 通过 `multi_match` 检索 `title` 与 `content` 字段，关闭分片失败时的部分结果。
     *
     * @param query 原样传入 Elasticsearch 查询体的查询文本。
     * @return Elasticsearch 搜索响应的 JSON 树，最多包含 20 条命中。
     * @throws RestClientException 上游请求失败或响应无正文。
     */
    fun search(query: String): JsonNode = client.post()
        .uri("/{index}/_search?allow_partial_search_results=false", index)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            mapOf(
                "size" to 20,
                "query" to mapOf("multi_match" to mapOf("query" to query, "fields" to listOf("title", "content"))),
            )
        )
        .retrieve()
        .jsonBody()

    /**
     * 读取集群健康响应中的 `status` 字段。
     *
     * @return Elasticsearch 返回的状态字符串。字段缺失或无法转换为字符串时返回 `unknown`。
     */
    internal fun clusterStatus(): String = client.get()
        .uri("/_cluster/health?filter_path=status")
        .retrieve()
        .jsonBody()
        .path("status")
        .asString("unknown")

    /**
     * 将响应正文读取为 JSON 树。
     *
     * @receiver 待读取正文的 Elasticsearch HTTP 响应。
     * @return 反序列化后的 JSON 树。
     * @throws RestClientException 请求失败、无法读取 JSON 或响应无正文。
     */
    private fun RestClient.ResponseSpec.jsonBody(): JsonNode =
        body(JsonNode::class.java) ?: throw RestClientException("Elasticsearch returned an empty response")
}

/**
 * 从客户端异常提取日志和响应可共用的失败原因。
 *
 * @param failure 调用 Elasticsearch 时抛出的异常。
 * @return HTTP 响应异常对应的状态码说明，或直接原因的异常类型名。没有直接原因时使用原异常类型名。
 */
internal fun elasticsearchFailureDetail(failure: RestClientException): String =
    if (failure is RestClientResponseException) "Elasticsearch HTTP ${failure.statusCode.value()}"
    else (failure.cause ?: failure).javaClass.simpleName
