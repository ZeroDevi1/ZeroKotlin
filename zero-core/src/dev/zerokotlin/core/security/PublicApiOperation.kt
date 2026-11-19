package dev.zerokotlin.core.security

import org.springframework.http.HttpMethod

/**
 * 允许匿名访问的 HTTP 操作，同时用于调整 OpenAPI 的认证声明。
 *
 * @property method 放行的 HTTP 方法。
 * @property path 安全过滤链匹配的请求路径。OpenAPI 定制器以同一字符串查找文档路径。
 */
data class PublicApiOperation(val method: HttpMethod, val path: String)
