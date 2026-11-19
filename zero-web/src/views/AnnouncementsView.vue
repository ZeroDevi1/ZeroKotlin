<script setup lang="ts">
import { computed, nextTick, reactive, ref, shallowRef } from 'vue'
import { request } from '@/api/client'
import { useAuth } from '@/store/auth'
import ResourcePager from '@/components/ResourcePager.vue'
import { errorMessage, formatTime, useResourcePage, type Announcement, type AnnouncementStatus } from '@/hooks/useResourcePage'

type Transition = 'publish' | 'revoke'
type AnnouncementPanel =
  | { mode: 'draft'; announcement: Announcement | null }
  | { mode: 'detail'; announcement: Announcement }
  | { mode: 'transition'; announcement: Announcement; action: Transition }

const statusLabels: Record<AnnouncementStatus, string> = { DRAFT: '草稿', PUBLISHED: '已发布', REVOKED: '已撤回' }
const actionLabels: Record<Transition, string> = { publish: '发布', revoke: '撤回' }
const endpoint = '/api/system/announcements'
const auth = useAuth()
const canRead = computed(() => auth.can('system:announcement:read'))
const canWrite = computed(() => canRead.value && auth.can('system:announcement:write'))
const { items, total, page, lastPage, loading, error, reload } = useResourcePage<Announcement>(
  (number, size) => canRead.value ? `${endpoint}?page=${number}&size=${size}` : null,
  '无法加载公告列表。',
)

const panel = shallowRef<AnnouncementPanel | null>(null)
const form = reactive({ title: '', body: '' })
const saving = ref(false)
const actionError = ref('')
const feedback = ref('')
const panelHeading = ref<HTMLElement | null>(null)
const titleInput = ref<HTMLInputElement | null>(null)
const panelTitle = computed(() => {
  switch (panel.value?.mode) {
    case 'draft': return panel.value.announcement ? '编辑草稿' : '新建公告草稿'
    case 'detail': return '公告详情'
    case 'transition': return `确认${actionLabels[panel.value.action]}公告`
    default: return ''
  }
})

function canTransition(announcement: Announcement, action: Transition) {
  return canWrite.value && (action === 'publish' ? announcement.status === 'DRAFT' : announcement.status === 'PUBLISHED')
}

async function openPanel(next: AnnouncementPanel) {
  if (!canRead.value || saving.value) return
  if (next.mode === 'draft') {
    if (!canWrite.value || (next.announcement && next.announcement.status !== 'DRAFT')) return
    form.title = next.announcement?.title ?? ''
    form.body = next.announcement?.body ?? ''
  }
  if (next.mode === 'transition' && !canTransition(next.announcement, next.action)) return
  panel.value = next
  actionError.value = ''
  feedback.value = ''
  await nextTick()
  if (next.mode === 'draft') titleInput.value?.focus()
  else panelHeading.value?.focus()
}

async function saveDraft() {
  const current = panel.value
  if (!canWrite.value || current?.mode !== 'draft' || saving.value) return
  if (current.announcement && current.announcement.status !== 'DRAFT') return
  const title = form.title.trim()
  const body = form.body.trim()
  if (!title || !body) {
    actionError.value = '请输入公告标题和正文。'
    return
  }

  saving.value = true
  actionError.value = ''
  try {
    if (current.announcement) {
      await request<void>(`${endpoint}/${encodeURIComponent(current.announcement.id)}`, { method: 'PUT', body: { title, body } })
    } else {
      await request<{ id: string }>(endpoint, { method: 'POST', body: { title, body } })
    }
    feedback.value = `已保存草稿：${title}。`
    panel.value = null
    reload()
  } catch (cause) {
    actionError.value = errorMessage(cause, '无法保存草稿。')
  } finally {
    saving.value = false
  }
}

