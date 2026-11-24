package dev.zerokotlin.ktor.system.inbox

import dev.zerokotlin.ktor.Page
import dev.zerokotlin.ktor.SystemStore
import dev.zerokotlin.ktor.Window
import dev.zerokotlin.ktor.system.auth.requireAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
internal data class InboxEntry(val id: String, val title: String, val body: String, val publishedAt: Long, val readAt: Long?)

/** 收件箱和已读标记都从会话获得用户 ID。 */
internal class InboxService(private val store: SystemStore) {
    suspend fun page(token: String, window: Window, unread: Boolean): Page<InboxEntry> = withContext(Dispatchers.IO) {
        store.transaction { db -> db.inboxPage(db.requireAccount(token).id, window, unread) }
    }

    suspend fun markRead(token: String, id: String?) {
        withContext(Dispatchers.IO) {
            store.transaction(write = true) { db -> db.markInboxRead(db.requireAccount(token).id, id) }
        }
    }
}
