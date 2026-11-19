package dev.zerokotlin.core.controller

import dev.zerokotlin.core.config.ConditionalOnZeroSearch
import dev.zerokotlin.core.dto.SearchDocument
import dev.zerokotlin.core.service.ElasticsearchDocuments
import dev.zerokotlin.core.service.elasticsearchDocumentIdPattern
import dev.zerokotlin.core.service.elasticsearchFailureDetail
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
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

/**
 * Elasticsearch 示例接口，仅在搜索功能启用时注册。
 *
 * @property documents 用于写入文档和执行搜索的 Elasticsearch 服务。
 */
@Tag(name = "Elasticsearch 搜索", description = "在配置的索引中写入和检索文档。")
@ApiResponse(
    responseCode = "502",
    description = "索引或检索上游请求失败",
    content = [Content(
        mediaType = "application/json",
        examples = [ExampleObject(value = """{"code":"ELASTICSEARCH_REQUEST_FAILED","message":"TimeoutException"}""")],
    )],
)
@RestController
@RequestMapping("/api/extensions/search")
@ConditionalOnZeroSearch
class ElasticsearchExampleController(private val documents: ElasticsearchDocuments) {
    /** 记录上游失败时提取的状态码或异常类型。 */
    private val logger = LogFactory.getLog(javaClass)

    /**
     * 按指定 ID 写入文档，需要 `extensions:search:write` 权限。输入校验失败时返回 HTTP 400。
     *
     * @param id 由 1 至 120 个英文字母、数字、下划线或连字符组成的文档 ID。
     * @param document 标题须非空白且最多 200 个 UTF-16 码元，正文最多 10 000 个 UTF-16 码元。
     * @return 刷新等待结束后，Elasticsearch 返回的写入结果 JSON。
     */
    @Operation(summary = "按 ID 写入文档", description = "需要 extensions:search:write。写入配置的索引并等待刷新。")
    @ApiResponse(responseCode = "200", description = "Elasticsearch 写入响应", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", description = "输入参数不符合接口约束", content = [Content()])
    @PutMapping("/documents/{id}")
    @PreAuthorize("hasAuthority('extensions:search:write')")
    fun index(
        @Parameter(description = "1 至 120 个英文字母、数字、下划线或连字符", example = "note-1")
        @PathVariable id: String,
        @RequestBody document: SearchDocument,
    ): JsonNode {
        if (!id.matches(elasticsearchDocumentIdPattern) || document.title.isBlank() ||
            document.title.length > 200 || document.content.length > 10_000) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid document ID, title, or content length")
        }
        return documents.index(id, document)
    }

    /**
     * 在标题和正文中查询文档，需要 `extensions:search:read` 权限。输入校验失败时返回 HTTP 400。
     *
     * @param q 非空白查询词，最多 200 个 UTF-16 码元。
     * @return Elasticsearch 的搜索响应 JSON，最多包含 20 条命中。
     */
    @Operation(summary = "检索文档", description = "需要 extensions:search:read。按标题和正文检索，最多返回 20 条。")
    @ApiResponse(responseCode = "200", description = "Elasticsearch 检索响应", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", description = "输入参数不符合接口约束", content = [Content()])
    @GetMapping("/documents")
    @PreAuthorize("hasAuthority('extensions:search:read')")
    fun search(@Parameter(description = "非空白检索词", example = "SQLite") @RequestParam q: String): JsonNode {
        if (q.isBlank() || q.length > 200) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Search query must contain 1 to 200 characters")
        }
        return documents.search(q)
    }

    /**
     * 将上游请求错误转换为示例接口的错误响应。
     *
     * @param failure Elasticsearch HTTP 客户端抛出的异常。
     * @return HTTP 502，包含错误代码及上游 HTTP 状态码或异常类型。
     */
    @ExceptionHandler(RestClientException::class)
    fun upstreamError(failure: RestClientException): ResponseEntity<Map<String, String>> {
        val reason = elasticsearchFailureDetail(failure)
        logger.warn("Elasticsearch request failed: $reason")
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(mapOf("code" to "ELASTICSEARCH_REQUEST_FAILED", "message" to reason))
    }
}
