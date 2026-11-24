# Kotlin 后端开发手册

可从用户列表的调用链了解 Kotlin 在 Ktor 工程中的用法。[AccountController.kt](../zero-system/src/dev/zerokotlin/ktor/system/account/AccountController.kt) 读取分页和筛选参数，[AccountServiceImpl.kt](../zero-system/src/dev/zerokotlin/ktor/system/account/AccountServiceImpl.kt) 校验权限，[UserMapper.kt](../zero-system/src/dev/zerokotlin/ktor/system/account/UserMapper.kt) 执行列表及总数查询，[SystemStore.kt](../zero-system/src/dev/zerokotlin/ktor/SystemStore.kt) 负责会话、权限和 JDBC 事务。

后端源码使用模块下的 `src/`。本页示例在 `zero-system` 主源码中使用工程内的模型。接入业务见[开发业务功能](new-module.md)。

## 定义函数

Kotlin 使用 `fun name(parameter: Type): ReturnType` 声明函数。块体用 `{}`，有返回值时写明返回类型并使用 `return`。表达式体用 `=`，可由编译器推断返回类型。对外接口显式写出返回类型，调用方就能直接从签名读出契约。[Kotlin 函数](https://kotlinlang.org/docs/functions.html)

下面的教学示例使用真实分页类型。把三个函数放在同一个 Kotlin 文件中即可互相调用。

```kotlin
import dev.zerokotlin.ktor.Page
import dev.zerokotlin.ktor.Window
import dev.zerokotlin.ktor.system.account.UserEntry

internal fun userPageWindow(page: Int = 1, size: Int = 20): Window =
    Window(page, size)

private fun Page<UserEntry>.enabledUserCountText(): String {
    val enabled = items.count { it.user.enabled }
    return "第 $page 页有 $enabled 个启用账号"
}

private fun printEnabledUserCount(page: Page<UserEntry>) {
    println(page.enabledUserCountText())
}
```

- `userPageWindow(size = 50)` 用命名实参指定页大小，`page` 取默认值 `1`。Ktor 的用户列表通过 `call.window()` 解析请求默认值。
- `printEnabledUserCount` 的返回类型是 `Unit`，块体可省略显式的 `: Unit`。
- 顶层 `private` 的作用域是当前文件，类成员的 `private` 限于声明它的类。`internal` 对同一个 Kotlin 编译模块（例如 `zero-system` 主源集）可见。新声明的函数默认是 `public`。[Kotlin 可见性](https://kotlinlang.org/docs/visibility-modifiers.html)
- 扩展函数按 `fun 接收者类型.名称(...)` 声明，可通过 `page.enabledUserCountText()` 调用。函数内部可通过 `this` 访问接收者。编译器按接收者的静态类型选择扩展，同签名的成员函数优先。[Kotlin 扩展](https://kotlinlang.org/docs/extensions.html)

## `val`、`var` 与空安全

依赖和中间结果优先声明为 `val`。局部 `val` 只能赋值一次，`val` 属性只提供读取入口。通过 `val` 仍可修改它指向的可变对象。比如 `val ids = mutableListOf<String>()` 可以调用 `ids.add(...)`。`List<T>` 提供只读接口，其他引用仍可能修改它背后的集合。需要重新赋值时用 `var`。

`String` 表示必有值，`String?` 允许缺失。查询中的 `departmentId == null` 表示省略显式的部门筛选，账号可见范围仍然生效。响应中的 `user.departmentId == null` 表示账号没有所属部门。

```kotlin
import dev.zerokotlin.ktor.system.account.User

private fun departmentLabel(user: User, departments: Map<String, String>): String {
    val departmentId = user.departmentId ?: return "未分配部门"
    return departments[departmentId] ?: "部门名称未知"
}
```

- `?.` 仅在接收者有值时调用。`?.let { ... }` 适合变换可选值。账号更新路由用它处理可选的新密码。
- `?:` 在左侧为 `null` 时求右侧的值，右侧也可以提前 `return` 或抛异常。`visibleUser` 用 `?: missing("用户")` 将空查询结果映射为 HTTP 404。`missing` 返回类型为 `Nothing`，表示该调用必然中断当前执行路径。
- 编译器可以在非空检查后缩窄稳定变量的类型。可变属性先读到局部 `val`，后续在同一次读取的值上做判断和访问。调用 JDBC 等 Java API 时，缺少空性标记的返回值可能是平台类型。按 Java API 的契约显式处理可能的 `null`，避免用 `!!` 把检查推迟到运行时。[Kotlin 空安全](https://kotlinlang.org/docs/null-safety.html)

## 用集合函数表达筛选和转换

函数类型 `(UserEntry) -> Boolean` 表示接收一个列表项并返回布尔值。`{ entry -> entry.user.enabled }` 是符合该类型的 lambda，只有一个参数时可写成 `{ it.user.enabled }`。`::isEnabled` 引用已有函数，`User::departmentId` 引用已有属性，两者都可作为相应的函数参数。接收或返回函数的函数叫高阶函数。最后一个实参是 lambda 时，可放到右括号外。[Kotlin 高阶函数与 lambda](https://kotlinlang.org/docs/lambdas.html)

这些教学函数消费已经查询出的 `Page<UserEntry>`，例如用于展示本页统计。

```kotlin
import dev.zerokotlin.ktor.Page
import dev.zerokotlin.ktor.system.account.AssignedRole
import dev.zerokotlin.ktor.system.account.UserEntry
import dev.zerokotlin.ktor.system.account.User

private fun usersMatching(
    page: Page<UserEntry>,
    matches: (UserEntry) -> Boolean,
): List<UserEntry> = page.items.filter(matches)

private fun isEnabled(entry: UserEntry): Boolean = entry.user.enabled

private fun enabledUsersByDepartment(
    page: Page<UserEntry>,
): Map<String?, List<User>> =
    usersMatching(page, ::isEnabled)
        .map { it.user }
        .groupBy(User::departmentId)

private fun rolesInDepartment(
    page: Page<UserEntry>,
    departmentId: String,
): Map<String, AssignedRole> =
    usersMatching(page) { it.user.departmentId == departmentId }
        .flatMap { it.roles }
        .associateBy(AssignedRole::id)
```

| 操作 | 结果与用途 |
| --- | --- |
| `filter` | 保留符合条件的列表项。 |
| `map` | 把每个列表项变为一个新值，例如从 `UserEntry` 取出 `User`。 |
| `flatMap` | 将每项对应的集合展开成一个列表，例如取出本页若干账号的全部角色。 |
| `associateBy` | 按键索引，每个键保留最后遇到的值。示例按角色 ID 合并重复角色。 |
| `groupBy` | 按键保留一组值。可空的部门 ID 会形成 `null` 分组。 |

项目在 `Connection.userPage` 中先提取本页账号 ID，通过一次查询取回角色，再用 `groupBy(keySelector = ..., valueTransform = ...)` 按账号组装角色 DTO。没有角色的账号通过 `orEmpty()` 得到空列表。涉及可见范围、部门、角色等全量筛选时，在数据库中应用条件，并在总数查询中使用同一条件。当前页在内存里的计算仅覆盖已经选出的记录。 · [Kotlin 集合变换](https://kotlinlang.org/docs/collection-transformations.html)

`List` 上的这些变换逐步求值，多个变换通常产生中间集合。`Sequence` 的 `filter`、`map` 和 `take` 在终止操作消费时才求值。以下函数可接在同一段教学代码后面，从本页只取前若干个启用账号的显示名。

```kotlin
private fun firstEnabledNames(page: Page<UserEntry>, limit: Int): List<String> {
    require(limit >= 0) { "limit 必须大于等于 0" }
    return page.items.asSequence()
        .filter(::isEnabled)
        .map { it.user.displayName }
        .take(limit)
        .toList()
}
```

- 简短的分页 DTO 转换可直接使用集合操作。链条较长、数据量大或可以提前结束时，再考虑 `Sequence`。`asSequence()` 基于已加载的本页数据，数据库查询仍由 JDBC 完成。[Kotlin 序列](https://kotlinlang.org/docs/sequences.html)
- 让筛选和转换 lambda 专注于计算值。序列中的副作用可能延迟到消费时发生，也可能因为 `take` 提前结束而只发生在部分元素上。重复遍历可重复计算。需要写库、发消息或记录日志时，用清晰的调用位置表达执行时机。
- 有 `break`、`continue`、多步状态更新或逐项错误处理时，普通 `for` 或 `while` 往往更好读。层级校验可以 先用 `associateBy` 建立 ID 索引，再用 `while` 沿父节点向上检查缺失和循环。

## 选择类、枚举或接口

| 声明 | 何时使用 | 示例 |
| --- | --- | --- |
| `class` | 对象有行为、生命周期或独立的可变状态。普通类继承 `Any.equals` 时按实例比较。 | `Window` 封装分页校验和偏移计算。 |
| `data class` | 一组字段共同定义值，需要生成 `equals`、`hashCode` 或 `copy`。 | `Page<T>`、`User`、`AnnouncementRequest`。 |
| `enum class` | 状态集合固定，每个常量有唯一实例，且可定义属性和方法。 | `AnnouncementStatus` 的 `DRAFT`、`PUBLISHED`、`REVOKED`。 |
| `sealed class` / `sealed interface` | 可选分支有限，每个分支可以携带不同的数据。需要基类构造器或共享状态时用 `sealed class`，只需契约时用 `sealed interface`。 | 下方的 `UserLookup` 教学示例。 |
| `object` | 需要单个具名实例来提供行为或共享状态。 | `BearerTokens` 集中定义令牌操作。 |
| `interface` | 调用方依赖行为契约，实现可独立替换。单抽象方法可声明为 `fun interface` 并用 lambda 构造。 | `FileStorage`、`SecurityState`。 |

### 值相等与浅拷贝

`==` 比较结构上的相等性，调用 `equals`。`===` 比较是否引用同一个实例。`data class` 默认根据主构造器中声明的属性生成 `equals` 和 `hashCode`，类体中另行声明的属性不参与。把对象用作 `HashMap` 键或放进 `HashSet` 时，保持参与相等性和哈希计算的值稳定。[Kotlin 相等性](https://kotlinlang.org/docs/equality.html) · [Kotlin 数据类](https://kotlinlang.org/docs/data-classes.html)

`copy()` 创建新的数据类实例。以 `val copied = page.copy()` 为例，`copied == page` 为 `true`，`copied === page` 为 `false`，`copied.items === page.items` 为 `true`。如果其他引用修改了 `items` 的底层可变列表，两个页面都会观察到变化。需要保存列表内容的快照时，可以传入 `page.copy(items = page.items.toList())`，其中的列表元素及其 `roles` 属性仍会共享引用。

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

项目将公告状态以字符串写入数据库，并在响应中返回。`PUBLISHED` 同时出现在收件箱查询里，迁移中的约束则检查状态与发布时间的组合。修改枚举常量名时，先核对已有数据、表约束、查询和 API 调用方，并安排兼容或迁移。新领域需要独立演进时，可以给枚举定义稳定的 `code`，在数据库和 HTTP 边界显式映射。`ordinal` 取决于声明顺序，插入常量或调整顺序可能改变已有常量的序号。 · [公告 API](organization-notices.md#公告管理) · [公告迁移](../zero-system/resources/db/migration/common/V2__positions_and_announcements.sql#L22) · [Kotlin 枚举](https://kotlinlang.org/docs/enum-classes.html)

### 用密封类型处理不同结果

下面的教学示例让找到用户的分支携带详情，缺失的分支只表达状态。`when` 表达式覆盖所有分支后可以省略 `else`。新增分支后，编译器会指出哪些 `when` 表达式需要补齐分支。当前仓库的用户详情服务通过 `missing("用户")` 处理查无用户。[Kotlin 密封类型](https://kotlinlang.org/docs/sealed-classes.html)

```kotlin
import dev.zerokotlin.ktor.system.account.UserDetail

private sealed interface UserLookup {
    data class Found(val details: UserDetail) : UserLookup
    data object Missing : UserLookup
}

private fun lookupLabel(result: UserLookup): String = when (result) {
    is UserLookup.Found -> result.details.user.displayName
    UserLookup.Missing -> "未找到用户"
}
```

## 在 Ktor 中落地

- 请求 DTO 定义可写字段、类型和默认值。含密码的 DTO 避免直接记录。响应模型只返回允许公开的数据。
- 路由通过 `call.receive<T>()` 读取请求。分页使用 `call.window()`，业务校验可使用 `invalid`、`missing`、`conflict` 返回对应 HTTP 错误。
- `SystemStore.read` 和 `SystemStore.write` 在数据库事务内校验会话及权限。系统写入在持有写锁时复核权限，业务变更和操作审计可同事务提交。[系统事务与权限](../zero-system/src/dev/zerokotlin/ktor/SystemStore.kt)

## 写 KDoc

用 `/** ... */` 记录调用方需要知道的约定。`@property` 说明类的属性，适合解释主构造器中的 `val` 或 `var`。`@param` 说明参数的意义或限制。`@return` 说明返回值的含义、排序或缺失约定。分页接口文档宜说明页码起点和 `total` 的范围。以下教学代码进一步记录了从页面到展示模型的契约。[Kotlin KDoc](https://kotlinlang.org/docs/kotlin-doc.html)

```kotlin
import dev.zerokotlin.ktor.Page
import dev.zerokotlin.ktor.system.account.UserEntry

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
internal fun enabledUserPreviews(page: Page<UserEntry>): List<EnabledUserPreview> =
    page.items.filter { it.user.enabled }.map { EnabledUserPreview(it.user.id, it.user.displayName) }
```
