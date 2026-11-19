<script setup lang="ts">
import { computed, nextTick, ref, shallowRef } from 'vue'
import { request } from '@/api/client'
import { useAuth } from '@/store/auth'
import ResourcePager from '@/components/ResourcePager.vue'
import { errorMessage, formatTime, useResourcePage, type InboxItem } from '@/hooks/useResourcePage'

const auth = useAuth()
const unreadOnly = ref(false)
const { items, total, page, lastPage, loading, error, reload } = useResourcePage<InboxItem>(
  (number, size) => auth.user ? `/api/inbox?page=${number}&size=${size}&unreadOnly=${unreadOnly.value}` : null,
  '无法加载收件箱。',
)

const reading = shallowRef<InboxItem | null>(null)
const markedRead = ref(false)
const readerHeading = ref<HTMLElement | null>(null)
const filters = ref<HTMLElement | null>(null)
const pendingId = ref<string | null>(null)
const markError = ref('')
const markingAll = ref(false)
const markAllError = ref('')
const readPending = computed(() => pendingId.value !== null || markingAll.value)
const readingUnread = computed(() => reading.value?.readAt === null && !markedRead.value)

function selectFilter(unread: boolean) {
  if (unreadOnly.value === unread) return
  page.value = 1
  unreadOnly.value = unread
}

async function openMessage(item: InboxItem) {
  if (reading.value?.id === item.id) {
    readerHeading.value?.focus()
    return
  }
  reading.value = item
  markedRead.value = false
  markError.value = ''
  await nextTick()
  readerHeading.value?.focus()
  if (item.readAt === null) await markRead(item.id)
}

async function markRead(id: string) {
  if (!auth.user || readPending.value || reading.value?.id !== id || !readingUnread.value) return
  pendingId.value = id
  markError.value = ''
  try {
    await request<void>(`/api/inbox/${encodeURIComponent(id)}/read`, { method: 'PUT' })
    if (reading.value?.id === id) markedRead.value = true
    reload()
  } catch (cause) {
    if (reading.value?.id === id) markError.value = errorMessage(cause, '无法标记为已读。')
  } finally {
    pendingId.value = null
  }
}

async function markAllRead() {
  if (!auth.user || readPending.value) return
  markingAll.value = true
  markAllError.value = ''
  try {
    await request<void>('/api/inbox/read-all', { method: 'PUT' })
    if (reading.value) markedRead.value = true
    markError.value = ''
    reload()
  } catch (cause) {
    markAllError.value = errorMessage(cause, '无法将所有未读公告标为已读。')
  } finally {
    markingAll.value = false
  }
}

async function closeReader() {
  reading.value = null
  await nextTick()
  filters.value?.querySelector<HTMLButtonElement>('button[aria-pressed="true"]')?.focus()
}
</script>

