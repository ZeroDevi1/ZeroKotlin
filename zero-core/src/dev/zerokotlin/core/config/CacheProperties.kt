package dev.zerokotlin.core.config

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("zero.cache")
internal class CacheProperties(
    val mode: CacheMode = CacheMode.AUTO,
    val ttl: Duration = Duration.ofMinutes(10),
    val caffeine: CaffeineProperties = CaffeineProperties(),
    val redis: RedisProperties = RedisProperties(),
)

internal enum class CacheMode { AUTO, CAFFEINE, REDIS }

internal class CaffeineProperties(val maximumSize: Long = 10_000)

internal class RedisProperties(
    val host: String? = null,
    val port: Int = 6379,
    val username: String? = null,
    val password: String? = null,
    val database: Int = 0,
    val tls: Boolean = false,
    val timeout: Duration = Duration.ofSeconds(2),
    val keyPrefix: String = "zero-kotlin:",
)
