# GraalVM Native Image

首个原生构建的目标是使用 SQLite、Caffeine 和系统管理 API，覆盖登录、授权、菜单及部门查询。该组合的构建与运行仍待实测。

## 构建

使用 GraalVM JDK 25，并在 `JAVA_HOME` 指定该 JDK。系统需要该平台的 Native Image 编译工具链。

```bash
./gradlew :zero-system:nativeCompile
```

生成路径为 `zero-system/build/native/nativeCompile/zero-kotlin`，Windows 可执行文件带 `.exe`。仅需生成 Spring AOT 元数据时，可执行 `./gradlew :zero-system:processAot`。

Gradle 为 `processAot` 指定 SQLite 和 Caffeine。处理器在构建时准备 Bean 定义和 Native Image 元数据，数据库连接、Flyway 迁移和首次管理员初始化属于应用运行期。默认构建只装配 `zero-core` 主变体和 `zero-system`。

## 元数据

- MyBatis-Plus Boot 4 starter 提供 Native Image 与 Spring AOT 集成。构建期扫描 `@Mapper` Bean，并为 MyBatis 代理以及从 Mapper 方法签名可达的模型生成反射信息。
- `zero-core` 登记默认配置、`mapper/**` 和 Flyway 迁移资源，保留运行期读取的配置、SQL 和 XML。`zero-system` 为系统 Mapper 的实体和查询对象登记反射提示。XML 若引用无法从 Mapper 签名推导的类，应为这些类补充反射提示。
- SQLite JDBC 使用驱动自带的 GraalVM Feature。Spring Boot Flyway 自动配置提供原生镜像中的迁移资源解析。
- Native Build Tools 默认使用 GraalVM 元数据仓库，其中包含 Caffeine 的动态缓存实现元数据。

## 运行和核对

```bash
ZERO_DATABASE_MODE=sqlite ZERO_CACHE_MODE=caffeine \
  ./zero-system/build/native/nativeCompile/zero-kotlin
```

数据库默认写入 `data/zero-kotlin.db`，可用 `ZERO_DATABASE_SQLITE_PATH` 指定可写位置。核心与可选模块有各自的 Flyway 历史表。首次管理员配置见 [系统说明](system.md)。

可先检查 `/actuator/health`，再配置管理员并验证登录、菜单和部门列表。部门和用户查询用于覆盖 Mapper 代理、XML 结果映射和 SQLite 数据加载。确认重启后 SQLite 中的数据仍可读取。

支持范围为上述最小组合。PostgreSQL、MySQL、Redis、Hibernate JPA、Elasticsearch 和 Kafka 的部署与接入使用 JVM 包。另行调整 Native Image 的构建组合时，按对应依赖和 Bean 条件补充元数据及运行验证。

[Spring Boot Native Image](https://docs.spring.io/spring-boot/reference/packaging/native-image/introducing-graalvm-native-images.html) · [SQLite JDBC Native Image 支持](https://github.com/xerial/sqlite-jdbc#graalvm-native-image-support)
