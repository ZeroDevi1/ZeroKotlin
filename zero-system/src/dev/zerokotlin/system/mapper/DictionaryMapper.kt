package dev.zerokotlin.system.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import dev.zerokotlin.system.entity.DictionaryRecord
import org.apache.ibatis.annotations.Mapper

/** 字典定义的数据访问接口，用于按业务编码查找字典和维护字典状态。 */
@Mapper
interface DictionaryMapper : BaseMapper<DictionaryRecord>
