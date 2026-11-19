package dev.zerokotlin.system.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import dev.zerokotlin.core.PageWindow
import dev.zerokotlin.system.dto.UserQuery
import dev.zerokotlin.system.dto.UserRow
import dev.zerokotlin.system.entity.UserRecord
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

/** 账号数据访问接口，提供用户可见性、会话身份和岗位成员查询。 */
@Mapper
interface UserMapper : BaseMapper<UserRecord> {
    /**
     * 统计可见范围内且符合列表筛选条件的用户。
     *
     * @param query 操作者身份、授权范围及可选的部门和角色筛选条件。
     * @return 应用筛选后、分页前的用户条数。
     */
    fun countUsers(@Param("query") query: UserQuery): Long

    /**
     * 分页查询可见范围内且符合列表筛选条件的用户。
     *
     * @param query 操作者身份、授权范围及可选的部门和角色筛选条件。
     * @param window 当前页的条数和偏移量。
     * @return 当前页的用户投影，按登录名和用户 ID 依次升序排列。
     */
    fun findUsers(@Param("query") query: UserQuery, @Param("window") window: PageWindow): List<UserRow>

    /**
     * 按 ID 查询操作者可访问的单个用户。
     *
     * @param query 提供操作者身份和授权范围的查询对象。此处仅应用可见性条件。
     * @param id 要查询的用户 ID。
     * @return 可访问的用户投影。用户不存在或不在可见范围内时返回 `null`。
     */
    fun findVisible(@Param("query") query: UserQuery, @Param("id") id: String): UserRow?

    /**
     * 查找有效会话所属的已启用用户。
     *
     * @param digest 访问令牌的 SHA-256 摘要，用作会话主键。
     * @param now 校验会话有效性所用的 Unix 毫秒时间戳，到期时间须晚于此值。
     * @return 已启用的会话用户投影。会话缺失、已到期或账号停用时返回 `null`。
     */
    fun findSessionActor(@Param("digest") digest: String, @Param("now") now: Long): UserRow?

    /**
     * 查询指定岗位关联的全部用户。
     *
     * @param positionId 要查询成员的岗位 ID。
     * @return 成员的用户投影，包含停用账号，按登录名和用户 ID 依次升序排列。
     */
    fun findPositionMembers(@Param("positionId") positionId: String): List<UserRow>
}
