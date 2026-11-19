package dev.zerokotlin.system.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import dev.zerokotlin.system.entity.FileRecord
import org.apache.ibatis.annotations.Delete
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Update

/** 文件元数据的查询、条件状态切换与删除。 */
@Mapper
interface FileMapper : BaseMapper<FileRecord> {
    /** 当前状态与预期一致时更新状态，返回更新行数。 */
    @Update("UPDATE sys_file SET state = #{next} WHERE id = #{id} AND state = #{expected}")
    fun changeState(@Param("id") id: String, @Param("expected") expected: String, @Param("next") next: String): Int

    /** 删除已完成对象清理的元数据，返回删除行数。 */
    @Delete("DELETE FROM sys_file WHERE id = #{id} AND state = 'DELETING'")
    fun finishDelete(@Param("id") id: String): Int
}
