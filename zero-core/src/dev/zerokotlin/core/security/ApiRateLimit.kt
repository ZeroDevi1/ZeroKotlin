package dev.zerokotlin.core.security

/** 接口配额按 Servlet 对端 IP 或已认证的用户 ID 计数。 */
enum class RateLimitBy { IP, USER }

/**
 * 为 Spring MVC 操作声明固定窗口配额。首个请求开始计时，配额用完后返回 HTTP 429。
 *
 * @property key 操作的稳定标识。多个接口使用同一标识时会共用配额，应使用相同规则。
 * @property requests 一个窗口内允许的请求数，须大于 0。
 * @property windowSeconds 窗口时长，范围为 1～86400 秒。
 * @property by 配额的划分依据。按用户计数时要求有效会话与 [dev.zerokotlin.core.CurrentActor]。
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ApiRateLimit(
    val key: String,
    val requests: Int,
    val windowSeconds: Long,
    val by: RateLimitBy = RateLimitBy.IP,
)
