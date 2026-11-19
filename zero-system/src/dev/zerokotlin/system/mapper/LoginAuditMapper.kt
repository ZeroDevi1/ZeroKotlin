package dev.zerokotlin.system.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import dev.zerokotlin.system.entity.LoginAuditRecord
import org.apache.ibatis.annotations.Mapper

/** 保存登录尝试的审计记录，并为按登录结果筛选的审计列表提供查询。 */
@Mapper
interface LoginAuditMapper : BaseMapper<LoginAuditRecord>
