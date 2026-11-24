package dev.zerokotlin.ktor.system.realtime

import dev.zerokotlin.core.SystemFailure
import dev.zerokotlin.core.security.digest
import dev.zerokotlin.ktor.bearer
import dev.zerokotlin.ktor.invalid
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val socketId = Regex("[A-Za-z0-9._-]{1,128}")
private val socketToken = Regex("[A-Za-z0-9_-]{43}")
private val destinations = setOf(broadcastDestination, privateDestination)

@Serializable private data class Broadcast(val body: String)
@Serializable private data class PrivateMessage(val recipientId: String, val body: String)
private data class StompFrame(val command: String, val headers: Map<String, String>)

/** /api/realtime。Controller 处理 SSE 分帧和 STOMP 协议，RealtimeService 管理授权及投递。 */
internal fun Route.realtimeController(service: RealtimeService) {
    route("realtime") {
        get("online") {
            call.respond(service.online(call.bearer()))
        }
        get("sse/demo") {
            val token = call.bearer()
            service.authorizeRead(token)
            val header = call.request.headers["Last-Event-ID"]
            val last = if (header == null) 0 else header.toIntOrNull()?.takeIf { it in 0..3 } ?: invalid("Last-Event-ID 必须在 0 到 3 之间")
            if (last == 3) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.response.header("X-Accel-Buffering", "no")
                call.respondTextWriter(ContentType.Text.EventStream) {
                    for (id in last + 1..3) {
                        if (id > last + 1) delay(300)
                        write("id: $id\nevent: tick\ndata: $id\n\n")
                        flush()
                    }
                    write("event: done\ndata: 3\n\n")
                    flush()
                }
            }
        }
        post("broadcast") {
            val token = call.bearer()
            service.broadcast(token, call.receive<Broadcast>().body)
            call.respond(HttpStatusCode.NoContent)
        }
        post("private") {
            val token = call.bearer()
            val input = call.receive<PrivateMessage>()
            service.sendPrivate(token, input.recipientId, input.body)
            call.respond(HttpStatusCode.NoContent)
        }
        webSocket("ws", protocol = "v12.stomp") {
            if (!call.sameOrigin()) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Origin not allowed"))
                return@webSocket
            }
            var peer: Subscriber? = null
            try {
                val frames = incoming.iterator()
                // 浏览器应立即发送 STOMP CONNECT，未认证连接不长期占用服务端。
                val first = withTimeoutOrNull(5_000) {
                    while (frames.hasNext()) {
                        val frame = frames.next() as? Frame.Text ?: denied()
                        val messages = parseFrames(frame.readText())
                        if (messages.isNotEmpty()) return@withTimeoutOrNull messages.singleOrNull() ?: denied()
                    }
                    null
                } ?: denied()
                if (first.command !in setOf("CONNECT", "STOMP") || first.headers["accept-version"]?.split(',')?.contains("1.2") != true) denied()
                val authorization = first.headers.entries.singleOrNull { it.key.equals("Authorization", ignoreCase = true) }?.value ?: denied()
                if (!authorization.startsWith("Bearer ", ignoreCase = true)) denied()
                val raw = authorization.substring(7)
                if (!socketToken.matches(raw)) denied()
                val tokenHash = digest(raw)
                val current = service.connect(tokenHash) ?: denied()
                peer = current
                send("CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\u0000")
                coroutineScope {
                    val delivery = launch {
                        for (outgoing in current.outbound) {
                            if (!service.isAuthorized(current)) {
                                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Session expired"))
                                break
                            }
                            for ((id, destination) in current.subscriptions) {
                                if (destination != outgoing.destination) continue
                                val body = Json.encodeToString(outgoing.message)
                                send(
                                    "MESSAGE\nsubscription:$id\ndestination:$destination\nmessage-id:${outgoing.message.id}\n" +
                                        "content-type:application/json\ncontent-length:${body.toByteArray().size}\n\n$body\u0000",
                                )
                            }
                        }
                    }
                    try {
                        var disconnected = false
                        while (!disconnected && frames.hasNext()) {
                            val frame = frames.next() as? Frame.Text ?: denied()
                            for (message in parseFrames(frame.readText())) {
                                when (message.command) {
                                    "SUBSCRIBE" -> {
                                        val id = message.headers["id"]?.takeIf(socketId::matches) ?: denied()
                                        val destination = message.headers["destination"]?.takeIf { it in destinations } ?: denied()
                                        if (!service.isAuthorized(current)) denied()
                                        if (current.subscriptions.size >= 16 || current.subscriptions.putIfAbsent(id, destination) != null) denied()
                                    }
                                    "UNSUBSCRIBE" -> current.subscriptions.remove(message.headers["id"]?.takeIf(socketId::matches) ?: denied())
                                    "DISCONNECT" -> disconnected = true
                                    else -> denied()
                                }
                                val receipt = message.headers["receipt"]?.takeIf(socketId::matches)
                                if (receipt != null) send("RECEIPT\nreceipt-id:$receipt\n\n\u0000")
                                if (disconnected) break
                            }
                        }
                    } finally {
                        delivery.cancel()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SystemFailure) {
                send("ERROR\nmessage:Access denied\n\n\u0000")
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Access denied"))
            } finally {
                peer?.let(service::disconnect)
            }
        }
    }
}

private fun ApplicationCall.sameOrigin(): Boolean {
    val headers = request.headers.getAll(HttpHeaders.Origin) ?: return true
    val origin = headers.singleOrNull() ?: return false
    return runCatching {
        val uri = URI(origin)
        val point = request.origin
        val port = if (uri.port != -1) uri.port else if (uri.scheme == "https") 443 else 80
        uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null && uri.rawPath.orEmpty().isEmpty() &&
            uri.scheme.equals(point.scheme, true) && uri.host.equals(point.serverHost, true) && port == point.serverPort
    }.getOrDefault(false)
}

private fun parseFrames(text: String): List<StompFrame> {
    if (text.isBlank()) return emptyList()
    val parts = text.split('\u0000')
    if (parts.size < 2 || parts.last().isNotBlank()) denied()
    return parts.dropLast(1).filterNot(String::isBlank).map { raw ->
        val normalized = raw.trimStart('\n', '\r').replace("\r\n", "\n")
        val head = normalized.substringBefore("\n\n")
        if ("\n\n" !in normalized || normalized.substringAfter("\n\n").isNotEmpty()) denied()
        val lines = head.split('\n')
        val headers = mutableMapOf<String, String>()
        for (line in lines.drop(1)) {
            val delimiter = line.indexOf(':')
            if (delimiter <= 0 || headers.putIfAbsent(line.substring(0, delimiter), line.substring(delimiter + 1)) != null) denied()
        }
        StompFrame(lines.first(), headers)
    }
}

private fun denied(): Nothing = throw SystemFailure(403, "实时连接或订阅被拒绝")
