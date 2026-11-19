package dev.zerokotlin.core.config

import dev.zerokotlin.core.security.PublicApiOperation
import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityScheme
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean

/** 为 Servlet 应用声明 OpenAPI 的全局 Bearer 认证方案，并标注允许匿名访问的操作。 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@OpenAPIDefinition(
    info = Info(title = "ZeroKotlin API", version = "v1"),
    security = [SecurityRequirement(name = "bearerAuth")],
)
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer")
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
    }
}
