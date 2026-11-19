# 个人中心、批量管理和角色菜单

## 本人资料和密码

以下接口使用 Bearer 会话身份。Web 的个人中心只要求登录。

| 方法和路径 | 行为 |
| --- | --- |
| `GET /api/auth/me/profile` | 返回 `{username,displayName,email,mobile}`。 |
| `PUT /api/auth/me/profile` | 提交 `{displayName,email,mobile}`，返回更新后的本人资料。省略联系方式或传 `null`、空字符串会清除相应字段。 |
| `PUT /api/auth/me/password` | 提交 `{oldPassword,newPassword}`。旧密码错误或新密码格式无效时返回 400，成功返回 204。 |
| `PUT /api/system/users/{id}/password` | 内置管理员凭 `system:user:manage` 提交 `{newPassword}`，为指定账号重置密码，成功返回 204。 |

显示名去除首尾空白后须为 1～120 字符。邮箱须符合基本格式且最多 254 字符。手机号接受 6～15 位数字及可选的国际前缀 `+`。新密码至少 12 个 Unicode 码点，最多 72 个 UTF-8 字节。修改或重置密码会撤销该账号的全部会话。本人改密成功后，Web 退出当前会话。原账号设置 API 继续接受可选的 `password` 字段。联系方式仅通过本人资料接口读取。

## 字典选项批量查询

登录后发送 `POST /api/system/dictionaries/by-codes/items`，请求体例如 `{"codes":["order_status","region"]}`。每次接受 1～100 个合法编码，重复编码合并。响应按编码分组，每组仅含启用的选项，按排序值和 ID 升序排列。缺失或停用的字典省略，启用但无可用选项的字典返回空数组。单编码接口继续对缺失或停用的字典返回 404。字典页可选中多个字典后预览。

## 单条和批量删除

删除要求内置管理员身份及资源对应的权限点。下表路径以 `/api/system` 为前缀。

| 资源 | 单条 `DELETE` | 批量 `POST` | 权限点和约束 |
| --- | --- | --- | --- |
| 账号 | `/users/{id}` | `/users/batch-delete` | `system:user:manage`。内置管理员账号受保护。删除时撤销会话并清除角色、岗位及公告已读关联，保留历史审计。 |
| 角色 | `/roles/{id}` | `/roles/batch-delete` | `system:role:write`。内置角色受保护。已分配给任一账号的角色返回 409，删除时清除其权限和菜单关联。 |
| 字典 | `/dictionaries/{id}` | `/dictionaries/batch-delete` | `system:dictionary:write`。仍含字典项的字典返回 409。 |
| 字典项 | `/dictionaries/{dictionaryId}/items/{id}` | `/dictionaries/{dictionaryId}/items/batch-delete` | `system:dictionary:write`。每次删除的字典项须属于指定字典。 |

批量提交 `{"ids":["id-1","id-2"]}`，接受 1～100 项，重复 ID 合并。全部成功返回 204，非法输入或内置目标返回 400，资源不存在返回 404。单条删除和批量删除共用相同约束。每批业务变更、关联清理和每条资源的操作审计在同一事务提交，任一失败整批回滚。用户页和角色页按当前页选择，字典项按当前字典选择。

## 角色直接分配菜单

在角色页选择业务角色的「分配菜单」。查询 `GET /api/system/roles/{id}/menus` 需要 `system:role:read`。勾选面板还使用 `system:menu:read` 加载菜单树。保存 `PUT /api/system/roles/{id}/menus` 需要内置管理员身份与 `system:role:write`，提交 `{mode,menuIds}` 整体替换，最多 256 个菜单 ID，成功返回 204。

- `LEGACY` 配合 `menuIds: []` 清除直接分配。旧角色迁移后默认使用此模式，内置管理员始终使用原有权限规则。
- `DIRECT` 逐项勾选。用户有至少一个启用的 `DIRECT` 角色时，菜单候选为其启用的直接菜单角色的勾选并集。其他角色继续提供权限点和数据范围。直接勾选的并集为空时，系统菜单为空。
  - 菜单及其祖先仍须通过启用、模块和权限点检查。可见子项的祖先会显示为分组，祖先自己的页面须另行勾选。勾选父项时也需要逐项勾选希望显示的子项。
  - 页面仍检查注册时的权限点和导航状态，业务接口继续检查对应权限点和数据范围。个人中心与收件箱属于登录后可访问的个人入口。
- 没有启用的 `DIRECT` 角色时，继续根据原有权限规则显示菜单。配置保存后，受影响用户在下次获取会话菜单时看到变化。

`V7__account_and_role_menus.sql` 在通用迁移目录新增可空联系方式、角色菜单模式及带外键的角色菜单关联。既有账号、角色权限和会话沿用原数据。
