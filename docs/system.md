# 系统模块

`zero-system` 提供账号、角色、权限点、菜单、部门、字典和审计 API。它复用 `zero-core` 的数据源、MyBatis-Plus 和 Bearer 过滤链，并通过 `BearerSessionAuthentication` 提供持久化会话查询和权限计算。Flyway 从 `classpath:db/migration/common` 按版本顺序执行系统迁移。MyBatis-Plus 扫描 `classpath*:/mapper/**/*.xml`，实体按驼峰属性映射下划线列名。

## 首次启动

空用户库默认创建 `admin`，登录密码为 `admin`，并关联内置角色 `system-admin`。使用默认编码器时，V9 SQL 写入 BCrypt 哈希。可以在首次启动前用下列环境变量覆盖初始凭据。[管理员初始化 SQL](../zero-system/resources/db/migration/common/V9__initial_administrator.sql)

| 环境变量 | Spring 属性 | 用途 |
| --- | --- | --- |
| `ZERO_SYSTEM_ADMIN_USERNAME` | `zero.system.admin.username` | 首次管理员用户名，默认 `admin`。3–64 个 ASCII 字符，以字母或数字开头，后续也可使用点、下划线和连字符。保存为小写。 |
| `ZERO_SYSTEM_ADMIN_PASSWORD` | `zero.system.admin.password` | 默认账号密码为 `admin`。自定义初始密码至少 12 个 Unicode 码点，最多 72 个 UTF-8 字节。 |
| `ZERO_SYSTEM_SESSION_SECONDS` | `zero.system.session.seconds` | 登录令牌有效期，默认 28800 秒，允许 1–604800 秒。 |

设置自定义凭据时，启动初始化器会使用部署配置的密码编码器创建该账号。初始化后可移除这两个变量。已有用户的数据库保留原密码和角色关联；用户可在个人中心修改密码。

## 登录和会话

发送 `POST /api/auth/login`，请求体为 JSON。

```json
{"username":"admin","password":"admin"}
```

登录成功返回 `accessToken`、`tokenType`（`Bearer`）和 `expiresAt`（Unix 毫秒）。将 `Authorization: Bearer <accessToken>` 加入后续请求。默认由 `zero-core` 提供工作因子为 12 的 BCrypt 编码器。账户创建、首次初始化和修改密码只写入非空哈希。数据库同时保存会话的 SHA-256 摘要。退出删除当前会话摘要。停用或删除账号、修改或重置密码会注销该用户的所有会话。重新启用账号后需重新登录。角色、权限点、账号和令牌状态在每次请求认证时读取数据库，变更提交后下一次请求立即生效。

| 方法和路径 | 结果 |
| --- | --- |
| `GET /api/auth/captcha` | 公开。启用时返回一次性验证码，关闭时返回 204。 |
| `POST /api/auth/login` | 公开。成功返回 200，凭据错误或账号禁用时返回 401。验证码校验失败返回 400，超额返回 429。 |
| `POST /api/auth/logout` | 需要登录。当前会话退出后返回 204。 |
| `GET /api/auth/me` | 需要登录。返回 `id`、`username`、`displayName`、`departmentId`、`administrator` 和 `permissions`。 |
| `GET /api/auth/me/menus` | 需要登录。返回当前用户可见的菜单树。 |

`GET /actuator/health`、`/v3/api-docs` 及 Swagger UI 的 GET 请求可匿名访问。启用存活和就绪探针时，其 GET 端点也可匿名访问。其他端点要求 Bearer 认证。

启用登录验证码后，请在登录请求中附带挑战 ID 和图片中的字符。登录审计响应也可启用地址脱敏。[安全控制](security-controls.md)

## 权限与行级范围

