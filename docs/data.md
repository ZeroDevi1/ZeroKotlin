# 数据库、持久化与缓存

默认使用当前工作目录下的 `data/zero-kotlin.db` 和进程内 Caffeine 缓存。启动时创建 SQLite 文件的父目录，随后由 Flyway 迁移结构。

## 数据源

| `zero.database` 配置 | 默认值 | 行为 |
| --- | --- | --- |
| `mode` | `auto` | 支持 `sqlite`、`postgresql`、`mysql`、`auto`。显式模式直接启动对应数据库。 |
| `sqlite.path` | `data/zero-kotlin.db` | SQLite 文件路径。相对路径以应用的工作目录为基准。 |
| `postgresql.url` | 留空 | 格式为 `jdbc:postgresql://host[:port]/database`，默认端口 `5432`。 |
| `postgresql.username`、`postgresql.password` | 留空 | PostgreSQL JDBC 凭证。 |
| `mysql.url` | 留空 | 格式为 `jdbc:mysql://host[:port]/database`，默认端口 `3306`。 |
| `mysql.username`、`mysql.password` | 留空 | MySQL JDBC 凭证。 |
| `probe-timeout` | `1s` | `auto` 模式中每个解析出的 IP 地址的 TCP 连接超时。DNS 查询和随后的 JDBC 登录遵循各自的超时设置。 |

`auto` 依次考虑配置了 URL 的 PostgreSQL、MySQL，最后使用 SQLite。远端 URL 需指定单个主机和数据库名，可附加 JDBC 驱动参数。只有 DNS 解析失败、连接拒绝、路由不可达或 TCP 探测超时时，才继续考虑下一项。发现可建立 TCP 连接的候选后，认证、TLS、数据库访问和迁移错误会使启动失败。在同一进程中固定使用选中的数据库，每次重启重新选择。各数据库分别保存其数据。

显式远端模式适用于固定数据库的部署。将用户名和密码分别从环境或密钥配置注入。

```yaml
zero:
  database:
    mode: postgresql
    postgresql:
      url: jdbc:postgresql://postgres.example:5432/zero_kotlin
      username: ${ZERO_POSTGRES_USER}
      password: ${ZERO_POSTGRES_PASSWORD}
```

切换到 MySQL 时，把 `mode` 和 `postgresql` 配置组都换成 `mysql`，并填入对应 URL 和凭证。SQLite 的 HikariCP 连接池最多持有一个连接，并在新连接上开启 `PRAGMA foreign_keys = ON`。数据库事务应保持简短，SQLite 下避免在持有连接的外层事务中开启需要第二个连接的 `REQUIRES_NEW` 事务。

## Flyway 迁移

在各模块的 `src/main/resources/db/migration/` 下放置 `V<版本>__<描述>.sql`。应用对选中的数据库从运行时类路径汇集以下目录。

| 位置 | 内容 |
| --- | --- |
| `classpath:db/migration/common` | SQLite、PostgreSQL、MySQL 均可执行的 SQL。 |
| `classpath:db/migration/{vendor}` | `{vendor}` 为 `sqlite`、`postgresql` 或 `mysql`。一次启动只加载选中数据库对应的目录。 |

随应用常驻的模块共享 Flyway 的版本序列和 `flyway_schema_history`。版本号必须唯一。需要为三种数据库编写不同 SQL 时，可在三个各自的数据库目录中使用同一版本号。系统模块保有 `V1__system.sql`，后续常驻模块的迁移使用新的版本号。

可移除的业务模块声明一个 `ModuleMigration("orders")` Bean，在 `db/modules/<模块名>/` 内提供以 V1 起始的可移植 SQL。实际名称使用小写英文字母、数字和下划线。主应用先迁移核心表，再迁移已装配的模块。每个模块有独立的 `zero_history_<模块名>` 表，卸载时保留历史和业务数据，重新装配会从该模块的下一版本继续。

Flyway 和应用使用同一个 `DataSource`。`zero-core` 按实际选中的数据库设置目录。配置连接参数时使用 `zero.database`。MyBatis 的 `SqlSessionFactory` 依赖迁移完成后再初始化。

## MyBatis-Plus 与可选 JPA

