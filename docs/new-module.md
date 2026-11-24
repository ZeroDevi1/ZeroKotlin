# 开发业务功能

从 [ApiRoutes.kt](../zero-system/src/dev/zerokotlin/ktor/ApiRoutes.kt) 找系统接口，从 [BusinessRoutes.kt](../zero-system/src/dev/zerokotlin/ktor/BusinessRoutes.kt) 找业务模块。注册点显示 Controller 及它使用的 ServiceImpl，具体 HTTP 方法和子路径在各 Controller 中声明。

权限点管理提供一个完整的系统例子。Controller 接收请求，Service 定义业务接口，ServiceImpl 校验权限并开启事务，Mapper 操作数据。

| 层 | 现有示例 | 职责 |
| --- | --- | --- |
| Controller | [PermissionController.kt](../zero-system/src/dev/zerokotlin/ktor/system/permission/controller/PermissionController.kt) | 读取参数和 Bearer 令牌，声明 HTTP 方法、路径与状态。 |
| Service | [PermissionService.kt](../zero-system/src/dev/zerokotlin/ktor/system/permission/service/PermissionService.kt) | 定义业务操作、权限要求和输入输出。 |
| ServiceImpl | [PermissionServiceImpl.kt](../zero-system/src/dev/zerokotlin/ktor/system/permission/service/impl/PermissionServiceImpl.kt) | 校验业务规则，调用 Mapper，记录操作审计。 |
| Mapper | [PermissionMapper.kt](../zero-system/src/dev/zerokotlin/ktor/system/permission/mapper/PermissionMapper.kt) | 使用 BaseMapper 执行单表 CRUD；复杂查询可用 参数化 JDBC 或 MyBatis XML。 |

## 新增订单业务

1. 将业务迁移放到 `zero-system/resources/db/modules/orders/V1__orders.sql`。创建业务表，写入权限编码。需要管理端菜单时同时写入 `sys_menu`，令 `module_name` 与业务模块名一致。每个模块有独立的 Flyway 历史表 `zero_history_orders`。[权限与菜单字段](system.md#业务模块注册)
2. 按 [CRUD 生成器](codegen.md) 生成 `OrderController`、`OrderService`、`OrderServiceImpl`、`OrderMapper` 和 `OrderRecord`。数据库连接参数指向已经应用表结构的库。
3. 在 [BusinessRoutes.kt](../zero-system/src/dev/zerokotlin/ktor/BusinessRoutes.kt) 导入生成的 Mapper、注册函数和 ServiceImpl，并在 `businessModules` 中加一项。

   ```kotlin
   BusinessModule("orders", setOf(OrderMapper::class.java)) { store ->
       registerOrderRoutes(OrderServiceImpl(store))
   },
   ```

4. 从项目根目录启动。配置名 `orders` 同时用于启用 Flyway 迁移、注册 Mapper 与挂载 Controller。可以用逗号分隔多个模块。

   ```sh
   ZERO_BUSINESS_MODULES=orders ./gradlew :zero-system:run
   ```

5. 为目标角色授予相应权限。若增加前端页面，在 [navigation.ts](../zero-web/src/router/navigation.ts) 注册与菜单一致的路径及读取权限。

## 权限、事务与数据范围

`SystemStore.read` 和 `SystemStore.write` 在 JDBC 事务内验证会话和操作权限。`write` 回调中的审计与业务写入一同提交。用 `store.mappers.withSession(db)` 获取 Mapper，`db` 来自该回调，Mapper 只在 Session 回调内使用。复杂查询可在 Mapper 接口声明方法，并将 XML 放到与接口全限定名对应的资源路径，例如 `zero-system/resources/dev/zerokotlin/orders/mapper/OrderMapper.xml`，XML 的 `namespace` 与接口全限定名一致。查询条件用绑定参数。

生成的单表 CRUD 只检查操作权限。需要按归属或租户隔离时，在 `OrderServiceImpl` 覆盖 `rowScope`，列表、详情、导出、更新和删除都会使用该条件。把归属列加入 `spec.managedFields` 的配置值，例如 `managedFields = setOf("ownerId")`，然后在 `prepareCreate` 从当前会话写入该列。创建与 Excel 导入共用这条规则，客户端提交托管字段会被拒绝。

```kotlin
private val ownerColumn = spec.columns.single { it.property == "ownerId" }

override fun rowScope(actor: CurrentUser, permission: String): ResourceFields =
    listOf(ownerColumn to actor.id)

override fun prepareCreate(actor: CurrentUser, values: ResourceFields): ResourceFields =
    values + (ownerColumn to actor.id)
```

从配置中关闭模块后，路由和菜单随之停用，数据库中的数据和迁移历史可供再次启用时使用。
