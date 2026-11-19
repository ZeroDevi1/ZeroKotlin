package dev.zerokotlin.core.controller

import dev.zerokotlin.core.config.ConditionalOnZeroKafka
import dev.zerokotlin.core.dto.KafkaMessage
import dev.zerokotlin.core.dto.KafkaPosition
import dev.zerokotlin.core.dto.PublishKafkaMessage
import dev.zerokotlin.core.service.KafkaMessages
import dev.zerokotlin.core.service.kafkaFailureDetail
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

@RestController
@RequestMapping("/api/extensions/kafka")
@ConditionalOnZeroKafka
class KafkaExampleController(private val messages: KafkaMessages) {
    private val logger = LogFactory.getLog(javaClass)

    @PostMapping("/messages")
    @PreAuthorize("hasAuthority('extensions:kafka:publish')")
    fun publish(@RequestBody request: PublishKafkaMessage): KafkaPosition {
        if ((request.key?.length ?: 0) > 200 || request.value.isBlank() || request.value.length > 10_000) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Kafka message key or value length")
        }
        return messages.publish(request.key, request.value)
    }

    @PostMapping("/consume")
    @PreAuthorize("hasAuthority('extensions:kafka:consume')")
    fun consume(): List<KafkaMessage> = messages.consume()

    @ExceptionHandler(KafkaException::class)
    fun upstreamError(failure: KafkaException): ResponseEntity<Map<String, String>> {
        val reason = kafkaFailureDetail(failure)
        logger.warn("Kafka request failed: $reason")
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(mapOf("code" to "KAFKA_REQUEST_FAILED", "message" to reason))
    }
}
