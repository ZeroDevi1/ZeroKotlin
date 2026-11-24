package dev.zerokotlin.ktor.system.position

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.extension.plugins.pagination.Page as MybatisPage
import dev.zerokotlin.core.SystemFailure
import dev.zerokotlin.ktor.Created
import dev.zerokotlin.ktor.Page
import dev.zerokotlin.ktor.SystemStore
import dev.zerokotlin.ktor.Window
import dev.zerokotlin.ktor.checkedName
import dev.zerokotlin.ktor.flag
import dev.zerokotlin.ktor.invalid
import dev.zerokotlin.ktor.missing
import dev.zerokotlin.ktor.newId
import dev.zerokotlin.ktor.permissionPattern
import dev.zerokotlin.ktor.system.account.requireAdministrator
import java.sql.Connection

/** 成员替换与审计在同一写事务完成，写入只允许系统管理员。 */
internal class PositionServiceImpl(private val store: SystemStore) : PositionService {
    private val read = "system:position:read"
    private val write = "system:position:write"

    override suspend fun list(token: String, window: Window): Page<Position> =
        store.read(token, read) { db, _ ->
            mapper(db) { positions ->
                val query = QueryWrapper<PositionRecord>().orderByAsc("sort_order", "id")
                val page = positions.selectPage(MybatisPage<PositionRecord>(window.page.toLong(), window.size.toLong()), query)
                window.result(page.records.map(PositionRecord::summary), page.total)
            }
        }

    override suspend fun create(token: String, request: NewPosition): Created {
        val id = newId()
        store.write(token, write, administratorOnly = true) { db, _, audit ->
            val code = request.code.takeIf(permissionPattern::matches) ?: invalid("岗位编码无效")
            mapper(db) { positions ->
                if (positions.selectCount(QueryWrapper<PositionRecord>().eq("code", code)) > 0L) {
                    throw SystemFailure(409, "岗位编码已存在")
                }
                check(positions.insert(PositionRecord().also {
                    it.id = id
                    it.code = code
                    it.name = checkedName(request.name)
                    it.sortOrder = request.sortOrder
                    it.enabled = 1
                }) == 1)
            }
            audit("position.create", id)
        }
        return Created(id)
    }

    override suspend fun update(token: String, id: String, request: EditPosition) {
        store.write(token, write, administratorOnly = true) { db, _, audit ->
            val changed = mapper(db) { positions ->
                positions.updateById(PositionRecord().also {
                    it.id = id
                    it.name = checkedName(request.name)
                    it.enabled = request.enabled.flag()
                    it.sortOrder = request.sortOrder
                })
            }
            if (changed == 0) missing("岗位")
            audit("position.update", id)
        }
    }

    override suspend fun members(token: String, id: String): List<PositionMember> =
        store.read(token, read) { db, actor ->
            requireAdministrator(actor)
            requirePosition(db, id)
            db.positionMembers(id)
        }

    override suspend fun replaceMembers(token: String, id: String, ids: Set<String?>) {
        store.write(token, write, administratorOnly = true) { db, _, audit ->
            requirePosition(db, id)
            val users = ids.map { it?.takeIf { user -> user.length in 1..36 } ?: invalid("用户 ID 无效") }
            db.replacePositionMembers(id, users)
            audit("position.members.replace", id)
        }
    }

    private fun requirePosition(db: Connection, id: String) {
        if (mapper(db) { it.selectById(id) } == null) missing("岗位")
    }

    private fun <T> mapper(db: Connection, action: (PositionMapper) -> T): T =
        store.mappers.withSession(db) { action(it.getMapper(PositionMapper::class.java)) }
}
