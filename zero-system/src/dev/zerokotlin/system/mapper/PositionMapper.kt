package dev.zerokotlin.system.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import dev.zerokotlin.system.entity.PositionRecord
import org.apache.ibatis.annotations.Delete
import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

/** 岗位定义及岗位与用户关联的数据访问接口。 */
@Mapper
interface PositionMapper : BaseMapper<PositionRecord> {
    /**
     * 建立岗位与一名用户的成员关系。
     *
     * @param positionId 岗位 ID。
     * @param userId 要加入该岗位的用户 ID。
     */
    @Insert("INSERT INTO sys_user_position (position_id, user_id) VALUES (#{positionId}, #{userId})")
    fun insertMember(@Param("positionId") positionId: String, @Param("userId") userId: String)

    /**
     * 删除指定岗位的全部成员关系。
     *
     * @param positionId 要清空成员关系的岗位 ID。
     */
    @Delete("DELETE FROM sys_user_position WHERE position_id = #{positionId}")
    fun deleteMembers(@Param("positionId") positionId: String)
}
