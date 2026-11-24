package dev.zerokotlin.core.security

import dev.zerokotlin.core.SystemFailure
import kotlinx.serialization.Serializable
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Expiry
import io.ktor.server.config.ApplicationConfig
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisCommandExecutionException
import io.lettuce.core.RedisConnectionException
import io.lettuce.core.RedisException
import io.lettuce.core.RedisURI
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.util.Locale
import java.util.PriorityQueue

interface SecurityState : AutoCloseable {
    suspend fun checkRate(key: String, requests: Int): Long
    suspend fun save(id: String, answer: String, ttl: Duration)
    suspend fun consume(id: String): String?
    suspend fun redisSnapshot(): RedisSnapshot = RedisSnapshot()
    override fun close() = Unit
}

private class LocalSecurityState : SecurityState {
    private data class Challenge(val hash: String, val ttl: Duration)
    private data class Window(val key: String, val end: Long, var used: Int = 1)

    private val challenges = Caffeine.newBuilder().maximumSize(10_000)
        .expireAfter(Expiry.creating<String, Challenge> { _, value -> value.ttl }).build<String, Challenge>()
    private val windows = mutableMapOf<String, Window>()
    private val expiry = PriorityQueue(compareBy(Window::end))
    private val origin = System.nanoTime()

    override suspend fun checkRate(key: String, requests: Int): Long = check(key, requests)

    @Synchronized
    private fun check(key: String, requests: Int): Long {
        val now = System.nanoTime() - origin
        while (expiry.peek()?.end?.let { it <= now } == true) windows.remove(expiry.remove().key)
        val window = windows[key]
        if (window == null) {
            if (windows.size >= 10_000) return (expiry.element().end - now + 999_999) / 1_000_000
            val start = Window(key, now + 60_000_000_000)
            windows[key] = start
            expiry.add(start)
        } else if (window.used >= requests) return (window.end - now + 999_999) / 1_000_000
        else window.used++
        return 0
    }

    override suspend fun save(id: String, answer: String, ttl: Duration) = challenges.put(id, Challenge(answer, ttl))
    override suspend fun consume(id: String): String? = challenges.asMap().remove(id)?.hash
}

private class RedisSecurityState(
    private val client: RedisClient,
    private val connection: StatefulRedisConnection<String, String>,
    private val prefix: String,
) : SecurityState {
    private val sync = connection.sync()

    override suspend fun checkRate(key: String, requests: Int): Long = redis {
        sync.eval<Long>(
            """local ttl = redis.call('PTTL', KEYS[1])
                if ttl < 0 then redis.call('SET', KEYS[1], '1', 'PX', 60000); return 0 end
                if tonumber(redis.call('GET', KEYS[1])) >= tonumber(ARGV[1]) then return math.max(ttl, 1) end
                redis.call('INCR', KEYS[1]); return 0
            """, ScriptOutputType.INTEGER, arrayOf("${prefix}rate:$key"), requests.toString(),
        )
    }

    override suspend fun save(id: String, answer: String, ttl: Duration) {
        redis { sync.psetex("${prefix}captcha:$id", ttl.toMillis(), answer) }
    }

    override suspend fun consume(id: String): String? = redis {
        sync.eval<String?>(
            "local v = redis.call('GET', KEYS[1]); if v then redis.call('DEL', KEYS[1]) end; return v",
            ScriptOutputType.VALUE, arrayOf("${prefix}captcha:$id"),
        )
    }

    override suspend fun redisSnapshot(): RedisSnapshot = try {
        redis {
            val info = sync.info().lineSequence().mapNotNull { line ->
                val offset = line.indexOf(':')
                if (offset <= 0) null else line.substring(0, offset) to line.substring(offset + 1).trim()
            }.toMap()
            RedisSnapshot(
                "AVAILABLE", info["redis_version"], info["connected_clients"]?.toLongOrNull(),
                info["used_memory"]?.toLongOrNull(), info["uptime_in_seconds"]?.toLongOrNull(),
            )
        }
    } catch (_: SystemFailure) {
        RedisSnapshot("UNAVAILABLE")
    }

    private suspend fun <T> redis(action: () -> T): T = withContext(Dispatchers.IO) {
        try { action() }
        catch (_: RedisException) { throw SystemFailure(503, "安全状态暂时不可用") }
    }

    override fun close() {
        try { connection.close() } finally { client.shutdown() }
    }
}

fun configuredSecurityState(config: ApplicationConfig): SecurityState {
    fun value(name: String) = config.propertyOrNull("zero.$name")?.getString()
    val mode = checkNotNull(value("cacheMode")).lowercase(Locale.ROOT)
    require(mode in setOf("auto", "redis", "caffeine")) { "ZERO_CACHE_MODE 须为 auto、caffeine 或 redis" }
    val host = value("redisHost")?.takeIf(String::isNotBlank)
    if (mode == "caffeine" || mode == "auto" && host == null) return LocalSecurityState()
    requireNotNull(host) { "Redis 主机未配置" }
    val port = checkNotNull(value("redisPort")).toInt()
    val database = checkNotNull(value("redisDatabase")).toInt()
    val user = value("redisUsername")?.takeIf(String::isNotBlank)
    val password = value("redisPassword")?.takeIf(String::isNotEmpty)
    val prefix = checkNotNull(value("redisKeyPrefix"))
    require(port in 1..65535 && database >= 0 && prefix.matches(Regex("[A-Za-z0-9._:-]+")) && (user == null || password != null)) {
        "Redis 配置无效"
    }
    val address = RedisURI.Builder.redis(host, port).withDatabase(database)
        .withSsl(checkNotNull(value("redisTls")).toBooleanStrict()).withTimeout(Duration.ofSeconds(2))
    if (user != null) address.withAuthentication(user, checkNotNull(password).toCharArray())
    else if (password != null) address.withPassword(password.toCharArray())
    val client = RedisClient.create(address.build())
    return try {
        val connection = client.connect()
        try { connection.sync().ping() } catch (failure: Exception) { connection.close(); throw failure }
        RedisSecurityState(client, connection, prefix + "security:")
    } catch (failure: Exception) {
        runCatching { client.shutdown() }.onFailure(failure::addSuppressed)
        val causes = generateSequence<Throwable>(failure) { it.cause }.toList()
        if (mode == "auto" && failure is RedisConnectionException && causes.none { it is RedisCommandExecutionException }) {
            LocalSecurityState()
        } else throw failure
    }
}

@Serializable data class RedisSnapshot(
    val status: String = "DISABLED", val version: String? = null, val connectedClients: Long? = null,
    val usedMemoryBytes: Long? = null, val uptimeSeconds: Long? = null,
)
