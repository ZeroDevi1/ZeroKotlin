package dev.zerokotlin.system.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import dev.zerokotlin.system.entity.DepartmentRecord
import org.apache.ibatis.annotations.Mapper

@Mapper
interface DepartmentMapper : BaseMapper<DepartmentRecord>
