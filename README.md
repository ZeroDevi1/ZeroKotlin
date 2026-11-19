# ZeroKotlin

ZeroKotlin 是基于 Kotlin 与 Spring Boot 的模块化后端脚手架，面向需要用户、角色、部门和数据权限的业务系统。默认部署目标是 SQLite 文件与本地缓存，可按需接入外部数据库和中间件。

## 工程

使用 JDK 25 和仓库自带的 Gradle Wrapper。Spring Boot 与 Kotlin 工具链版本集中在 `gradle/libs.versions.toml`。

| 模块 | 职责 |
| --- | --- |
| zero-core | 当前用户、分页等公共业务契约 |
| zero-system | 身份、授权、部门、字典与审计 |
| zero-data-mybatis / zero-data-jpa | 数据源、迁移与持久化接入 |
| zero-cache | 本地与 Redis 缓存 |
| zero-search-elasticsearch / zero-messaging-kafka | 搜索与消息扩展 |
| modules/example | 可移除的业务模块示例 |
| zero-app | 应用启动与模块装配 |

设计参考 [JeecgBoot](https://github.com/jeecgboot/JeecgBoot) 的组织和权限模型，以及 [zeta-kotlin](https://github.com/xia5800/zeta-kotlin) 的 Kotlin 业务模块划分。
