package dev.zerokotlin.core.config

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * `zero.cache` 下的 Spring Cache 后端和过期配置。
 *
 * @property mode 缓存选择模式，默认为 [CacheMode.AUTO]。
 * @property ttl 条目从写入起的有效期，默认 10 分钟，至少为 1 毫秒。
 * @property caffeine 进程内 Caffeine 缓存的容量配置。
 * @property redis 单机 Redis 的连接和键前缀配置。
 */
@ConfigurationProperties("zero.cache")
internal class CacheProperties(
    val mode: CacheMode = CacheMode.AUTO,
    val ttl: Duration = Duration.ofMinutes(10),
    val caffeine: CaffeineProperties = CaffeineProperties(),
    val redis: RedisProperties = RedisProperties(),
)

/** 应用启动时选择 Spring Cache 后端的方式。 */
internal enum class CacheMode {
    /** 配置了 Redis 主机时先探测 Redis，主机未配置或探测出现可降级的网络故障时使用 Caffeine。 */
    AUTO,

    /** 直接使用进程内缓存。 */
    CAFFEINE,

    /** 必须使用 Redis，启动探测失败时终止启动。 */
    REDIS
}

/**
 * 进程内缓存配置。
 *
 * @property maximumSize 每个缓存的条目数上限，默认 10 000，须大于 0。
 */
internal class CaffeineProperties(val maximumSize: Long = 10_000)

/**
 * 单机 Redis 的连接、认证和键空间配置。
 *
 * @property host 主机名或 IP，默认未配置。使用 Redis 时必填且不能为纯空白。
 * @property port TCP 端口，默认 6379，范围为 1 至 65535。
 * @property username ACL 用户名，默认未配置。指定时须为非空白用户名，并提供非空密码。
 * @property password Redis 密码，默认未配置。只提供密码时使用 Redis 默认用户。
 * @property database 逻辑库编号，默认 0，须为非负整数。
 * @property tls 是否启用 TLS，默认关闭。
 * @property timeout TCP 连接和 Redis 命令各自使用的超时，默认 2 秒，至少为 1 毫秒。
 * @property keyPrefix 缓存键的应用前缀，默认 `zero-kotlin:`。
 *   至少包含一个字符，允许英文字母、数字、点、下划线、冒号和连字符。
 */
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
