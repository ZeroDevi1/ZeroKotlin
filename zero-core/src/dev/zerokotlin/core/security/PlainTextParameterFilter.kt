package dev.zerokotlin.core.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.util.AntPathMatcher
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 统一清理显式配置的查询或表单参数，包括同名多值参数。请求体由原有消息转换器处理。
 * @property cleaner 与服务层共用的纯文本清理器。
 * @property names 可清理的参数名。
 * @property excludedPaths 由部署方指定的路径豁免。
 */
internal class PlainTextParameterFilter(
    private val cleaner: PlainTextInput,
    private val names: Set<String>,
    private val excludedPaths: List<String>,
) : OncePerRequestFilter() {
    private val paths = AntPathMatcher()

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI.removePrefix(request.contextPath)
        return names.isEmpty() || path.startsWith("/api/auth/") || path == "/api/realtime/ws" ||
            request.contentType?.startsWith("multipart/", ignoreCase = true) == true ||
            excludedPaths.any { paths.match(it, path) }
    }

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val filtered = object : HttpServletRequestWrapper(request) {
            override fun getParameter(name: String): String? =
                super.getParameter(name)?.let { if (name in names) cleaner.clean(it) else it }

            override fun getParameterValues(name: String): Array<String>? =
                super.getParameterValues(name)?.let { cleanValues(name, it) }

            override fun getParameterMap(): Map<String, Array<String>> =
                super.getParameterMap().mapValues { (name, values) -> cleanValues(name, values) }
        }
        chain.doFilter(filtered, response)
    }

    private fun cleanValues(name: String, values: Array<String>): Array<String> =
        if (name in names) Array(values.size) { cleaner.clean(values[it]) } else values
}
