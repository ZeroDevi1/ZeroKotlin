package dev.zerokotlin.system

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import dev.zerokotlin.core.PageWindow
import org.apache.ibatis.annotations.Delete
import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import org.apache.ibatis.annotations.Update

@Mapper
interface UserMapper : BaseMapper<UserRecord> {
    fun countUsers(@Param("query") query: UserQuery): Long
    fun findUsers(@Param("query") query: UserQuery, @Param("window") window: PageWindow): List<UserRow>
    fun findVisible(@Param("query") query: UserQuery, @Param("id") id: String): UserRow?
    fun findSessionActor(@Param("digest") digest: String, @Param("now") now: Long): UserRow?
    fun findPositionMembers(@Param("positionId") positionId: String): List<UserRow>
}

@Mapper
interface DepartmentMapper : BaseMapper<DepartmentRecord>

@Mapper
interface PositionMapper : BaseMapper<PositionRecord> {
    @Insert("INSERT INTO sys_user_position (position_id, user_id) VALUES (#{positionId}, #{userId})")
    fun insertMember(@Param("positionId") positionId: String, @Param("userId") userId: String)

    @Delete("DELETE FROM sys_user_position WHERE position_id = #{positionId}")
    fun deleteMembers(@Param("positionId") positionId: String)
}

@Mapper
interface AnnouncementMapper : BaseMapper<AnnouncementRecord> {
    fun findInbox(
        @Param("userId") userId: String,
        @Param("unreadOnly") unreadOnly: Boolean,
        @Param("window") window: PageWindow,
    ): List<InboxEntry>

    fun countInbox(@Param("userId") userId: String, @Param("unreadOnly") unreadOnly: Boolean): Long

    @Select("SELECT id FROM sys_announcement WHERE id = #{id} AND status = 'PUBLISHED'")
    fun findPublishedId(@Param("id") id: String): String?

    @Select("SELECT read_at FROM sys_announcement_read WHERE user_id = #{userId} AND announcement_id = #{id}")
    fun findReadAt(@Param("userId") userId: String, @Param("id") id: String): Long?

    @Insert("INSERT INTO sys_announcement_read (user_id, announcement_id, read_at) VALUES (#{userId}, #{id}, #{now})")
    fun insertRead(@Param("userId") userId: String, @Param("id") id: String, @Param("now") now: Long)
}

@Mapper
interface RoleMapper : BaseMapper<RoleRecord> {
    fun countRoles(@Param("enabled") enabled: Int?): Long
    fun findRoles(@Param("enabled") enabled: Int?, @Param("window") window: PageWindow): List<RoleRecord>
}

@Mapper
interface PermissionMapper : BaseMapper<PermissionRecord>

@Mapper
interface MenuMapper : BaseMapper<MenuRecord>

@Mapper
interface DictionaryMapper : BaseMapper<DictionaryRecord>

@Mapper
interface DictionaryItemMapper : BaseMapper<DictionaryItemRecord>

@Mapper
interface SessionMapper : BaseMapper<SessionRecord>

@Mapper
interface LoginAuditMapper : BaseMapper<LoginAuditRecord>

@Mapper
interface OperationAuditMapper : BaseMapper<OperationAuditRecord>

@Mapper
interface SystemRelationMapper {
    fun findAssignedRoles(@Param("userIds") userIds: List<String>): List<AssignedRoleRow>

    fun findAuthorities(@Param("userId") userId: String): List<AuthorityRow>

    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId} ORDER BY role_id")
    fun findUserRoleIds(@Param("userId") userId: String): List<String>

    @Select("SELECT permission_code FROM sys_role_permission WHERE role_id = #{roleId} ORDER BY permission_code")
    fun findRolePermissionCodes(@Param("roleId") roleId: String): List<String>

    @Select(
        """SELECT COUNT(*) FROM sys_user_role ur
           JOIN sys_role r ON r.id = ur.role_id AND r.enabled = 1
           WHERE ur.user_id = #{userId} AND r.id = 'system-admin'""",
    )
    fun countAdministratorRoles(@Param("userId") userId: String): Long

    @Insert("INSERT INTO sys_user_role (user_id, role_id) VALUES (#{userId}, #{roleId})")
    fun insertUserRole(@Param("userId") userId: String, @Param("roleId") roleId: String)

    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    fun deleteUserRoles(@Param("userId") userId: String)

    @Insert("INSERT INTO sys_role_permission (role_id, permission_code) VALUES (#{roleId}, #{permissionCode})")
    fun insertRolePermission(@Param("roleId") roleId: String, @Param("permissionCode") permissionCode: String)

    @Delete("DELETE FROM sys_role_permission WHERE role_id = #{roleId}")
    fun deleteRolePermissions(@Param("roleId") roleId: String)

    @Update("UPDATE sys_write_guard SET revision = revision + 1 WHERE id = 'system'")
    fun lockSystemWrites(): Int
}
