package dev.zerokotlin.ktor.system.department

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import com.baomidou.mybatisplus.core.mapper.BaseMapper

/** 部门主键、父级和顺序由既有 Flyway 表维护。 */
@TableName("sys_department")
internal class DepartmentRecord {
    @field:TableId(type = IdType.INPUT)
    var id: String? = null
    var name: String? = null
    @field:TableField("parent_id")
    var parentId: String? = null
    @field:TableField("sort_order")
    var sortOrder: Int? = null
}

internal interface DepartmentMapper : BaseMapper<DepartmentRecord>
