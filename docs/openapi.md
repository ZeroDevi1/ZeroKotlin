# Swagger UI 与 OpenAPI

在项目根目录通过 `./gradlew :zero-system:run` 启动 Ktor。文档默认可访问，并沿用 `app.contextPath`。设置 `ZERO_API_DOCS_ENABLED=false` 可关闭这些入口。

| 路径 | 内容 |
| --- | --- |
| `/swagger-ui/` | 在浏览器调试接口 |
| `/v3/api-docs` | OpenAPI JSON |
| `/v3/api-docs.yaml` | OpenAPI YAML |

在 Swagger UI 提交 `POST /api/auth/login`，复制响应的 `accessToken`，再通过 **Authorize** 授权。可发送 `GET /api/auth/me` 确认当前会话。启用验证码时先调用 `GET /api/auth/captcha`。

路由和 OpenAPI 入口在 [Application.kt](../zero-system/src/dev/zerokotlin/ktor/Application.kt) 注册。对外契约以实际启用的路由及其请求和响应类型为准。
