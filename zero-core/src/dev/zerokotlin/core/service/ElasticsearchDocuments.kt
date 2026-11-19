package dev.zerokotlin.core.service

import dev.zerokotlin.core.dto.SearchDocument
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.JsonNode

internal val elasticsearchDocumentIdPattern = Regex("[a-zA-Z0-9_-]{1,120}")

class ElasticsearchDocuments internal constructor(private val client: RestClient, private val index: String) {
    fun index(id: String, document: SearchDocument): JsonNode {
        require(id.matches(elasticsearchDocumentIdPattern)) { "Invalid search document ID" }
        return client.put()
            .uri("/{index}/_doc/{id}?refresh=wait_for", index, id)
            .contentType(MediaType.APPLICATION_JSON)
            .body(document)
            .retrieve()
            .jsonBody()
    }

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

    internal fun clusterStatus(): String = client.get()
        .uri("/_cluster/health?filter_path=status")
        .retrieve()
        .jsonBody()
        .path("status")
        .asString("unknown")

    private fun RestClient.ResponseSpec.jsonBody(): JsonNode =
        body(JsonNode::class.java) ?: throw RestClientException("Elasticsearch returned an empty response")
}

internal fun elasticsearchFailureDetail(failure: RestClientException): String =
    if (failure is RestClientResponseException) "Elasticsearch HTTP ${failure.statusCode.value()}"
    else (failure.cause ?: failure).javaClass.simpleName
