# ZeroKotlin

ZeroKotlin 是 Ktor + Netty 后端与 Vue 3 管理工作台组成的多模块脚手架。仓库和 `init` 生成项目使用同一套源码、Gradle 配置和模块目录。系统提供账号、组织与数据权限、字典、公告与收件箱、文件、Excel、实时消息、调度和审计。

## 首次启动

准备 JDK 25、Node.js 22.12 或更高版本和 pnpm。使用 vfox 时，可在项目根目录执行 `vfox install java@25+36` 和 `vfox use --project java@25+36`。

在项目根目录启动后端。Windows PowerShell 使用 `.\gradlew.bat`。

```sh
./gradlew :zero-system:run
```

进入 `zero-web/` 启动管理端。

```sh
pnpm install --frozen-lockfile
pnpm dev
```

默认管理台位于 [127.0.0.1:5173](http://127.0.0.1:5173/)，后端提供[存活探针](http://127.0.0.1:8080/health)、[就绪探针](http://127.0.0.1:8080/actuator/health)和 [Swagger UI](http://127.0.0.1:8080/swagger-ui/)。Flyway 在空用户表中创建 `admin / admin`，首次登录后请改密。已有账号和历史迁移继续使用原数据库记录。

## 初始化项目

在新项目的父目录运行以下命令。使用 Git 提交 SHA 或标签代替 `#ktor` 可固定模板版本。

```sh
npx --yes "github:ZeroDevi1/ZeroKotlin#ktor" init --name order-service --context-url /apps/orders
```

也可用 `node "<ZeroKotlin 工作树>/scripts/zerokotlin.mjs" init --name order-service --context-url /apps/orders` 从本地源码生成。两个参数均必填，目标目录必须可新建。访问前缀可使用 `/`、绝对路径或完整 HTTP(S) URL，完整 URL 取路径部分。

生成后进入 `order-service/`，按[首次启动](#首次启动)运行前后端。`init` 将访问前缀同时写入后端 HOCON 和 Vite 配置。这个示例的管理台访问路径是 `/apps/orders/`。

## 模块与开发入口

| 模块 | 职责 |
| --- | --- |
| `zero-core` | JDBC 数据源、文件存储、验证码与安全状态、Excel、Kafka 等通用基础设施。 |
| `zero-system` | Ktor 启动入口、系统业务与路由、数据库迁移。入口配置在 [application.conf](zero-system/resources/application.conf)。 |
| `zero-codegen` | 从 SQLite、PostgreSQL 或 MySQL 表生成 Ktor JDBC CRUD 路由。 |
| `zero-agent` | 可选 AgentScope 开发模块，使用 `-PwithAgent=true` 加入后端运行包。 |
| `zero-web` | Vue 3 + TypeScript 管理工作台。 |

后端模块沿用 `src/`、`resources/` 和 `test/` 布局。配置、部署和迁移边界见 [Ktor 开发指南](docs/development.md)。业务模块可参考 [CRUD 生成器](docs/codegen.md)和[开发入口](docs/new-module.md)。[Kotlin 后端开发手册](docs/kotlin-development.md)提供工程内的代码示例。已有项目升级见[合并模板更新](docs/template-upgrades.md)。
