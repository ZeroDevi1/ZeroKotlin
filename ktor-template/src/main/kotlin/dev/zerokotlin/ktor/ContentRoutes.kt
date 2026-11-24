package dev.zerokotlin.ktor

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import java.sql.Connection
import java.sql.ResultSet

internal const val dictionaryRead = "system:dictionary:read"
internal const val dictionaryWrite = "system:dictionary:write"
private const val announcementRead = "system:announcement:read"
private const val announcementWrite = "system:announcement:write"
private const val auditRead = "system:audit:read"

@Serializable private data class Dictionary(val id: String, val code: String, val name: String, val enabled: Boolean)
@Serializable internal data class DictionaryItem(val id: String, val value: String, val label: String, val sortOrder: Int, val enabled: Boolean)
@Serializable private data class NewDictionary(val code: String, val name: String)
@Serializable private data class EditDictionary(val name: String, val enabled: Boolean)
@Serializable private data class DictionaryCodes(val codes: List<String?>)
@Serializable internal data class EditItem(val value: String, val label: String, val sortOrder: Int = 0, val enabled: Boolean = true)
@Serializable private data class EditAnnouncement(val title: String, val body: String)
@Serializable private data class Announcement(
    val id: String, val title: String, val body: String, val status: String,
    val createdAt: Long, val updatedAt: Long, val publishedAt: Long?,
)
@Serializable private data class LoginAudit(
    val id: String, val userId: String?, val username: String?, val successful: Boolean,
    val remoteAddress: String, val occurredAt: Long, val ipRegion: String?,
)
@Serializable private data class OperationAudit(
    val id: String, val actorId: String, val action: String, val targetId: String, val occurredAt: Long,
)
@Serializable private data class RequestAudit(
    val id: String, val actorId: String?, val action: String, val httpMethod: String, val routePattern: String,
    val httpStatus: Int, val durationMs: Long, val exceptionType: String?, val remoteAddress: String,
    val ipRegion: String?, val userAgent: String?, val occurredAt: Long,
)

