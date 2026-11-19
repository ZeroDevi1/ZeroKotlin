package dev.zerokotlin.system.security

import dev.zerokotlin.system.dto.UserRow
import dev.zerokotlin.system.entity.DataScope
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.server.ResponseStatusException

/**
 * 表示通过系统会话认证的当前用户，供接口鉴权与数据范围判断使用。
 *
 * @property user 认证时读取的用户资料。
 * @property sessionDigest 当前 Bearer 令牌的摘要，用于查询或注销会话。
 * @property administrator 用户是否关联已启用的内置管理员角色。
 * @property scopes 当前有效权限及对应的数据范围，以权限编码为键。
 */
class SystemActor(
    val user: UserRow,
    val sessionDigest: String,
    val administrator: Boolean,
    val scopes: Map<String, DataScope>,
) {
    /**
     * 检查当前主体是否具备指定权限，供后续数据范围过滤使用。
     *
     * @param authority 要求具备的权限编码。
     * @return 该权限对应的有效数据范围。
     * @throws AccessDeniedException 当前主体缺少此权限。
     */
    fun requireScope(authority: String): DataScope =
        scopes[authority] ?: throw AccessDeniedException("缺少权限 $authority")

    /**
     * 要求当前主体拥有系统管理员身份。
     *
     * @throws AccessDeniedException 当前主体没有系统管理员身份。
     */
    fun requireAdministrator() {
        if (!administrator) throw AccessDeniedException("仅系统管理员可执行此操作")
    }
}

/**
 * 从 Spring Security 上下文读取系统会话主体。
 *
 * @return 当前已认证的系统用户及其有效权限。
 * @throws ResponseStatusException 安全上下文没有系统会话主体时抛出 HTTP 401 异常。
 */
fun currentSystemActor(): SystemActor =
    SecurityContextHolder.getContext().authentication?.principal as? SystemActor
        ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
