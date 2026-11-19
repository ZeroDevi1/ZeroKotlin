package dev.zerokotlin.system.security

import dev.zerokotlin.system.entity.DataScope
import dev.zerokotlin.system.entity.PermissionRecord
import dev.zerokotlin.system.mapper.PermissionMapper
import dev.zerokotlin.system.mapper.SystemRelationMapper
import dev.zerokotlin.system.mapper.UserMapper
import dev.zerokotlin.system.support.query
import org.springframework.stereotype.Component

@Component
class SystemSessionAuthentication(
    private val users: UserMapper,
    private val permissions: PermissionMapper,
    private val relations: SystemRelationMapper,
) {
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
