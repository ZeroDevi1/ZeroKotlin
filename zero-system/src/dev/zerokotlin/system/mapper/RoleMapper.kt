package dev.zerokotlin.system.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import dev.zerokotlin.core.PageWindow
import dev.zerokotlin.system.entity.RoleRecord
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

/** 角色定义的数据访问接口，提供按启用状态筛选的分页查询。 */
@Mapper
interface RoleMapper : BaseMapper<RoleRecord> {
    /**
     * 统计符合启用状态筛选条件的角色。
     *
     * @param enabled 状态筛选值，`1` 表示启用，`0` 表示停用，`null` 表示不限状态。
     * @return 应用筛选后、分页前的角色条数。
     */
    fun countRoles(@Param("enabled") enabled: Int?): Long

    /**
     * 分页查询符合启用状态筛选条件的角色。
     *
     * @param enabled 状态筛选值，`1` 表示启用，`0` 表示停用，`null` 表示不限状态。
     * @param window 当前页的条数和偏移量。
     * @return 当前页的角色，按角色编码和角色 ID 依次升序排列。
     */
    fun findRoles(@Param("enabled") enabled: Int?, @Param("window") window: PageWindow): List<RoleRecord>
}
