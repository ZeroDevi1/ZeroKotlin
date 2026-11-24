package dev.zerokotlin.ktor.system.department

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper
import dev.zerokotlin.ktor.Created
import dev.zerokotlin.ktor.SystemStore
import dev.zerokotlin.ktor.checkedName
import dev.zerokotlin.ktor.missing
import dev.zerokotlin.ktor.newId
import dev.zerokotlin.ktor.validateParent

/** 只有具备部门写入权限的系统管理员可以修改部门结构。 */
internal class DepartmentServiceImpl(private val store: SystemStore) : DepartmentService {
    override suspend fun tree(token: String): List<DepartmentNode> =
        store.read(token, "system:department:read") { db, _ ->
            val entries = store.mappers.withSession(db) {
                it.getMapper(DepartmentMapper::class.java)
                    .selectList(QueryWrapper<DepartmentRecord>().orderByAsc("sort_order", "id"))
            }.groupBy(DepartmentRecord::parentId)
            fun tree(parent: String?): List<DepartmentNode> = entries[parent].orEmpty().map { row ->
                val id = checkNotNull(row.id)
                DepartmentNode(id, checkNotNull(row.name), checkNotNull(row.sortOrder), tree(id))
            }
            tree(null)
        }

    override suspend fun create(token: String, request: DepartmentRequest): Created {
        val id = newId()
        save(token, id, request, create = true)
        return Created(id)
    }

    override suspend fun update(token: String, id: String, request: DepartmentRequest) {
        save(token, id, request, create = false)
    }

    private suspend fun save(token: String, id: String, request: DepartmentRequest, create: Boolean) {
        store.write(token, "system:department:write", administratorOnly = true) { db, _, audit ->
            store.mappers.withSession(db) { session ->
                val mapper = session.getMapper(DepartmentMapper::class.java)
                val parents = mapper.selectList(QueryWrapper<DepartmentRecord>().select("id", "parent_id"))
                    .associate { checkNotNull(it.id) to it.parentId }
                if (!create && id !in parents) missing("部门")
                validateParent(id, request.parentId, parents)
                val name = checkedName(request.name)
                if (create) {
                    check(mapper.insert(DepartmentRecord().also {
                        it.id = id
                        it.name = name
                        it.parentId = request.parentId
                        it.sortOrder = request.sortOrder
                    }) == 1)
                } else {
                    val changes = UpdateWrapper<DepartmentRecord>().eq("id", id)
                        .set("parent_id", request.parentId).set("name", name).set("sort_order", request.sortOrder)
                    mapper.update(null, changes)
                }
            }
            audit(if (create) "department.create" else "department.update", id)
        }
    }
}
