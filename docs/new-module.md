# 新建业务模块

仓库中的 `zero-agent` 演示最小模块。它的 Gradle 脚本声明对 `zero-core` 和 AgentScope SDK 的依赖，由启动模块决定是否装配。下文的 `zero-orders` 教学示例在同一目录结构上增加数据库、权限、HTTP 接口和管理端页面。[zero-agent 模块说明](../zero-agent/README.md) · [zero-agent 构建脚本](../zero-agent/build.gradle.kts)

## 依赖边界与拆分时机

每个新业务模块直接依赖 `zero-core`。它用 `api` 暴露 Spring MVC、Spring Security、MyBatis-Plus 和 Swagger 注解，并提供 `CurrentActor`、`OperationAudit`、`ModuleMigration` 和分页类型。业务模块通过这些契约获取当前用户、记录审计、注册迁移和描述接口。[核心依赖](../zero-core/build.gradle.kts) · [Gradle Java Library 依赖范围](https://docs.gradle.org/current/userguide/java_library_plugin.html)

以 `zero-system` 为唯一启动项目时，Gradle 依赖如下。箭头从使用方指向依赖方。

```text
zero-system ── implementation ──▶ zero-core
zero-system ── runtimeOnly ──────▶ zero-orders
zero-orders ── implementation ──▶ zero-core
```

`zero-system` 把业务库加入运行时类路径。`dev.zerokotlin.ZeroKotlinApplication` 从 `dev.zerokotlin` 扫描组件，MyBatis-Plus 按 Boot 应用包扫描带 `@Mapper` 的接口。业务模块在 `dev.zerokotlin.orders` 下注册自己的组件，启动项目便可通过扫描装配。Gradle 项目依赖保持有向无环，业务 Bean 通过 Spring 容器取得具体实现。[系统构建配置](../zero-system/build.gradle.kts) · [启动入口](../zero-system/src/dev/zerokotlin/ZeroKotlinApplication.kt) · [Spring Boot 的根包示例](https://docs.spring.io/spring-boot/reference/using/structuring-your-code.html)

| 业务所需能力 | 完整系统在运行期的供给方 |
| --- | --- |
| 登录、Bearer 认证与方法鉴权 | `zero-system` 提供登录接口及 `BearerSessionAuthentication` Bean，用持久化会话解析用户，将有效权限点映射成 Spring Security authority。`zero-core` 的 Bearer 自动配置以该 Bean 为条件启用过滤链和方法鉴权。[Bearer 自动配置](../zero-core/src/dev/zerokotlin/core/config/BearerSecurityAutoConfiguration.kt) · [系统认证装配](../zero-system/src/dev/zerokotlin/system/config/SystemSecurity.kt) |
| 当前操作者 | `zero-system` 提供 `CurrentActor` Bean，从已认证的 principal 读取用户 ID。[CurrentActor](../zero-core/src/dev/zerokotlin/core/CurrentActor.kt) · [系统认证装配](../zero-system/src/dev/zerokotlin/system/config/SystemSecurity.kt) |
| 操作审计 | `zero-system` 提供 `OperationAudit` 实现，要求调用方已有事务。在事务内重新核验会话与权限，再写入 `sys_operation_audit`。[OperationAudit](../zero-core/src/dev/zerokotlin/core/OperationAudit.kt) · [系统写入与审计](../zero-system/src/dev/zerokotlin/system/service/SystemWrites.kt) |
| 数据库与导航 | `zero-core` 先执行主迁移，再执行已声明 `ModuleMigration` Bean 的独立迁移。订单权限和菜单种子依赖 `zero-system` 主迁移建立的 `sys_permission` 和 `sys_menu`。系统按已装配模块和当前权限返回可见菜单。[迁移策略](../zero-core/src/dev/zerokotlin/core/config/DatabaseAutoConfiguration.kt) · [系统建表](../zero-system/resources/db/migration/common/V1__system.sql) · [菜单可见性](../zero-system/src/dev/zerokotlin/system/service/SystemOrganization.kt#L137) |

当前建议保留 `zero-system` 的启动入口、账号、权限、菜单和审计。现有 `zero-core` 契约已经覆盖接入这些能力的最小需求，系统功能也共用会话模型、数据源和事务管理。业务库如果需要独立运行，须另行提供启动入口、认证与审计实现，以及自身迁移所依赖的系统表。出现以下需求时再拆分。

- 多个业务模块确实需要同一项组织查询时，例如根据部门 ID 批量查询名称，可以在 `zero-core` 增加一个只读接口和必要的返回类型，由 `zero-system` 实现。业务模块仍只依赖核心契约，表结构、Mapper 和查询实现都由系统模块维护。
- 需要多个应用产物或长期维护不同的模块组合。可以把入口和装配移到独立的 `zero-boot`，让它单向依赖 `zero-system` 与各业务库。每个应用产物保留一个启动入口。
- 某个业务已经拥有独立的数据、权限或演进周期，而且需要按应用选配。把该业务移到同级库，让它维护自己的表、权限和迁移历史。订单示例采用这种结构。

## 创建并查看自己的订单

在仓库根目录新增 `zero-orders`。示例接受订单标题，返回新订单 ID，并分页列出当前用户创建的订单。API 路径为 `/api/orders`，管理端路径为 `/orders`。

### 1. 注册库并装配到启动项目

在根目录 `settings.gradle.kts` 追加新项目，保留已有模块。[当前项目清单](../settings.gradle.kts)

```kotlin
include("zero-orders")
```

创建 `zero-orders/build.gradle.kts`。根构建已经统一配置 JDK 25、`src/` 和 `resources/`。`kotlin.spring` 会开放 Spring 组件供方法鉴权和事务代理使用。示例中的 `@Tag` 和 `@Operation` 由核心模块导出。[根构建配置](../build.gradle.kts) · [核心依赖](../zero-core/build.gradle.kts)

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
}

dependencies {
    implementation(project(":zero-core"))
}
```

在 `zero-system/build.gradle.kts` 的 `dependencies` 块中增加一行。`bootRun` 和 `bootJar` 都使用主源集的运行时类路径。如果启动项目需要在源码里显式导入订单模块的配置类，将这一行改为 `implementation(project(":zero-orders"))`，依赖方向相同。[系统构建配置](../zero-system/build.gradle.kts) · [Spring Boot Gradle 插件的打包与运行](https://docs.spring.io/spring-boot/gradle-plugin/reacting.html)

```kotlin
runtimeOnly(project(":zero-orders"))
```

给新库添加以下源码与资源。后端源码小节的文件名相对于 `zero-orders/src/dev/zerokotlin/orders/`。

```text
zero-orders/
├── build.gradle.kts
├── src/dev/zerokotlin/orders/
│   ├── config/OrdersConfiguration.kt
│   ├── controller/OrderController.kt
│   ├── dto/OrderModels.kt
│   ├── entity/OrderRecord.kt
│   ├── mapper/OrderMapper.kt
│   └── service/OrderService.kt
└── resources/db/modules/orders/V1__orders.sql
```

### 2. 声明迁移、权限和菜单

创建 `config/OrdersConfiguration.kt`，将模块名 `orders` 注册为 Bean。这个名字也用于迁移目录和菜单的 `module_name`。[ModuleMigration 契约](../zero-core/src/dev/zerokotlin/core/ModuleMigration.kt) · [内置扩展的注册示例](../zero-system/src/dev/zerokotlin/system/config/SystemExtensionMigrations.kt)

```kotlin
package dev.zerokotlin.orders.config

import dev.zerokotlin.core.ModuleMigration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class OrdersConfiguration {
    @Bean
    fun ordersModuleMigration() = ModuleMigration("orders")
}
```

创建 `resources/db/modules/orders/V1__orders.sql`。`owner_id` 关联创建订单的用户，`created_at` 使用 Unix 毫秒。先插入权限，再插入引用读权限的菜单。管理员拥有新注册且启用的权限，普通账号由管理员通过角色授予相应权限。[系统权限及菜单字段](system.md#业务模块注册) · [内置权限种子示例](../zero-system/resources/db/modules/kafka/V1__kafka_permissions.sql)

```sql
CREATE TABLE orders_order (
    id VARCHAR(36) PRIMARY KEY NOT NULL,
    title VARCHAR(120) NOT NULL,
    owner_id VARCHAR(36) NOT NULL,
    created_at BIGINT NOT NULL,
    FOREIGN KEY (owner_id) REFERENCES sys_user (id)
);

CREATE INDEX idx_orders_order_owner_created ON orders_order (owner_id, created_at, id);

INSERT INTO sys_permission (code, name) VALUES
    ('orders:order:read', '查看订单'),
    ('orders:order:write', '创建订单');

INSERT INTO sys_menu (id, module_name, name, path, permission_code, sort_order)
VALUES ('orders-list', 'orders', '订单列表', '/orders', 'orders:order:read', 100);
```

模块发布后固定 `orders` 和已执行的迁移脚本。后续变更追加 `V2__*.sql` 等新版本，沿用 `zero_history_orders`。要暂时停用模块，从启动项目移除装配依赖。重启后系统菜单隐藏该模块，表、权限种子、菜单记录和独立迁移历史保留。重新加入同名模块时，Flyway 校验已有历史并执行待应用的版本。[迁移说明](data.md#flyway-迁移) · [可选模块装配与卸载](extensions.md#添加或移除自己的模块)

### 3. 实现订单写入和按归属分页

创建 `dto/OrderModels.kt`。创建接口接收 `title` 并返回 `{id}`，列表项包含 `id`、`title` 和 `createdAt`。

```kotlin
package dev.zerokotlin.orders.dto

data class CreateOrderRequest(val title: String)
data class CreatedOrder(val id: String)
data class OrderEntry(val id: String, val title: String, val createdAt: Long)
```

创建 `entity/OrderRecord.kt`，沿用现有实体的主键和列名映射约定。[系统实体示例](../zero-system/src/dev/zerokotlin/system/entity/PositionRecord.kt)

```kotlin
package dev.zerokotlin.orders.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName

@TableName("orders_order")
class OrderRecord(
    @field:TableId(type = IdType.INPUT)
    var id: String = "",
    var title: String = "",
    var ownerId: String = "",
    var createdAt: Long = 0,
)
```

创建 `mapper/OrderMapper.kt`。示例只需 `BaseMapper` 的插入和分页。复杂查询可在 `resources/mapper/orders/` 增加 XML。[MyBatis-Plus 的 Mapper 和分页配置](data.md#mybatis-plus-与可选-jpa)

```kotlin
package dev.zerokotlin.orders.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import dev.zerokotlin.orders.entity.OrderRecord
import org.apache.ibatis.annotations.Mapper

@Mapper
interface OrderMapper : BaseMapper<OrderRecord>
```

创建 `service/OrderService.kt`。方法鉴权检查是否拥有对应权限点，SQL 的 `owner_id` 条件决定可见记录。`CurrentActor` 只返回用户 ID，本例采用固定的本人范围，即使系统角色具有 `ALL` 也按本人过滤。如果业务要采用部门及下级部门范围，应先为订单定义组织归属，再由系统实现通过明确的契约提供当前权限的数据范围，并将行级条件用于查询和更新。[现有行级权限示例](../zero-system/src/dev/zerokotlin/system/service/SystemUsers.kt) · [系统数据范围](system.md#权限与行级范围)

```kotlin
package dev.zerokotlin.orders.service

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.extension.plugins.pagination.Page
import dev.zerokotlin.core.CurrentActor
import dev.zerokotlin.core.OperationAudit
import dev.zerokotlin.core.PageResult
import dev.zerokotlin.core.PageWindow
import dev.zerokotlin.orders.dto.CreateOrderRequest
import dev.zerokotlin.orders.dto.CreatedOrder
import dev.zerokotlin.orders.dto.OrderEntry
import dev.zerokotlin.orders.entity.OrderRecord
import dev.zerokotlin.orders.mapper.OrderMapper
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

private const val READ_ORDER = "orders:order:read"
private const val WRITE_ORDER = "orders:order:write"

@Service
class OrderService(
    private val orders: OrderMapper,
    private val actor: CurrentActor,
    private val audit: OperationAudit,
) {
    @PreAuthorize("hasAuthority('$READ_ORDER')")
    @Transactional(readOnly = true)
    fun list(window: PageWindow): PageResult<OrderEntry> {
        val results = orders.selectPage(
            Page<OrderRecord>(window.page.toLong(), window.size.toLong()),
            QueryWrapper<OrderRecord>().eq("owner_id", actor.requireUserId()).orderByDesc("created_at", "id"),
        )
        return window.result(results.records.map { OrderEntry(it.id, it.title, it.createdAt) }, results.total)
    }

    @PreAuthorize("hasAuthority('$WRITE_ORDER')")
    @Transactional
    fun create(request: CreateOrderRequest): CreatedOrder {
        val title = request.title.trim()
        if (title.length !in 1..120) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "title 必须为 1 到 120 个字符")
        }
        val order = OrderRecord(
            id = UUID.randomUUID().toString(),
            title = title,
            ownerId = actor.requireUserId(),
            createdAt = System.currentTimeMillis(),
        )
        check(orders.insert(order) == 1) { "订单写入失败" }
        audit.record(WRITE_ORDER, order.id)
        return CreatedOrder(order.id)
    }
}
```

控制器通过注入的服务调用公开方法，Spring 代理据此应用鉴权与事务。服务从认证上下文取归属 ID。订单插入与 `audit.record` 共用同一事务，审计复核或写入抛出异常时，订单写入随事务回滚。[系统审计实现](../zero-system/src/dev/zerokotlin/system/service/SystemWrites.kt#L26)

### 4. 发布 HTTP 接口和 OpenAPI 文档

创建 `controller/OrderController.kt`。`GET /api/orders?page=1&size=20` 返回 `{items,total,page,size}`。`POST /api/orders` 接收 `{"title":"第一笔订单"}`，成功返回 HTTP 201 和 `{id}`。[公共分页类型](../zero-core/src/dev/zerokotlin/core/PageResult.kt)

```kotlin
package dev.zerokotlin.orders.controller

