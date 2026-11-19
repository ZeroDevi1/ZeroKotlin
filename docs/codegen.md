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

上例生成 `src/dev/zerokotlin/orders/` 下的 `entity/OrderRecord.kt`、`dto/OrderModels.kt`、`mapper/OrderMapper.kt`、`service/OrderService.kt`、`service/impl/OrderServiceImpl.kt`、`controller/OrderController.kt`，以及 `resources/mapper/orders/OrderMapper.xml`。Mapper 继承 MyBatis-Plus 的 `BaseMapper`，Service 接口继承 `IService`，实现继承 `ServiceImpl`。实体和 DTO 的字段说明优先采用 JDBC 列注释，缺失时使用数据库列名。

| 操作 | 上例路径 | 权限后缀和成功响应 |
| --- | --- | --- |
| 分页 | `GET /api/orders/order?page=1&size=20&filter.status=OPEN` | `read`，按主键升序返回 `PageResult` |
| 详情 | `GET /api/orders/order/{id}` | `read`，返回单条记录 |
| 创建 | `POST /api/orders/order` | `create`，返回 HTTP 201 和 `{id}` |
| 替换可写字段 | `PUT /api/orders/order/{id}` | `update`，返回 HTTP 204 |
| 物理删除 | `DELETE /api/orders/order/{id}` | `delete`，返回 HTTP 204 |
| 导出报表 | `GET /api/orders/order/excel/export?filter.status=OPEN` | `read`，下载筛选结果的 XLSX |
| 下载模板 | `GET /api/orders/order/excel/template` | `create`，下载空白 XLSX |
| 批量新增 | `POST /api/orders/order/excel/import` | `create`，上传表单字段 `file`，返回 HTTP 201 和 `{"imported":3}` |

生成的 Controller 按操作鉴权，写入和 `OperationAudit` 在同一事务提交。缺少必填字段返回 HTTP 400，按主键操作时记录不存在返回 HTTP 404。默认读权限覆盖整张表，响应包含已映射的全部列。业务接入时按数据归属补充行级条件，按公开契约调整 DTO 字段，核对物理删除是否符合业务规则。表结构、权限注册与角色分配由目标模块维护。

## 筛选与报表

- 列表和导出共用 `filter.<Kotlin 属性名>` 等值筛选。多个字段按 AND 组合。字段须标注 `@field:CrudFilter`，且有 MyBatis-Plus 列映射。未知筛选字段、重复传值或类型错误返回 HTTP 400。
- Excel 列由 DTO 上的 `@field:ReportColumn(name = "列名", order = 0)` 决定。生成器默认用数据库列名作表头，导出选入非二进制读字段，模板选入可从 Excel 填写的创建字段。表没有可导入字段或创建依赖必填二进制字段时，只生成导出接口。
- 导入和导出各限 1000 条。上传限 1 MiB，使用下载模板，保留一张可见工作表的表头和列顺序。日期时间按 ISO 格式填写，长整数和定点小数用文本单元格保留精度，布尔值接受 `true`、`false`、`1`、`0`。空单元格沿用创建请求的空值和数据库默认值规则。
- 导入逐行走创建服务，写入与审计在一个事务内提交。行级格式、必填项和数据库约束错误会标出原始 Excel 行号。唯一键冲突返回 HTTP 409，其他字段约束错误返回 HTTP 400。

## 在手写 Controller 中复用

继承 `ZeroCrudController<实体>`，传入该实体的 `IService`、实体类、事务管理器和 `OperationAudit`。在业务 Controller 上显式声明 Spring MVC 路由和 `@PreAuthorize`。

| 基类方法 | 接入方式 |
| --- | --- |
| `queryPage`、`queryById` | 直接查询实体并转为公开 DTO。分页传入 `PageWindow` 和用 `@RequestParam` 接收的 `MultiValueMap<String, String>`。 |
| `add`、`edit`、`delete` | 将已校验的请求转换为实体，传入对应权限编码。更新和删除遵循 MyBatis-Plus 的实体映射策略。 |
| `exportXls` | 传入下载名、与分页相同的筛选参数、`XlsxReport` 和公开报表 DTO 的转换函数。 |
| `templateXls`、`importXls` | 使用同一个 `XlsxReport`。给导入方法传入可写请求 DTO 的工厂和业务创建方法。创建方法加入当前事务。 |

有数据归属、状态变更或跨表规则时，在业务 Service 中实现对应的查询和写入。在原生镜像模块中，参照生成的 Controller，用 `@RegisterReflection` 注册实体、Mapper 使用的 DTO 和报表字段。

## 表结构约定

- 表须有一列不可空的整数、字符串或 PostgreSQL UUID 主键。支持 SQLite `INTEGER PRIMARY KEY` 的 rowid 别名、PostgreSQL identity/serial 和 MySQL auto_increment。其他主键由创建请求提供。字符串主键写入时须是长度为 1 至 120 的合法 URL 路径段。
- 常用字段类型涵盖整数、布尔、浮点、定点小数、文本、二进制和日期时间。SQLite 整数映射到 `Long`，日期时间声明映射到 `String` 以保留存储表示。远程库的日期时间映射到对应的 `java.time` 类型。无法映射的类型、无主键和复合主键会在写文件前报错。
- 计算列和数据库自增列只用于读取。创建时，带默认值的非主键列传 `null` 或省略会采用数据库默认值。PUT 全量替换可写字段，省略可空字段会写入 `NULL`，不可空字段始终必填。没有可更新字段时省略 PUT 和对应权限；只有数据库托管字段时 POST 无需请求体。
