package dev.zerokotlin.ktor

import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
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

/**
 * 使用按调用创建的 Kafka 客户端执行发布、消费和主题探测。
 *
 * @property properties 提供代理地址、目标主题、消费组、超时和客户端附加属性。
 */
class KafkaMessages(private val properties: KafkaProperties) {
    /** 单次消费轮询或同步提交的等待时间。 */
    private val timeout = properties.timeout

    /** 以整数毫秒传给客户端配置和异步结果等待的超时。 */
    private val timeoutMillis = timeout.toMillis().toInt()

    /** 客户端公共属性。代理地址和 API 超时取自模块配置，单次代理请求超时为其一半。 */
    private val commonProperties: Map<String, Any> = properties.clientProperties + mapOf(
        CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to properties.bootstrapServers,
        CommonClientConfigs.DEFAULT_API_TIMEOUT_MS_CONFIG to timeoutMillis,
        CommonClientConfigs.REQUEST_TIMEOUT_MS_CONFIG to timeoutMillis / 2,
    )

    /**
     * 使用字符串序列化器发布消息，启用生产者幂等性并等待所有同步副本确认。
     * 等待元数据或缓冲区、交付以及等待确认分别使用配置的超时。
     * 等待确认超时后，消息仍可能已写入。重新调用会创建新生产者，可能写入另一条消息。
     *
     * @param key 用于 Kafka 分区选择的可选消息键。
     * @param value 待发送的文本消息。
     * @return 代理确认后返回的消息分区和偏移量。
     */
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

    /**
     * 在配置的消费组中订阅主题并轮询一次。没有有效的已提交偏移量时，从最早可用的位置开始。
     * 对非空批次，先同步提交各已消费分区的下一条读取位置再返回，因而 HTTP 客户端重试时可能得到下一批。
     * 轮询和偏移量提交分别使用配置的超时，关闭消费者时另设 2 秒超时。
     *
     * @return 最多 20 条记录。已获分区但本次轮询没有记录时返回空列表。
     * @throws KafkaException 轮询期间未获得分区且没有记录，或 Kafka 客户端操作失败。
     */
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

    /**
     * 查询已配置主题的元数据，用于健康探测。
     *
     * @return 主题的分区总数。
     */
    internal fun partitionCount(): Int {
        val admin = Admin.create(commonProperties)
        return AutoCloseable { admin.close(Duration.ZERO) }.use {
            val topics = admin.describeTopics(listOf(properties.topic)).allTopicNames().await()
            topics.getValue(properties.topic).partitions().size
        }
    }

    /**
     * 在配置的毫秒超时内等待操作完成。等待被中断时恢复线程的中断标志。
     *
     * @param T 异步结果类型。
     * @receiver 发布或主题探测的异步结果。
     * @return 操作成功完成后的结果。
     * @throws KafkaException 异步执行失败、等待超时或等待被中断。
     */
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

/** 单次轮询结束时既未取得记录，也未分配到任何分区。 */
private class KafkaPartitionAssignmentTimeout :
    KafkaException("Kafka consumer did not receive a partition assignment")

/**
 * 提取日志和响应可共用的 Kafka 失败原因。
 *
 * @param failure Kafka 客户端或消息服务抛出的异常。
 * @return 直接原因的异常类型名。没有直接原因时使用原异常类型名。
 */
internal fun kafkaFailureDetail(failure: KafkaException): String =
    (failure.cause ?: failure).javaClass.simpleName
