package dev.zerokotlin.system.mapper

import dev.zerokotlin.system.dto.AssignedRoleRow
import dev.zerokotlin.system.dto.AuthorityRow
import org.apache.ibatis.annotations.Delete
import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import org.apache.ibatis.annotations.Update

/** 管理用户、角色和权限之间的授权关联，并提供系统写入锁。 */
@Mapper
interface SystemRelationMapper {
    /**
     * 批量查询用户列表所需的角色分配信息。
     *
     * @param userIds 待查询的用户 ID 列表，须至少包含一个 ID。
     * @return 已分配的角色，包含停用角色，按用户 ID 和角色编码依次升序排列。
     */
    fun findAssignedRoles(@Param("userIds") userIds: List<String>): List<AssignedRoleRow>

    /**
     * 查询已启用业务角色授予用户的有效权限，以及各角色配置的数据范围。
     *
     * @param userId 权限所属的用户 ID。
     * @return 已启用权限点的授权行。同一权限可由多个角色授予，返回多条记录。
     */
    fun findAuthorities(@Param("userId") userId: String): List<AuthorityRow>

    /**
     * 查询指定用户的全部角色关联。
     *
     * @param userId 获分配角色的用户 ID。
     * @return 已分配的角色 ID，按 ID 升序排列，包含停用角色。
     */
    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId} ORDER BY role_id")
    fun findUserRoleIds(@Param("userId") userId: String): List<String>

    /**
     * 查询角色与权限点的直接关联。
     *
     * @param roleId 角色 ID。
     * @return 已关联的权限编码，按编码升序排列，包含停用权限点。
     */
    @Select("SELECT permission_code FROM sys_role_permission WHERE role_id = #{roleId} ORDER BY permission_code")
    fun findRolePermissionCodes(@Param("roleId") roleId: String): List<String>

    /**
     * 检查用户是否拥有已启用的内置系统管理员角色。
     *
     * @param userId 待检查的用户 ID。
     * @return 满足条件的角色关联数。拥有该角色且角色启用时为 `1`，否则为 `0`。
     */
    @Select(
        """SELECT COUNT(*) FROM sys_user_role ur
           JOIN sys_role r ON r.id = ur.role_id AND r.enabled = 1
           WHERE ur.user_id = #{userId} AND r.id = 'system-admin'""",
    )
    fun countAdministratorRoles(@Param("userId") userId: String): Long

    /**
     * 建立一条用户与角色的关联。
     *
     * @param userId 获分配角色的用户 ID。
     * @param roleId 被分配的角色 ID。
     */
    @Insert("INSERT INTO sys_user_role (user_id, role_id) VALUES (#{userId}, #{roleId})")
    fun insertUserRole(@Param("userId") userId: String, @Param("roleId") roleId: String)

    /**
     * 删除指定用户的全部角色关联。
     *
     * @param userId 要清除角色关联的用户 ID。
     */
    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    fun deleteUserRoles(@Param("userId") userId: String)

    /**
     * 建立一条角色与权限点的关联。
     *
     * @param roleId 获授权限的角色 ID。
     * @param permissionCode 授予该角色的权限编码。
     */
    @Insert("INSERT INTO sys_role_permission (role_id, permission_code) VALUES (#{roleId}, #{permissionCode})")
    fun insertRolePermission(@Param("roleId") roleId: String, @Param("permissionCode") permissionCode: String)

    /**
     * 删除指定角色的全部权限关联。
     *
     * @param roleId 要清除权限关联的角色 ID。
     */
    @Delete("DELETE FROM sys_role_permission WHERE role_id = #{roleId}")
    fun deleteRolePermissions(@Param("roleId") roleId: String)

    /**
     * 递增系统写入锁记录的版本号，使使用该锁的写事务串行执行。
     *
     * @return 更新的记录数。成功取得锁时为 `1`，锁记录缺失时为 `0`。
     */
    @Update("UPDATE sys_write_guard SET revision = revision + 1 WHERE id = 'system'")
    fun lockSystemWrites(): Int
}