<template>
  <section aria-labelledby="inbox-title">
    <header class="page-header">
      <div>
        <h1 id="inbox-title">个人收件箱</h1>
        <p>阅读已发布的系统公告。</p>
      </div>
    </header>

    <p v-if="!auth.user" class="empty">登录后可以查看收件箱。</p>
    <div v-else class="inbox-grid" :class="{ 'has-reader': reading }">
      <section class="panel inbox-list" aria-label="公告列表" :aria-busy="loading">
        <div class="toolbar">
          <div ref="filters" class="filter-buttons" role="group" aria-label="阅读状态筛选">
            <button type="button" class="btn" :class="unreadOnly ? 'btn-quiet' : 'btn-primary'" :aria-pressed="!unreadOnly" @click="selectFilter(false)">
              全部
            </button>
            <button type="button" class="btn" :class="unreadOnly ? 'btn-primary' : 'btn-quiet'" :aria-pressed="unreadOnly" @click="selectFilter(true)">
              未读
            </button>
          </div>
          <button type="button" class="btn" :disabled="readPending" @click="markAllRead">
            {{ markingAll ? '正在标记…' : '一键已读' }}
          </button>
        </div>

        <p v-if="markAllError" class="notice notice-error list-error" role="alert">
          一键已读失败：{{ markAllError }}
        </p>
        <p v-if="loading" class="empty" role="status">正在加载公告…</p>
        <div v-else-if="error" class="notice notice-error list-error" role="alert">
          <p>{{ error }}</p>
          <button type="button" class="btn btn-quiet" @click="reload">重新加载</button>
        </div>
        <p v-else-if="items.length === 0" class="empty">
          {{ unreadOnly ? '没有未读公告。' : '暂时没有公告。' }}
        </p>
        <ul v-else class="message-list">
          <li v-for="item in items" :key="item.id">
            <button
              type="button"
              class="message-row"
              :aria-current="reading?.id === item.id ? 'true' : undefined"
              :disabled="markingAll || (pendingId !== null && reading?.id !== item.id)"
              @click="openMessage(item)"
            >
              <span class="message-title">{{ item.title }}</span>
              <span class="message-meta">
                <span>{{ formatTime(item.publishedAt) }}</span>
                <span v-if="item.readAt === null" class="unread">未读</span>
              </span>
            </button>
          </li>
        </ul>

        <ResourcePager
          v-if="total > 0"
          v-model:page="page"
          :last-page="lastPage"
          :total="total"
          :disabled="loading"
          label="收件箱分页"
        />
      </section>

      <article v-if="reading" class="panel reader" aria-labelledby="inbox-reading-title">
        <div class="reader-header">
          <div class="reader-heading">
            <h2 id="inbox-reading-title" ref="readerHeading" tabindex="-1">{{ reading.title }}</h2>
            <button type="button" class="btn btn-quiet" @click="closeReader">关闭正文</button>
          </div>
          <p class="message-meta">
            <span>发布于 {{ formatTime(reading.publishedAt) }}</span>
            <span v-if="readingUnread" class="unread">未读</span>
            <span v-else>已读</span>
          </p>
        </div>
        <p v-if="pendingId === reading.id" class="message-meta" role="status">正在标记为已读…</p>
        <div v-if="markError" class="notice notice-error" role="alert">
          <p>标记已读失败：{{ markError }}</p>
          <button type="button" class="btn btn-quiet" :disabled="readPending" @click="markRead(reading.id)">重试标记已读</button>
        </div>
        <div class="message-body">{{ reading.body }}</div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.inbox-grid { display: grid; grid-template-columns: minmax(0, 1fr); align-items: start; gap: 1rem; }
.inbox-list, .reader { min-width: 0; }
.inbox-grid > .reader { margin: 0; padding: 1rem; }
.reader-heading { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: .5rem; }
.list-error { margin: 1rem; }
.filter-buttons, .message-meta { display: flex; align-items: center; flex-wrap: wrap; gap: .5rem; }
.message-list { display: grid; gap: .375rem; margin: 0; padding: 0 1rem .75rem; list-style: none; }
.message-row {
  display: grid;
  gap: .25rem;
  width: 100%;
  padding: .75rem;
  text-align: start;
  font: inherit;
  color: inherit;
  background: transparent;
  border: 1px solid var(--line, #e5e4e9);
  border-radius: .5rem;
  cursor: pointer;
}
.message-row:disabled { cursor: wait; }
.message-row:not(:disabled):hover, .message-row[aria-current='true'] { background: var(--accent-soft, #f1efff); border-color: #c4baf5; }
.message-title { font-weight: 600; overflow-wrap: anywhere; }
.message-meta { color: var(--muted, #5a5770); font-size: .875rem; }
.unread { color: var(--accent, #48359d); background: var(--accent-soft, #ebe7ff); border-radius: 99px; padding: .125rem .5rem; font-weight: 600; }
.reader-header h2 { margin: 0; overflow-wrap: anywhere; }
.message-body { margin-top: 1rem; border-top: 1px solid var(--line, #e5e4e9); padding-top: 1rem; white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.7; }

@media (min-width: 70rem) {
  .inbox-grid.has-reader { grid-template-columns: minmax(0, 2fr) minmax(0, 3fr); }
}
</style>
