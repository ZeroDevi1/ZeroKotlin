package dev.zerokotlin.system.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import dev.zerokotlin.system.entity.OperationAuditRecord
import org.apache.ibatis.annotations.Mapper

@Mapper
interface OperationAuditMapper : BaseMapper<OperationAuditRecord>
