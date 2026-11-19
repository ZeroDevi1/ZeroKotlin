package dev.zerokotlin.system.security

/** 查询管理端公告列表的权限编码。 */
const val ANNOUNCEMENT_READ = "system:announcement:read"

/** 创建、修改、发布和撤回公告的权限编码。 */
const val ANNOUNCEMENT_WRITE = "system:announcement:write"

/** 新建和修改字典及字典项的权限编码。 */
const val DICTIONARY_WRITE = "system:dictionary:write"

/** 创建和修改菜单节点的权限编码。 */
const val MENU_WRITE = "system:menu:write"

/** 读取岗位和岗位成员的权限编码，成员查询还要求管理员身份。 */
const val POSITION_READ = "system:position:read"

/** 维护岗位和岗位成员的权限编码。 */
const val POSITION_WRITE = "system:position:write"

/** 维护业务角色和权限分配的权限编码。 */
const val ROLE_WRITE = "system:role:write"

/** 维护自定义权限点的权限编码。 */
const val PERMISSION_WRITE = "system:permission:write"

/** 创建和修改部门及层级关系的权限编码。 */
const val DEPARTMENT_WRITE = "system:department:write"

/** 用于首次初始化和管理员身份判断的内置角色 ID。 */
const val ADMIN_ROLE_ID = "system-admin"

/** 查询用户资料的权限编码，其数据范围决定可见用户。 */
const val USER_READ = "system:user:read"

/** 修改用户显示名称的权限编码，其数据范围决定可修改的用户。 */
const val USER_UPDATE = "system:user:update"

/** 创建用户或更新账号状态、密码、部门及角色分配的权限编码。 */
const val USER_MANAGE = "system:user:manage"
