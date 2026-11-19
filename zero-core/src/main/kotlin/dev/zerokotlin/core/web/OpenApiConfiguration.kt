package dev.zerokotlin.core.web

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

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@OpenAPIDefinition(
    info = Info(title = "ZeroKotlin API", version = "v1"),
    security = [SecurityRequirement(name = "bearerAuth")],
)
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer")
internal class OpenApiConfiguration {
    @Bean
    fun publicApiOperations(operations: ObjectProvider<PublicApiOperation>): OpenApiCustomizer = OpenApiCustomizer { api ->
        operations.stream().forEach { endpoint ->
            api.paths?.get(endpoint.path)?.readOperationsMap()?.entries
                ?.firstOrNull { it.key.name == endpoint.method.name() }?.value?.security = emptyList()
        }
    }
}
