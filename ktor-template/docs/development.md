# Ktor 版开发指南

Ktor + Netty 负责 HTTP 和 WebSocket，JDBC、HikariCP 与 Flyway 负责数据访问和迁移。Spring Security Crypto 仅用于兼容原 BCrypt 口令哈希，不启动 Spring 容器。

## 启动与目录

| 使用场景 | 后端启动（JDK 25） | 管理端目录 | SQL 来源 |
| --- | --- | --- | --- |
| 在 ZeroKotlin 仓库中修改 Ktor 模板 | 仓库根目录执行 `./gradlew -p ktor-template run` | `zero-web/` | `zero-system/resources/db/`，构建时打包 |
| 在 `init` 生成的项目中开发 | 项目根目录执行 `./gradlew run` | `web/` | `src/main/resources/db/`，由 CLI 复制 |

Windows PowerShell 把 `./gradlew` 换成 `.\gradlew.bat`。另开终端，在管理端目录执行 `pnpm install --frozen-lockfile` 和 `pnpm dev`，使用 Node.js 22.12 或更新版本。

- **同一个访问前缀必须贯穿浏览器、Vite 和 Ktor。** 模板工作树使用 `/`；`init --context-url /apps/orders` 会同时写入 Ktor 的 `app.contextPath` 和 Vite 的 `contextPath`。此时前端入口为 `http://127.0.0.1:5173/apps/orders/`，Ktor 就绪探针为 `http://127.0.0.1:8080/apps/orders/actuator/health`。反代应保留该前缀；Vite 开发代理可用 `ZERO_WEB_API_TARGET` 指向其他后端。
- **默认管理员由 SQL 创建，不读管理员环境变量。** 空用户表首次应用 `db/migration/common/V9__initial_administrator.sql` 时插入 `admin / admin`，授予 `system-admin`。SQL 只写 BCrypt 哈希。V9 不给已有用户补授管理员角色；已执行过的 Flyway 版本也不会重跑。不要事后修改已在数据库上执行的 V9，否则会导致校验和不一致。默认账号只用于本地首次登录；开放网络访问前改密并确认旧默认密码无法再登录。
- **存活与就绪分开。** `GET /health` 不检查依赖；`GET /actuator/health` 检查数据库和 Redis 状态，依赖不可用时返回 503。这两个路径以及下文的 API 路径都在配置的访问前缀下。

## 配置

Ktor 读取 [application.conf](../src/main/resources/application.conf)。相对数据库和文件路径从后端进程工作目录解析。Spring Boot 的 `application*.yaml` 与 `SPRING_PROFILES_ACTIVE` 对 Ktor 不生效。

| 目标 | 配置与默认行为 |
| --- | --- |
| 地址和 API 文档 | `ZERO_HOST=127.0.0.1`，`ZERO_PORT=8080`；`ZERO_API_DOCS_ENABLED=false` 同时关闭 OpenAPI 与 Swagger UI。 |
| 数据库 | `ZERO_DATABASE_MODE=auto\|sqlite\|postgresql\|mysql`。自动模式按 PostgreSQL、MySQL、SQLite 顺序选择；外库未配置或报告连接类故障时才继续，凭据或迁移错误不当成回退条件。生产建议显式指定数据库模式。 |
| 数据库地址 | SQLite 默认 `data/zero-ktor.db`，用 `ZERO_DATABASE_SQLITE_PATH` 覆盖；远端使用 `ZERO_DATABASE_POSTGRESQL_URL`（或 `ZERO_DATABASE_MYSQL_URL`）及同前缀的 `_USERNAME`、`_PASSWORD`。URL 须含 `jdbc:` 前缀。数据库连接密码与管理台登录密码无关。 |
| 会话和验证码 | `ZERO_SYSTEM_SESSION_SECONDS=28800`，`ZERO_SYSTEM_CAPTCHA_ENABLED=false`，`ZERO_SYSTEM_CAPTCHA_TTL_SECONDS=120`。会话在数据库中保存令牌摘要，验证码和限流由安全状态后端处理。 |
| 安全状态 | `ZERO_CACHE_MODE=auto\|caffeine\|redis`。Redis 使用 `ZERO_CACHE_REDIS_HOST`、`_PORT`、`_USERNAME`、`_PASSWORD`、`_DATABASE`、`_TLS`、`_KEY_PREFIX`。Caffeine 的验证码和限流状态只存在于当前实例，多实例需要共享状态时显式选 Redis。 |
| 文件 | `ZERO_FILES_MODE=local\|minio\|aliyun`。本地默认 `data/ktor-files`，可用 `ZERO_FILES_LOCAL_PATH` 修改。对象存储字段见配置文件。既有文件根据数据库记录的后端和位置访问；换工作目录、路径或桶不会迁移对象。文件最多 50 MiB，整个 multipart 请求最多 51 MiB；反代上传上限也要与之对齐。 |
| 扩展 | `ZERO_SEARCH_ENABLED` 与 `ZERO_KAFKA_ENABLED` 默认 false，未启用时不会注册对应路由；其余配置在 `extensions` 中。 |
| 文本清理 | `ZERO_SECURITY_XSS_ENABLED=true` 清理个人显示名称、账号显示名称和公告标题；不是全局请求过滤器，界面仍应按输出上下文转义。 |

