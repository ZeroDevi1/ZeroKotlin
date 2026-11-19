package dev.zerokotlin.system.entity

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 角色权限适用的数据范围。枚举按范围从小到大排列，多个角色授予同一权限时取最大值。
 * 查询用户时，操作者没有所属部门的情况下，两种部门范围均按 [SELF] 处理。
 */
@Schema(description = "行级数据范围：本人、本部门、本部门及下级、全部。多个启用角色授予同一权限时，取最广范围。")
enum class DataScope {
    /** 仅访问操作者自己的用户记录。 */
    SELF,

    /** 可访问操作者所在部门的用户记录。 */
    DEPARTMENT,

    /** 可访问操作者所在部门及所有下级部门的用户记录。 */
    DEPARTMENT_AND_DESCENDANTS,

    /** 可访问所有用户记录。 */
    ALL,
}