internal fun Route.dictionaryRoutes(store: SystemStore) = route("dictionaries") {
    get {
        call.respond(store.read(call.bearer(), dictionaryRead) { db, _ ->
            db.rows("SELECT id, code, name, enabled FROM sys_dictionary ORDER BY code") {
                Dictionary(it.getString(1), it.getString(2), it.getString(3), it.getInt(4) == 1)
            }
        })
    }
    get("by-code/{code}/items") {
        val token = call.bearer()
        val code = checkedCode(call.parameters["code"])
        call.respond(store.read(token, null) { db, _ ->
            db.items(db.enabledDictionaryId(code) ?: missing("字典"), enabledOnly = true)
        })
    }
    post("by-codes/items") {
        val token = call.bearer()
        val request = call.receive<DictionaryCodes>()
        if (request.codes.size !in 1..100) invalid("每次须查询 1 至 100 个字典编码")
        val codes = request.codes.map(::checkedCode).distinct()
        call.respond(store.read(token, null) { db, _ ->
            codes.mapNotNull { code -> db.enabledDictionaryId(code)?.let { code to db.items(it, enabledOnly = true) } }.toMap()
        })
    }
    post {
        val token = call.bearer()
        val request = call.receive<NewDictionary>()
        val id = newId()
        store.write(token, dictionaryWrite) { db, _, audit ->
            db.insertDictionary(id, request.code, request.name)
            audit("dictionary.create", id)
        }
        call.respond(HttpStatusCode.Created, Created(id))
    }
    put("{id}") {
        val token = call.bearer()
        val id = call.id()
        val request = call.receive<EditDictionary>()
        store.write(token, dictionaryWrite) { db, _, audit ->
            if (db.change(
                    "UPDATE sys_dictionary SET name = ?, enabled = ? WHERE id = ?",
                    checkedName(request.name), request.enabled.flag(), id,
                ) == 0
            ) missing("字典")
            audit("dictionary.update", id)
        }
        call.respond(HttpStatusCode.NoContent)
    }
    delete("{id}") {
        deleteDictionaries(store, call.bearer(), listOf(call.id()))
        call.respond(HttpStatusCode.NoContent)
    }
    post("batch-delete") {
        val token = call.bearer()
        deleteDictionaries(store, token, call.receive<DeleteBatch>().ids)
        call.respond(HttpStatusCode.NoContent)
    }
    route("{dictionaryId}/items") {
        get {
            val token = call.bearer()
            val dictionary = call.parameters["dictionaryId"] ?: missing("字典")
            call.respond(store.read(token, dictionaryRead) { db, _ ->
                db.requireDictionary(dictionary)
                db.items(dictionary, enabledOnly = false)
            })
        }
        post {
            val token = call.bearer()
            val dictionary = call.parameters["dictionaryId"] ?: missing("字典")
            val id = newId()
            writeItem(store, token, dictionary, id, call.receive<EditItem>(), create = true)
            call.respond(HttpStatusCode.Created, Created(id))
        }
        put("{id}") {
            val token = call.bearer()
            val dictionary = call.parameters["dictionaryId"] ?: missing("字典")
            writeItem(store, token, dictionary, call.id(), call.receive<EditItem>(), create = false)
            call.respond(HttpStatusCode.NoContent)
        }
        delete("{id}") {
            val token = call.bearer()
            val dictionary = call.parameters["dictionaryId"] ?: missing("字典")
            deleteItems(store, token, dictionary, listOf(call.id()))
            call.respond(HttpStatusCode.NoContent)
        }
        post("batch-delete") {
            val token = call.bearer()
            val dictionary = call.parameters["dictionaryId"] ?: missing("字典")
            deleteItems(store, token, dictionary, call.receive<DeleteBatch>().ids)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

internal fun checkedCode(code: String?) = code?.takeIf(permissionPattern::matches) ?: invalid("编码无效")
private fun deleteTargets(ids: List<String?>): Set<String> {
    if (ids.size !in 1..100) invalid("每次须指定 1 至 100 个待删除 ID")
    return ids.map { it?.takeIf { id -> id.isNotBlank() && id.length <= 36 } ?: invalid("资源 ID 无效") }.toSet()
}

private fun Connection.enabledDictionaryId(code: String): String? =
    rows("SELECT id FROM sys_dictionary WHERE code = ? AND enabled = 1", code) { it.getString(1) }.singleOrNull()

internal fun Connection.requireDictionary(id: String) {
    if (!exists(this, "SELECT 1 FROM sys_dictionary WHERE id = ?", id)) missing("字典")
}

internal fun Connection.items(dictionary: String, enabledOnly: Boolean): List<DictionaryItem> {
    val filter = if (enabledOnly) " AND enabled = 1" else ""
    return rows(
        """SELECT id, item_value, label, sort_order, enabled FROM sys_dictionary_item
           WHERE dictionary_id = ?$filter ORDER BY sort_order, id""", dictionary,
    ) { DictionaryItem(it.getString(1), it.getString(2), it.getString(3), it.getInt(4), it.getInt(5) == 1) }
}

private suspend fun deleteDictionaries(store: SystemStore, token: String, ids: List<String?>) {
    store.write(token, dictionaryWrite, administratorOnly = true) { db, _, audit ->
        val targets = deleteTargets(ids)
        targets.forEach { id ->
            db.requireDictionary(id)
            if (exists(db, "SELECT 1 FROM sys_dictionary_item WHERE dictionary_id = ?", id)) {
                throw SystemFailure(409, "请先删除字典下的全部字典项")
            }
        }
        targets.forEach { id ->
            db.change("DELETE FROM sys_dictionary WHERE id = ?", id)
            audit("dictionary.delete", id)
        }
    }
}

private suspend fun writeItem(store: SystemStore, token: String, dictionary: String, id: String, request: EditItem, create: Boolean) {
    store.write(token, dictionaryWrite) { db, _, audit ->
        db.saveItem(dictionary, id, request, create)
        audit(if (create) "dictionary.item.create" else "dictionary.item.update", id)
    }
}

private suspend fun deleteItems(store: SystemStore, token: String, dictionary: String, ids: List<String?>) {
    store.write(token, dictionaryWrite, administratorOnly = true) { db, _, audit ->
        db.requireDictionary(dictionary)
        deleteTargets(ids).forEach { id ->
            if (db.change("DELETE FROM sys_dictionary_item WHERE id = ? AND dictionary_id = ?", id, dictionary) != 1) missing("字典项")
            audit("dictionary.item.delete", id)
        }
    }
}

internal fun Route.announcementRoutes(store: SystemStore) = route("announcements") {
    get {
        val window = call.window()
        call.respond(store.read(call.bearer(), announcementRead) { db, _ ->
            val count = db.rows("SELECT COUNT(*) FROM sys_announcement") { it.getLong(1) }.single()
            val records = db.rows(
                "SELECT * FROM sys_announcement ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?", window.size, window.offset,
            ) {
                Announcement(
                    it.getString("id"), it.getString("title"), it.getString("body"), it.getString("status"),
                    it.getLong("created_at"), it.getLong("updated_at"), it.nullableLong("published_at"),
                )
            }
            window.result(records, count)
        })
    }
    post {
        val token = call.bearer()
        val request = call.receive<EditAnnouncement>()
        val id = newId()
        store.write(token, announcementWrite) { db, _, audit ->
            val now = System.currentTimeMillis()
            db.change(
                "INSERT INTO sys_announcement (id, title, body, status, created_at, updated_at) VALUES (?, ?, ?, 'DRAFT', ?, ?)",
                id, store.plainName(request.title), announcementBody(request.body), now, now,
            )
            audit("announcement.create", id)
        }
        call.respond(HttpStatusCode.Created, Created(id))
    }
    put("{id}") {
        val token = call.bearer()
        val id = call.id()
        val request = call.receive<EditAnnouncement>()
        store.write(token, announcementWrite) { db, _, audit ->
            if (db.announcementState(id) != "DRAFT") throw SystemFailure(409, "仅草稿公告可以修改")
            db.change(
                "UPDATE sys_announcement SET title = ?, body = ?, updated_at = ? WHERE id = ?",
                store.plainName(request.title), announcementBody(request.body), System.currentTimeMillis(), id,
            )
            audit("announcement.update", id)
        }
        call.respond(HttpStatusCode.NoContent)
    }
    post("{id}/publish") {
        transitionAnnouncement(store, call.bearer(), call.id(), publish = true)
        call.respond(HttpStatusCode.NoContent)
    }
    post("{id}/revoke") {
        transitionAnnouncement(store, call.bearer(), call.id(), publish = false)
        call.respond(HttpStatusCode.NoContent)
    }
}

private fun ResultSet.nullableLong(name: String) = getLong(name).takeUnless { wasNull() }
private fun announcementBody(value: String) = value.trim().takeIf { it.length in 1..16000 } ?: invalid("正文长度须为 1 至 16000 字符")
private fun Connection.announcementState(id: String) =
    rows("SELECT status FROM sys_announcement WHERE id = ?", id) { it.getString(1) }.singleOrNull() ?: missing("公告")

private suspend fun transitionAnnouncement(store: SystemStore, token: String, id: String, publish: Boolean) {
    store.write(token, announcementWrite) { db, _, audit ->
        if (db.announcementState(id) != if (publish) "DRAFT" else "PUBLISHED") throw SystemFailure(409, "当前公告状态不允许此操作")
        val now = System.currentTimeMillis()
        if (publish) db.change(
            "UPDATE sys_announcement SET status = 'PUBLISHED', updated_at = ?, published_at = ? WHERE id = ?", now, now, id,
        ) else db.change("UPDATE sys_announcement SET status = 'REVOKED', updated_at = ? WHERE id = ?", now, id)
        audit(if (publish) "announcement.publish" else "announcement.revoke", id)
    }
}

internal fun Route.auditRoutes(store: SystemStore) = route("audits") {
    get("logins") {
        val window = call.window()
        val successful = call.request.queryParameters["successful"]?.let { it.toBooleanStrictOrNull() ?: invalid("successful 必须为布尔值") }
        val where = if (successful == null) "" else " WHERE successful = ?"
        call.respond(store.read(call.bearer(), auditRead) { db, _ ->
            db.auditPage(window, "sys_login_audit", where, listOfNotNull(successful?.flag())) {
                LoginAudit(
                    it.getString("id"), it.getString("user_id"), it.getString("username"), it.getInt("successful") == 1,
                    it.getString("remote_address"), it.getLong("occurred_at"), it.getString("ip_region"),
                )
            }
        })
    }
    get("operations") {
        val window = call.window()
        val query = call.request.queryParameters
        val filters = listOfNotNull(
            query["actorId"]?.let { "actor_id = ?" to it },
            query["action"]?.let { "action = ?" to it },
        )
        val where = if (filters.isEmpty()) "" else filters.joinToString(" AND ", prefix = " WHERE ") { it.first }
        call.respond(store.read(call.bearer(), auditRead) { db, _ ->
            db.auditPage(window, "sys_operation_audit", where, filters.map { it.second }) {
                OperationAudit(it.getString("id"), it.getString("actor_id"), it.getString("action"), it.getString("target_id"), it.getLong("occurred_at"))
            }
        })
    }
    get("requests") {
        val window = call.window()
        val successful = call.request.queryParameters["successful"]?.let { it.toBooleanStrictOrNull() ?: invalid("successful 必须为布尔值") }
        val where = when (successful) { true -> " WHERE http_status < 400"; false -> " WHERE http_status >= 400"; null -> "" }
        call.respond(store.read(call.bearer(), auditRead) { db, _ ->
            db.auditPage(window, "sys_request_audit", where, emptyList()) {
                RequestAudit(
                    it.getString("id"), it.getString("actor_id"), it.getString("action"), it.getString("http_method"),
                    it.getString("route_pattern"), it.getInt("http_status"), it.getLong("duration_ms"),
                    it.getString("exception_type"), it.getString("remote_address"), it.getString("ip_region"),
                    it.getString("user_agent"), it.getLong("occurred_at"),
                )
            }
        })
    }
}

private fun <T> Connection.auditPage(window: Window, table: String, where: String, filters: List<Any>, map: (ResultSet) -> T): Page<T> {
    val args = filters.toTypedArray()
    val count = rows("SELECT COUNT(*) FROM $table$where", *args) { it.getLong(1) }.single()
    val records = rows("SELECT * FROM $table$where ORDER BY occurred_at DESC, id DESC LIMIT ? OFFSET ?",
        *args, window.size, window.offset, map = map)
    return window.result(records, count)
}

internal fun Connection.insertDictionary(id: String, rawCode: String, name: String) {
    val code = checkedCode(rawCode)
    if (exists(this, "SELECT 1 FROM sys_dictionary WHERE code = ?", code)) throw SystemFailure(409, "字典编码已存在")
    change("INSERT INTO sys_dictionary (id, code, name) VALUES (?, ?, ?)", id, code, checkedName(name))
}

internal fun Connection.saveItem(dictionary: String, id: String, request: EditItem, create: Boolean) {
    requireDictionary(dictionary)
    val value = checkedName(request.value)
    val label = checkedName(request.label)
    if (exists(this, "SELECT 1 FROM sys_dictionary_item WHERE dictionary_id = ? AND item_value = ? AND id <> ?", dictionary, value, id)) {
        throw SystemFailure(409, "字典项值已存在")
    }
    if (create) {
        change(
            "INSERT INTO sys_dictionary_item (id, dictionary_id, item_value, label, sort_order, enabled) VALUES (?, ?, ?, ?, ?, ?)",
            id, dictionary, value, label, request.sortOrder, request.enabled.flag(),
        )
    } else if (change(
            "UPDATE sys_dictionary_item SET item_value = ?, label = ?, sort_order = ?, enabled = ? WHERE id = ? AND dictionary_id = ?",
            value, label, request.sortOrder, request.enabled.flag(), id, dictionary,
        ) == 0
    ) missing("字典项")
}
