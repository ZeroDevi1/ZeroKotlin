# Kotlin 后端开发手册

如果你熟悉 Java 和 Spring，可以从用户列表的调用链开始。它用到了 Kotlin 的空安全、扩展函数、集合变换和构造注入。

1. [SystemUserController.list](../zero-system/src/dev/zerokotlin/system/controller/SystemUserController.kt#L63) 接收页码、页大小和可选的部门、角色筛选参数。
2. [SystemUserServiceImpl.list](../zero-system/src/dev/zerokotlin/system/service/impl/SystemUserServiceImpl.kt#L103) 用当前用户的可见范围和请求的筛选项构造 `UserQuery`，查询当前页，再批量查询本页账号的角色。
3. [UserMapper.xml](../zero-system/resources/mapper/system/UserMapper.xml) 让列表和总数查询共用可见范围和筛选条件。服务把查询结果转成 `UserListEntry`，由 `PageWindow.result` 组装 `PageResult<UserListEntry>`。

后端源码放在模块的 `src/` 下，Mapper XML 和迁移 SQL 放在 `resources/`。本手册中的教学示例使用 `zero-system` 和 `zero-core` 的现有类型。Gradle 接入、模块装配和迁移步骤见[新增业务模块](new-module.md)。

## 定义函数

Kotlin 使用 `fun name(parameter: Type): ReturnType` 声明函数。块体用 `{}`，有返回值时写明返回类型并使用 `return`。表达式体用 `=`，可由编译器推断返回类型。对外接口显式写出返回类型，调用方就能直接从签名读出契约。[Kotlin 函数](https://kotlinlang.org/docs/functions.html)

下面的教学示例使用真实分页类型。把三个函数放在同一个 Kotlin 文件中即可互相调用。

```kotlin
import dev.zerokotlin.core.PageResult
import dev.zerokotlin.core.PageWindow
import dev.zerokotlin.system.dto.UserListEntry

internal fun userPageWindow(page: Int = 1, size: Int = 20): PageWindow =
    PageWindow(page, size)

private fun PageResult<UserListEntry>.enabledUserCountText(): String {
    val enabled = items.count { it.user.enabled }
    return "第 $page 页有 $enabled 个启用账号"
}

private fun printEnabledUserCount(page: PageResult<UserListEntry>) {
    println(page.enabledUserCountText())
}
```

- `userPageWindow(size = 50)` 用命名实参指定页大小，`page` 取默认值 `1`。真实 HTTP 接口通过 `@RequestParam(defaultValue = "1")` 等注解指定请求默认值。[用户控制器](../zero-system/src/dev/zerokotlin/system/controller/SystemUserController.kt#L63)
- `printEnabledUserCount` 的返回类型是 `Unit`，块体可省略显式的 `: Unit`。项目中的 `SystemAnnouncementServiceImpl.publish` 则是 `Unit` 表达式体，它把工作交给 `transition`。[公告服务](../zero-system/src/dev/zerokotlin/system/service/impl/SystemAnnouncementServiceImpl.kt#L108)
- 顶层 `private` 的作用域是当前文件，类成员的 `private` 限于声明它的类。`internal` 对同一个 Kotlin 编译模块（例如 `zero-system` 主源集）可见。新声明的函数默认是 `public`。本项目的 `UserRow.summary()` 是文件私有的扩展函数。[Kotlin 可见性](https://kotlinlang.org/docs/visibility-modifiers.html) · [用户服务](../zero-system/src/dev/zerokotlin/system/service/impl/SystemUserServiceImpl.kt#L59)
- 扩展函数按 `fun 接收者类型.名称(...)` 声明，可通过 `page.enabledUserCountText()` 调用。函数内部可通过 `this` 访问接收者。编译器按接收者的静态类型选择扩展，同签名的成员函数优先。[Kotlin 扩展](https://kotlinlang.org/docs/extensions.html)

## `val`、`var` 与空安全

依赖和中间结果优先声明为 `val`。局部 `val` 只能赋值一次，`val` 属性只提供读取入口。通过 `val` 仍可修改它指向的可变对象。比如 `val ids = mutableListOf<String>()` 可以调用 `ids.add(...)`。`List<T>` 提供只读接口，其他引用仍可能修改它背后的集合。需要重新赋值时用 `var`，例如 `UserRecord.enabled` 随账号状态变更而更新。[用户持久化模型](../zero-system/src/dev/zerokotlin/system/entity/UserRecord.kt)

`String` 表示必有值，`String?` 允许缺失。查询中的 `departmentId == null` 表示省略显式的部门筛选，账号可见范围仍然生效。响应中的 `user.departmentId == null` 表示账号没有所属部门。[UserQuery](../zero-system/src/dev/zerokotlin/system/dto/UserQuery.kt) · [UserSummary](../zero-system/src/dev/zerokotlin/system/dto/UserModels.kt#L77)

```kotlin
import dev.zerokotlin.system.dto.UserSummary

private fun departmentLabel(user: UserSummary, departments: Map<String, String>): String {
    val departmentId = user.departmentId ?: return "未分配部门"
    return departments[departmentId] ?: "部门名称未知"
}
```

- `?.` 仅在接收者有值时调用。`?.let { ... }` 适合变换可选值。`SystemUserServiceImpl.updateAccount` 用它处理可选的新密码。[账号更新服务](../zero-system/src/dev/zerokotlin/system/service/impl/SystemUserServiceImpl.kt#L244)
- `?:` 在左侧为 `null` 时求右侧的值，右侧也可以提前 `return` 或抛异常。`SystemUserServiceImpl.visibleUser` 用 `?: missing("用户")` 将空查询结果映射为 HTTP 404。`missing` 返回类型为 `Nothing`，表示该调用必然中断当前执行路径。[用户查询服务](../zero-system/src/dev/zerokotlin/system/service/impl/SystemUserServiceImpl.kt#L317) · [错误辅助函数](../zero-system/src/dev/zerokotlin/system/support/SystemSupport.kt#L124)
- 编译器可以在非空检查后缩窄稳定变量的类型。可变属性先读到局部 `val`，后续在同一次读取的值上做判断和访问。调用 Java Mapper 时，缺少空性标记的返回值可能是平台类型，例如 IDE 显示的 `UserRecord!`。按 Java API 的契约显式处理可能的 `null`，避免用 `!!` 把检查推迟到运行时。[Kotlin 空安全](https://kotlinlang.org/docs/null-safety.html)

## 用集合函数表达筛选和转换

函数类型 `(UserListEntry) -> Boolean` 表示接收一个列表项并返回布尔值。`{ entry -> entry.user.enabled }` 是符合该类型的 lambda，只有一个参数时可写成 `{ it.user.enabled }`。`::isEnabled` 引用已有函数，`UserSummary::departmentId` 引用已有属性，两者都可作为相应的函数参数。接收或返回函数的函数叫高阶函数。最后一个实参是 lambda 时，可放到右括号外。[Kotlin 高阶函数与 lambda](https://kotlinlang.org/docs/lambdas.html)

这些教学函数消费已经查询出的 `PageResult<UserListEntry>`，例如用于展示本页统计。

```kotlin
import dev.zerokotlin.core.PageResult
import dev.zerokotlin.system.dto.AssignedRole
import dev.zerokotlin.system.dto.UserListEntry
import dev.zerokotlin.system.dto.UserSummary

private fun usersMatching(
    page: PageResult<UserListEntry>,
    matches: (UserListEntry) -> Boolean,
): List<UserListEntry> = page.items.filter(matches)

private fun isEnabled(entry: UserListEntry): Boolean = entry.user.enabled

private fun enabledUsersByDepartment(
    page: PageResult<UserListEntry>,
): Map<String?, List<UserSummary>> =
    usersMatching(page, ::isEnabled)
        .map { it.user }
        .groupBy(UserSummary::departmentId)

private fun rolesInDepartment(
    page: PageResult<UserListEntry>,
    departmentId: String,
): Map<String, AssignedRole> =
    usersMatching(page) { it.user.departmentId == departmentId }
        .flatMap { it.roles }
        .associateBy(AssignedRole::id)
```

| 操作 | 结果与用途 |
| --- | --- |
| `filter` | 保留符合条件的列表项。 |
| `map` | 把每个列表项变为一个新值，例如从 `UserListEntry` 取出 `UserSummary`。 |
| `flatMap` | 将每项对应的集合展开成一个列表，例如取出本页若干账号的全部角色。 |
| `associateBy` | 按键索引，每个键保留最后遇到的值。示例按角色 ID 合并重复角色。 |
| `groupBy` | 按键保留一组值。可空的部门 ID 会形成 `null` 分组。 |

项目在 `SystemUserServiceImpl.list` 中先提取本页账号 ID，通过一次查询取回角色，再用 `groupBy(keySelector = ..., valueTransform = ...)` 按账号组装角色 DTO。没有角色的账号通过 `orEmpty()` 得到空列表。涉及可见范围、部门、角色等全量筛选时，在数据库中应用条件，并在总数查询中使用同一条件。当前页在内存里的计算仅覆盖已经选出的记录。[用户服务](../zero-system/src/dev/zerokotlin/system/service/impl/SystemUserServiceImpl.kt#L103) · [用户查询 SQL](../zero-system/resources/mapper/system/UserMapper.xml#L40) · [Kotlin 集合变换](https://kotlinlang.org/docs/collection-transformations.html)

`List` 上的这些变换逐步求值，多个变换通常产生中间集合。`Sequence` 的 `filter`、`map` 和 `take` 在终止操作消费时才求值。以下函数可接在同一段教学代码后面，从本页只取前若干个启用账号的显示名。

```kotlin
private fun firstEnabledNames(page: PageResult<UserListEntry>, limit: Int): List<String> {
    require(limit >= 0) { "limit 必须大于等于 0" }
    return page.items.asSequence()
        .filter(::isEnabled)
        .map { it.user.displayName }
        .take(limit)
        .toList()
}
```

- 简短的分页 DTO 转换可直接使用集合操作。链条较长、数据量大或可以提前结束时，再考虑 `Sequence`。`asSequence()` 基于已加载的本页数据，数据库查询仍由 Mapper 完成。[Kotlin 序列](https://kotlinlang.org/docs/sequences.html)
- 让筛选和转换 lambda 专注于计算值。序列中的副作用可能延迟到消费时发生，也可能因为 `take` 提前结束而只发生在部分元素上。重复遍历可重复计算。需要写库、发消息或记录日志时，用清晰的调用位置表达执行时机。
- 有 `break`、`continue`、多步状态更新或逐项错误处理时，普通 `for` 或 `while` 往往更好读。项目里的 `validateParent` 先用 `associateBy` 建立 ID 索引，再用 `while` 沿父节点向上检查缺失和循环。[层级校验](../zero-system/src/dev/zerokotlin/system/support/SystemSupport.kt#L153)

## 选择类、枚举或接口

| 声明 | 何时使用 | 示例 |
| --- | --- | --- |
| `class` | 对象有行为、生命周期或独立的可变状态。普通类继承 `Any.equals` 时按实例比较。 | `PageWindow` 封装分页校验和偏移计算，`UserRecord` 供 Mapper 填充和更新。 |
| `data class` | 一组字段共同定义值，需要生成 `equals`、`hashCode` 或 `copy`。 | `PageResult<T>`、`UserSummary`、`AnnouncementRequest`。 |
| `enum class` | 状态集合固定，每个常量有唯一实例，且可定义属性和方法。 | `AnnouncementStatus` 的 `DRAFT`、`PUBLISHED`、`REVOKED`。 |
| `sealed class` / `sealed interface` | 可选分支有限，每个分支可以携带不同的数据。需要基类构造器或共享状态时用 `sealed class`，只需契约时用 `sealed interface`。 | 下方的 `UserLookup` 教学示例。 |
| `object` | 需要单个具名实例来提供行为或共享状态。 | `BearerTokens` 集中定义令牌操作。 |
| `interface` | 调用方依赖行为契约，实现可独立替换。单抽象方法可声明为 `fun interface` 并用 lambda 构造。 | `UserMapper`、`CurrentActor`。 |

### 值相等与浅拷贝

`==` 比较结构上的相等性，调用 `equals`。`===` 比较是否引用同一个实例。`data class` 默认根据主构造器中声明的属性生成 `equals` 和 `hashCode`，类体中另行声明的属性不参与。把对象用作 `HashMap` 键或放进 `HashSet` 时，保持参与相等性和哈希计算的值稳定。[Kotlin 相等性](https://kotlinlang.org/docs/equality.html) · [Kotlin 数据类](https://kotlinlang.org/docs/data-classes.html)

`copy()` 创建新的数据类实例。以 `val copied = page.copy()` 为例，`copied == page` 为 `true`，`copied === page` 为 `false`，`copied.items === page.items` 为 `true`。如果其他引用修改了 `items` 的底层可变列表，两个页面都会观察到变化。需要保存列表内容的快照时，可以传入 `page.copy(items = page.items.toList())`，其中的列表元素及其 `roles` 属性仍会共享引用。[分页模型](../zero-core/src/dev/zerokotlin/core/PageResult.kt)

### 枚举值与外部契约

假设订单接口需要传递状态，可以给每个枚举常量定义稳定的编码。一次创建订单的请求则用数据类承载各个调用方提交的字段。

```kotlin
enum class OrderStatus(val code: String) {
    DRAFT("draft"),
    CONFIRMED("confirmed"),
}

data class CreateOrderRequest(val title: String)

fun orderStatusFromCode(code: String): OrderStatus? =
    OrderStatus.entries.firstOrNull { it.code == code }
```

项目把 `AnnouncementStatus` 的 `.name` 写入 `AnnouncementRecord.status`，公告响应也返回这个字符串。`PUBLISHED` 同时出现在收件箱查询里，迁移中的约束则检查状态与发布时间的组合。修改枚举常量名时，先核对已有数据、表约束、查询和 API 调用方，并安排兼容或迁移。新领域需要独立演进时，可以给枚举定义稳定的 `code`，在数据库和 HTTP 边界显式映射。`ordinal` 取决于声明顺序，插入常量或调整顺序可能改变已有常量的序号。[公告服务](../zero-system/src/dev/zerokotlin/system/service/impl/SystemAnnouncementServiceImpl.kt#L108) · [公告 API](organization-notices.md#公告管理) · [公告迁移](../zero-system/resources/db/migration/common/V2__positions_and_announcements.sql#L22) · [Kotlin 枚举](https://kotlinlang.org/docs/enum-classes.html)

### 用密封类型处理不同结果

下面的教学示例让找到用户的分支携带详情，缺失的分支只表达状态。`when` 表达式覆盖所有分支后可以省略 `else`。新增分支后，编译器会指出哪些 `when` 表达式需要补齐分支。当前仓库的用户详情服务通过 `missing("用户")` 处理查无用户。[Kotlin 密封类型](https://kotlinlang.org/docs/sealed-classes.html) · [用户详情服务](../zero-system/src/dev/zerokotlin/system/service/impl/SystemUserServiceImpl.kt#L135)

```kotlin
import dev.zerokotlin.system.dto.UserDetails

private sealed interface UserLookup {
    data class Found(val details: UserDetails) : UserLookup
    data object Missing : UserLookup
}

private fun lookupLabel(result: UserLookup): String = when (result) {
    is UserLookup.Found -> result.details.user.displayName
    UserLookup.Missing -> "未找到用户"
}
```

## 在 Spring 代码中落地

### 请求和响应 DTO、查询投影与实体

- 请求 DTO 声明客户端能填写的字段。本项目同时使用普通类 `CreateUserRequest` 和数据类 `UserProfileRequest`。数据类默认生成的 `toString()` 会包含主构造器属性，含密码的请求应避免被直接记录。[用户 DTO](../zero-system/src/dev/zerokotlin/system/dto/UserModels.kt)
- 响应 DTO 明确 API 可以返回什么。`UserSummary.enabled` 是 `Boolean`，查询投影 `UserRow.enabled` 和持久化模型 `UserRecord.enabled` 则用 `Int`。`UserRow.summary()` 完成转换，用户列表查询的列也排除了 `password_hash`。[查询投影](../zero-system/src/dev/zerokotlin/system/dto/QueryRows.kt) · [用户查询 SQL](../zero-system/resources/mapper/system/UserMapper.xml#L52)
- MyBatis-Plus 持久化模型如 `UserRecord` 采用带默认值和 `var` 属性的普通类，通过 `@TableName`、`@field:TableId` 等注解映射列。`departmentId` 上的 `FieldStrategy.ALWAYS` 允许通过实体更新将原有部门写成数据库 `NULL`。[用户持久化模型](../zero-system/src/dev/zerokotlin/system/entity/UserRecord.kt) · [持久化指南](data.md#mybatis-plus-与可选-jpa)

### 构造注入和事务边界

控制器通过 `class SystemUserController(private val users: SystemUserService)` 注入服务接口。该接口继承 MyBatis-Plus 的 `IService<UserRecord>`。标注了 `@Service` 的实现 `SystemUserServiceImpl` 通过 `ServiceImpl` 取得 Mapper，并通过构造器接收密码编码器和 `SystemWrites`。Spring 可自动注入唯一构造器的依赖。项目应用了 `kotlin-spring` 插件，自动开放 `@Service` 等组件类及其成员，供 Spring 创建基于子类的代理。[用户控制器](../zero-system/src/dev/zerokotlin/system/controller/SystemUserController.kt#L46) · [系统模块构建配置](../zero-system/build.gradle.kts) · [Spring 的 Kotlin 支持](https://docs.spring.io/spring-framework/reference/languages/kotlin/spring-projects-in.html)

- 查询入口如 `SystemUserServiceImpl.list` 使用 `@Transactional(readOnly = true)`。`readOnly` 向事务设施声明只读意图。
- 写入入口如 `SystemAnnouncementServiceImpl.publish` 使用 Spring 的 `@Transactional`。控制器通过 Spring Bean 调用它，事务内的私有 `transition` 完成状态检查和写入。`SystemWrites.authorize` 先取得系统写入锁并重新鉴权，数据变更和 `writes.record` 插入的审计随同一事务提交。`SystemWrites` 使用 `Propagation.MANDATORY` 要求调用方持有事务。[公告服务](../zero-system/src/dev/zerokotlin/system/service/impl/SystemAnnouncementServiceImpl.kt#L108) · [系统写入服务](../zero-system/src/dev/zerokotlin/system/service/SystemWrites.kt#L26)
- 默认代理模式在经过代理的调用入口应用事务配置。同一个类中的方法直接调用另一个方法时，沿用当前事务上下文。默认情况下，从事务入口抛出的运行时异常和 `Error` 会触发回滚。[Spring 声明式事务](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html)

### 分层校验参数

1. 在 HTTP 边界声明请求类型、可空性和默认值。分页交给 `PageWindow`，它要求 `page >= 1`、`size in 1..100`，越界抛出 HTTP 400。`PageResult.total` 表示应用可见范围和筛选条件后的总记录数。[分页类型](../zero-core/src/dev/zerokotlin/core/PageResult.kt)
2. 在服务中做字段规范化和业务校验。公告服务用 `checkedPlainText` 校验标题，并在启用配置时清理 HTML 标记。正文用 `checkedText` 去除首尾空白并检查非空及长度。权限检查与当前状态确认在写事务内完成，状态不允许变更时抛出 HTTP 409。项目通过 `invalid`、`missing`、`conflict` 分别表达 HTTP 400、404、409。[公告服务](../zero-system/src/dev/zerokotlin/system/service/impl/SystemAnnouncementServiceImpl.kt) · [安全控制](security-controls.md)
3. 在数据库维护最终约束。比如公告迁移用 `CHECK` 保证草稿无发布时间，已发布和已撤回状态拥有发布时间。[公告迁移](../zero-system/resources/db/migration/common/V2__positions_and_announcements.sql#L22)

教学例里的 `require(limit >= 0)` 适用于函数调用方的前置条件，失败抛出 `IllegalArgumentException`。当前系统接口的业务校验沿用上述 HTTP 错误辅助函数。若其他模块已配置 Jakarta Bean Validation，可在请求 DTO 的主构造器属性上使用 `@field:NotBlank` 等字段约束，并在控制器的 `@RequestBody` 参数上加 `@Valid` 来触发校验。`@field:` 明确把注解加到 JVM 字段。[Kotlin 注解目标](https://kotlinlang.org/docs/annotations.html#annotation-use-site-targets) · [Spring MVC 校验](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html)

## 写 KDoc

用 `/** ... */` 记录调用方需要知道的约定。`@property` 说明类的属性，适合解释主构造器中的 `val` 或 `var`。`@param` 说明参数的意义或限制。`@return` 说明返回值的含义、排序或缺失约定。项目的 `PageResult` KDoc 就指出了页码起点和 `total` 的范围。以下教学代码进一步记录了从页面到展示模型的契约。[Kotlin KDoc](https://kotlinlang.org/docs/kotlin-doc.html) · [分页类型](../zero-core/src/dev/zerokotlin/core/PageResult.kt#L17)

```kotlin
import dev.zerokotlin.core.PageResult
import dev.zerokotlin.system.dto.UserListEntry

/**
 * 供调用方展示的启用账号信息。
 *
 * @property id 可用于定位账号的系统 ID。
 * @property label 界面展示的账号显示名。
 */
internal data class EnabledUserPreview(val id: String, val label: String)

/**
 * 提取当前页中的启用账号。
 *
 * @param page 已在数据库中完成可见范围筛选的用户页。
 * @return 与原页顺序一致的启用账号信息，当前页没有启用账号时返回空列表。
 */
internal fun enabledUserPreviews(page: PageResult<UserListEntry>): List<EnabledUserPreview> =
    page.items.filter { it.user.enabled }.map { EnabledUserPreview(it.user.id, it.user.displayName) }
```
