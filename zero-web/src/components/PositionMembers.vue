<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'
import { request } from '@/api/client'
import { useAuth } from '@/store/auth'
import ResourcePager from '@/components/ResourcePager.vue'
import { errorMessage, useResourcePage, type Position } from '@/hooks/useResourcePage'

type Member = { userId: string; username: string; displayName: string }
type UserDirectoryEntry = {
  user: { id: string; username: string; displayName: string; enabled: boolean }
}

const props = defineProps<{ position: Position }>()
const emit = defineEmits<{ close: []; busy: [busy: boolean] }>()
const auth = useAuth()
const canRead = computed(() => auth.user?.administrator === true && auth.can('system:position:read'))
const canWrite = computed(() => canRead.value && auth.can('system:position:write'))
const canBrowse = computed(() => canWrite.value && auth.can('system:user:read'))
const membersPath = computed(() => `/api/system/positions/${encodeURIComponent(props.position.id)}/members`)

const selected = shallowRef(new Map<string, Member>())
const originalIds = shallowRef<Set<string> | null>(null)
const membersLoading = ref(false)
const membersError = ref('')
const loadVersion = ref(0)
const saving = ref(false)
const saveError = ref('')
const saved = ref(false)
const changed = computed(() => {
  const baseline = originalIds.value
  return baseline !== null && (baseline.size !== selected.value.size ||
    [...selected.value.keys()].some((id) => !baseline.has(id)))
})

watch([() => canRead.value ? membersPath.value : null, loadVersion], async ([path], _, onCleanup) => {
  selected.value = new Map()
  originalIds.value = null
  membersError.value = ''
  membersLoading.value = path !== null
  if (!path) return

  const controller = new AbortController()
  onCleanup(() => controller.abort())
  try {
    const members = await request<Member[]>(path, { signal: controller.signal })
    if (controller.signal.aborted) return
    selected.value = new Map(members.map((member) => [member.userId, member]))
    originalIds.value = new Set(selected.value.keys())
  } catch (cause) {
    if (!controller.signal.aborted) membersError.value = errorMessage(cause, '无法加载岗位成员。')
  } finally {
    if (!controller.signal.aborted) membersLoading.value = false
  }
}, { immediate: true })

const { items, total, page, lastPage, loading, error, reload } = useResourcePage<UserDirectoryEntry>(
  (number, size) => canBrowse.value && originalIds.value !== null
    ? `/api/system/users?page=${number}&size=${size}`
    : null,
  '无法加载用户目录。',
  10,
)

function setMember(member: Member, include: boolean) {
  if (!canWrite.value || originalIds.value === null || saving.value) return
  const next = new Map(selected.value)
  if (include) next.set(member.userId, member)
  else next.delete(member.userId)
  selected.value = next
  saveError.value = ''
  saved.value = false
}

function toggleUser(user: UserDirectoryEntry['user'], event: Event) {
  if (!canBrowse.value) return
  const target = event.currentTarget as HTMLInputElement
  setMember({ userId: user.id, username: user.username, displayName: user.displayName }, target.checked)
}

async function saveMembers() {
  if (!canWrite.value || !changed.value || saving.value) return
  const userIds = [...selected.value.keys()]
  saving.value = true
  saved.value = false
  saveError.value = ''
  emit('busy', true)
  try {
    await request<void>(membersPath.value, { method: 'PUT', body: { userIds } })
    originalIds.value = new Set(userIds)
    saved.value = true
  } catch (cause) {
    saveError.value = errorMessage(cause, '无法保存岗位成员。')
  } finally {
    saving.value = false
    emit('busy', false)
  }
}
</script>

