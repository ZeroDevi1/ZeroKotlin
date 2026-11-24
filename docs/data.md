# 数据库、迁移与安全状态

Ktor 通过 JDBC 和 HikariCP 访问数据库。配置位于 [application.conf](../zero-system/resources/application.conf)，连接选型由 [DatabaseSettings.kt](../zero-core/src/dev/zerokotlin/core/database/DatabaseSettings.kt) 实现。

## 数据源

- `ZERO_DATABASE_MODE=auto` 按 PostgreSQL、MySQL、SQLite 顺序选择。无远端地址或驱动返回连接不可达时尝试下一个候选。
- `ZERO_DATABASE_MODE=sqlite` 使用 `ZERO_DATABASE_SQLITE_PATH`，默认为项目工作目录下的 `data/zero-ktor.db`。启用 SQLite 外键检查。
- `ZERO_DATABASE_MODE=postgresql` 或 `mysql` 使用对应的 `ZERO_DATABASE_POSTGRESQL_*` 或 `ZERO_DATABASE_MYSQL_*` 环境变量。使用 `URL`、`USERNAME` 和 `PASSWORD` 分别指定 JDBC 地址、用户名和密码。

## Flyway 迁移

应用启动时运行 `zero-system/resources/db/migration/` 中的公用迁移和所选数据库的专用迁移。业务模块迁移放在 `db/modules/<名称>/`。迁移历史与业务数据保存在各自的数据库中。增量修改应新增版本，已执行的 SQL 参与 Flyway 校验。[业务模块迁移](new-module.md)

## 安全状态

`ZERO_CACHE_MODE=auto` 优先使用已配置且可连接的 Redis，否则使用进程内的 Caffeine。可显式设置 `redis` 或 `caffeine`。多实例共享验证码和限流状态时，为各实例配置相同的 Redis 数据库与键前缀。数据库和 Redis 连接配置见 [Ktor 开发指南](development.md#配置)。
