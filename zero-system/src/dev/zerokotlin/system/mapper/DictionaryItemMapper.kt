package dev.zerokotlin.system.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import dev.zerokotlin.system.entity.DictionaryItemRecord
import org.apache.ibatis.annotations.Mapper

@Mapper
interface DictionaryItemMapper : BaseMapper<DictionaryItemRecord>
