package dev.zerokotlin.system.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName

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
