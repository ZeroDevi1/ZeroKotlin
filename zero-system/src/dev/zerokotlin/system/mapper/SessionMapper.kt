package dev.zerokotlin.system.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import dev.zerokotlin.system.entity.SessionRecord
import org.apache.ibatis.annotations.Mapper

/** 登录会话的数据访问接口，用于保存摘要、撤销会话和清理过期记录。 */
@Mapper
interface SessionMapper : BaseMapper<SessionRecord>
