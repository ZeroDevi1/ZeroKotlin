package dev.zerokotlin.system.security

import dev.zerokotlin.system.entity.DataScope
import dev.zerokotlin.system.entity.PermissionRecord
import dev.zerokotlin.system.mapper.PermissionMapper
import dev.zerokotlin.system.mapper.SystemRelationMapper
import dev.zerokotlin.system.mapper.UserMapper
import dev.zerokotlin.system.support.query
import org.springframework.stereotype.Component

/**
 * 从有效会话构建认证主体，并按当前角色配置计算有效权限。
 *
 * @property users 通过令牌摘要查询未过期会话对应的启用账号。
 * @property permissions 读取内置管理员所拥有的全部已启用权限点。
 * @property relations 判断管理员身份，并查询业务角色授予的启用权限及数据范围。
 */
@Component
class SystemSessionAuthentication(
    private val users: UserMapper,
    private val permissions: PermissionMapper,
    private val relations: SystemRelationMapper,
) {
    /**
     * 校验会话有效性并计算权限。系统管理员对每个启用的权限点拥有全量数据范围。
     * 多个业务角色授予同一权限时，采用其中覆盖范围最大的一项。
     *
     * @param digest 请求中的 Bearer 令牌摘要。
     * @return 对应用户的认证主体。会话不存在或已过期，或关联账号已停用时返回 null。
     */
    fun authenticate(digest: String): SystemActor? {
        val user = users.findSessionActor(digest, System.currentTimeMillis()) ?: return null
        val administrator = relations.countAdministratorRoles(user.id) != 0L
        val scopes = if (administrator) {
            permissions.selectList(query<PermissionRecord>().eq("enabled", 1)).associate { it.code to DataScope.ALL }
        } else {
            val effective = mutableMapOf<String, DataScope>()
            for (row in relations.findAuthorities(user.id)) {
                val scope = DataScope.valueOf(row.dataScope)
                effective.merge(row.code, scope) { existing, granted -> maxOf(existing, granted) }
            }
            effective
        }
        return SystemActor(user, digest, administrator, scopes)
    }
}
