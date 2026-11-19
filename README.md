# ZeroKotlin

ZeroKotlin 是基于 Kotlin 与 Spring Boot 的模块化后端脚手架，提供用户、角色、部门、菜单、数据范围与审计 API。默认使用 SQLite 文件和 Caffeine 本地缓存，可按需接入 PostgreSQL、MySQL、Redis、Elasticsearch 和 Kafka。

## 首次启动

安装 JDK 25。在仓库根目录的 Bash 或 Zsh 中执行以下命令。密码至少为 12 个字符，最多 72 个 UTF-8 字节。

```sh
export ZERO_SYSTEM_ADMIN_USERNAME=admin
printf '首次管理员密码（输入时隐藏） '
read -rs ZERO_SYSTEM_ADMIN_PASSWORD
printf '\n'
export ZERO_SYSTEM_ADMIN_PASSWORD
./gradlew :zero-app:bootRun
```

应用在空用户表中创建首个管理员。后续启动可以移除这两个环境变量。运行期间可在 [Swagger UI](http://localhost:8080/swagger-ui/index.html) 发送 `POST /api/auth/login`，用返回的 `accessToken` 在 **Authorize** 中授权，再调用 `GET /api/auth/me` 查看当前用户及权限。健康状态位于 [Actuator Health](http://localhost:8080/actuator/health)。

## 工程

工程锁定 Kotlin 2.4.21、Spring Boot 4.1.2 和 Gradle 9.7.0。Kotlin 编译插件与运行库 BOM 共用一个版本号。[Gradle 版本目录](gradle/libs.versions.toml)

| 模块 | 职责 |
| --- | --- |
| zero-core | 公共契约、数据源、Flyway、MyBatis-Plus、缓存、Bearer 鉴权框架、OpenAPI 和 Native 资源提示。JPA、Elasticsearch、Kafka 由同一项目的 Gradle feature 提供。 |
| zero-system | 账号、角色、权限、部门、菜单、字典、令牌会话和审计 |
| modules/example | 可移除的业务模块示例 |
| zero-app | 应用启动与模块装配 |

## 配置与二次开发

| 场景 | 入口 |
| --- | --- |
| 选择数据库、配置 Redis、添加 MyBatis 或 JPA 访问层 | [数据源、迁移与缓存](docs/data.md) |
| 调用登录、角色、部门、行级权限和审计接口 | [系统 API 和权限表](docs/system.md) |
| 添加可卸载模块、启用搜索或收发 Kafka 消息 | [扩展指南与请求示例](docs/extensions.md) |
| 用 GraalVM 25 构建 SQLite 与 Caffeine 的最小原生应用 | [Native 构建配置与待验证范围](docs/native.md) |

数据库自动模式只探测已配置的 PostgreSQL、MySQL 地址，均不可达时使用 SQLite。缓存自动模式优先使用已配置且可达的 Redis。数据源与缓存后端在本次进程内固定，切换数据库只改变读写目标，各库独立保存数据。

Web、JPA、健康端点和 Swagger UI 的通用默认值位于 `zero-core/src/main/resources/zero-core-defaults.properties`。部署时可通过应用配置、环境变量或命令行参数覆盖。

工程划分参考 JeecgBoot 将共享基础设施集中到 `jeecg-boot-base-core` 的做法。组织与权限模型参考 JeecgBoot，Kotlin 业务模块划分参考 zeta-kotlin。[JeecgBoot](https://github.com/jeecgboot/JeecgBoot/tree/main/jeecg-boot/jeecg-boot-base-core) · [zeta-kotlin](https://github.com/xia5800/zeta-kotlin)
