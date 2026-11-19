package dev.zerokotlin.core.config

import dev.zerokotlin.core.controller.ElasticsearchExampleController
import dev.zerokotlin.core.service.ElasticsearchDocuments
import dev.zerokotlin.core.service.elasticsearchFailureDetail
import java.net.URI
import java.net.http.HttpClient
import java.time.Duration
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
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

/**
 * `zero.search` 下的 Elasticsearch HTTP 连接与索引配置。
 *
 * @property url HTTP(S) 基础地址，默认 `http://localhost:9200`，须含主机，且不可携带凭证、查询串或片段。
 * @property index 单个索引名，默认 `zero-example`。长度为 1 至 128，首字符为小写英文字母或数字，
 *   其余字符还可使用点、下划线或连字符。
 * @property timeout 连接和读取各自使用的超时，默认 5 秒。
 * @property apiKey Elasticsearch 编码后的 API Key，默认未配置。非空白时用作 `Authorization: ApiKey` 凭证。
 */
@ConfigurationProperties("zero.search")
class ElasticsearchProperties(
    val url: URI = URI.create("http://localhost:9200"),
    val index: String = "zero-example",
    val timeout: Duration = Duration.ofSeconds(5),
    val apiKey: String? = null,
)

/** 搜索功能启用时注册 HTTP 客户端、文档服务、健康探测和示例接口。 */
@AutoConfiguration
@ConditionalOnZeroSearch
@EnableConfigurationProperties(ElasticsearchProperties::class)
@Import(ElasticsearchExampleController::class)
class ElasticsearchConfiguration {
    /**
     * 为 Elasticsearch 建立带连接超时的 JDK HTTP 客户端。
     *
     * @param properties 提供建立连接时使用的超时。
     * @return 由 Spring 在 Bean 销毁时关闭的 HTTP 客户端。
     */
    @Bean(destroyMethod = "close")
    fun elasticsearchHttpClient(properties: ElasticsearchProperties): HttpClient =
        HttpClient.newBuilder().connectTimeout(properties.timeout).build()

    /**
     * 校验基础地址与索引名，并设置读取超时和可选的 API Key 请求头。
     *
     * @param properties 提供 Elasticsearch 地址、索引、读取超时和可选凭证。
     * @param httpClient 负责实际 HTTP 连接的 Elasticsearch 专用客户端。
     * @return 绑定到指定索引的 Elasticsearch 文档服务。
     */
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

    /**
     * 用集群健康接口判断 Elasticsearch 是否可用。
     *
     * @param documents 提供集群状态查询的文档服务。
     * @return `green` 或 `yellow` 报告 UP，其余状态或请求失败报告 DOWN 的健康指示器。
     */
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
