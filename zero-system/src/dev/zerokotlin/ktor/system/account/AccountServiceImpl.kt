package dev.zerokotlin.ktor.system.account

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper
import dev.zerokotlin.ktor.Created
import dev.zerokotlin.ktor.Page
import dev.zerokotlin.ktor.SystemStore
import dev.zerokotlin.ktor.Window
import dev.zerokotlin.ktor.administratorRole
import dev.zerokotlin.ktor.checkedDeleteIds
import dev.zerokotlin.ktor.flag
import dev.zerokotlin.ktor.invalid
import dev.zerokotlin.ktor.newId
import dev.zerokotlin.ktor.userRead
import java.sql.Connection

/** 账号变更先复核账号可见范围，随后在同一事务更新账号、关系、会话和审计。 */
internal class AccountServiceImpl(private val store: SystemStore) : AccountService {
    private val profileWrite = "system:user:update"

    override suspend fun list(token: String, window: Window, department: String?, role: String?): Page<UserEntry> =
        store.read(token, userRead) { db, actor -> db.userPage(department, role, window, actor) }

    override suspend fun detail(token: String, id: String): UserDetail =
        store.read(token, userRead) { db, actor ->
            UserDetail(db.visibleUser(id, actor, userRead), db.userRoleIds(id))
        }

    override suspend fun create(token: String, request: CreateUser): Created {
        store.read(token, userManage) { _, actor -> requireAdministrator(actor) }
        val name = username(request.username) ?: invalid("用户名格式无效")
        val display = store.plainName(request.displayName)
        val hash = accountPassword(request.password)
        val id = newId()
        store.write(token, userManage, administratorOnly = true) { db, _, audit ->
            store.insertUser(db, id, name, display, hash, request.departmentId, request.roleIds)
            audit("user.create", id)
        }
        return Created(id)
    }

    override suspend fun editProfile(token: String, id: String, request: EditUserProfile): User {
        val display = store.plainName(request.displayName)
        return store.write(token, profileWrite) { db, actor, audit ->
            val user = db.visibleUser(id, actor, profileWrite)
            update(db, id) { set("display_name", display) }
            audit("user.profile.update", id)
            user.copy(displayName = display)
        }
    }

    override suspend fun editAccount(token: String, id: String, request: EditUserAccount) {
        val hash = request.password?.let {
            store.read(token, userManage) { _, actor -> requireAdministrator(actor) }
            accountPassword(it)
        }
        store.write(token, userManage, administratorOnly = true) { db, actor, audit ->
            db.visibleUser(id, actor, userManage)
            val isAdministrator = administratorRole in db.userRoleIds(id)
            if (isAdministrator && (!request.enabled || administratorRole !in request.roleIds)) {
                invalid("内置管理员必须保持启用并保留系统管理员角色")
            }
            if (!isAdministrator && administratorRole in request.roleIds) invalid("内置管理员角色只能在初始化时授予")
            db.validateDepartment(request.departmentId)
            db.validateRoles(request.roleIds - administratorRole)
            update(db, id) {
                set("department_id", request.departmentId)
                set("enabled", request.enabled.flag())
                if (hash != null) set("password_hash", hash)
            }
            db.setUserRoles(id, request.roleIds)
            if (!request.enabled || hash != null) db.deleteUserSessions(id)
            audit("user.account.update", id)
        }
    }

    override suspend fun resetPassword(token: String, id: String, password: String) {
        store.read(token, userManage) { _, actor -> requireAdministrator(actor) }
        val hash = accountPassword(password)
        store.write(token, userManage, administratorOnly = true) { db, actor, audit ->
            db.visibleUser(id, actor, userManage)
            update(db, id) { set("password_hash", hash) }
            db.deleteUserSessions(id)
            audit("user.password.reset", id)
        }
    }

    /** 先验证整批目标，再清除关系和记录，任一账号受保护时整批回滚。 */
    override suspend fun delete(token: String, ids: List<String?>) {
        store.write(token, userManage, administratorOnly = true) { db, actor, audit ->
            val targets = checkedDeleteIds(ids)
            targets.forEach { id ->
                db.visibleUser(id, actor, userManage)
                if (administratorRole in db.userRoleIds(id)) invalid("内置管理员账号由系统维护")
            }
            targets.forEach { id ->
                db.deleteUserRelations(id)
                store.mappers.withSession(db) { it.getMapper(UserMapper::class.java).deleteById(id) }
                audit("user.delete", id)
            }
        }
    }

    private fun update(db: Connection, id: String, fields: UpdateWrapper<UserRecord>.() -> Unit) {
        val changes = UpdateWrapper<UserRecord>().eq("id", id).apply(fields)
        store.mappers.withSession(db) { it.getMapper(UserMapper::class.java).update(null, changes) }
    }
}
