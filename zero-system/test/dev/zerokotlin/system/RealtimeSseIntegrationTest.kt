package dev.zerokotlin.system

import dev.zerokotlin.ZeroKotlinApplication
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
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import tools.jackson.databind.json.JsonMapper

/** 在回环端口以真实 HTTP 连接验证 SQLite 启动、Bearer 鉴权及 SSE 分帧。 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RealtimeSseIntegrationTest {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    private lateinit var context: ConfigurableApplicationContext
    private lateinit var origin: String
    private lateinit var admin: String
    private val password = UUID.randomUUID().toString()
    private val mapper = JsonMapper.builder().build()

    @BeforeAll
    fun start(@TempDir directory: Path) {
        context = SpringApplication.run(
            ZeroKotlinApplication::class.java,
            "--spring.profiles.active=dev", "--server.address=127.0.0.1", "--server.port=0",
            "--zero.database.mode=sqlite", "--zero.database.sqlite.path=${directory.resolve("system.db")}",
            "--zero.cache.mode=caffeine", "--zero.system.captcha.enabled=false",
            "--zero.system.admin.username=admin", "--zero.system.admin.password=$password",
        )
        origin = "http://127.0.0.1:${context.environment.getRequiredProperty("local.server.port")}"
        admin = login("admin")
    }

    @AfterAll
    fun stop() {
        client.close()
        if (::context.isInitialized) context.close()
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

    private fun login(name: String): String {
        val response = post("/api/auth/login", mapOf("username" to name, "password" to password))
        assertEquals(200, response.statusCode())
        return mapper.readTree(response.body()).get("accessToken").asString()
    }

    private fun post(path: String, body: Map<String, String>, token: String? = null): HttpResponse<String> {
        val request = request(path, token).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build()
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
