package dev.zerokotlin.core.controller

import dev.zerokotlin.core.config.ConditionalOnZeroSearch
import dev.zerokotlin.core.dto.SearchDocument
import dev.zerokotlin.core.service.ElasticsearchDocuments
import dev.zerokotlin.core.service.elasticsearchDocumentIdPattern
import dev.zerokotlin.core.service.elasticsearchFailureDetail
import org.apache.commons.logging.LogFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestClientException
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.JsonNode

@RestController
@RequestMapping("/api/extensions/search")
@ConditionalOnZeroSearch
class ElasticsearchExampleController(private val documents: ElasticsearchDocuments) {
    private val logger = LogFactory.getLog(javaClass)

    @PutMapping("/documents/{id}")
    @PreAuthorize("hasAuthority('extensions:search:write')")
    fun index(@PathVariable id: String, @RequestBody document: SearchDocument): JsonNode {
        if (!id.matches(elasticsearchDocumentIdPattern) || document.title.isBlank() ||
            document.title.length > 200 || document.content.length > 10_000) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid document ID, title, or content length")
        }
        return documents.index(id, document)
    }

    @GetMapping("/documents")
    @PreAuthorize("hasAuthority('extensions:search:read')")
    fun search(@RequestParam q: String): JsonNode {
        if (q.isBlank() || q.length > 200) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Search query must contain 1 to 200 characters")
        }
        return documents.search(q)
    }

    @ExceptionHandler(RestClientException::class)
    fun upstreamError(failure: RestClientException): ResponseEntity<Map<String, String>> {
        val reason = elasticsearchFailureDetail(failure)
        logger.warn("Elasticsearch request failed: $reason")
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(mapOf("code" to "ELASTICSEARCH_REQUEST_FAILED", "message" to reason))
    }
}
