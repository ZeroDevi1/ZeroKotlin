package dev.zerokotlin.ktor.system.menu

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper
import dev.zerokotlin.ktor.Created
import dev.zerokotlin.ktor.SystemStore
import dev.zerokotlin.ktor.checkedName
import dev.zerokotlin.ktor.exists
import dev.zerokotlin.ktor.flag
import dev.zerokotlin.ktor.invalid
import dev.zerokotlin.ktor.missing
import dev.zerokotlin.ktor.newId
import dev.zerokotlin.ktor.permissionPattern
import dev.zerokotlin.ktor.system.auth.MenuNode
import dev.zerokotlin.ktor.validateParent

/** 校验树结构与写入使用同一事务，避免并发移动形成循环。 */
internal class MenuServiceImpl(private val store: SystemStore) : MenuService {
    override suspend fun tree(token: String): List<MenuNode> =
        store.read(token, "system:menu:read") { db, _ ->
            val entries = store.mappers.withSession(db) {
                it.getMapper(MenuMapper::class.java)
                    .selectList(QueryWrapper<MenuRecord>().orderByAsc("sort_order", "id"))
            }.groupBy(MenuRecord::parentId)
            fun tree(parent: String?): List<MenuNode> = entries[parent].orEmpty().map { row ->
                val id = checkNotNull(row.id)
                MenuNode(id, checkNotNull(row.name), row.path, row.permissionCode, checkNotNull(row.sortOrder), row.enabled == 1, tree(id))
            }
            tree(null)
        }

    override suspend fun create(token: String, request: MenuRequest): Created {
        val id = newId()
        save(token, id, request, create = true)
        return Created(id)
    }

    override suspend fun update(token: String, id: String, request: MenuRequest) {
        save(token, id, request, create = false)
    }

    private suspend fun save(token: String, id: String, request: MenuRequest, create: Boolean) {
        store.write(token, "system:menu:write") { db, _, audit ->
            store.mappers.withSession(db) { session ->
                val mapper = session.getMapper(MenuMapper::class.java)
                val parents = mapper.selectList(QueryWrapper<MenuRecord>().select("id", "parent_id"))
                    .associate { checkNotNull(it.id) to it.parentId }
                if (!create && id !in parents) missing("菜单")
                validateParent(id, request.parentId, parents)
                val code = request.permissionCode
                if (code != null && (!permissionPattern.matches(code) || !exists(db, "SELECT 1 FROM sys_permission WHERE code = ?", code))) {
                    invalid("权限点不存在或编码无效")
                }
                val path = request.path?.trim()
                if (path != null && (!path.startsWith("/") || path.startsWith("//") || path.length > 255 ||
                        path.any { it == '\\' || Character.isISOControl(it) })
                ) invalid("菜单路径须为站内路径")
                val name = checkedName(request.name)
                if (create) {
                    check(mapper.insert(MenuRecord().also {
                        it.id = id
                        it.name = name
                        it.parentId = request.parentId
                        it.path = path
                        it.permissionCode = code
                        it.sortOrder = request.sortOrder
                        it.enabled = request.enabled.flag()
                    }) == 1)
                } else {
                    // Wrapper 显式设置 null，以便清空父级、链接或权限点。
                    val changes = UpdateWrapper<MenuRecord>().eq("id", id)
                        .set("parent_id", request.parentId).set("name", name).set("path", path)
                        .set("permission_code", code).set("sort_order", request.sortOrder).set("enabled", request.enabled.flag())
                    mapper.update(null, changes)
                }
            }
            audit(if (create) "menu.create" else "menu.update", id)
        }
    }
}