<template>
  <section class="panel member-panel" aria-labelledby="position-members-heading" :aria-busy="membersLoading || saving">
    <div class="toolbar member-toolbar">
      <h2 id="position-members-heading" tabindex="-1">岗位成员：{{ position.name }}</h2>
      <button type="button" class="btn btn-quiet" :disabled="saving" @click="emit('close')">关闭</button>
    </div>

    <p v-if="membersLoading" class="empty" role="status">正在读取完整成员名单…</p>
    <div v-else-if="membersError" class="notice notice-error" role="alert">
      <p>{{ membersError }}</p>
      <button type="button" class="btn btn-quiet" @click="loadVersion++">重新加载成员</button>
    </div>
    <p v-else-if="!canRead || originalIds === null" class="empty">无法查看岗位成员。</p>

    <form v-else @submit.prevent="saveMembers">
      <div class="member-grid" :class="{ 'with-directory': canBrowse }">
        <section class="member-list" aria-labelledby="position-selected-heading">
          <h3 id="position-selected-heading">{{ canWrite ? '已选成员' : '当前成员' }}（{{ selected.size }}）</h3>
          <p v-if="selected.size === 0" class="empty">{{ canWrite ? '未选择成员。' : '此岗位暂无成员。' }}</p>
          <ul v-else class="people selected-people">
            <li v-for="member in selected.values()" :key="member.userId" class="person-row">
              <span class="person">
                <strong>{{ member.displayName }}</strong>
                <span>{{ member.username }}</span>
              </span>
              <button
                v-if="canWrite"
                type="button"
                class="btn btn-quiet"
                :disabled="saving"
                :aria-label="`移除 ${member.displayName}（${member.username}）`"
                @click="setMember(member, false)"
              >移除</button>
            </li>
          </ul>
        </section>

        <section v-if="canBrowse" class="member-list" aria-labelledby="position-directory-heading" :aria-busy="loading">
          <h3 id="position-directory-heading">用户目录</h3>
          <p v-if="loading" class="empty" role="status">正在加载用户…</p>
          <div v-else-if="error" class="notice notice-error" role="alert">
            <p>{{ error }}</p>
            <button type="button" class="btn btn-quiet" @click="reload">重新加载用户</button>
          </div>
          <p v-else-if="items.length === 0" class="empty">用户目录为空。</p>
          <ul v-else class="people">
            <li v-for="{ user } in items" :key="user.id">
              <label class="person-row directory-row">
                <input type="checkbox" :checked="selected.has(user.id)" :disabled="saving" @change="toggleUser(user, $event)">
                <span class="person">
                  <strong>{{ user.displayName }}</strong>
                  <span>{{ user.username }}<template v-if="!user.enabled"> · 已停用</template></span>
                </span>
              </label>
            </li>
          </ul>
          <ResourcePager
            v-if="total > 0"
            v-model:page="page"
            :last-page="lastPage"
            :total="total"
            :disabled="loading || saving"
            label="用户目录分页"
          />
        </section>
      </div>

      <template v-if="canWrite">
        <p v-if="!canBrowse" class="member-help">缺少查看用户权限，无法从用户目录添加成员。</p>
        <p v-if="changed && selected.size === 0" class="notice">保存后将移除此岗位的所有成员。</p>
        <p v-else class="member-help">保存后，岗位成员将以已选名单为准。</p>
        <p v-if="saveError" class="notice notice-error" role="alert">保存成员失败：{{ saveError }}</p>
        <p v-if="saved" class="notice notice-success" role="status">岗位成员已保存。</p>
        <button type="submit" class="btn btn-primary" :disabled="!changed || saving">
          {{ saving ? '正在保存…' : '保存成员' }}
        </button>
      </template>
    </form>
  </section>
</template>

<style scoped>
.member-panel { padding: 0 1rem 1rem; }
.member-panel > .member-toolbar { justify-content: space-between; flex-wrap: wrap; gap: .5rem; padding-inline: 0; }
.member-toolbar h2 { margin: 0; overflow-wrap: anywhere; }
.member-grid { display: grid; align-items: start; gap: 1rem; }
.member-list { min-width: 0; padding: .75rem; border: 1px solid var(--line, #e5e4e9); border-radius: .5rem; }
.member-list h3 { margin: 0 0 .5rem; font-size: 1rem; }
.people { margin: 0; padding: 0; list-style: none; }
.selected-people { max-height: 19rem; overflow: auto; }
.people li + li { border-top: 1px solid var(--line, #e5e4e9); }
.person-row { display: flex; min-height: 2.75rem; align-items: center; gap: .75rem; padding: .375rem .25rem; }
.person { display: grid; min-width: 0; flex: 1; overflow-wrap: anywhere; }
.person span, .member-help { color: var(--muted, #5a5770); font-size: .875rem; }
.member-help, .member-grid + .notice { margin-block: .75rem; }
.directory-row { cursor: pointer; }
.directory-row:hover { background: var(--accent-soft, #f3f1fa); }
.directory-row input { width: 1rem; height: 1rem; margin: 0; accent-color: var(--accent, #4d3bb2); }

@media (min-width: 57rem) {
  .member-grid.with-directory { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
