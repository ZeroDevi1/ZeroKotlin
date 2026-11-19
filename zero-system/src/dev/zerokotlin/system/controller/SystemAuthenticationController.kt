package dev.zerokotlin.system.controller

import dev.zerokotlin.system.dto.CurrentUserResponse
import dev.zerokotlin.system.dto.LoginRequest
import dev.zerokotlin.system.dto.LoginResponse
import dev.zerokotlin.system.dto.MenuNode
import dev.zerokotlin.system.security.currentSystemActor
import dev.zerokotlin.system.service.SystemAuthentication
import dev.zerokotlin.system.service.SystemMenus
import dev.zerokotlin.system.service.SystemSessionTransactions
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 提供登录、注销及当前用户信息接口。
 *
 * @property authentication 验证凭据并记录登录结果。
 * @property sessions 处理当前会话的注销事务。
 * @property menus 根据当前用户的权限构建可见菜单。
 */
@RestController
@RequestMapping("/api/auth")
class SystemAuthenticationController(
    private val authentication: SystemAuthentication,
    private val sessions: SystemSessionTransactions,
    private val menus: SystemMenus,
) {
    /**
     * 校验凭据并尝试建立会话，响应禁止缓存。
     *
     * @param credentials 客户端提交的用户名和密码。
     * @param request 当前 HTTP 请求，从中读取远端地址并截取前 128 个字符用于审计。
     * @return 成功时返回 HTTP 200 及 Bearer 令牌和过期时间，认证失败时返回 HTTP 401。
     */
    @PostMapping("/login")
    fun login(@RequestBody credentials: LoginRequest, request: HttpServletRequest): ResponseEntity<LoginResponse> {
        val result = authentication.login(credentials, request.remoteAddr.take(128))
        return if (result == null) {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).cacheControl(CacheControl.noStore()).build()
        } else {
            ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result)
        }
    }

    /** 注销当前令牌对应的会话，成功时返回 HTTP 204。 */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout() = sessions.logout()

    /**
     * 读取当前已认证用户的信息。
     *
     * @return 用户资料、按权限编码索引的数据范围及系统管理员标志。
     */
    @GetMapping("/me")
    fun me(): CurrentUserResponse {
        val actor = currentSystemActor()
        return CurrentUserResponse(
            actor.user.id, actor.user.username, actor.user.displayName, actor.user.departmentId, actor.scopes,
            actor.administrator,
        )
    }

    /**
     * 按当前用户的权限获取导航菜单。
     *
     * @return 当前会话可见的菜单树。
     */
    @GetMapping("/me/menus")
    fun menus(): List<MenuNode> = menus.visibleTree(currentSystemActor())
}
