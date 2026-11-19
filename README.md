# ZeroKotlin

ZeroKotlin 是基于 Kotlin 与 Spring Boot 的模块化脚手架，配套 Vue 3 + TypeScript 管理端。它提供组织与岗位管理、角色与数据权限、字典、审计、公告发布和个人收件箱。默认使用 SQLite 文件和 Caffeine 本地缓存，可按需接入 PostgreSQL、MySQL、Redis、Elasticsearch 和 Kafka。

## 首次启动

项目使用 JDK 25。已使用 vfox 的开发者可在仓库根目录安装并选定项目版本。首次使用 Java 插件时先执行 `vfox add java`。

```sh
vfox install java@25.0.5-tem
vfox use --project java@25.0.5+8-tem
./gradlew -q :zero-system:javaToolchains
```

Gradle 会从 `.vfox/sdks/java` 查找本项目的工具链，也支持自动发现已安装的其他 JDK 25。工具链报告应显示 `Language Version: 25` 和 `Is JDK: true`。

macOS 上，若旧版 vfox Java 插件留下空的 `Contents/Home`，Gradle 会在该目录中查找 `bin/java`。确认 `.vfox/sdks/java/bin/javac` 存在后，可执行 `rmdir .vfox/sdks/java/Contents/Home` 移除空目录，再刷新 Gradle 项目。

准备好 JDK 后，在仓库根目录的 Bash 或 Zsh 中设置首次管理员密码。密码至少为 12 个字符，最多 72 个 UTF-8 字节。

```sh
export ZERO_SYSTEM_ADMIN_USERNAME=admin
printf '首次管理员密码（输入时隐藏） '
read -rs ZERO_SYSTEM_ADMIN_PASSWORD
printf '\n'
export ZERO_SYSTEM_ADMIN_PASSWORD
./gradlew :zero-system:bootRun
```

应用在空用户表中创建首个管理员。后续启动可以移除这两个环境变量。运行期间可在 [Swagger UI](http://localhost:8080/swagger-ui/index.html) 发送 `POST /api/auth/login`，用返回的 `accessToken` 在 **Authorize** 中授权，再调用 `GET /api/auth/me` 查看当前用户及权限。健康状态位于 [Actuator Health](http://localhost:8080/actuator/health)。

## 启动管理端

后端启动后，在另一个终端进入 `zero-web`。使用 Node.js 22.12 或更高版本和 pnpm，在该目录执行以下命令。

```sh
pnpm install --frozen-lockfile
pnpm dev
```

打开终端显示的地址（默认 [localhost:5173](http://localhost:5173)），使用后端初始化的账号登录。开发服务器将 `/api` 转发至 `http://127.0.0.1:8080`，可通过 `ZERO_WEB_API_TARGET` 调整。生产部署说明见[管理端文档](zero-web/README.md)。

## 工程

工程锁定 Kotlin 2.4.21、Spring Boot 4.1.2 和 Gradle 9.7.0。Kotlin 编译插件与运行库 BOM 共用一个版本号。[Gradle 版本目录](gradle/libs.versions.toml)

| 模块 | 职责 |
| --- | --- |
| zero-core | 公共契约、数据源、Flyway、MyBatis-Plus、缓存、Bearer 鉴权框架、OpenAPI 和 Native 资源提示。Gradle feature 控制 JPA、Elasticsearch 和 Kafka 的可选装配。 |
| zero-system | Spring Boot 启动入口、模块装配，以及账号、组织、权限、公告和审计等系统业务 |
| [zero-agent](zero-agent/README.md) | AgentScope Java v2 示例库。演示新模块如何依赖 `zero-core`，以及如何通过 `-PwithAgent=true` 加入启动包。 |
| zero-web | Vue 3 + TypeScript 管理端，使用 Vite 开发与打包 |

后端按职责组织 Kotlin 包。系统模块的 [controller](zero-system/src/dev/zerokotlin/system/controller) 接收 HTTP 请求，[service](zero-system/src/dev/zerokotlin/system/service) 处理业务和事务，[mapper](zero-system/src/dev/zerokotlin/system/mapper) 与 [entity](zero-system/src/dev/zerokotlin/system/entity) 负责持久化。接口模型在 `dto`，Spring 配置在 `config`。新增功能沿用这些目录。后端模块的 Kotlin 源码根都是 `src/`，包 `dev.zerokotlin.core` 对应 `zero-core/src/dev/zerokotlin/core/`。配置、Mapper XML 和 Flyway SQL 放在模块的 `resources/`。

从独立启动模块升级时，若已有 SQLite 文件位于旧启动目录，可用 `ZERO_DATABASE_SQLITE_PATH` 指定该文件的绝对路径，继续使用原有账号和业务数据。

## 配置与二次开发

| 场景 | 入口 |
| --- | --- |
| 选择数据库、配置 Redis、添加 MyBatis 或 JPA 访问层 | [数据源、迁移与缓存](docs/data.md) |
| 学习 Kotlin 函数、集合操作、类型选择和 KDoc | [Kotlin 后端开发手册](docs/kotlin-development.md) |
| 创建只依赖 `zero-core` 的业务模块，接入权限、迁移和 Vue 页面 | [新建业务模块](docs/new-module.md) |
| 用 Swagger UI 调试接口、获取 OpenAPI JSON 或 YAML、为新接口添加说明 | [Swagger UI 与 OpenAPI](docs/openapi.md) |
| 上传、检索、下载和删除本地或对象存储中的文件 | [文件管理](docs/files.md) |
| 发送群发消息、私信及查看在线人数 | [实时消息](docs/realtime.md) |
| 下载模板、导入或导出用户、字典和字典项 | [Excel 操作](docs/excel.md) |
| 配置验证码、按 IP 或用户限流、输入清理与字段脱敏 | [安全控制](docs/security-controls.md) |
| 配置任务计划、立即执行和查看运行日志 | [Quartz 任务调度](docs/jobs.md) |
| 调用登录、角色、部门、行级权限和审计接口 | [系统 API 和权限表](docs/system.md) |
| 管理岗位和成员、发布或撤回公告、读取个人收件箱 | [岗位、公告和收件箱 API](docs/organization-notices.md) |
| 添加可卸载模块、启用搜索或收发 Kafka 消息 | [扩展指南与请求示例](docs/extensions.md) |
| 用 GraalVM 25 构建 SQLite 与 Caffeine 的最小原生应用 | [Native 构建配置与待验证范围](docs/native.md) |

数据库自动模式只探测已配置的 PostgreSQL、MySQL 地址，均不可达时使用 SQLite。缓存自动模式优先使用已配置且可达的 Redis。数据源与缓存后端在本次进程内固定，切换数据库只改变读写目标，各库独立保存数据。

Web、JPA、健康端点和 Swagger UI 的通用默认值位于 `zero-core/resources/zero-core-defaults.properties`。部署时可通过应用配置、环境变量或命令行参数覆盖。

工程划分参考 JeecgBoot 将共享基础设施集中到 `jeecg-boot-base-core` 的做法。组织与权限模型参考 JeecgBoot，Kotlin 业务模块划分参考 zeta-kotlin。[JeecgBoot](https://github.com/jeecgboot/JeecgBoot/tree/main/jeecg-boot/jeecg-boot-base-core) · [zeta-kotlin](https://github.com/xia5800/zeta-kotlin)
