package dev.zerokotlin.messaging

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.consumer.CloseOptions
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class KafkaMessages(private val properties: KafkaProperties) {
    private val timeout = properties.timeout
    private val timeoutMillis = timeout.toMillis().toInt()
    private val commonProperties: Map<String, Any> = properties.clientProperties + mapOf(
        CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to properties.bootstrapServers,
        CommonClientConfigs.DEFAULT_API_TIMEOUT_MS_CONFIG to timeoutMillis,
        CommonClientConfigs.REQUEST_TIMEOUT_MS_CONFIG to timeoutMillis / 2,
    )

    fun publish(key: String?, value: String): KafkaPosition {
        val producerProperties = commonProperties + mapOf(
            ProducerConfig.ACKS_CONFIG to "all",
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true,
            ProducerConfig.LINGER_MS_CONFIG to 0,
            ProducerConfig.MAX_BLOCK_MS_CONFIG to timeoutMillis,
            ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG to timeoutMillis,
        )
        val producer = KafkaProducer(producerProperties, StringSerializer(), StringSerializer())
        return AutoCloseable { producer.close(Duration.ZERO) }.use {
            val result = producer.send(ProducerRecord(properties.topic, key, value)).await()
            KafkaPosition(result.partition(), result.offset())
        }
    }

    fun consume(): List<KafkaMessage> {
        val consumerProperties = commonProperties + mapOf(
            ConsumerConfig.GROUP_ID_CONFIG to properties.groupId,
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
            ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG to false,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.MAX_POLL_RECORDS_CONFIG to 20,
        )
        val consumer = KafkaConsumer(consumerProperties, StringDeserializer(), StringDeserializer())
        return AutoCloseable { consumer.close(CloseOptions.timeout(Duration.ofSeconds(2))) }.use {
            consumer.subscribe(listOf(properties.topic))
            val records = consumer.poll(timeout)
            if (records.isEmpty && consumer.assignment().isEmpty()) {
                throw KafkaPartitionAssignmentTimeout()
            }
            val messages = records.map { KafkaMessage(it.partition(), it.offset(), it.key(), it.value()) }
            if (messages.isNotEmpty()) {
                consumer.commitSync(timeout)
            }
            messages
        }
    }

    internal fun partitionCount(): Int {
        val admin = Admin.create(commonProperties)
        return AutoCloseable { admin.close(Duration.ZERO) }.use {
            val topics = admin.describeTopics(listOf(properties.topic)).allTopicNames().await()
            topics.getValue(properties.topic).partitions().size
        }
    }

    private fun <T> Future<T>.await(): T {
        try {
            return get(timeoutMillis.toLong(), TimeUnit.MILLISECONDS)
        } catch (failure: ExecutionException) {
            throw KafkaException("Kafka request failed", failure.cause ?: failure)
        } catch (failure: TimeoutException) {
            throw KafkaException("Kafka request timed out", failure)
        } catch (failure: InterruptedException) {
            Thread.currentThread().interrupt()
            throw KafkaException("Kafka request was interrupted", failure)
        }
    }
}

data class KafkaPosition(val partition: Int, val offset: Long)

data class KafkaMessage(val partition: Int, val offset: Long, val key: String?, val value: String?)

private class KafkaPartitionAssignmentTimeout :
    KafkaException("Kafka consumer did not receive a partition assignment")

internal fun kafkaFailureDetail(failure: KafkaException): String =
    (failure.cause ?: failure).javaClass.simpleName
