package dev.zerokotlin.system.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import dev.zerokotlin.system.entity.MenuRecord
import org.apache.ibatis.annotations.Mapper

@Mapper
interface MenuMapper : BaseMapper<MenuRecord>
