package dev.zerokotlin.ktor.system.inbox

import dev.zerokotlin.ktor.Page
import dev.zerokotlin.ktor.Window
import dev.zerokotlin.ktor.change
import dev.zerokotlin.ktor.exists
import dev.zerokotlin.ktor.missing
import dev.zerokotlin.ktor.rows
import java.sql.Connection

/** 只查询已发布公告；总数与分页使用同一份已读筛选。 */
internal fun Connection.inboxPage(user: String, window: Window, unread: Boolean): Page<InboxEntry> {
    val filter = """FROM sys_announcement a LEFT JOIN sys_announcement_read r
        ON r.announcement_id = a.id AND r.user_id = ?
        WHERE a.status = 'PUBLISHED'""" + if (unread) " AND r.user_id IS NULL" else ""
    val items = rows(
        "SELECT a.id, a.title, a.body, a.published_at, r.read_at $filter ORDER BY a.published_at DESC, a.id DESC LIMIT ? OFFSET ?",
        user, window.size, window.offset,
    ) { row ->
        InboxEntry(row.getString(1), row.getString(2), row.getString(3), row.getLong(4), row.getLong(5).takeUnless { row.wasNull() })
    }
    return window.result(items, rows("SELECT COUNT(*) $filter", user) { it.getLong(1) }.single())
}

/** INSERT SELECT 在同一事务下幂等处理读取单条和全部已读。 */
internal fun Connection.markInboxRead(user: String, id: String?) {
    if (id != null && !exists(this, "SELECT 1 FROM sys_announcement WHERE id = ? AND status = 'PUBLISHED'", id)) missing("公告")
    val byId = if (id == null) "" else " AND a.id = ?"
    val args = listOfNotNull(user, System.currentTimeMillis(), user, id).toTypedArray()
    change(
        """INSERT INTO sys_announcement_read (user_id, announcement_id, read_at)
           SELECT ?, a.id, ? FROM sys_announcement a
           WHERE a.status = 'PUBLISHED' AND NOT EXISTS (
               SELECT 1 FROM sys_announcement_read r WHERE r.user_id = ? AND r.announcement_id = a.id
           )$byId""",
        *args,
    )
}
