package dev.zerokotlin.system.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName

/**
 * `sys_position` 中的岗位定义，成员关系由独立关联表保存。
 *
 * @property id 岗位主键。
 * @property code 岗位的唯一业务编码。
 * @property name 岗位的显示名称。
 * @property enabled 启用标志，`1` 表示启用，`0` 表示停用。
 * @property sortOrder 岗位列表的排序值，较小值排在前面。
 */
@TableName("sys_position")
class PositionRecord(
    @field:TableId(type = IdType.INPUT)
    var id: String = "",
    var code: String = "",
    var name: String = "",
    var enabled: Int = 1,
    var sortOrder: Int = 0,
)
