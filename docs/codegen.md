# 从业务表生成 Kotlin CRUD

`zero-codegen` 是开发用 Gradle CLI。它复用 `zero-core` 提供的 JDBC 驱动，读取一张已有表的结构，在指定业务模块生成 Kotlin 源码和 MyBatis XML。

## 生成

在仓库根目录运行。以下命令假定已有 `zero-orders` 模块及开发数据库中的 `orders_order` 表。

```bash
export ZERO_CODEGEN_JDBC_URL='jdbc:sqlite:./data/zero-kotlin.db'
./gradlew :zero-codegen:run --args='--module zero-orders --package dev.zerokotlin.orders --table orders_order --name Order --dry-run'
```

确认输出的文件路径、接口路径和权限编码后，去掉 `--dry-run` 执行写入。预览也会连接数据库并检查目标文件冲突。带空格的参数值可在 `--args` 的内容中用双引号包裹。

| 参数 | 用法 |
| --- | --- |
| `--module` | 指向含 `build.gradle.kts` 的现有业务模块，接受相对路径或绝对路径。 |
| `--package` | 使用 `dev.zerokotlin` 下的小写基础包，如 `dev.zerokotlin.orders`。 |
| `--table` | 使用数据库实际表名及大小写。表、列和显式 schema/catalog 名支持 ASCII 字母、数字及下划线，以字母或下划线开头。SQL 保留字按方言引用。 |
| `--name` | 可选的 PascalCase 类名词干。省略时将 `orders_order` 转为 `OrdersOrder`。列名转为 Kotlin 小驼峰，关键字加 `Value` 后缀，容易触发特殊 getter 的 `isX` 属性加 `field` 前缀。转换冲突会报错。 |
| `--jdbc-url` | 可选，优先于 `ZERO_CODEGEN_JDBC_URL`。远程连接可使用 `ZERO_CODEGEN_DB_USERNAME` 和 `ZERO_CODEGEN_DB_PASSWORD` 提供凭据。 |
| `--schema` / `--catalog` | 分别选择 PostgreSQL 的 schema、MySQL 的数据库。省略时使用连接当前选择。 |
| `--dry-run` / `--help` | 分别用于预览和查看帮助。 |

| 数据库 | URL 示例及 SQL 范围 |
| --- | --- |
| SQLite | `jdbc:sqlite:./data/zero-kotlin.db`。只读打开本地已有文件中的 `main` 库。 |
| PostgreSQL | `jdbc:postgresql://localhost:5432/zero_kotlin`。XML 使用选定 schema 限定表名。 |
| MySQL | `jdbc:mysql://localhost:3306/zero_kotlin`。使用 InnoDB 表。传 `--catalog` 时 XML 显式限定数据库，否则使用应用当前数据库。 |

XML 按读取表结构时的数据库方言生成。切换数据库类型时，从对应库生成到另一个带 `build.gradle.kts` 的目录，对比差异后合入业务代码。生成器拒绝覆盖任何目标文件。写入中途发生 I/O 错误时，会尝试删除此次新建的文件。CLI 退出码为 `0`（成功或帮助）、`2`（参数错误）或 `1`（连接、元数据、输出冲突或文件操作失败）。Gradle 会将 CLI 的非零退出报告为任务失败。

## 装配与鉴权

1. 按[新建业务模块](new-module.md)在 Gradle 中注册目标模块，启用 Kotlin JVM 和 `kotlin.spring` 插件，并添加 `implementation(project(":zero-core"))`。
2. 将目标模块通过 `runtimeOnly(project(":zero-orders"))` 加入 `zero-system` 的运行时类路径。启动应用扫描职责包中的 Spring 组件和 `@Mapper`，并加载 `resources/mapper/` 下的 XML。
3. 按[业务模块注册](system.md#业务模块注册)将 CLI 打印的编码注册到 `sys_permission`，再通过角色授予所需操作。上例生成 `orders:order:read`、`orders:order:create`、`orders:order:update`、`orders:order:delete`。

上例生成 `src/dev/zerokotlin/orders/` 下的 `entity/OrderRecord.kt`、`dto/OrderModels.kt`、`mapper/OrderMapper.kt`、`service/OrderService.kt`、`controller/OrderController.kt`，以及 `resources/mapper/orders/OrderMapper.xml`。

| 操作 | 上例路径 | 权限后缀和成功响应 |
| --- | --- | --- |
| 分页 | `GET /api/orders/order?page=1&size=20` | `read`，按主键升序返回 `PageResult` |
| 详情 | `GET /api/orders/order/{id}` | `read`，返回单条记录 |
| 创建 | `POST /api/orders/order` | `create`，返回 HTTP 201 和 `{id}` |
| 替换可写字段 | `PUT /api/orders/order/{id}` | `update`，返回 HTTP 204 |
| 物理删除 | `DELETE /api/orders/order/{id}` | `delete`，返回 HTTP 204 |

生成的 Service 按操作鉴权，写入和 `OperationAudit` 在同一事务提交。缺少必填字段返回 HTTP 400，按主键操作时记录不存在返回 HTTP 404。默认读权限覆盖整张表，响应包含已映射的全部列。业务接入时按数据归属补充行级条件，按公开契约调整 DTO 字段，核对物理删除是否符合业务规则。表结构、权限注册与角色分配由目标模块维护。

## 表结构约定

- 表须有一列不可空的整数、字符串或 PostgreSQL UUID 主键。支持 SQLite `INTEGER PRIMARY KEY` 的 rowid 别名、PostgreSQL identity/serial 和 MySQL auto_increment。其他主键由创建请求提供。字符串主键写入时须是长度为 1 至 120 的合法 URL 路径段。
- 常用字段类型涵盖整数、布尔、浮点、定点小数、文本、二进制和日期时间。SQLite 整数映射到 `Long`，日期时间声明映射到 `String` 以保留存储表示。远程库的日期时间映射到对应的 `java.time` 类型。无法映射的类型、无主键和复合主键会在写文件前报错。
- 计算列和数据库自增列只用于读取。创建时，带默认值的非主键列传 `null` 或省略会采用数据库默认值。PUT 全量替换可写字段，省略可空字段会写入 `NULL`，不可空字段始终必填。没有可更新字段时省略 PUT 和对应权限；只有数据库托管字段时 POST 无需请求体。
