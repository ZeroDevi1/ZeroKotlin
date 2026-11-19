package dev.zerokotlin.system.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName

/**
 * `sys_dictionary` 中可按业务编码查找的字典定义。
 *
 * @property id 字典主键，供选项表关联。
 * @property code 字典的唯一业务编码。
 * @property name 字典的显示名称。
 * @property enabled 启用标志，`1` 允许按编码查询选项，`0` 表示停用。
 */
@TableName("sys_dictionary")
class DictionaryRecord(
    @field:TableId(type = IdType.INPUT)
    var id: String = "",
    var code: String = "",
    var name: String = "",
    var enabled: Int = 1,
)
