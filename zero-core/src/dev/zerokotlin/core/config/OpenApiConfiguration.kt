package dev.zerokotlin.core.config

import dev.zerokotlin.core.security.PublicApiOperation
import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityScheme
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.security.access.prepost.PreAuthorize

/** 为 Servlet 应用声明 OpenAPI 的全局 Bearer 认证方案，并标注允许匿名访问的操作。 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@OpenAPIDefinition(
    info = Info(
        title = "ZeroKotlin API",
        version = "v1",
        description = "先调用登录接口取得 accessToken，再在 Authorize 中填入令牌。接口按业务分组，列表页码从 1 开始，时间戳使用 Unix 毫秒。",
    ),
    security = [SecurityRequirement(name = "bearerAuth")],
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    description = "填入登录响应中的 accessToken。Swagger UI 会自动添加 Bearer 前缀。",
)
internal class OpenApiConfiguration {
    /**
     * 按路径字符串和 HTTP 方法查找公开操作，并将其安全声明设为空列表以覆盖全局认证要求。
     *
     * @param operations 安全过滤链和 API 文档共用的公开操作声明。
     * @return 定制已存在的 OpenAPI 操作的组件。
     */
    @Bean
    fun publicApiOperations(operations: ObjectProvider<PublicApiOperation>): OpenApiCustomizer = OpenApiCustomizer { api ->
        operations.stream().forEach { endpoint ->
            api.paths?.get(endpoint.path)?.readOperationsMap()?.entries
                ?.firstOrNull { it.key.name == endpoint.method.name() }?.value?.security = emptyList()
        }
        api.paths?.values?.flatMap { it.readOperations() }?.forEach { operation ->
            val security = operation.security ?: api.security.orEmpty()
            if (security.isNotEmpty() && security.all { it.containsKey("bearerAuth") }) {
                operation.describeResponse("401", "缺少有效的 Bearer 会话，或会话已失效")
            }
        }
    }

    /**
     * 为启用方法授权的控制器标明权限不足时的响应，保留接口自己声明的响应描述。
     * @return 读取控制器类与方法授权注解的 OpenAPI 操作定制器。
     */
    @Bean
    fun authorizationResponses(): OperationCustomizer = OperationCustomizer { operation, handler ->
        if (handler.hasMethodAnnotation(PreAuthorize::class.java) ||
            AnnotatedElementUtils.hasAnnotation(handler.beanType, PreAuthorize::class.java)
        ) {
            operation.describeResponse("403", "当前账号缺少所需权限")
        }
        operation
    }

    /**
     * 补充缺失的 HTTP 响应说明。
     * @param code OpenAPI 使用的三位 HTTP 状态码字符串。
     * @param description 客户端收到该状态码的原因。
     */
    private fun Operation.describeResponse(code: String, description: String) {
        val entries = responses ?: ApiResponses().also { responses = it }
        entries.putIfAbsent(code, ApiResponse().description(description))
    }
}
