# Swagger UI 与 OpenAPI

启动 `zero-system` 后，springdoc 从当前注册的 Spring MVC 接口生成文档。下表使用默认的本地后端地址。

| 用途 | 地址 |
| --- | --- |
| 浏览接口、输入参数、查看响应和执行请求 | [Swagger UI](http://localhost:8080/swagger-ui/index.html) |
| 导入 API 客户端或生成 SDK | [OpenAPI JSON](http://localhost:8080/v3/api-docs) |
| 下载 YAML 格式的同一份定义 | [OpenAPI YAML](http://localhost:8080/v3/api-docs.yaml) |

## 用登录账号调试

1. 在 Swagger UI 找到 `POST /api/auth/login`，点击 **Try it out**。填写当前部署的账号和密码后点击 **Execute**。启用验证码时，须先请求 `GET /api/auth/captcha`，解码响应中的 PNG 并填写 `captchaId` 和 `captchaCode`。[验证码约定](security-controls.md#一次性登录验证码)
2. 从成功响应中复制 `accessToken`。点击页面上的 **Authorize**，在 `bearerAuth` 中粘贴令牌本身。Swagger UI 会为后续请求添加 `Authorization: Bearer …`。
3. 调用 `GET /api/auth/me` 查看登录主体、内置管理员标志及各权限的数据范围。再调用 `GET /api/system/users?page=1&size=20`，具备用户读取权限的账号将看到自己有权访问的记录。
4. 完成调试后调用 `POST /api/auth/logout` 注销服务端会话，再在 **Authorize** 面板清除浏览器中的令牌。

登录接口允许匿名调用。受保护接口缺少有效会话会返回 401，缺少所需操作权限通常返回 403。用户详情和更新在目标超出数据范围时返回 404。各操作的成功状态、特殊冲突条件和输入约束见相应页面。[系统授权与数据范围](system.md)

## 给自定义模块编写接口说明

`implementation(project(":zero-core"))` 提供 Swagger 注解。给控制器添加 `@Tag`，给每个请求处理方法添加 `@Operation`。使用 `@Parameter` 说明路径和查询参数，使用 `@field:Schema` 描述 Kotlin DTO 的 JSON 字段。页码起点、时间单位、空值语义及集合更新时的替换规则尤其需要写清楚。

下例展示一个模块来定义“查询自己的订单”接口时，文档与授权各自如何声明。示例中的 `OrderQueries` 由业务模块实现，须在查询中按当前用户过滤。

```kotlin
data class OrderView(
    @field:Schema(description = "订单 ID")
    val id: String,
    @field:Schema(description = "创建时间，Unix 毫秒", format = "int64")
    val createdAt: Long,
)

@Tag(name = "订单")
@RestController
@RequestMapping("/api/orders")
class OrderController(private val orders: OrderQueries) {
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('orders:order:read')")
    @Operation(summary = "查询自己的订单", description = "仅返回当前用户拥有的订单。")
    @ApiResponse(responseCode = "200", description = "订单详情", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", description = "订单不存在或不属于当前用户")
    fun detail(
        @Parameter(description = "订单 ID") @PathVariable id: String,
    ): OrderView = orders.requireOwnOrder(id)
}
```

让 springdoc 从方法的返回类型推导成功响应，包括 `PageResult<OrderView>` 这样的泛型。如果显式声明成功状态，`useReturnTypeSchema = true` 可以保留类型推导。对于真实返回 204 的方法，声明 `@ResponseStatus(HttpStatus.NO_CONTENT)`，并在文档中声明无响应体。密码等输入字段可使用 `Schema.AccessMode.WRITE_ONLY` 和 `format = "password"`。

默认使用全局 Bearer 安全声明。需要公开的接口可注册一个 `PublicApiOperation` Bean，安全过滤链和文档会使用相同的路径和 HTTP 方法。Swagger 注解描述契约，授权仍由 Spring Security 和业务查询执行。[核心 OpenAPI 配置](../zero-core/src/dev/zerokotlin/core/config/OpenApiConfiguration.kt) · [springdoc 官方文档](https://springdoc.org/)

## 可选能力

Elasticsearch 和 Kafka 完成打包装配且运行开关开启后，对应的控制器才会出现在文档中。第一次联调前检查上游地址、凭据、索引或主题设置。调用 Kafka 消费接口会提交消费组偏移量。[可选能力配置](extensions.md)
