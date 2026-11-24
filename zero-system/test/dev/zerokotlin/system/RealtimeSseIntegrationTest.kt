package dev.zerokotlin.system

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigResolveOptions
import dev.zerokotlin.ktor.module as zeroKotlinModule
import io.ktor.server.application.applicationEnvironment
import io.ktor.server.application.serverConfig
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir

/** 在回环端口以真实 HTTP 连接验证 SQLite 启动、Bearer 鉴权及 SSE 分帧。 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RealtimeSseIntegrationTest {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    private lateinit var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>
    private lateinit var origin: String
    private lateinit var admin: String
    private val password = UUID.randomUUID().toString()

    @BeforeAll
    fun start(@TempDir directory: Path) {
        val overrides = ConfigFactory.parseMap(mapOf(
            "app.contextPath" to "/",
            "zero.databaseMode" to "sqlite",
            "zero.databasePath" to directory.resolve("system.db").toString(),
            "zero.cacheMode" to "caffeine",
            "zero.captchaEnabled" to false,
            "extensions.businessModules" to "",
            "extensions.searchEnabled" to false,
            "extensions.kafkaEnabled" to false,
        ))
        val environment = applicationEnvironment {
            config = HoconApplicationConfig(
                overrides.withFallback(ConfigFactory.parseResources("application.conf"))
                    .resolve(ConfigResolveOptions.noSystem()),
            )
        }
        server = embeddedServer(Netty, serverConfig(environment) { module { zeroKotlinModule() } }) {
            connector {
                host = "127.0.0.1"
                port = 0
            }
        }
        server.start(wait = false)
        val port = runBlocking { server.engine.resolvedConnectors().single().port }
        origin = "http://127.0.0.1:$port"
        admin = login("admin", "admin")
    }

    @AfterAll
    fun stop() {
        client.close()
        if (::server.isInitialized) server.stop()
    }

    @Test
    fun `匿名连接返回 401 无读取权限的用户返回 403`() {
        assertEquals(401, stream().statusCode())
        val created = post("/api/system/users", mapOf("username" to "reader", "displayName" to "访客", "password" to password), admin)
        assertEquals(201, created.statusCode(), created.body())
        assertEquals(403, stream(login("reader")).statusCode())
    }

    @Test
    fun `授权用户逐帧收到 tick 及结束事件`() {
        val request = request("/api/realtime/sse/demo", admin).header("Accept", "text/event-stream").GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        assertEquals(200, response.statusCode())
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/event-stream"))
        response.body().bufferedReader(Charsets.UTF_8).use { reader ->
            assertEquals("id: 1", reader.readLine())
            val first = System.nanoTime()
            val remaining = reader.readText()
            assertEquals(
                "event: tick\ndata: 1\n\nid: 2\nevent: tick\ndata: 2\n\nid: 3\nevent: tick\ndata: 3\n\nevent: done\ndata: 3\n\n",
                remaining,
            )
            assertTrue(System.nanoTime() - first >= Duration.ofMillis(200).toNanos(), "事件应分批到达")
        }
    }

    @Test
    fun `重连只接收剩余事件并拒绝无效游标`() {
        assertEquals("id: 3\nevent: tick\ndata: 3\n\nevent: done\ndata: 3\n\n", stream(admin, "2").body())
        assertEquals(204, stream(admin, "3").statusCode())
        assertEquals(400, stream(admin, "invalid").statusCode())
    }

    private fun login(name: String, password: String = this.password): String {
        val response = post("/api/auth/login", mapOf("username" to name, "password" to password))
        assertEquals(200, response.statusCode())
        return Json.parseToJsonElement(response.body()).jsonObject.getValue("accessToken").jsonPrimitive.content
    }

    private fun post(path: String, body: Map<String, String>, token: String? = null): HttpResponse<String> {
        val request = request(path, token).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(Json.encodeToString(body))).build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun stream(token: String? = null, lastId: String? = null): HttpResponse<String> {
        val request = request("/api/realtime/sse/demo", token).header("Accept", "text/event-stream")
        if (lastId != null) request.header("Last-Event-ID", lastId)
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun request(path: String, token: String?): HttpRequest.Builder =
        HttpRequest.newBuilder(URI.create(origin + path)).timeout(Duration.ofSeconds(10)).apply {
            if (token != null) header("Authorization", "Bearer $token")
        }
}
