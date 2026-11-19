package dev.zerokotlin.search

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.net.URI
import java.net.http.HttpClient
import java.time.Duration

@ConfigurationProperties("zero.search")
class ElasticsearchProperties(
    val url: URI = URI.create("http://localhost:9200"),
    val index: String = "zero-example",
    val timeout: Duration = Duration.ofSeconds(5),
    val apiKey: String? = null,
)

@AutoConfiguration
@ConditionalOnProperty(prefix = "zero.search", name = ["enabled"], havingValue = "true")
@EnableConfigurationProperties(ElasticsearchProperties::class)
@Import(ElasticsearchExampleController::class)
class ElasticsearchConfiguration {
    @Bean(destroyMethod = "close")
    fun elasticsearchHttpClient(properties: ElasticsearchProperties): HttpClient =
        HttpClient.newBuilder().connectTimeout(properties.timeout).build()

    @Bean
    fun elasticsearchDocuments(
        properties: ElasticsearchProperties,
        @Qualifier("elasticsearchHttpClient") httpClient: HttpClient,
    ): ElasticsearchDocuments {
        val url = properties.url
        require(
            url.scheme in setOf("http", "https") && url.host != null && url.rawUserInfo == null &&
                url.rawQuery == null && url.rawFragment == null,
        ) {
            "zero.search.url must be an HTTP(S) base URL without credentials, query, or fragment"
        }
        require(properties.index.matches(Regex("[a-z0-9][a-z0-9._-]{0,127}"))) {
            "zero.search.index must be a single lowercase index name"
        }
        val requestFactory = JdkClientHttpRequestFactory(httpClient)
        requestFactory.setReadTimeout(properties.timeout)
        val client = RestClient.builder()
            .baseUrl(url.toString().trimEnd('/'))
            .requestFactory(requestFactory)
        properties.apiKey?.takeIf { it.isNotBlank() }?.let {
            client.defaultHeader(HttpHeaders.AUTHORIZATION, "ApiKey $it")
        }
        return ElasticsearchDocuments(client.build(), properties.index)
    }

    @Bean
    fun elasticsearchHealthIndicator(documents: ElasticsearchDocuments): HealthIndicator = HealthIndicator {
        try {
            val status = documents.clusterStatus()
            val health = if (status == "green" || status == "yellow") Health.up() else Health.down()
            health.withDetail("clusterStatus", status).build()
        } catch (failure: RestClientException) {
            Health.down().withDetail("reason", elasticsearchFailureDetail(failure)).build()
        }
    }
}
