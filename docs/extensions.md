# 可选能力与业务模块

Elasticsearch 与 Kafka 通过 `application.conf` 的运行配置启用，默认关闭。下例将通过系统登录取得的令牌保存在 `TOKEN`，URL 使用默认的根访问前缀。[系统登录](system.md)

## Elasticsearch

使用 Elasticsearch HTTP 接口。服务账号需要配置索引的读写权限。使用预建索引，或由管理员授予自动创建索引所需的权限。建议为示例使用专用索引，并将 `title` 与 `content` 映射为 `text`。

| 配置 | 默认值或含义 |
| --- | --- |
| `ZERO_SEARCH_ENABLED` | `false` |
| `ZERO_SEARCH_URL` | `http://127.0.0.1:9200`，部署时使用受 JVM 信任的 HTTPS |
| `ZERO_SEARCH_INDEX` | `zero-documents` |
| `ZERO_SEARCH_API_KEY` | 可选，Elasticsearch 编码后的 API Key |

设置 `ZERO_SEARCH_ENABLED=true` 后启用。首次启用时，模块迁移注册 `extensions:search:write` 和 `extensions:search:read`。系统管理员拥有已启用权限，其他账号通过角色获权。

```bash
curl -X PUT http://localhost:8080/api/extensions/search/documents/note-1 \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"SQLite notes","content":"An optional search example"}'

curl -G http://localhost:8080/api/extensions/search/documents \
  -H "Authorization: Bearer $TOKEN" --data-urlencode 'q=SQLite'
```

写入请求等待索引刷新后返回。查询匹配 `title` 和 `content`，最多返回 20 条，返回 Elasticsearch 的搜索结果。索引与查询的上游失败返回 HTTP 502，响应包含上游 HTTP 状态或异常类型。

[Elasticsearch 索引刷新参数](https://www.elastic.co/docs/reference/elasticsearch/rest-apis/refresh-parameter)

## Kafka

预先创建示例主题并准备具备主题读写、描述及消费组访问权限的 Kafka 账号。开启幂等发布所需的集群权限取决于代理端版本与 ACL 配置。

| 配置 | 默认值或含义 |
| --- | --- |
| `ZERO_KAFKA_ENABLED` | `false` |
| `ZERO_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092`，可配置多个地址 |
| `ZERO_KAFKA_TOPIC` | `zero-example` |
| `ZERO_KAFKA_GROUP_ID` | `zero-example` |
| `ZERO_KAFKA_TIMEOUT_SECONDS` | `8s`，支持 1 至 30 秒，单次轮询或等待 Kafka 响应的时间 |
| `extensions.kafkaClientProperties` | 可选 Kafka 客户端属性，供代理 TLS 和 SASL 配置使用 |

设置 `ZERO_KAFKA_ENABLED=true` 后启用。模块迁移注册 `extensions:kafka:publish` 和 `extensions:kafka:consume`，其他账号通过角色获权。以下配置从部署环境读取 SASL 凭证。

```hocon
extensions.kafkaClientProperties {
    "security.protocol" = "SASL_SSL"
    "sasl.mechanism" = "SCRAM-SHA-512"
    "sasl.jaas.config" = ${?ZERO_KAFKA_SASL_JAAS_CONFIG}
}
```

```bash
curl -X POST http://localhost:8080/api/extensions/kafka/messages \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"key":"note-1","value":"A Kafka example"}'

curl -X POST http://localhost:8080/api/extensions/kafka/consume \
  -H "Authorization: Bearer $TOKEN"
```

发布等待代理确认后返回分区和偏移量。消费接口使用配置的消费组，单次轮询最多 20 条并同步提交偏移量，再返回记录。没有已提交偏移量时从最早可用记录开始。可使用独立示例主题和消费组，避免检索到无关消息或推进其他业务的消费位置。偏移提交与 HTTP 送达是独立操作，客户端重试可能读到下一批。发布确认超时后结果可能未知，重复提交请求可能产生重复消息。

代理访问、鉴权、未在轮询期限内获得分区及提交故障返回 HTTP 502。没有可用消息且已获得分区时返回空列表。

[KafkaConsumer 轮询与提交偏移说明](https://kafka.apache.org/42/javadoc/org/apache/kafka/clients/consumer/KafkaConsumer.html)

## 业务模块

使用 `ZERO_BUSINESS_MODULES` 启用自有模块的迁移。路由、权限和菜单接入见[开发业务功能](new-module.md)。