内置角色 `system-admin` 的 ID 为 `system-admin`，拥有所有已启用的权限点及 `ALL` 数据范围。系统 API 保护此角色和 `system:` 权限点的存续。内置管理员可改变账号的部门、状态、角色关联和重置密码，并可维护部门树、角色与权限点。用户可校验旧密码后修改本人的密码。普通角色可获授 `system:user:update`，在授权行范围内更新显示名。账号更新接口要求内置管理员账号维持启用，并保留 `system-admin` 关联。

同一用户可拥有多个角色。对每个权限点只合并授予该权限的已启用角色，采用最宽的数据范围。用户列表、详情和资料更新分别应用对应权限的行级范围。无部门用户的部门范围退回本人。

| `dataScope` | 可访问的用户 |
| --- | --- |
| `SELF` | 登录用户本人 |
| `DEPARTMENT` | 登录用户所在部门的成员 |
| `DEPARTMENT_AND_DESCENDANTS` | 登录用户所在部门和所有下级部门的成员 |
| `ALL` | 全部用户 |

用户详情或更新的目标超出范围时返回 404。分页的部门和角色筛选与行级范围同时生效。部门筛选只匹配所指定部门，角色筛选匹配实际关联（含已停用角色）。XML 用 `EXISTS` 筛选用户，再按本页用户 ID 批量取回全部关联角色。列表项的 `user` 是用户摘要，`roles` 包含角色 ID、编码及名称。

## 管理 API

岗位、公告发布和个人收件箱的接口见[岗位、公告和收件箱](organization-notices.md)。

本人资料、改密、批量字典选项、删除和角色菜单的接口见[个人中心、批量管理和角色菜单](accounts.md)。

以下路径均以 `/api/system` 为前缀。权限列列出完整的 Spring Security authority。分页使用 `page=1&size=20`，`size` 可为 1–100。响应为 `{items,total,page,size}`。

| 方法和路径 | 权限 | 请求和响应 |
| --- | --- | --- |
| `GET /users` | `system:user:read` | 分页。可传 `departmentId`、`roleId`。 |
| `GET /users/{id}` | `system:user:read` | 返回 `{user,roleIds}`。 |
| `POST /users` | `system:user:manage` | JSON 包含 `username`、`displayName`、`password`，可传 `departmentId`、`roleIds`。返回 201 和 `{id}`。 |
| `PATCH /users/{id}` | `system:user:update` | JSON 包含 `displayName`。返回更新后的用户摘要。 |
| `PUT /users/{id}/account` | `system:user:manage` | JSON 包含 `enabled`、`roleIds`，可传 `departmentId`、`password`。`roleIds` 替换现有关联，`departmentId` 省略或为 `null` 表示无部门。返回 204。 |
| `GET /departments/tree` | `system:department:read` | 通过 `children` 返回完整部门树。 |
| `GET /departments/{id}/members` | `system:user:read` | 该部门的用户分页，支持 `roleId`。 |
| `POST /departments`、`PUT /departments/{id}` | `system:department:write` | JSON 包含 `name`，可传 `parentId`、`sortOrder`。`parentId` 缺省或为 `null` 表示根节点。分别返回 201 和 `{id}`、204。 |
| `GET /roles` | `system:role:read` | 分页。可传 `enabled`。 |
| `GET /roles/{id}` | `system:role:read` | 返回 `{role,permissionCodes}`。 |
| `POST /roles` | `system:role:write` | JSON 包含 `code`、`name`、`dataScope`，可传 `permissionCodes`。返回 201 和 `{id}`。 |
| `PUT /roles/{id}` | `system:role:write` | JSON 包含 `name`、`dataScope`、`enabled`、`permissionCodes`。提交完整权限点集合。返回 204。 |
| `GET /permissions` | `system:permission:read` | 权限点列表。 |
| `POST /permissions` | `system:permission:write` | JSON 包含 `code`、`name`。返回 201 和新权限点。 |
| `PUT /permissions/{code}` | `system:permission:write` | JSON 包含 `name`、`enabled`。返回更新后的权限点。 |
| `GET /menus` | `system:menu:read` | 完整菜单树，含停用记录。节点使用 `children` 返回子菜单。 |
| `POST /menus`、`PUT /menus/{id}` | `system:menu:write` | JSON 包含 `name`，可传 `parentId`、`path`、`permissionCode`、`sortOrder`、`enabled`。默认根节点，排序值 0，启用。分别返回 201 和 `{id}`、204。 |
| `GET /dictionaries` | `system:dictionary:read` | 字典列表。 |
| `GET /dictionaries/{id}/items` | `system:dictionary:read` | 字典项列表，含停用项。 |
| `GET /dictionaries/by-code/{code}/items` | 需要登录 | 通过编码查询启用的字典和字典项。 |
| `POST /dictionaries`、`PUT /dictionaries/{id}` | `system:dictionary:write` | 创建传 `code`、`name`，更新传 `name`、`enabled`。分别返回 201 和 `{id}`、204。 |
| `POST /dictionaries/{id}/items`、`PUT /dictionaries/{dictionaryId}/items/{id}` | `system:dictionary:write` | JSON 包含 `value`、`label`，可传 `sortOrder`、`enabled`。默认排序值 0，启用。分别返回 201 和 `{id}`、204。 |
| `GET /audits/logins` | `system:audit:read` | 登录审计分页，可按 `successful` 筛选。 |
| `GET /audits/operations` | `system:audit:read` | 操作审计分页，可按 `actorId`、`action` 筛选。 |

