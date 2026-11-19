package dev.zerokotlin.system.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import dev.zerokotlin.system.entity.DictionaryRecord
import org.apache.ibatis.annotations.Mapper

@Mapper
interface DictionaryMapper : BaseMapper<DictionaryRecord>
