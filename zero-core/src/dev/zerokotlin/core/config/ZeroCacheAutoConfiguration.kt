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
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer
import org.springframework.data.redis.serializer.JacksonObjectReader
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair
import tools.jackson.databind.DefaultTyping
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import tools.jackson.module.kotlin.KotlinModule

/** 启用 Spring Cache，并在应用未提供缓存管理器时选择 Caffeine 或 Redis。 */
@AutoConfiguration(before = [CacheAutoConfiguration::class])
@EnableConfigurationProperties(CacheProperties::class)
@EnableCaching
internal class ZeroCacheAutoConfiguration {
    /** 记录选中的缓存后端及启动时发生的 Redis 降级。 */
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 在启动时选定缓存后端。自动模式仅在 Redis 主机未配置或探测发生可降级的网络故障时使用 Caffeine。
     * Redis 的配置、认证或证书等错误会使启动失败。
     *
     * @param properties 缓存选择模式、有效期和两个后端的配置。
     * @return 固定使用选定后端的缓存管理器，由 Spring 在 Bean 销毁时关闭。
     */
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

    /**
     * 创建进程内缓存，按写入时间过期并禁止缓存空值。
     *
     * @param properties 提供过期时长和每个 Caffeine 缓存的条目上限。
     * @return 以 Caffeine 为后端的缓存管理器。
     */
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

    /**
     * 校验单机 Redis 配置并以 `PING` 确认连通性，初始化失败时销毁连接工厂。
     * 禁止缓存空值，使用 JSON 存值，键格式为 `<应用前缀><缓存名>::<键>`。
     * 清理缓存时使用 `SCAN`，每批大小配置为 1000。
     *
     * @param properties 提供连接参数、缓存有效期及键前缀。
     * @return 持有 Redis 连接工厂清理动作的缓存管理器。
     */
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
            return ManagedCacheManager(manager, StringRedisTemplate(factory), factory::destroy)
        } catch (failure: RuntimeException) {
            runCatching(factory::destroy).exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        }
    }

    /**
     * 使用 Kotlin 模块序列化缓存对象，并在声明类型为 `Any` 的值上写入适用的类型信息。
     * 反序列化时将多态子类型限制在项目包、数字类型及配置的时间和集合相关包范围内。
     *
     * @return 用同一 Jackson 类型配置写入和读取缓存值的 JSON 序列化器。
     */
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

    /**
     * 识别允许自动模式降级的 Redis 初始化故障。
     *
     * @param failure 配置、连接或启动探测期间捕获的运行时异常。
     * @return [QueryTimeoutException] 直接返回 `true`。Redis 连接失败异常或命令超时异常须在自身或原因链中
     *   匹配连接拒绝、路由不可达、套接字或命令超时、地址无法解析等类型才返回 `true`。
     */
    private fun isNetworkFailure(failure: RuntimeException): Boolean {
        if (failure is QueryTimeoutException) return true
        if (failure !is RedisConnectionFailureException && failure !is RedisCommandTimeoutException) return false
        return generateSequence<Throwable>(failure) { it.cause }.any {
            it is ConnectException || it is NoRouteToHostException || it is SocketTimeoutException ||
                it is RedisCommandTimeoutException || it is UnknownHostException || it is UnresolvedAddressException
        }
    }
}

/**
 * 将缓存操作委托给选定后端，并在关闭时释放该后端的外部资源。
 *
 * @param delegate 实际处理 Spring Cache 操作的管理器。
 * @property redis Redis 模式下供原子安全操作复用的字符串模板，与缓存共用连接工厂。
 * @property onClose 关闭时调用的资源清理动作，默认不执行任何操作。
 */
internal class ManagedCacheManager(
    delegate: CacheManager,
    val redis: StringRedisTemplate? = null,
    private val onClose: () -> Unit = {},
) : CacheManager by delegate, AutoCloseable {
    /** 调用构造时提供的资源清理动作。 */
    override fun close() = onClose()
}
