package dev.zerokotlin.system.entity

import com.baomidou.mybatisplus.annotation.FieldStrategy
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName

/**
 * `sys_department` 中组织树的一个部门节点。
 *
 * @property id 部门主键。
 * @property parentId 上级部门 ID，可写入 `null` 将部门移至根层级。
 * @property name 部门的显示名称。
 * @property sortOrder 同级部门的排序值，较小值排在前面。
 */
@TableName("sys_department")
class DepartmentRecord(
    @field:TableId(type = IdType.INPUT)
    var id: String = "",
    @field:TableField(updateStrategy = FieldStrategy.ALWAYS)
    var parentId: String? = null,
    var name: String = "",
    var sortOrder: Int = 0,
)
