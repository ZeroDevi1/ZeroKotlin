package dev.zerokotlin.core.controller

import dev.zerokotlin.core.config.ConditionalOnZeroKafka
import dev.zerokotlin.core.dto.KafkaMessage
import dev.zerokotlin.core.dto.KafkaPosition
import dev.zerokotlin.core.dto.PublishKafkaMessage
import dev.zerokotlin.core.service.KafkaMessages
import dev.zerokotlin.core.service.kafkaFailureDetail
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.apache.commons.logging.LogFactory
import org.apache.kafka.common.KafkaException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * Kafka 示例接口，仅在 Kafka 功能启用时注册。
 *
 * @property messages 使用配置的主题和消费组发布、读取消息的服务。
 */
@Tag(name = "Kafka 消息", description = "发布示例主题消息，或按配置的消费组轮询和提交偏移量。")
@ApiResponse(
    responseCode = "502",
    description = "Kafka 客户端或代理请求失败",
    content = [Content(
        mediaType = "application/json",
        examples = [ExampleObject(value = """{"code":"KAFKA_REQUEST_FAILED","message":"TimeoutException"}""")],
    )],
)
@RestController
@RequestMapping("/api/extensions/kafka")
@ConditionalOnZeroKafka
class KafkaExampleController(private val messages: KafkaMessages) {
    /** 记录 Kafka 请求失败时提取的异常类型。 */
    private val logger = LogFactory.getLog(javaClass)

    /**
     * 向示例主题发布一条消息，需要 `extensions:kafka:publish` 权限。输入校验失败时返回 HTTP 400。
     *
     * @param request 可选键最多 200 个 UTF-16 码元，值须非空白且最多 10 000 个 UTF-16 码元。
     * @return 代理确认写入后返回的分区编号和分区内偏移量。
     */
    @Operation(summary = "发布消息", description = "需要 extensions:kafka:publish。等待代理确认后返回分区及偏移量。")
    @ApiResponse(responseCode = "200", description = "代理已确认写入", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", description = "输入参数不符合接口约束", content = [Content()])
    @PostMapping("/messages")
    @PreAuthorize("hasAuthority('extensions:kafka:publish')")
    fun publish(@RequestBody request: PublishKafkaMessage): KafkaPosition {
        if ((request.key?.length ?: 0) > 200 || request.value.isBlank() || request.value.length > 10_000) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Kafka message key or value length")
        }
        return messages.publish(request.key, request.value)
    }

    /**
     * 轮询一次并在返回非空批次前同步提交消费组偏移量，需要 `extensions:kafka:consume` 权限。
     *
     * @return 最多 20 条消息。已获分区但本次轮询没有记录时返回空列表。
     */
    @Operation(summary = "消费一批消息", description = "需要 extensions:kafka:consume。最多取 20 条，非空批次在响应前提交偏移量。")
    @ApiResponse(responseCode = "200", description = "已消费的消息列表", useReturnTypeSchema = true)
    @PostMapping("/consume")
    @PreAuthorize("hasAuthority('extensions:kafka:consume')")
    fun consume(): List<KafkaMessage> = messages.consume()

    /**
     * 将 Kafka 发布、分区分配或消费等失败转换为接口错误。
     *
     * @param failure Kafka 客户端或消息服务抛出的异常。
     * @return HTTP 502，包含错误代码和异常类型。
     */
    @ExceptionHandler(KafkaException::class)
    fun upstreamError(failure: KafkaException): ResponseEntity<Map<String, String>> {
        val reason = kafkaFailureDetail(failure)
        logger.warn("Kafka request failed: $reason")
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(mapOf("code" to "KAFKA_REQUEST_FAILED", "message" to reason))
    }
}
