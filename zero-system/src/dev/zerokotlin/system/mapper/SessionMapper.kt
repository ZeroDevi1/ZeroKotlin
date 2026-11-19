package dev.zerokotlin.system.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import dev.zerokotlin.system.entity.SessionRecord
import org.apache.ibatis.annotations.Mapper

@Mapper
interface SessionMapper : BaseMapper<SessionRecord>