## 接口与代码入口

| 场景 | 入口与边界 |
| --- | --- |
| 登录与权限 | `POST /api/auth/login` 接收 JSON 的 `username` 和 `password`；开启验证码时先取 `GET /api/auth/captcha`，登录时一并提交 `captchaId` 和 `captchaCode`。用返回的 `accessToken` 作 `Authorization: Bearer <token>`，调用 `GET /api/auth/me` 查看用户与权限，`PUT /api/auth/me/password` 修改密码。 |
| 接口文档 | `/v3/api-docs`、`/v3/api-docs.yaml`、`/swagger-ui/`。路由与已注册功能以当前 Ktor 版本为准；根目录原 Spring Boot 文档不能替代这几个端点。 |
| 路由与数据权限 | [Application.kt](../src/main/kotlin/dev/zerokotlin/ktor/Application.kt) 装配应用，[SystemRoutes.kt](../src/main/kotlin/dev/zerokotlin/ktor/SystemRoutes.kt) 挂载系统路由，[SystemStore.kt](../src/main/kotlin/dev/zerokotlin/ktor/SystemStore.kt) 提供事务、会话、权限与审计。业务需要限制行级可见范围时，须把过滤条件写进实际查询；接口权限检查不会自动过滤业务表。 |
| 实时消息 | `/api/realtime/sse/demo` 为 SSE 演示，支持 `Last-Event-ID`；`/api/realtime/ws` 使用 `v12.stomp`，在 STOMP CONNECT 发送 Bearer 令牌。实时订阅与在线人数仅在当前进程内统计；消息不落库、不重放，慢连接可能丢消息。反代需要允许 WebSocket 升级，并关闭 SSE 响应缓冲。 |
| 调度 | Quartz 用于计算触发时间，不运行 Quartz Scheduler。Ktor 轮询数据库中到期的任务和手动队列，在写锁内复核到期记录。不能把原 Quartz Job、监听器或配置当作 Ktor 插件复用。 |

## 生成业务 CRUD

在生成项目的根目录执行，下例先预览文件：

```sh
./gradlew codegen --args='--module . --package dev.zerokotlin.orders --table orders_order --dry-run'
```

提供 `--jdbc-url` 或 `ZERO_CODEGEN_JDBC_URL`，外库凭据使用 `ZERO_CODEGEN_DB_USERNAME` 与 `ZERO_CODEGEN_DB_PASSWORD`。在模板仓库开发时给命令加 `-p ktor-template`。去掉 `--dry-run` 后生成，生成器不会改数据库或覆盖已有文件。

1. 将生成的 `register…Routes(store)` 接到 `GeneratedResource.kt` 中的 `businessRoutes`，沿用 `/api` 下的访问前缀。
2. 将业务 SQL 放在资源目录的 `db/modules/<name>/`，通过 `ZERO_BUSINESS_MODULES` 启用，多个模块用逗号分隔。模块各自使用 `zero_history_<name>` 记录迁移。
3. 按生成结果将权限编码加入业务迁移，并分配给角色。对有归属或租户的数据，在业务查询中补上相应的数据范围和写入校验。

## 打包与排查

- 生成项目在根目录执行 `./gradlew installDist`；在模板仓库执行 `./gradlew -p ktor-template installDist`。在相应项目的 `build/install/<项目名>/bin/` 中使用启动脚本，运行环境仍需 JDK 25。
- 管理端执行 `pnpm build`，部署该前端目录下的 `dist/`。同站点反代须优先匹配访问前缀下的 `/api/`、探针及启用的文档端点，其他页面路径再交给前端的 SPA 回退。跨实例或经反代部署时，额外核对 WebSocket 的 Host/Origin 与升级头。
- Gradle 找不到 JDK 25 时，核对 `JAVA_HOME`、`java -version` 和 IDE 的 Gradle JVM。页面访问返回 404 时，先直接请求带前缀的后端探针，再核对 Vite 转发目标和反代路径。探针返回 503 时，检查数据库连接及已配置的 Redis。已有库无法用默认账号登录时，不要删除数据库或重跑 V9，使用现有管理员账号。

## 从 Spring Boot 迁移

共用的是 Flyway 脚本、表结构和 Vue 工作台。不要直接用生产库做验收：先用副本检查 Flyway 历史、已有账号登录、角色数据范围、文件位置及调度状态，再结合目标数据库、对象存储和反代验证上线配置。切换后端不会复制数据库内容或移动文件对象。

Spring 自动装配、MyBatis XML、JPA 扩展示例、`zero-agent` 示例和原 GraalVM Native Image 配置不是 Ktor 运行时的一部分。已有业务模块需要改成显式的 Ktor 路由和 JDBC 实现，不能只复制 Spring 注解或配置。
