package dev.zerokotlin.system.controller

import dev.zerokotlin.core.security.ApiRateLimit
import dev.zerokotlin.system.dto.CurrentUserResponse
import dev.zerokotlin.system.dto.LoginCaptcha
import dev.zerokotlin.system.dto.LoginRequest
import dev.zerokotlin.system.dto.LoginResponse
import dev.zerokotlin.system.dto.MenuNode
import dev.zerokotlin.system.security.currentSystemActor
import dev.zerokotlin.system.service.SystemAuthentication
import dev.zerokotlin.system.service.SystemLoginCaptcha
import dev.zerokotlin.system.service.SystemMenus
import dev.zerokotlin.system.service.SystemSessionTransactions
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
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
 * @property captcha 签发可在会话建立前获取的一次性验证码。
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "认证与个人信息")
class SystemAuthenticationController(
    private val authentication: SystemAuthentication,
    private val sessions: SystemSessionTransactions,
    private val menus: SystemMenus,
    private val captcha: SystemLoginCaptcha,
) {
    /** 查询是否启用验证码，启用时签发新挑战，关闭时返回 HTTP 204。 */
    @GetMapping("/captcha")
    @ApiRateLimit(key = "auth.captcha", requests = 20, windowSeconds = 60)
    @Operation(
        summary = "获取登录验证码",
        description = "允许匿名调用。启用时每次请求签发新挑战，有效期内只能提交一次。响应禁止缓存。",
        responses = [
            ApiResponse(responseCode = "200", description = "验证码图片和一次性挑战 ID", useReturnTypeSchema = true),
            ApiResponse(responseCode = "204", description = "当前部署关闭验证码", content = [Content()]),
            ApiResponse(
                responseCode = "429",
                description = "对端 IP 超出请求配额",
                headers = [Header(name = "Retry-After", description = "距允许再次请求的秒数。")],
                content = [Content()],
            ),
            ApiResponse(responseCode = "503", description = "安全状态存储暂时不可用"),
        ],
    )
    fun captcha(response: HttpServletResponse): ResponseEntity<LoginCaptcha> {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store")
        val challenge = captcha.issue() ?: return ResponseEntity.noContent().build()
        return ResponseEntity.ok(challenge)
    }

    /**
     * 校验凭据并尝试建立会话，响应禁止缓存。
     *
     * @param credentials 客户端提交的用户名、密码和可选验证码。
     * @param request 当前 HTTP 请求，从中读取远端地址并截取前 128 个字符用于审计。
     * @param response 当前 HTTP 响应，设置禁止缓存。
     * @return 成功时返回 HTTP 200 及 Bearer 令牌和过期时间，认证失败时返回 HTTP 401。
     */
    @PostMapping("/login")
    @ApiRateLimit(key = "auth.login", requests = 30, windowSeconds = 60)
    @Operation(
        summary = "账号密码登录",
        description = "允许匿名调用。启用验证码时须同时提交一次性挑战 ID 和答案。成功后返回 Bearer 令牌，过期时间使用 Unix 毫秒。登录结果禁止缓存。",
        responses = [
            ApiResponse(responseCode = "200", description = "登录成功", useReturnTypeSchema = true),
            ApiResponse(responseCode = "400", description = "请求数据无效，或验证码缺失、错误、过期或已核销"),
            ApiResponse(responseCode = "401", description = "凭据无效或账号已禁用", content = [Content()]),
            ApiResponse(
                responseCode = "429",
                description = "对端 IP 超出请求配额",
                headers = [Header(name = "Retry-After", description = "距允许再次请求的秒数。")],
                content = [Content()],
            ),
            ApiResponse(responseCode = "503", description = "安全状态存储暂时不可用"),
        ],
    )
    fun login(
        @RequestBody credentials: LoginRequest,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<LoginResponse> {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store")
        val result = authentication.login(credentials, request.remoteAddr.take(128))
        return if (result == null) {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        } else {
            ResponseEntity.ok(result)
        }
    }

    /** 注销当前令牌对应的会话，成功时返回 HTTP 204。 */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "退出当前会话",
        description = "撤销本次请求使用的 Bearer 令牌。",
        responses = [ApiResponse(responseCode = "204", description = "当前会话已退出", content = [Content()])],
    )
    fun logout() = sessions.logout()

    /**
     * 读取当前已认证用户的信息。
     *
     * @return 用户资料、按权限编码索引的数据范围及系统管理员标志。
     */
    @GetMapping("/me")
    @Operation(
        summary = "获取当前用户信息",
        description = "返回当前会话用户的基本信息、内置管理员标记及每项有效权限对应的数据范围。",
        responses = [ApiResponse(responseCode = "200", description = "当前用户信息", useReturnTypeSchema = true)],
    )
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
    @Operation(
        summary = "获取当前用户可见菜单",
        description = "根据节点及其祖先的启用状态、关联模块是否安装和当前用户持有的权限筛选菜单树。",
        responses = [ApiResponse(responseCode = "200", description = "可见菜单树", useReturnTypeSchema = true)],
    )
    fun menus(): List<MenuNode> = menus.visibleTree(currentSystemActor())
}
