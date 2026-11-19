package dev.zerokotlin.system.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import dev.zerokotlin.system.entity.DictionaryItemRecord
import org.apache.ibatis.annotations.Mapper

/** 字典选项的数据访问接口，支持按所属字典查询和维护选项。 */
@Mapper
interface DictionaryItemMapper : BaseMapper<DictionaryItemRecord>
