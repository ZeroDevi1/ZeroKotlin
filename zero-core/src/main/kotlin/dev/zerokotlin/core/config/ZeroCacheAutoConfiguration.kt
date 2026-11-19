package dev.zerokotlin.core.config

import com.github.benmanes.caffeine.cache.Caffeine
import io.lettuce.core.ClientOptions
import io.lettuce.core.RedisCommandTimeoutException
import io.lettuce.core.SocketOptions
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.dao.QueryTimeoutException
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.cache.BatchStrategies
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.cache.RedisCacheWriter
import org.springframework.data.redis.connection.RedisPassword
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer
import org.springframework.data.redis.serializer.JacksonObjectReader
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair
import tools.jackson.databind.DefaultTyping
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import tools.jackson.module.kotlin.KotlinModule

@AutoConfiguration(before = [CacheAutoConfiguration::class])
@EnableConfigurationProperties(CacheProperties::class)
@EnableCaching
internal class ZeroCacheAutoConfiguration {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(CacheManager::class)
    fun cacheManager(properties: CacheProperties): ManagedCacheManager {
        require(properties.ttl.toMillis() > 0) { "zero.cache.ttl must be at least 1 ms" }
        if (properties.mode == CacheMode.CAFFEINE ||
            (properties.mode == CacheMode.AUTO && properties.redis.host == null)
        ) {
            return caffeineCacheManager(properties)
        }
        return try {
            redisCacheManager(properties)
        } catch (failure: RuntimeException) {
            if (properties.mode != CacheMode.AUTO || !isNetworkFailure(failure)) throw failure
            logger.warn("Redis endpoint unavailable during cache selection, using Caffeine")
            caffeineCacheManager(properties)
        }
    }

    private fun caffeineCacheManager(properties: CacheProperties): ManagedCacheManager {
        require(properties.caffeine.maximumSize > 0) { "zero.cache.caffeine.maximum-size must be positive" }
        val manager = CaffeineCacheManager().apply {
            setAllowNullValues(false)
            setCaffeine(
                Caffeine.newBuilder().maximumSize(properties.caffeine.maximumSize).expireAfterWrite(properties.ttl),
            )
        }
        logger.info("Selected cache: caffeine")
        return ManagedCacheManager(manager)
    }

    private fun redisCacheManager(properties: CacheProperties): ManagedCacheManager {
        val settings = properties.redis
        val host = settings.host
        require(!host.isNullOrBlank()) { "zero.cache.redis.host is required" }
        require(settings.port in 1..65535) { "zero.cache.redis.port must be between 1 and 65535" }
        require(settings.database >= 0) { "zero.cache.redis.database must be non-negative" }
        require(settings.timeout.toMillis() > 0) { "zero.cache.redis.timeout must be at least 1 ms" }
        require(settings.username == null || (!settings.username.isBlank() && !settings.password.isNullOrEmpty())) {
            "zero.cache.redis.username requires a non-empty username and password"
        }
        require(settings.keyPrefix.matches(Regex("[a-zA-Z0-9._:-]+"))) {
            "zero.cache.redis.key-prefix must contain only letters, digits, dots, underscores, colons or hyphens"
        }

        val server = RedisStandaloneConfiguration(host, settings.port).apply {
            database = settings.database
            username = settings.username
            password = RedisPassword.of(settings.password)
        }
        val client = LettuceClientConfiguration.builder()
            .commandTimeout(settings.timeout)
            .clientOptions(
                ClientOptions.builder().socketOptions(SocketOptions.builder().connectTimeout(settings.timeout).build()).build(),
            )
        if (settings.tls) client.useSsl()

        val factory = LettuceConnectionFactory(server, client.build())
        try {
            factory.afterPropertiesSet()
            factory.connection.use { check(it.ping() == "PONG") { "Redis did not respond with PONG" } }
            val config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(properties.ttl)
                .disableCachingNullValues()
                .prefixCacheNameWith(settings.keyPrefix)
                .serializeValuesWith(SerializationPair.fromSerializer(cacheValueSerializer()))
            val writer = RedisCacheWriter.nonLockingRedisCacheWriter(factory, BatchStrategies.scan(1000))
            val manager = RedisCacheManager.builder(writer).cacheDefaults(config).build().apply { afterPropertiesSet() }
            logger.info("Selected cache: redis")
            return ManagedCacheManager(manager, factory::destroy)
        } catch (failure: RuntimeException) {
            runCatching(factory::destroy).exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        }
    }

    private fun cacheValueSerializer(): JacksonJsonRedisSerializer<Any> {
        val allowedTypes = BasicPolymorphicTypeValidator.builder()
            .allowIfSubType("dev.zerokotlin.")
            .allowIfSubType(Number::class.java)
            .allowIfSubType("java.time.")
            .allowIfSubType("java.util.")
            .allowIfSubType("kotlin.collections.")
            .build()
        val mapper = JsonMapper.builder()
            .addModule(KotlinModule.Builder().build())
            .activateDefaultTyping(allowedTypes, DefaultTyping.JAVA_LANG_OBJECT)
            .build()
        val valueType = mapper.constructType(Any::class.java)
        return JacksonJsonRedisSerializer(mapper, valueType, JacksonObjectReader.create()) { json, value ->
            json.writerFor(valueType).writeValueAsBytes(value)
        }
    }

    private fun isNetworkFailure(failure: RuntimeException): Boolean {
        if (failure is QueryTimeoutException) return true
        if (failure !is RedisConnectionFailureException && failure !is RedisCommandTimeoutException) return false
        return generateSequence<Throwable>(failure) { it.cause }.any {
            it is ConnectException || it is NoRouteToHostException || it is SocketTimeoutException ||
                it is RedisCommandTimeoutException || it is UnknownHostException || it is UnresolvedAddressException
        }
    }
}

internal class ManagedCacheManager(
    delegate: CacheManager,
    private val onClose: () -> Unit = {},
) : CacheManager by delegate, AutoCloseable {
    override fun close() = onClose()
}
