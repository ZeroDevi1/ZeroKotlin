# 开发业务功能

后端按模块使用 `src/` 和 `resources/`。把对外接口接到 [Application.kt](../zero-system/src/dev/zerokotlin/ktor/Application.kt) 注册的 `businessRoutes`。通用库可以独立作为 Gradle 子项目，在 `settings.gradle.kts` 注册并由使用方声明依赖。

1. 将业务迁移放到运行时类路径上的 `db/modules/<模块名>/`，例如 `zero-system/resources/db/modules/orders/V1__orders.sql`。通过 `ZERO_BUSINESS_MODULES=orders` 启用，多个名称以逗号分隔。Flyway 为每个模块维护 `zero_history_<模块名>`。
2. 在业务迁移里创建权限和菜单，设置对应的 `sys_menu.module_name`。通过角色授予业务权限。数据库字段契约见[系统模块](system.md#业务模块注册)。
3. 在路由中读取 Bearer 会话，在 [SystemStore.kt](../zero-system/src/dev/zerokotlin/ktor/SystemStore.kt) 提供的读写入口校验权限。需要行级授权时，在实际 SQL 中加入相应条件。业务变更和操作审计可一起提交。
4. 需要页面时，在 [navigation.ts](../zero-web/src/router/navigation.ts) 注册页面路径和读取权限，使用与菜单一致的路径。

可用 [CRUD 生成器](codegen.md) 读取现有表并创建基本路由。业务模块从 `ZERO_BUSINESS_MODULES` 移除后，其迁移历史和数据仍保存在数据库中。应同时按业务需求调整路由注册。
