# 岗位、公告和收件箱

所有接口均需要 Bearer 会话。`GET /api/auth/me` 同时返回 `administrator`，表示当前账号是否具有启用的内置管理员角色。权限点继续由 `permissions` 提供。`/api/auth/me/menus` 提供对应菜单，登录用户均可访问 `/inbox`。岗位和公告管理菜单分别要求对应的读取权限。

分页使用 `page=1&size=20`，`size` 支持 1–100。响应为 `{items,total,page,size}`，`total` 表示本次筛选后的总数。所有时间字段使用 Unix 毫秒。

## 岗位管理

| 方法和路径 | 权限 | 请求和响应 |
| --- | --- | --- |
| `GET /api/system/positions` | `system:position:read` | 分页。每项为 `{id,code,name,enabled,sortOrder}`。按 `sortOrder`、`id` 升序。 |
| `POST /api/system/positions` | 内置管理员及 `system:position:write` | 请求 `{code,name,sortOrder}`。新岗位默认启用，返回 201 和 `{id}`。 |
| `PUT /api/system/positions/{id}` | 内置管理员及 `system:position:write` | 请求 `{name,enabled,sortOrder}`。返回 204。 |
| `GET /api/system/positions/{id}/members` | 内置管理员及 `system:position:read` | 返回完整的 `[{userId,username,displayName}]`，按用户名排序，包含已停用账号。 |
| `PUT /api/system/positions/{id}/members` | 内置管理员及 `system:position:write` | 请求 `{userIds}`，元素为账号 ID。事务内验证账号后替换完整名单，返回 204。空数组清空成员。 |

岗位编码沿用系统编码格式且全局唯一，创建后固定。岗位目录是全局配置。岗位成员编辑应以完整成员接口为准，在此基础上增删选中的账号。分页查找候选人时应保留已选成员。若并发替换成员名单，以最后提交的完整名单为准。停用岗位仍保留成员关系，角色及权限决定账号的访问范围。

## 公告管理

| 方法和路径 | 权限 | 请求和响应 |
| --- | --- | --- |
| `GET /api/system/announcements` | `system:announcement:read` | 分页。每项为 `{id,title,body,status,createdAt,updatedAt,publishedAt}`，按 `createdAt`、`id` 降序。 |
| `POST /api/system/announcements` | `system:announcement:write` | 请求 `{title,body}`，创建草稿，返回 201 和 `{id}`。 |
| `PUT /api/system/announcements/{id}` | `system:announcement:write` | 请求 `{title,body}`，保存草稿。返回 204。 |
| `POST /api/system/announcements/{id}/publish` | `system:announcement:write` | 发布 `DRAFT` 公告，设为 `PUBLISHED`。返回 204。 |
| `POST /api/system/announcements/{id}/revoke` | `system:announcement:write` | 撤回 `PUBLISHED` 公告，设为终态 `REVOKED`。返回 204。 |

标题和正文字段去除首尾空白后须有内容，最长分别为 120 和 16,000 个 UTF-16 代码单元。正文字段按纯文本处理。草稿仅在管理接口可见，`publishedAt` 为 `null`。撤回保留原发布时间，撤回时间记录在 `updatedAt`。对当前状态不适用的操作返回 409。

## 个人收件箱

| 方法和路径 | 请求和响应 |
| --- | --- |
| `GET /api/inbox` | 分页。可传 `unreadOnly=true` 只查询未读，默认包含已读和未读。每项为 `{id,title,body,publishedAt,readAt}`，按 `publishedAt`、`id` 降序。未读的 `readAt` 为 `null`。 |
| `PUT /api/inbox/{id}/read` | 将当前登录用户的指定公告标为已读。返回 204，重复提交保留首次已读时间。未发布、已撤回或不存在的公告返回 404。 |

收件箱实时查询全局已发布公告与当前账号的已读记录。新账号登录后也能看到仍处于发布状态的公告。撤回提交后，后续收件箱请求即排除该公告。账号身份由服务端会话确定，已读记录按账号和公告唯一。

## 数据库与审计

核心 Flyway 迁移 `V2__positions_and_announcements.sql` 在 V1 后创建岗位、岗位成员、公告和已读表，并注册权限与菜单。岗位写入、成员替换及公告管理操作沿用系统事务锁，操作审计随变更一同提交。标记已读也使用该锁，避免与撤回或同一账号的重复请求交错写入。

功能设计参考 JeecgBoot 的岗位人员维护和个人消息列表。[岗位管理指南](https://help.jeecg.com/java/system/departmentPost/) · [系统消息指南](https://help.jeecg.com/ui/2dev/systemjump/)
