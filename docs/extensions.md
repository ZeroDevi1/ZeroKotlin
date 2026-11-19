# 可选能力与业务模块

`zero-app` 默认装配 `zero-core` 主变体和 `zero-system`。以下 Gradle 属性可以组合使用，接受 `true` 或 `false`。

| 构建属性 | JVM 应用内容 | 运行开关 |
| --- | --- | --- |
| `-PwithSearch=true` | Elasticsearch 客户端及示例接口 | `zero.search.enabled`，默认 `false` |
| `-PwithKafka=true` | Kafka 客户端及示例接口 | `zero.kafka.enabled`，默认 `false` |

```bash
./gradlew :zero-app:bootJar -PwithSearch=true -PwithKafka=true
```

Elasticsearch 和 Kafka feature 的运行开关同时控制客户端、接口和健康贡献器。`zero-system` 随对应 feature 启用而注册权限种子迁移。JVM 应用需要在打包时选择 feature，并在启动时打开对应的配置才能启用。以下请求使用通过系统登录获取的 Bearer Token，示例假设 `TOKEN` 已设为当前访问令牌。[系统登录](system.md)

## Elasticsearch

使用 Elasticsearch HTTP 接口。服务账号需要配置索引的读写权限以及查询集群健康的权限。使用预建索引，或由管理员授予自动创建索引所需的权限。建议为示例使用专用索引，并将 `title` 与 `content` 映射为 `text`。

| 配置 | 默认值或含义 |
| --- | --- |
| `zero.search.enabled` | `false` |
| `zero.search.url` | `http://localhost:9200`，部署时使用受 JVM 信任的 HTTPS |
| `zero.search.index` | `zero-example` |
| `zero.search.timeout` | `5s`，连接和请求各自的超时 |
| `zero.search.api-key` | 可选，Elasticsearch 编码后的 API Key，可通过 `ZERO_SEARCH_APIKEY` 注入 |

设置 `ZERO_SEARCH_ENABLED=true` 后启用。首次启用时，模块迁移注册 `extensions:search:write` 和 `extensions:search:read`。系统管理员拥有已启用权限，其他账号通过角色获权。

```bash
curl -X PUT http://localhost:8080/api/extensions/search/documents/note-1 \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"SQLite notes","content":"An optional search example"}'

curl -G http://localhost:8080/api/extensions/search/documents \
  -H "Authorization: Bearer $TOKEN" --data-urlencode 'q=SQLite'
```

写入请求等待索引刷新后返回。查询匹配 `title` 和 `content`，最多返回 20 条，返回 Elasticsearch 的搜索结果。索引与查询的上游失败返回 HTTP 502，响应包含上游 HTTP 状态或异常类型。启用时 Elasticsearch 贡献器参与 `/actuator/health` 聚合，集群状态为 red 或探测失败时报告 DOWN。

[Elasticsearch 索引刷新参数](https://www.elastic.co/docs/reference/elasticsearch/rest-apis/refresh-parameter)

## Kafka

预先创建示例主题并准备具备主题读写、描述及消费组访问权限的 Kafka 账号。开启幂等发布所需的集群权限取决于代理端版本与 ACL 配置。

| 配置 | 默认值或含义 |
| --- | --- |
| `zero.kafka.enabled` | `false` |
| `zero.kafka.bootstrap-servers` | `localhost:9092`，可配置多个地址 |
| `zero.kafka.topic` | `zero-example` |
| `zero.kafka.group-id` | `zero-example` |
| `zero.kafka.timeout` | `8s`，支持 1 至 30 秒，单次轮询或等待 Kafka 响应的时间 |
| `zero.kafka.client-properties` | 可选 Kafka 客户端属性，供代理 TLS 和 SASL 配置使用 |

设置 `ZERO_KAFKA_ENABLED=true` 后启用。模块迁移注册 `extensions:kafka:publish` 和 `extensions:kafka:consume`，其他账号通过角色获权。以下配置从部署环境读取 SASL 凭证。

```yaml
zero:
  kafka:
    client-properties:
      "[security.protocol]": SASL_SSL
      "[sasl.mechanism]": SCRAM-SHA-512
      "[sasl.jaas.config]": "${ZERO_KAFKA_SASL_JAAS_CONFIG}"
```

```bash
curl -X POST http://localhost:8080/api/extensions/kafka/messages \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"key":"note-1","value":"A Kafka example"}'

curl -X POST http://localhost:8080/api/extensions/kafka/consume \
  -H "Authorization: Bearer $TOKEN"
```

发布等待代理确认后返回分区和偏移量。消费接口使用配置的消费组，单次轮询最多 20 条并同步提交偏移量，再返回记录。没有已提交偏移量时从最早可用记录开始。可使用独立示例主题和消费组，避免检索到无关消息或推进其他业务的消费位置。偏移提交与 HTTP 送达是独立操作，客户端重试可能读到下一批。发布确认超时后结果可能未知，重复提交请求可能产生重复消息。

代理访问、鉴权、未在轮询期限内获得分区及提交故障返回 HTTP 502。没有可用消息且已获得分区时返回空列表。启用时 Kafka 贡献器用主题元数据参与 `/actuator/health` 聚合，主题不存在或无法查询元数据时报告 DOWN。

[KafkaConsumer 轮询与提交偏移说明](https://kafka.apache.org/42/javadoc/org/apache/kafka/clients/consumer/KafkaConsumer.html)

## 添加或移除自己的模块

1. 在 `settings.gradle.kts` 包含新子项目，并在启动模块增加该项目的依赖或构建开关。
2. 在子项目中依赖 `zero-core`，在 `dev.zerokotlin` 下定义控制器、服务和 Mapper。使用 JPA 的模块声明 `zero-core-jpa` feature。[JPA 依赖配置](data.md#mybatis-plus-与可选-jpa)
3. 声明一个 `ModuleMigration("orders")` Bean，在 `src/main/resources/db/modules/orders/V1__orders.sql` 放置业务表和权限种子。需要菜单时将 `sys_menu.module_name` 设为 `orders`。每个模块独立编号。
4. 在服务方法上使用 `@PreAuthorize` 执行权限检查。通过 `CurrentActor` 获取用户 ID，并在写事务中调用 `OperationAudit.record(permission, targetId)` 记录业务操作。

需要直接调用 Elasticsearch 或 Kafka 的业务模块可以在自己的 `build.gradle.kts` 中声明对应的 feature。下例同时选择两项，可按实际用途保留所需依赖。

```kotlin
dependencies {
    implementation(project(":zero-core")) {
        capabilities { requireCapability("dev.zerokotlin:zero-core-elasticsearch") }
    }
    implementation(project(":zero-core")) {
        capabilities { requireCapability("dev.zerokotlin:zero-core-kafka") }
    }
}
```

装配业务模块时，这些 feature 随依赖进入应用包。注入 `ElasticsearchDocuments` 或 `KafkaMessages` 的组件应使用对应的 `zero.search.enabled` 或 `zero.kafka.enabled` 条件，以便在关闭中间件时启动。Gradle 会同时引入 `zero-core` 主变体。[Gradle feature 变体](https://docs.gradle.org/current/userguide/how_to_create_feature_variants_of_a_library.html)

卸载业务模块时，从启动工程移除其依赖。该模块的菜单会从用户导航中隐藏，模块数据和独立迁移历史保留。以后重新装配同名模块时，Flyway 会从已记录版本继续迁移。