async function applyTransition() {
  const current = panel.value
  if (current?.mode !== 'transition' || !canTransition(current.announcement, current.action) || saving.value) return
  saving.value = true
  actionError.value = ''
  try {
    await request<void>(`${endpoint}/${encodeURIComponent(current.announcement.id)}/${current.action}`, { method: 'POST' })
    feedback.value = `已${actionLabels[current.action]}公告：${current.announcement.title}。`
    panel.value = null
    reload()
  } catch (cause) {
    actionError.value = errorMessage(cause, `无法${actionLabels[current.action]}公告。`)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section aria-labelledby="announcements-title">
    <header class="page-header">
      <div>
        <h1 id="announcements-title">公告管理</h1>
        <p>编写草稿、发布公告或撤回已发布的公告。</p>
      </div>
      <button v-if="canWrite" type="button" class="btn btn-primary" :disabled="saving" @click="openPanel({ mode: 'draft', announcement: null })">
        新建草稿
      </button>
    </header>

    <p v-if="!canRead" class="empty">当前账号没有查看公告的权限。</p>
    <div v-else class="page-sections">
      <p v-if="feedback" class="notice notice-success" role="status">{{ feedback }}</p>
      <section v-if="panel && (canWrite || panel.mode === 'detail')" class="panel editor-panel" aria-labelledby="announcement-panel-heading" :aria-busy="saving">
        <div class="toolbar panel-toolbar">
          <h2 id="announcement-panel-heading" ref="panelHeading" tabindex="-1">{{ panelTitle }}</h2>
          <button v-if="panel.mode === 'detail'" type="button" class="btn btn-quiet" @click="panel = null">关闭详情</button>
        </div>

        <form v-if="panel.mode === 'draft'" @submit.prevent="saveDraft">
          <fieldset class="draft-fields" :disabled="saving">
            <div class="form-grid draft-grid">
              <label class="field">
                <span>公告标题</span>
                <input v-model="form.title" ref="titleInput" required maxlength="120" autocomplete="off">
              </label>
              <label class="field">
                <span>公告正文</span>
                <textarea v-model="form.body" rows="8" required maxlength="16000"></textarea>
              </label>
            </div>
            <p v-if="actionError" class="notice notice-error" role="alert">保存草稿失败：{{ actionError }}</p>
            <div class="actions panel-actions">
              <button type="submit" class="btn btn-primary">{{ saving ? '正在保存…' : '保存草稿' }}</button>
              <button type="button" class="btn btn-quiet" @click="panel = null">取消</button>
            </div>
          </fieldset>
        </form>

        <article v-else-if="panel.mode === 'detail'" aria-labelledby="announcement-detail-title">
          <h3 id="announcement-detail-title" class="announcement-title">{{ panel.announcement.title }}</h3>
          <div class="announcement-meta">
            <span class="status" :data-status="panel.announcement.status">{{ statusLabels[panel.announcement.status] }}</span>
            <span>创建于 {{ formatTime(panel.announcement.createdAt) }}</span>
            <span>更新于 {{ formatTime(panel.announcement.updatedAt) }}</span>
            <span v-if="panel.announcement.publishedAt !== null">发布于 {{ formatTime(panel.announcement.publishedAt) }}</span>
          </div>
          <div class="announcement-body">{{ panel.announcement.body }}</div>
        </article>

        <form v-else @submit.prevent="applyTransition">
          <p class="announcement-title"><strong>{{ panel.announcement.title }}</strong></p>
          <p v-if="panel.action === 'publish'">发布后，用户可在个人收件箱阅读这条公告。</p>
          <p v-else>撤回后，这条公告将从收件箱移除，且不能再次发布。</p>
          <p v-if="actionError" class="notice notice-error" role="alert">{{ actionLabels[panel.action] }}失败：{{ actionError }}</p>
          <div class="actions panel-actions">
            <button type="submit" class="btn btn-primary" :disabled="saving || !canTransition(panel.announcement, panel.action)">
              {{ saving ? `正在${actionLabels[panel.action]}…` : `确认${actionLabels[panel.action]}` }}
            </button>
            <button type="button" class="btn btn-quiet" :disabled="saving" @click="panel = null">取消</button>
          </div>
        </form>
      </section>

      <section class="panel" aria-label="公告列表" :aria-busy="loading">
        <p v-if="loading" class="empty" role="status">正在加载公告…</p>
        <div v-else-if="error" class="notice notice-error list-error" role="alert">
          <p>{{ error }}</p>
          <button type="button" class="btn btn-quiet" @click="reload">重新加载</button>
        </div>
        <p v-else-if="items.length === 0" class="empty">暂无公告。</p>
        <div v-else class="table-wrap">
          <table class="data-table announcement-table">
            <thead>
              <tr>
                <th scope="col">标题</th>
                <th scope="col">状态</th>
                <th scope="col">最近更新</th>
                <th scope="col">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="announcement in items" :key="announcement.id">
                <th scope="row">{{ announcement.title }}</th>
                <td><span class="status" :data-status="announcement.status">{{ statusLabels[announcement.status] }}</span></td>
                <td class="updated-time">{{ formatTime(announcement.updatedAt) }}</td>
                <td>
                  <div class="actions">
                    <button type="button" class="btn btn-quiet" :disabled="saving" :aria-label="`查看公告：${announcement.title}`" @click="openPanel({ mode: 'detail', announcement })">查看</button>
                    <template v-if="canWrite && announcement.status === 'DRAFT'">
                      <button type="button" class="btn btn-quiet" :disabled="saving" :aria-label="`编辑草稿：${announcement.title}`" @click="openPanel({ mode: 'draft', announcement })">编辑</button>
                      <button type="button" class="btn btn-quiet" :disabled="saving" :aria-label="`发布公告：${announcement.title}`" @click="openPanel({ mode: 'transition', announcement, action: 'publish' })">发布</button>
                    </template>
                    <button v-if="canTransition(announcement, 'revoke')" type="button" class="btn btn-quiet" :disabled="saving" :aria-label="`撤回公告：${announcement.title}`" @click="openPanel({ mode: 'transition', announcement, action: 'revoke' })">撤回</button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <ResourcePager
          v-if="total > 0"
          v-model:page="page"
          :last-page="lastPage"
          :total="total"
          :disabled="loading"
          label="公告管理分页"
        />
      </section>
    </div>
  </section>
</template>

<style scoped>
.page-sections { display: grid; grid-template-columns: minmax(0, 1fr); gap: 1rem; }
.page-sections > .panel + .panel, .page-sections > .notice { margin: 0; }
.editor-panel > form, .editor-panel > article { padding: 0 1rem 1rem; }
.editor-panel .notice { margin-top: .75rem; }
.list-error { margin: 1rem; }
.panel-toolbar, .actions, .announcement-meta { display: flex; align-items: center; flex-wrap: wrap; gap: .5rem; }
.panel-toolbar { justify-content: space-between; }
.panel-toolbar h2 { margin: 0; }
.draft-fields { min-width: 0; border: 0; margin: 0; padding: 0; }
.draft-grid { display: grid; grid-template-columns: minmax(0, 1fr); gap: .75rem; }
.draft-grid textarea { resize: vertical; min-height: 8rem; }
.panel-actions { margin-top: 1rem; }
.announcement-title, .announcement-table tbody th { overflow-wrap: anywhere; white-space: normal; }
.announcement-table tbody th { background: none; color: inherit; text-align: start; border-top: 1px solid var(--line, #e5e4e9); font-size: inherit; }
.announcement-meta { color: var(--muted, #5a5770); gap: .5rem 1rem; font-size: .875rem; }
.announcement-body { margin-top: 1rem; border-top: 1px solid var(--line, #e5e4e9); padding-top: 1rem; white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.7; }
.updated-time { white-space: nowrap; }
.status { display: inline-block; color: #534184; background: #efe9ff; border-radius: 99px; padding: .125rem .5rem; font-size: .8125rem; white-space: nowrap; }
.status[data-status='PUBLISHED'] { color: #166045; background: #e1f3eb; }
.status[data-status='REVOKED'] { color: #4b475a; background: #edecef; }
</style>
