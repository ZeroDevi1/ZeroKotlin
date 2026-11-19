package dev.zerokotlin.system.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import dev.zerokotlin.system.entity.SessionRecord
import org.apache.ibatis.annotations.Delete
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

/** 登录会话的数据访问接口，用于保存摘要、撤销会话和清理过期记录。 */
@Mapper
interface SessionMapper : BaseMapper<SessionRecord> {
    /** 删除指定账号的全部会话。 */
    @Delete("DELETE FROM sys_session WHERE user_id = #{userId}")
    fun revokeForUser(@Param("userId") userId: String)
}
