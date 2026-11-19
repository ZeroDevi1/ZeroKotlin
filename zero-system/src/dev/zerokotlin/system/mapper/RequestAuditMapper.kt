package dev.zerokotlin.system.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import dev.zerokotlin.system.entity.RequestAuditRecord
import org.apache.ibatis.annotations.Mapper

/** 保存及分页读取请求审计。 */
@Mapper
interface RequestAuditMapper : BaseMapper<RequestAuditRecord>
