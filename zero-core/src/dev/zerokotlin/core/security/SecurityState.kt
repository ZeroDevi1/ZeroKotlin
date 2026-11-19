package dev.zerokotlin.core.security

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Expiry
import java.time.Duration
import java.util.PriorityQueue
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/** 保存短期验证码摘要，允许每条挑战最多读取一次。 */
interface LoginChallengeStore {
    /** 保存挑战 ID 对应的答案摘要，并设置其有效期。 */
    fun save(id: String, digest: String, ttl: Duration)

    /** 原子读取并删除答案摘要。挑战不存在、已过期或已被使用时返回 null。 */
    fun consume(id: String): String?
}

/** 限流与一次性验证码共用选定的安全状态后端。 */
internal interface SecurityState : LoginChallengeStore {
    /** 原子登记一次请求。接受时返回 0，拒绝时返回距可重试的毫秒数。 */
    fun retryAfterMillis(key: String, requests: Int, windowSeconds: Long): Long
}

/** 进程内的固定窗口配额和一次性验证码存储。 */
internal class LocalSecurityState(private val capacity: Long) : SecurityState {
    private data class Window(val key: String, val expiresAt: Long, var count: Int = 1)
    private data class Challenge(val digest: String, val ttl: Duration)

    private val origin = System.nanoTime()
    private val windows = mutableMapOf<String, Window>()
    private val expirations = PriorityQueue(compareBy(Window::expiresAt))
    private val challenges = Caffeine.newBuilder()
        .maximumSize(capacity)
        .expireAfter(Expiry.creating<String, Challenge> { _, challenge -> challenge.ttl })
        .build<String, Challenge>()

    @Synchronized
    override fun retryAfterMillis(key: String, requests: Int, windowSeconds: Long): Long {
        val now = System.nanoTime() - origin
        while (expirations.peek()?.expiresAt?.let { it <= now } == true) {
            windows.remove(expirations.remove().key)
        }
        val current = windows[key]
        if (current == null) {
            // 容量耗尽时保留已有配额，防止新键挤掉仍有效的窗口。
            if (windows.size >= capacity) return millisUntil(expirations.element().expiresAt, now)
            val window = Window(key, now + windowSeconds * 1_000_000_000)
            windows[key] = window
            expirations.add(window)
        } else if (current.count >= requests) {
            return millisUntil(current.expiresAt, now)
        } else {
            current.count++
        }
        return 0
    }

    private fun millisUntil(expiration: Long, now: Long): Long = (expiration - now + 999_999) / 1_000_000

    override fun save(id: String, digest: String, ttl: Duration) {
        challenges.put(id, Challenge(digest, ttl))
    }

    override fun consume(id: String): String? = challenges.asMap().remove(id)?.digest
}

/** 通过 Redis 脚本原子更新固定窗口计数并核销验证码。 */
internal class RedisSecurityState(private val redis: StringRedisTemplate, private val keyPrefix: String) : SecurityState {
    private val countRequest = DefaultRedisScript(
        """
        local ttl = redis.call('PTTL', KEYS[1])
        if ttl < 0 then
            redis.call('SET', KEYS[1], '1', 'PX', ARGV[2])
            return 0
        end
        if tonumber(redis.call('GET', KEYS[1])) >= tonumber(ARGV[1]) then
            return math.max(ttl, 1)
        end
        redis.call('INCR', KEYS[1])
        return 0
        """.trimIndent(),
        Long::class.javaObjectType,
    )

    private val consumeChallenge = DefaultRedisScript(
        """
        local digest = redis.call('GET', KEYS[1])
        if digest then redis.call('DEL', KEYS[1]) end
        return digest
        """.trimIndent(),
        String::class.java,
    )

    override fun retryAfterMillis(key: String, requests: Int, windowSeconds: Long): Long = withRedis {
        redis.execute(countRequest, listOf("${keyPrefix}$key"), requests.toString(), (windowSeconds * 1_000).toString())
            ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "安全状态暂时不可用")
    }

    override fun save(id: String, digest: String, ttl: Duration) = withRedis {
        redis.opsForValue().set("${keyPrefix}captcha:$id", digest, ttl)
    }

    override fun consume(id: String): String? = withRedis {
        redis.execute(consumeChallenge, listOf("${keyPrefix}captcha:$id"))
    }

    private inline fun <T> withRedis(operation: () -> T): T = try {
        operation()
    } catch (failure: DataAccessException) {
        throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "安全状态暂时不可用", failure)
    }
}
