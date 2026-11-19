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
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody

/**
 * 提供实时消息发布和在线统计接口。
 *
 * @property messages 校验消息发送请求、记录审计并提交实时投递。
 */
@RestController
@RequestMapping("/api/realtime")
@Tag(name = "实时消息")
class SystemRealtimeController(private val messages: SystemRealtimeMessages) {
    /**
     * 在当前 HTTP 连接内发送三个示例序号及结束事件，每个事件写完后立即刷新。
     * @param lastId 客户端最后收到的 tick ID，省略时从 1 开始。
     * @param response 每帧写完后刷新 Servlet 响应缓冲区。
     * @return 已接收全部 tick 时返回 204，否则返回剩余事件流。
     */
    @GetMapping("/sse/demo", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @PreAuthorize("hasAuthority('$MESSAGE_READ')")
    @Operation(
        summary = "订阅 SSE 演示流",
        description = "需要 $MESSAGE_READ 权限。tick 的 ID 为 1 到 3，done 表示流结束。",
        responses = [
            ApiResponse(responseCode = "200", description = "逐个发送的 tick 和 done 事件"),
            ApiResponse(responseCode = "204", description = "已收到最后一个 tick"),
            ApiResponse(responseCode = "400", description = "Last-Event-ID 必须在 0 到 3 之间"),
        ],
    )
    fun demoEvents(
        @RequestHeader("Last-Event-ID", required = false) lastId: String?,
        response: HttpServletResponse,
    ): ResponseEntity<StreamingResponseBody> {
        val last = if (lastId == null) 0 else lastId.toIntOrNull()?.takeIf { it in 0..3 }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Last-Event-ID 必须在 0 到 3 之间")
        if (last == 3) return ResponseEntity.noContent().build()
        val stream = StreamingResponseBody { output ->
            val writer = output.bufferedWriter(Charsets.UTF_8)
            for (id in (last + 1)..3) {
                if (id > last + 1) Thread.sleep(300)
                writer.write("id: $id\nevent: tick\ndata: $id\n\n")
                writer.flush()
                // Spring 对响应正文流的 flush 可延迟到处理结束，逐帧投递需要刷新 Servlet 缓冲区。
                response.flushBuffer()
            }
            writer.write("event: done\ndata: 3\n\n")
            writer.flush()
        }
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .cacheControl(CacheControl.noStore())
            .header("X-Accel-Buffering", "no")
            .body(stream)
    }

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
