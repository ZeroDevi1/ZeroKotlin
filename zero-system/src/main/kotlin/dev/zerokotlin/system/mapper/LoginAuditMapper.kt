package dev.zerokotlin.system.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import dev.zerokotlin.system.entity.LoginAuditRecord
import org.apache.ibatis.annotations.Mapper

@Mapper
interface LoginAuditMapper : BaseMapper<LoginAuditRecord>