账号、角色、权限点、菜单和字典通过 `enabled` 停用。部门与菜单可以移动到已有父节点或移动到根层级。树结构拒绝循环。菜单的 `path` 为站内路径，也可省略以表示分组。`/api/auth/me/menus` 按节点启用状态、模块、所需权限与祖先可见性筛选。启用直接分配时还应用角色菜单配置。业务 API 的访问由 Spring Security 校验。

认证服务记录成功和失败的登录尝试，包括用户 ID（能识别时）、用户名、结果、请求来源地址和发生时间。审计记录使用 Unix 毫秒。成功的系统管理写入和退出会在同一事务中登记操作审计，包含操作者 ID、操作类型和目标 ID。

## 业务模块注册

可选模块声明 `ModuleMigration("orders")` Bean，并在 `resources/db/modules/orders/V1__orders.sql` 中提供迁移。核心迁移成功后，各模块按自己的版本序列执行。先注册权限，再注册引用它的菜单。

```sql
INSERT INTO sys_permission (code, name)
VALUES ('orders:order:read', '查看订单');

INSERT INTO sys_menu (id, module_name, name, path, permission_code, sort_order)
VALUES ('orders-list', 'orders', '订单列表', '/orders', 'orders:order:read', 100);
```

| 表 | 字段契约 |
| --- | --- |
| `sys_permission` | `code` 为字符串主键，最长 120 字符。建议使用模块命名空间。必填 `name`，`enabled` 默认 1。 |
| `sys_menu` | `id` 为字符串主键，最长 36 字符。必填 `name`。可空 `module_name` 对应可选模块名称，模块移除后导航自动隐藏其菜单。可空 `parent_id`、`path`、`permission_code`，分别关联父菜单、站内路径和权限点。`sort_order` 默认 0，`enabled` 默认 1。 |
| `sys_role_permission` | 复合主键 `(role_id, permission_code)`，关联已有角色、权限点。业务角色的授予关系也可通过系统 API 配置。 |

服务方法使用 `@PreAuthorize("hasAuthority('orders:order:read')")` 鉴权，注入 `dev.zerokotlin.core.CurrentActor` 并调用 `requireUserId()` 获取登录用户。业务模块按自己的实体所有权和组织关系约束查询。在业务模块的 `@Transactional` 写方法中调用 `dev.zerokotlin.core.OperationAudit.record(permission, targetId)`，系统会在当前事务内复核权限并记录操作。业务写入失败时审计一同回滚。
