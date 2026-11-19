package dev.zerokotlin.core

/**
 * 为 Spring MVC 端点启用请求元数据审计。
 *
 * @property action 稳定的动作标识，最多 120 个字符。已有登录或事务操作审计的端点无需标注。
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequestAudit(val action: String)

/**
 * MVC 请求完成时交给持久化模块的审计元数据。
 *
 * @property action 注解声明的动作标识。
 * @property method 请求的 HTTP 方法。
 * @property route Spring MVC 的匹配路径模板。
 * @property status HTTP 结果状态码。
 * @property durationMs 从匹配处理器到处理完成的耗时，单位为毫秒。
 * @property exceptionType 异常类型的简单类名，无异常时为 null。
 * @property remoteAddress 服务端记录的请求对端地址。
 * @property ipRegion 可选的离线 IP 归属地。
 * @property userAgent 开启采集时记录的 User-Agent，缺失或未开启时为 null。
 * @property occurredAt 开始处理请求时的 Unix 毫秒时间。
 */
data class RequestAuditEvent(
    val action: String,
    val method: String,
    val route: String,
    val status: Int,
    val durationMs: Long,
    val exceptionType: String?,
    val remoteAddress: String,
    val ipRegion: String?,
    val userAgent: String?,
    val occurredAt: Long,
)

/** 接收已完成的请求审计事件，由业务模块决定存储方式。 */
fun interface RequestAuditSink {
    /** 在请求上下文仍可用时保存一条审计记录。 */
    fun save(event: RequestAuditEvent)
}
