package dev.zerokotlin.core.config

import dev.zerokotlin.core.IpRegionLookup
import dev.zerokotlin.core.RequestAudit
import dev.zerokotlin.core.RequestAuditEvent
import dev.zerokotlin.core.RequestAuditSink
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.ErrorResponse
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping

/** 使用请求作用域保存计时及异常信息，并在 MVC 完成请求处理后写入审计。 */
internal class RequestAuditInterceptor(
    private val sink: RequestAuditSink,
    private val regions: IpRegionLookup,
    private val includeUserAgent: Boolean,
) : HandlerInterceptor {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val attribute = javaClass.name

    private class Pending(
        val action: String,
        val route: String,
        val startedAt: Long = System.nanoTime(),
        val occurredAt: Long = System.currentTimeMillis(),
        var exceptionType: String? = null,
    )

    /** 从显式标注的处理器开始计时，并用 MVC 路由模板避免记录用户输入的路径片段。 */
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val annotation = (handler as? HandlerMethod)?.getMethodAnnotation(RequestAudit::class.java)
        val route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)?.toString()
        if (annotation != null && route != null && request.getAttribute(attribute) == null) {
            request.setAttribute(attribute, Pending(annotation.action.take(120), route.take(255)))
        }
        return true
    }

    /** 观察 MVC 异常并继续交由现有解析器生成响应。 */
    fun observeException(request: HttpServletRequest, exception: Exception) {
        (request.getAttribute(attribute) as? Pending)?.exceptionType = exception.javaClass.simpleName.take(120)
    }

    /** 将状态、耗时和客户端元数据交给审计接收器，写入异常只记服务器告警。 */
    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?,
    ) {
        val pending = request.getAttribute(attribute) as? Pending ?: return
        request.removeAttribute(attribute)
        val durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - pending.startedAt)
        val status = when {
            ex == null || response.isCommitted || response.status >= 400 -> response.status
            ex is ErrorResponse -> ex.statusCode.value()
            ex is AccessDeniedException -> 403
            ex is AuthenticationException -> 401
            else -> 500
        }
        try {
            val address = request.remoteAddr.take(128)
            val agent = if (includeUserAgent) {
                request.getHeader("User-Agent")?.filterNot { it.isISOControl() }
                    ?.take(512)?.trimEnd { it.isHighSurrogate() }?.ifBlank { null }
            } else null
            sink.save(
                RequestAuditEvent(
                    pending.action, request.method.take(16), pending.route, status, durationMs,
                    pending.exceptionType ?: ex?.javaClass?.simpleName?.take(120), address,
                    regions.find(address), agent, pending.occurredAt,
                ),
            )
        } catch (_: RuntimeException) {
            logger.warn("写入请求审计失败，动作 {}", pending.action)
        }
    }
}
