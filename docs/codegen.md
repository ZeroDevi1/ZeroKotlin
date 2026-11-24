# Ktor CRUD 生成器

`zero-codegen` 从 SQLite、PostgreSQL 或 MySQL 的表元数据生成 Kotlin 路由。仓库和初始化项目使用同一个入口。以下示例在项目根目录执行，并预览要写入的源码。

```sh
./gradlew :zero-codegen:run --args='--module zero-system --package dev.zerokotlin.orders --table orders_order --dry-run'
```

通过 `--jdbc-url` 或 `ZERO_CODEGEN_JDBC_URL` 提供连接地址。远端凭据使用 `ZERO_CODEGEN_DB_USERNAME` 和 `ZERO_CODEGEN_DB_PASSWORD`。PostgreSQL 可使用 `--schema`，MySQL 可使用 `--catalog`。SQLite 输入为已有数据库文件。可用 `./gradlew :zero-codegen:run --args='--help'` 查看参数。

去掉 `--dry-run` 后，生成器将代码写到目标模块的 `src/`，例如 `zero-system/src/dev/zerokotlin/orders/OrdersOrderRoutes.kt`。生成器只读取数据库元数据，遇到已有目标文件会退出。Ktor 路由复用 `zero-system` 的内部会话和事务接口，所以使用 `--module zero-system`。

1. 在 [GeneratedResource.kt](../zero-system/src/dev/zerokotlin/ktor/GeneratedResource.kt) 的 `businessRoutes` 中调用生成的 `register…Routes(store)`。
2. 在 [应用代码入口](new-module.md) 的业务迁移中写入生成器输出的权限编码，再分配给角色。
3. 对有归属或租户的记录，在查询及写入条件中加入业务数据范围。
