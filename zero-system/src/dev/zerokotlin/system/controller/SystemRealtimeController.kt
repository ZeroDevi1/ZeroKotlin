package dev.zerokotlin.system.controller

import dev.zerokotlin.system.dto.RealtimeBroadcastRequest
import dev.zerokotlin.system.dto.RealtimePresence
import dev.zerokotlin.system.dto.RealtimePrivateRequest
import dev.zerokotlin.system.security.MESSAGE_BROADCAST
import dev.zerokotlin.system.security.MESSAGE_PRIVATE
import dev.zerokotlin.system.security.MESSAGE_READ
import dev.zerokotlin.system.service.SystemRealtimeMessages
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 提供实时消息发布和在线统计接口。
 *
 * @property messages 校验消息发送请求、记录审计并提交实时投递。
 */
@RestController
@RequestMapping("/api/realtime")
@Tag(name = "实时消息")
class SystemRealtimeController(private val messages: SystemRealtimeMessages) {
    /** 查询当前实例上按用户 ID 去重的 STOMP 在线人数。 */
    @GetMapping("/online")
    @PreAuthorize("hasAuthority('$MESSAGE_READ')")
    @Operation(
        summary = "查询实时在线人数",
        description = "需要 $MESSAGE_READ 权限。每个已建立 STOMP 连接的用户计一次。",
        responses = [ApiResponse(responseCode = "200", description = "在线人数", useReturnTypeSchema = true)],
    )
    fun online(): RealtimePresence = messages.online()

    /** 提交一条实时群发消息。HTTP 204 表示请求已交给消息代理。 */
    @PostMapping("/broadcast")
    @PreAuthorize("hasAuthority('$MESSAGE_BROADCAST')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "群发实时消息",
        description = "需要 $MESSAGE_BROADCAST 权限。向当前实例上已订阅群发主题的用户发送消息。",
        responses = [
            ApiResponse(responseCode = "204", description = "已提交实时投递", content = [Content()]),
            ApiResponse(responseCode = "400", description = "正文校验失败", content = [Content()]),
        ],
    )
    fun broadcast(@RequestBody request: RealtimeBroadcastRequest) = messages.broadcast(request)

    /** 向指定启用账号提交一条实时私发消息。目标账号须在私发权限的用户范围内。 */
    @PostMapping("/private")
    @PreAuthorize("hasAuthority('$MESSAGE_PRIVATE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "私发实时消息",
        description = "需要 $MESSAGE_PRIVATE 权限。只向目标用户的私发订阅会话发送消息。",
        responses = [
            ApiResponse(responseCode = "204", description = "已提交实时投递", content = [Content()]),
            ApiResponse(responseCode = "400", description = "请求参数校验失败", content = [Content()]),
            ApiResponse(responseCode = "404", description = "目标用户不存在、已停用或超出权限范围", content = [Content()]),
        ],
    )
    fun sendPrivate(@RequestBody request: RealtimePrivateRequest) = messages.sendPrivate(request)
}
