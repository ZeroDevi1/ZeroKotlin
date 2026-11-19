package dev.zerokotlin

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityScheme
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(
    info = Info(title = "ZeroKotlin API", version = "v1"),
    security = [SecurityRequirement(name = "bearerAuth")],
)
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer")
class OpenApiConfiguration {
    @Bean
    fun publicLoginOperation(): OpenApiCustomizer = OpenApiCustomizer { api ->
        api.paths?.get("/api/auth/login")?.post?.security = emptyList()
    }
}
