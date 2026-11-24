package dev.zerokotlin.ktor.system.permission.service.impl

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import dev.zerokotlin.core.SystemFailure
import dev.zerokotlin.ktor.SystemStore
import dev.zerokotlin.ktor.checkedName
import dev.zerokotlin.ktor.flag
import dev.zerokotlin.ktor.invalid
import dev.zerokotlin.ktor.missing
import dev.zerokotlin.ktor.permissionPattern
import dev.zerokotlin.ktor.system.permission.dto.CreatePermission
import dev.zerokotlin.ktor.system.permission.dto.PermissionSummary
import dev.zerokotlin.ktor.system.permission.dto.UpdatePermission
import dev.zerokotlin.ktor.system.permission.entity.PermissionRecord
import dev.zerokotlin.ktor.system.permission.mapper.PermissionMapper
import dev.zerokotlin.ktor.system.permission.service.PermissionService

/** 权限校验、Mapper 写入和操作审计使用同一个 SystemStore 事务。 */
internal class PermissionServiceImpl(private val store: SystemStore) : PermissionService {
    override suspend fun list(token: String): List<PermissionSummary> =
        store.read(token, "system:permission:read") { db, _ ->
            store.mappers.withSession(db) { session ->
                session.getMapper(PermissionMapper::class.java)
                    .selectList(QueryWrapper<PermissionRecord>().orderByAsc("code"))
                    .map { PermissionSummary(checkNotNull(it.code), checkNotNull(it.name), it.enabled == 1) }
            }
        }

    override suspend fun create(token: String, request: CreatePermission): PermissionSummary =
        save(token, request.code, request.name, enabled = true, create = true)

    override suspend fun update(token: String, code: String, request: UpdatePermission): PermissionSummary =
        save(token, code, request.name, request.enabled, create = false)

    private suspend fun save(token: String, code: String, name: String, enabled: Boolean, create: Boolean): PermissionSummary =
        store.write(token, "system:permission:write", administratorOnly = true) { db, _, audit ->
            if (!permissionPattern.matches(code) || code.startsWith("system:")) invalid("权限编码无效或由系统维护")
            val label = checkedName(name)
            val row = PermissionRecord().apply {
                this.code = code
                this.name = label
                this.enabled = enabled.flag()
            }
            store.mappers.withSession(db) { session ->
                val mapper = session.getMapper(PermissionMapper::class.java)
                if (create) {
                    if (mapper.selectById(code) != null) throw SystemFailure(409, "权限编码已存在")
                    check(mapper.insert(row) == 1)
                } else if (mapper.updateById(row) == 0) {
                    missing("权限点")
                }
            }
            audit(if (create) "permission.create" else "permission.update", code)
            PermissionSummary(code, label, enabled)
        }
}
