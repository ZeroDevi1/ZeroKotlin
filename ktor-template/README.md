# ZeroKotlin

用于独立 HTTP API 和小型服务的 Ktor 模板。使用 Kotlin、Netty、kotlinx.serialization 和 Logback。

## 启动服务

准备 JDK 25，在工程目录运行 Gradle Wrapper。首次运行会下载 Gradle 和依赖。

```powershell
.\gradlew.bat run
```

macOS 和 Linux 使用 `./gradlew run`。启动后访问 `http://127.0.0.1:8080/health`，应返回 HTTP 200，JSON 中的 `status` 为 `UP`，`name` 为初始化时指定的项目名。

## 示例接口

| 方法 | 地址 | 用途 |
| --- | --- | --- |
| `GET` | `http://127.0.0.1:8080/health` | 查看项目名和运行状态 |
| `POST` | `http://127.0.0.1:8080/api/hello` | 提交 `{"name":"Lin"}`，返回 `{"message":"你好，Lin"}` |

默认监听 `127.0.0.1:8080`，可用 `ZERO_HOST` 和 `ZERO_PORT` 覆盖。路由前缀配置在 [application.conf](src/main/resources/application.conf) 的 `app.contextPath`。

[中文开发指南](docs/development.md)包含目录说明、PowerShell 和 curl 请求示例、新增路由、错误处理及 JVM 打包步骤。
