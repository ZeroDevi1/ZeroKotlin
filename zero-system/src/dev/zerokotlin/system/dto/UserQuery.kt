package dev.zerokotlin.system.dto

import dev.zerokotlin.system.security.SystemActor

/**
 * 根据当前操作者的授权构造用户可见范围，并附加列表筛选条件。
 *
 * @param actor 发起用户查询的已认证操作者。
 * @param authority 本次操作所需的权限编码，用于读取数据范围。缺少权限时构造失败。
 * @property departmentId 列表按所属部门 ID 精确筛选，`null` 表示不限部门。
 * @property roleId 列表按已分配的角色 ID 筛选，`null` 表示不限角色。
 */
class UserQuery(
    actor: SystemActor,
    authority: String,
    val departmentId: String? = null,
    val roleId: String? = null,
) {
    /** 当前操作对应的数据范围枚举名，供用户查询 SQL 选择可见性条件。 */
    val scope: String = actor.requireScope(authority).name

    /** 操作者的用户 ID，用于仅允许访问本人时匹配记录。 */
    val actorId: String = actor.user.id

    /** 操作者的部门 ID。未分配部门时，部门相关的数据范围按仅本人处理。 */
    val actorDepartmentId: String? = actor.user.departmentId
}
