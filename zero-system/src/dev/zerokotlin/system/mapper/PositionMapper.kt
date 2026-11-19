package dev.zerokotlin.system.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import dev.zerokotlin.system.entity.PositionRecord
import org.apache.ibatis.annotations.Delete
import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface PositionMapper : BaseMapper<PositionRecord> {
    @Insert("INSERT INTO sys_user_position (position_id, user_id) VALUES (#{positionId}, #{userId})")
    fun insertMember(@Param("positionId") positionId: String, @Param("userId") userId: String)

    @Delete("DELETE FROM sys_user_position WHERE position_id = #{positionId}")
    fun deleteMembers(@Param("positionId") positionId: String)
}