- 业务模块通过 `implementation(project(":zero-core"))` 使用 MyBatis-Plus Boot 4 starter。将 `@Mapper` 接口放在 `dev.zerokotlin` 下，XML 放在各模块的 `src/main/resources/mapper/` 下。`classpath*:/mapper/**/*.xml` 会把它们加载到同一个 `SqlSessionFactory`，XML `namespace` 使用接口的全限定名。
- 基础 CRUD 可以使用 `BaseMapper`。跨表查询使用 XML Mapper，因为 MPJ 的 `1.5.9` starter 仍以 Boot 3 starter 作为装配依赖，项目采用 Boot 4。在列表方法中传入 `IPage`，分页拦截器按当前 JDBC URL 选择数据库方言。复杂联查可在 XML 中写独立的总数查询，通过分页对象的 `countId` 引用，并为列表排序指定稳定的次序。[MPJ 1.5.9 依赖声明](https://repo.maven.apache.org/maven2/com/github/yulichang/mybatis-plus-join-boot-starter/1.5.9/mybatis-plus-join-boot-starter-1.5.9.pom)
- 业务模块通过 `zero-core-jpa` feature 引入 Spring Data JPA。实体和仓库沿用 Boot 的应用包扫描。声明 Kotlin 实体的模块应用 `alias(libs.plugins.kotlin.jpa)`，由该插件生成无参构造器并开放实体供 Hibernate 代理。Hibernate 使用同一数据源，通过 JDBC 元数据解析方言，SQLite 由相同版本的 `hibernate-community-dialects` 提供支持。表结构由 Flyway 管理，Hibernate DDL 默认 `none`。[Kotlin JPA 插件](https://kotlinlang.org/docs/kotlin-jpa-plugin.html)
- 仅引入 MyBatis 时，Boot 4 默认配置 JDBC 的 `JdbcTransactionManager`。启用 JPA 后，Boot 以 `JpaTransactionManager` 作为单一事务管理器，MyBatis 通过 Spring 管理的 JDBC 连接加入同一事务。在服务方法上使用 Spring 的 `@Transactional`。需要 JDBC 紧接着读取 JPA 的写入时，先调用 `flush()`。同一事务中用 JDBC 更新了已被 JPA 加载的实体时，按需 `refresh()` 或清空持久化上下文后重读。

在使用 JPA 的业务模块 `build.gradle.kts` 中声明该 feature，核心的公共契约和 MyBatis 配置会随之引入。

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
}

dependencies {
    implementation(project(":zero-core")) {
        capabilities { requireCapability("dev.zerokotlin:zero-core-jpa") }
    }
}
```

## 缓存

| `zero.cache` 配置 | 默认值 | 行为 |
| --- | --- | --- |
| `mode` | `auto` | 支持 `auto`、`caffeine`、`redis`。显式模式使用对应实现。 |
| `ttl` | `10m` | 条目自写入起的有效期。 |
| `caffeine.maximum-size` | `10000` | 每个进程内缓存的条目上限。 |
| `redis.host` | 留空 | 单机 Redis 主机名或 IP。显式 `redis` 模式必填。 |
| `redis.port` | `6379` | Redis TCP 端口。 |
| `redis.username`、`redis.password` | 留空 | Redis ACL 凭证。只配置密码时使用 Redis 默认用户。 |
| `redis.database` | `0` | Redis 逻辑库编号。 |
| `redis.tls` | `false` | 启用 TLS 与服务端证书验证。 |
| `redis.timeout` | `2s` | Lettuce 的 TCP 连接及 Redis 命令超时。 |
| `redis.key-prefix` | `zero-kotlin:` | 应用键空间前缀。允许字母、数字、点、下划线、冒号和连字符。 |

`auto` 在未配置 `redis.host` 时使用 Caffeine。配置主机后，启动时向 Redis 发出 `PING`。Redis 可用则选用 Redis，连接拒绝、连接或探测命令超时、地址无法解析时使用 Caffeine。Redis 的认证、证书和其他协议错误会使启动失败。显式 `redis` 模式下，连接失败也会使启动失败。启动选定的缓存实现用于整个进程生命周期。

```yaml
zero:
  cache:
    mode: redis
    redis:
      host: redis.example
      tls: true
      username: ${ZERO_REDIS_USER}
      password: ${ZERO_REDIS_PASSWORD}
      key-prefix: "zero-kotlin:prod:"
```

缓存通过 Spring Cache 的 `CacheManager` 和 `@Cacheable` 等注解访问。两个后端都要求缓存值非空，可用 `unless = "#result == null"` 跳过空结果。Redis 键采用 `<key-prefix><cache-name>::<key>`，值使用 Jackson JSON，支持常见标量、集合和 `dev.zerokotlin` 下的可序列化 DTO。应用的 Redis 用户需要使用 `PING`、键读写和扫描移除缓存键等命令的权限。系统角色、数据范围和授权检查以数据库为准。

## AOT

Spring AOT 根据构建时类路径和自动配置条件生成 Bean 定义。数据库和缓存的 `mode`、连接地址和凭证都在运行时绑定，TCP 探测、SQLite 文件操作、Flyway 迁移和 Redis `PING` 在运行时创建相应 Bean 时发生。JPA 的装配由构建产物是否包含 `zero-core-jpa` 决定。Flyway 使用 Boot 的 `db/migration/**` 资源提示，MyBatis-Plus starter 提供 Mapper AOT 集成。若为原生镜像引入自定义缓存 DTO，还需覆盖这些 DTO 的 Jackson 反射元数据。
