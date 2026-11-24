# Ktor MyBatis-Plus CRUD 生成器

`zero-codegen` 从 SQLite、PostgreSQL 或 MySQL 的表元数据生成 Entity、Mapper、Service、ServiceImpl 和 Controller。仓库与 `npx init` 生成的项目使用同一个入口。在项目根目录先预览拟生成的文件。

```sh
./gradlew :zero-codegen:run --args='--module zero-system --package dev.zerokotlin.orders --table orders_order --name Order --dry-run'
```

通过 `--jdbc-url` 或 `ZERO_CODEGEN_JDBC_URL` 提供连接地址。远端凭据使用 `ZERO_CODEGEN_DB_USERNAME` 和 `ZERO_CODEGEN_DB_PASSWORD`。PostgreSQL 可使用 `--schema`，MySQL 可使用 `--catalog`。SQLite 输入为已有数据库文件。

去掉 `--dry-run` 后，生成器把文件写入 `zero-system/src/dev/zerokotlin/orders/`。已有目标文件会使生成退出，数据库访问只读取元数据。

| 文件 | 内容 |
| --- | --- |
| `entity/OrderRecord.kt` | MyBatis-Plus 表、主键与列映射。字段 KDoc 优先使用 JDBC 列注释。 |
| `mapper/OrderMapper.kt` | `BaseMapper<OrderRecord>`，可在这里声明复杂查询。 |
| `service/OrderService.kt` | 业务接口，供 Controller 调用。 |
| `service/impl/OrderServiceImpl.kt` | 权限声明、数据映射、归属与租户规则，以及 Mapper 装配。 |
| `controller/OrderController.kt` | HTTP 方法、路径和请求需要的权限。 |

示例路由基路径为 `/api/orders/order`。注册生成的 Mapper 和 Controller、编写权限迁移以及启动业务模块的步骤见 [开发业务功能](new-module.md#新增订单业务)。

## 数据与接口契约

- 表须有单列主键。生成器根据元数据处理自增主键、数据库默认值、计算列与可空字段。HikariCP 的数据源由应用统一维护。
- 列表使用 `page`、`size` 和 `filter.<属性名>`，返回 `{items,total,page,size}`。筛选值通过 Mapper 参数绑定。
- 默认生成列表、详情、新增、删除及 Excel 导出。可更新列存在时生成更新，可导入列满足约束时生成 Excel 模板与导入。导入失败按行号报告并回滚整批写入。
- 生成器输出操作权限编码。存在归属或租户隔离时，在 ServiceImpl 中设置托管列并实现数据范围。[权限、事务与数据范围](new-module.md#权限事务与数据范围)

早期单文件生成的 `register…Routes(store)` 可继续注册。把它放进对应的 `BusinessModule` 注册函数，Mapper 集合使用 `emptySet()`。需要切换到 MyBatis-Plus 时，在升级分支用同一表生成四层代码，再合入原业务定制。
