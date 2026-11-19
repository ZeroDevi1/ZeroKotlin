package dev.zerokotlin.system.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import dev.zerokotlin.system.entity.OperationAuditRecord
import org.apache.ibatis.annotations.Mapper

/** 保存操作审计记录，并为按操作者和动作筛选的审计列表提供查询。 */
@Mapper
interface OperationAuditMapper : BaseMapper<OperationAuditRecord>