import dev.zerokotlin.core.PageResult
import dev.zerokotlin.core.PageWindow
import dev.zerokotlin.orders.dto.CreateOrderRequest
import dev.zerokotlin.orders.dto.CreatedOrder
import dev.zerokotlin.orders.dto.OrderEntry
import dev.zerokotlin.orders.service.OrderService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/orders")
@Tag(name = "订单")
class OrderController(private val orders: OrderService) {
    @GetMapping
    @Operation(summary = "分页查看本人订单", description = "需要 orders:order:read 权限")
    @ApiResponse(responseCode = "200", description = "本人订单分页", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", description = "页码或每页条数越界")
    @ApiResponse(responseCode = "403", description = "缺少订单读取权限")
    fun list(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResult<OrderEntry> = orders.list(PageWindow(page, size))

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "创建订单", description = "需要 orders:order:write 权限")
    @ApiResponse(responseCode = "201", description = "新订单的 ID", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", description = "订单标题为空或过长")
    @ApiResponse(responseCode = "403", description = "缺少订单创建权限")
    fun create(@RequestBody request: CreateOrderRequest): CreatedOrder = orders.create(request)
}
```

启动后，在 OpenAPI JSON 中查看 `paths["/api/orders"]`，或打开 Swagger UI 的「订单」标签。在 **Authorize** 中填写登录返回的 `accessToken` 即可调用。核心配置会为 Bearer 接口补充 401；本例的权限检查位于服务层，所以控制器显式记录 403。[Swagger 使用指南](openapi.md) · [OpenAPI JSON](http://localhost:8080/v3/api-docs) · [Swagger UI](http://localhost:8080/swagger-ui/index.html)

### 5. 注册管理端路由和页面

在 `zero-web/src/router/navigation.ts` 的 `extensionPages` 数组中注册页面。`ClipboardList` 已在该文件导入。[页面注册表](../zero-web/src/router/navigation.ts)

```ts
const extensionPages: Page[] = [
  {
    path: '/orders',
    label: '订单列表',
    icon: ClipboardList,
    authority: 'orders:order:read',
    view: () => import('@/views/OrdersView.vue'),
  },
]
```

`router/index.ts` 根据页面注册表创建固定路由。登录后前端从 `/api/auth/me/menus` 获取可见菜单，并用 `/orders` 关联页面。菜单须启用，当前用户须拥有 `orders:order:read`，后端须装配 `orders`。创建表单再按 `orders:order:write` 显示，服务端方法权限始终在请求时检查。[路由与守卫](../zero-web/src/router/index.ts) · [会话与菜单读取](../zero-web/src/store/auth.ts)

创建 `zero-web/src/views/OrdersView.vue`，通过现有请求客户端和分页组件接入两个接口。

```vue
<script setup lang="ts">
import { ref } from 'vue'
import { request } from '@/api/client'
import ResourcePager from '@/components/ResourcePager.vue'
import { errorMessage, formatTime, useResourcePage } from '@/hooks/useResourcePage'
import { useAuth } from '@/store/auth'

type OrderEntry = { id: string; title: string; createdAt: number }

const auth = useAuth()
const endpoint = '/api/orders'
const { items, total, page, lastPage, loading, error, reload } = useResourcePage<OrderEntry>(
  (number, size) => auth.can('orders:order:read') ? `${endpoint}?page=${number}&size=${size}` : null,
  '无法加载订单。',
)

const title = ref('')
const saving = ref(false)
const saveError = ref('')

async function createOrder() {
  if (!auth.can('orders:order:write') || saving.value || !title.value.trim()) return
  saving.value = true
  saveError.value = ''
  try {
    await request<{ id: string }>(endpoint, { method: 'POST', body: { title: title.value.trim() } })
    page.value = 1
    title.value = ''
    reload()
  } catch (cause) {
    saveError.value = errorMessage(cause, '无法创建订单。')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section aria-labelledby="orders-title">
    <header class="page-header"><h1 id="orders-title">订单列表</h1></header>
    <div class="page-sections">
      <form v-if="auth.can('orders:order:write')" class="panel" @submit.prevent="createOrder">
        <label class="field">
          订单标题
          <input v-model.trim="title" required maxlength="120" :disabled="saving" />
        </label>
        <button class="btn btn-primary" type="submit" :disabled="saving || !title">创建订单</button>
        <p v-if="saveError" role="alert">{{ saveError }}</p>
      </form>
      <section class="panel" aria-label="本人订单" :aria-busy="loading">
        <p v-if="loading" role="status">正在加载订单…</p>
        <p v-else-if="error" role="alert">{{ error }}</p>
        <p v-else-if="items.length === 0">暂无订单。</p>
        <ul v-else>
          <li v-for="item in items" :key="item.id">{{ item.title }} · {{ formatTime(item.createdAt) }}</li>
        </ul>
        <ResourcePager v-model:page="page" :last-page="lastPage" :total="total" label="订单分页" :disabled="loading" />
      </section>
    </div>
  </section>
</template>
```

### 6. 启动并验收

1. 在仓库根目录执行 `./gradlew :zero-system:bootRun`。空库启动前先配置管理员环境变量，已有库继续使用原来的账号。订单迁移成功时，数据库中应出现 `orders_order` 和 `zero_history_orders`。[首次启动](../README.md#首次启动)
2. 用管理员账号从 Swagger UI 登录并完成 Bearer 授权，再发送 `POST /api/orders` 创建订单。记录 HTTP 201 响应中的 `id`。[登录接口](system.md#登录和会话)
3. 调用 `GET /api/orders?page=1&size=20`，确认 `items` 包含刚创建的 ID。
4. 用管理员账号查询 `GET /api/system/audits/operations?action=orders:order:write`，确认该订单的 ID 出现在 `targetId`，`actorId` 为创建者的用户 ID。[审计 API](system.md#管理-api)
5. 用普通账号验证访问边界。给 A 授予读写权限，给 B 仅授予读权限。A 创建订单后，B 的 `items` 和 `total` 应只反映 B 自己的订单，尝试创建时应返回 403。用已登录但无读权限的账号查询应返回 403，匿名查询应返回 401。
6. 在 `zero-web/` 启动 `pnpm dev`，验证管理端。用 A 登录，确认「订单列表」显示在侧栏，打开 `/orders` 后可创建并看到自己的订单。用 B 登录时列表可见，创建表单隐藏。已登录但无读权限的账号直接访问 `/orders` 时应回到收件箱。[管理端启动](../README.md#启动管理端)
7. 在开发库验证订单模块可重新装配。暂时移除 `zero-system` 的订单依赖并重启，确认 `/api/auth/me/menus` 隐藏订单菜单。恢复依赖再启动，确认原订单可查询且 Flyway 沿用 `zero_history_orders`。
