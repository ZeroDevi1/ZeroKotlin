# Ktor 开发指南

## 从启动到第一个请求

1. 安装 JDK 25。用 IntelliJ IDEA 打开生成的项目根目录，Gradle JVM 选用 JDK 25。终端启动时，把 `JAVA_HOME` 指向该 JDK。
2. 在项目根目录执行 `.\gradlew.bat run`。macOS 和 Linux 使用 `./gradlew run`。终端显示监听地址后，请求健康检查。
3. 在另一个终端发送请求。示例假定初始化时传入了 `--context-url /apps/orders`。根据项目 [README](../README.md) 里的健康检查地址调整变量，保留 `/health` 之前的部分。根路径对应 `http://127.0.0.1:8080`。

PowerShell：

```powershell
$baseUrl = 'http://127.0.0.1:8080/apps/orders'
Invoke-RestMethod "$baseUrl/health"
Invoke-RestMethod "$baseUrl/api/hello" -Method Post -ContentType 'application/json' -Body '{"name":"Lin"}'
```

macOS 和 Linux：

```sh
base_url='http://127.0.0.1:8080/apps/orders'
curl -i "$base_url/health"
curl -i "$base_url/api/hello" -H 'Content-Type: application/json' -d '{"name":"Lin"}'
```

健康检查应返回项目名和 `"status":"UP"`。问候接口应返回 `{"message":"你好，Lin"}`。运行服务的终端可以用 `Ctrl+C` 停止服务。

## 从哪里读代码

| 文件 | 职责 |
| --- | --- |
| [build.gradle.kts](../build.gradle.kts) | 声明 Kotlin、序列化插件、Ktor BOM、Netty 和 Gradle 启动入口 |
| [application.conf](../src/main/resources/application.conf) | 声明要加载的应用模块、监听地址、端口、项目名和访问前缀 |
| [Application.kt](../src/main/kotlin/dev/zerokotlin/ktor/Application.kt) | 安装插件、注册路由前缀和健康检查、统一处理响应错误 |
| [GreetingRoutes.kt](../src/main/kotlin/dev/zerokotlin/ktor/GreetingRoutes.kt) | 演示读取 JSON、校验输入和返回数据类 |
| [logback.xml](../src/main/resources/logback.xml) | 配置日志级别和控制台输出 |

`./gradlew run` 启动 Netty 的 `EngineMain`。它读取 `application.conf`，根据 `ktor.application.modules` 调用 `Application.module()`。在 `module()` 中显式安装插件、注册路由。使用过 Spring Boot 的开发者可以先把 `routing {}` 理解为注册 URL 与处理函数的地方，处理函数里的 `call` 对应一次 HTTP 请求和响应。 [Ktor 配置文档](https://ktor.io/docs/server-configuration-file.html) · [Ktor 路由文档](https://ktor.io/docs/server-routing.html)

## 新增一个 GET 接口

1. 在与 `GreetingRoutes.kt` 相同的包下创建 `PingRoutes.kt`，声明响应数据类和路由。

   ```kotlin
   package dev.zerokotlin.ktor

   import io.ktor.server.response.respond
   import io.ktor.server.routing.Route
   import io.ktor.server.routing.get
   import kotlinx.serialization.Serializable

   @Serializable
   private data class PingResponse(val message: String)

   internal fun Route.pingRoutes() {
       get("ping") {
           call.respond(PingResponse("pong"))
       }
   }
   ```

2. 在 `Application.kt` 的 `route("api")` 中，在 `greetingRoutes()` 后调用 `pingRoutes()`。Ktor 会把上层的访问前缀、`api` 和 `ping` 拼成完整路径。
3. 重启服务后请求 `<baseUrl>/api/ping`。预期 HTTP 200，响应是 `{"message":"pong"}`。

接口增多后，可以按业务把路由拆成多个 `Route` 扩展函数。路由负责读取请求、检查输入并返回响应。需要复用的业务规则放到普通 Kotlin 类中，在 `module()` 创建实例，再作为参数传给对应路由。处理函数可以调用挂起函数。如果后续直接调用同步 JDBC 或文件读写，把阻塞操作放到 `Dispatchers.IO`。 [Kotlin 协程调度器](https://kotlinlang.org/docs/coroutine-context-and-dispatchers.html)

## 处理 JSON 和错误

`ContentNegotiation` 安装了 kotlinx.serialization 的 JSON 转换器。给请求和响应数据类加 `@Serializable`，通过 `call.receive<T>()` 读取请求体，通过 `call.respond(...)` 返回对象。POST 调用需带 `Content-Type: application/json`。字段之间的业务约束在处理函数或业务服务里校验。 [Ktor JSON 文档](https://ktor.io/docs/server-serialization.html)

| 场景 | HTTP 状态 | JSON `code` |
| --- | --- | --- |
| JSON 格式错误或缺少必需字段 | `400` | `bad_request` |
| `name` 去掉首尾空白后长度不在 1–80 | `400` | `invalid_name` |
| 请求路径不存在 | `404` | `not_found` |
| POST 请求体使用 `text/plain` 等不支持的类型 | `415` | `unsupported_media_type` |
| 处理请求时抛出未预料的异常 | `500` | `internal_error` |

例如提交 `{"name":" "}` 会收到 `{"code":"invalid_name","message":"name 长度须为 1–80 个字符"}`。通用错误由 `StatusPages` 处理。排查 `500` 时查看服务端控制台，客户端只收到固定错误消息。 [Ktor StatusPages 文档](https://ktor.io/docs/server-status-pages.html)

## 配置和打包

| 配置 | 默认值 | 修改方式 |
| --- | --- | --- |
| 监听地址 | `127.0.0.1` | 环境变量 `ZERO_HOST`。容器内需要监听所有接口时可设为 `0.0.0.0` |
| 监听端口 | `8080` | 环境变量 `ZERO_PORT` |
| 访问前缀 | 由 `init --context-url` 指定 | 修改 `application.conf` 的 `app.contextPath` 后重启。使用 `/` 或 `/apps/orders` 这样的路径 |

1. 在项目根目录执行 `./gradlew installDist`。Windows PowerShell 使用 `.\gradlew.bat installDist`。Gradle 会将启动脚本和依赖放进 `build/install/<项目名>/`。
2. 用 JDK 25 运行 `build/install/<项目名>/bin/` 下的同名脚本。Windows 选用 `.bat`。部署环境可设置 `ZERO_HOST` 和 `ZERO_PORT`。
3. 通过部署地址请求带访问前缀的 `/health`，确认服务可达。

### 常见问题

- **Gradle 提示找不到 Java 25**。检查 `JAVA_HOME`、`java -version` 和 IntelliJ 的 Gradle JVM。
- **提示端口已被占用**。PowerShell 中用 `$env:ZERO_PORT = '8081'` 后重新运行。macOS 和 Linux 使用 `ZERO_PORT=8081 ./gradlew run`。
- **启动了但请求返回 404**。核对 `app.contextPath` 和请求地址。前缀为 `/apps/orders` 时，健康检查地址的路径是 `/apps/orders/health`。
