package dev.zerokotlin.system.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName

/**
 * `sys_dictionary_item` 中属于单个字典的选项。
 *
 * @property id 字典选项主键。
 * @property dictionaryId 所属字典的 ID。
 * @property itemValue 业务使用的选项值，在所属字典内唯一。
 * @property label 供界面展示的选项标签。
 * @property sortOrder 字典内的排序值，较小值排在前面。
 * @property enabled 启用标志，`1` 表示启用，`0` 表示停用。
 */
@TableName("sys_dictionary_item")
class DictionaryItemRecord(
    @field:TableId(type = IdType.INPUT)
    var id: String = "",
    var dictionaryId: String = "",
    var itemValue: String = "",
    var label: String = "",
    var sortOrder: Int = 0,
    var enabled: Int = 1,
)
