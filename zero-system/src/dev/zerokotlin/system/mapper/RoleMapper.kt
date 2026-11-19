package dev.zerokotlin.system.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import dev.zerokotlin.core.PageWindow
import dev.zerokotlin.system.entity.RoleRecord
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface RoleMapper : BaseMapper<RoleRecord> {
    fun countRoles(@Param("enabled") enabled: Int?): Long
    fun findRoles(@Param("enabled") enabled: Int?, @Param("window") window: PageWindow): List<RoleRecord>
}
