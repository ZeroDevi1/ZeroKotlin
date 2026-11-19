package dev.zerokotlin.system.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import dev.zerokotlin.core.PageWindow
import dev.zerokotlin.system.dto.UserQuery
import dev.zerokotlin.system.dto.UserRow
import dev.zerokotlin.system.entity.UserRecord
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface UserMapper : BaseMapper<UserRecord> {
    fun countUsers(@Param("query") query: UserQuery): Long
    fun findUsers(@Param("query") query: UserQuery, @Param("window") window: PageWindow): List<UserRow>
    fun findVisible(@Param("query") query: UserQuery, @Param("id") id: String): UserRow?
    fun findSessionActor(@Param("digest") digest: String, @Param("now") now: Long): UserRow?
    fun findPositionMembers(@Param("positionId") positionId: String): List<UserRow>
}
