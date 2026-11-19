<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Plus } from '@lucide/vue'
import { request } from '@/api/client'
import {
  adminRoleId, apiPath, fetchRoles, flattenTree, passwordError, usernamePattern,
  type DepartmentNode, type UserDetails, type UserListEntry, type UserSummary,
} from '@/api/system'
import { refreshSession, useAuth } from '@/store/auth'
import EditDialog from '@/components/EditDialog.vue'
import ListState from '@/components/ListState.vue'
import Pager from '@/components/Pager.vue'
import StatusChip from '@/components/StatusChip.vue'
import UserTable from '@/components/UserTable.vue'
import { useAction, usePage, useQuery, useResource } from '@/hooks/useQueries'

const auth = useAuth()
const manage = computed(() => auth.user?.administrator && auth.can('system:user:manage'))
const canReadRoles = computed(() => auth.can('system:role:read'))
const canReadDepartments = computed(() => auth.can('system:department:read'))

const {
  data: departments, error: departmentError, loading: departmentLoading, reload: reloadDepartments,
} = useResource<DepartmentNode[]>(() => canReadDepartments.value ? apiPath('/departments/tree') : null)
const departmentRows = computed(() => flattenTree(departments.value ?? []))
const departmentNames = computed(() => Object.fromEntries(departmentRows.value.map(({ node, trail }) => [node.id, trail])))
const {
  data: roles, error: roleError, loading: roleLoading, reload: reloadRoles,
} = useQuery((signal) => canReadRoles.value ? fetchRoles(signal) : Promise.resolve(null), canReadRoles)

const filters = reactive({ departmentId: '', roleId: '' })
const applied = ref({ ...filters })
const { data, loading, error, reload, page, size, total, setSize } = usePage<UserListEntry>(
  (page, size) => apiPath('/users', { page, size, ...applied.value }),
)

function applyFilters() {
  page.value = 1
  const newFilters = { departmentId: filters.departmentId.trim(), roleId: filters.roleId.trim() }
  if (newFilters.departmentId === applied.value.departmentId && newFilters.roleId === applied.value.roleId) {
    if (!loading.value) void reload()
  } else applied.value = newFilters
}

function reset() {
  filters.departmentId = ''
  filters.roleId = ''
  applyFilters()
}

function canEditProfile(user: UserSummary) {
  if (!auth.can('system:user:update') || !auth.user) return false
  const scope = auth.user.permissions['system:user:update']
  if (scope === 'ALL' || auth.user.id === user.id) return true
  if (!auth.user.departmentId || !user.departmentId || scope === 'SELF') return false
  if (auth.user.departmentId === user.departmentId) return true
  if (scope !== 'DEPARTMENT_AND_DESCENDANTS') return false
  const department = departmentRows.value.find(({ node }) => node.id === user.departmentId)
  return !department || department.ancestors.includes(auth.user.departmentId)
}

type Mode = 'detail' | 'create' | 'profile' | 'account'
const modal = ref<{ mode: Mode; entry?: UserListEntry } | null>(null)
const titles: Record<Mode, string> = {
  detail: '用户详情', create: '创建账号', profile: '编辑资料', account: '管理账号',
}
const draft = reactive({ username: '', displayName: '', departmentId: '', password: '', roleIds: [] as string[], enabled: true })
const feedback = ref('')
const { busy, error: saveError, run } = useAction()
const adminAccount = computed(() => draft.roleIds.includes(adminRoleId))
const needsOptions = computed(() => modal.value?.mode === 'create' || modal.value?.mode === 'account')
const {
  data: detail, loading: detailLoading, error: detailError, reload: reloadDetail,
} = useResource<UserDetails>(() => {
  const item = modal.value
  return item && (item.mode === 'detail' || item.mode === 'account') && item.entry
    ? apiPath(`/users/${encodeURIComponent(item.entry.user.id)}`)
    : null
})

watch(detail, (result) => {
  if (result && modal.value?.mode === 'account') {
    draft.departmentId = result.user.departmentId ?? ''
    draft.roleIds = [...result.roleIds]
    draft.enabled = result.user.enabled
  }
})

function open(mode: Mode, entry?: UserListEntry) {
  const user = entry?.user
  Object.assign(draft, {
    username: user?.username ?? '', displayName: user?.displayName ?? '', password: '',
    departmentId: user?.departmentId ?? '', roleIds: entry?.roles.map(({ id }) => id) ?? [], enabled: user?.enabled ?? true,
  })
  saveError.value = ''
  modal.value = { mode, entry }
}

function close() {
  draft.password = ''
  modal.value = null
}

async function save() {
  const item = modal.value
  if (!item || item.mode === 'detail') return
  const selectedId = item.entry?.user.id
  const completed = await run(async () => {
    if (item.mode === 'profile' && selectedId) {
      await request(apiPath(`/users/${encodeURIComponent(selectedId)}`), {
        method: 'PATCH', body: { displayName: draft.displayName.trim() },
      })
      return
    }
    if (draft.password || item.mode === 'create') {
      const invalid = passwordError(draft.password)
      if (invalid) throw new Error(invalid)
    }
    const account = { departmentId: draft.departmentId || null, roleIds: draft.roleIds }
    if (item.mode === 'create') {
      await request(apiPath('/users'), {
        method: 'POST',
        body: { ...account, username: draft.username.trim(), displayName: draft.displayName.trim(), password: draft.password },
      })
    } else if (selectedId) {
      await request(apiPath(`/users/${encodeURIComponent(selectedId)}/account`), {
        method: 'PUT',
        body: { ...account, enabled: draft.enabled, password: draft.password || null },
      })
    }
  })
  if (!completed) return
  feedback.value = item.mode === 'create' ? '账号已创建。' : '用户信息已保存。'
  close()
  if (selectedId === auth.user?.id) await refreshSession().catch(() => undefined)
  await reload()
}
</script>

<template>
  <header class="page-header">
    <div><h1>用户</h1><p>查看授权范围内的用户及其部门和角色。</p></div>
    <button v-if="manage" type="button" class="btn btn-primary" @click="open('create')">
      <Plus :size="17" aria-hidden="true" /> 创建账号
    </button>
  </header>
  <p v-if="feedback" class="notice notice-success" role="status">{{ feedback }}</p>
  <section class="panel" aria-label="用户目录">
    <form class="toolbar" @submit.prevent="applyFilters">
      <label class="field">
        <span>{{ departments ? '部门' : '部门 ID' }}</span>
        <select v-if="departments" v-model="filters.departmentId">
          <option value="">全部部门</option>
          <option v-for="{ node, trail } in departmentRows" :key="node.id" :value="node.id">{{ trail }}</option>
        </select>
        <input v-else v-model="filters.departmentId" maxlength="36">
      </label>
      <label class="field">
        <span>{{ roles ? '角色' : '角色 ID' }}</span>
        <select v-if="roles" v-model="filters.roleId">
          <option value="">全部角色</option>
          <option v-for="role in roles" :key="role.id" :value="role.id">{{ role.name }}{{ role.enabled ? '' : '（停用）' }}</option>
        </select>
        <input v-else v-model="filters.roleId" maxlength="36">
      </label>
      <button class="btn" type="submit">筛选</button>
      <button class="btn btn-quiet" type="button" @click="reset">重置</button>
    </form>
    <div v-if="departmentError || roleError" class="toolbar">
      <span class="muted" role="status">部分筛选选项读取失败，可以按 ID 筛选。</span>
      <button v-if="departmentError" class="btn btn-quiet" type="button" @click="reloadDepartments">重读部门</button>
      <button v-if="roleError" class="btn btn-quiet" type="button" @click="reloadRoles">重读角色</button>
    </div>
    <ListState :loading="loading" :error="error" :empty="!data?.items.length" empty-text="当前筛选条件下暂无用户。" @retry="reload">
      <UserTable :entries="data?.items ?? []" :department-names="departmentNames" show-department>
        <template #actions="{ entry }">
          <button type="button" class="btn btn-quiet" @click="open('detail', entry)">详情</button>
          <button v-if="canEditProfile(entry.user)" type="button" class="btn btn-quiet" @click="open('profile', entry)">编辑资料</button>
          <button v-if="manage" type="button" class="btn btn-quiet" @click="open('account', entry)">管理账号</button>
        </template>
      </UserTable>
    </ListState>
    <Pager v-if="data" v-model:page="page" :size="size" :total="total" @update:size="setSize" />
  </section>

  <EditDialog v-if="modal" :title="titles[modal.mode]" :busy="busy" @close="close">
    <ListState v-if="modal.mode === 'detail'" :loading="detailLoading" :error="detailError" @retry="reloadDetail">
      <dl v-if="detail" class="details-list">
        <dt>显示名</dt><dd>{{ detail.user.displayName }}</dd>
        <dt>账号</dt><dd>{{ detail.user.username }}</dd>
        <dt>用户 ID</dt><dd class="mono">{{ detail.user.id }}</dd>
        <dt>部门</dt><dd>{{ detail.user.departmentId ? (departmentNames[detail.user.departmentId] ?? detail.user.departmentId) : '无部门' }}</dd>
        <dt>状态</dt><dd><StatusChip :enabled="detail.user.enabled" /></dd>
        <dt>角色</dt><dd>{{ detail.roleIds.map(id => roles?.find(role => role.id === id)?.name ?? modal?.entry?.roles.find(role => role.id === id)?.name ?? id).join('、') || '无角色' }}</dd>
      </dl>
    </ListState>
    <ListState v-else-if="modal.mode === 'account' && !detail" :loading="detailLoading" :error="detailError" @retry="reloadDetail" />
    <ListState v-else-if="needsOptions && (!departments || !roles)"
      :loading="departmentLoading || roleLoading" :error="departmentError || roleError"
      @retry="() => { if (!departments) reloadDepartments(); if (!roles) reloadRoles() }" />
    <form v-else @submit.prevent="save">
      <div class="form-grid">
        <label v-if="modal.mode !== 'profile'" class="field">
          <span>账号</span>
          <input v-model="draft.username" :disabled="modal.mode === 'account'" :pattern="usernamePattern"
            maxlength="64" autocomplete="off" autocapitalize="none" spellcheck="false" required>
        </label>
        <label v-if="modal.mode !== 'account'" class="field" :class="{ 'span-full': modal.mode === 'profile' }">
          <span>显示名</span><input v-model.trim="draft.displayName" required maxlength="120">
        </label>
        <template v-if="needsOptions">
          <label class="field">
            <span>部门</span>
            <select v-model="draft.departmentId">
              <option value="">无部门</option>
              <option v-for="{ node, trail } in departmentRows" :key="node.id" :value="node.id">{{ trail }}</option>
            </select>
          </label>
          <label class="field">
            <span>{{ modal.mode === 'create' ? '初始密码' : '新密码' }}</span>
            <input v-model="draft.password" type="password" autocomplete="new-password" :required="modal.mode === 'create'">
            <small>{{ modal.mode === 'create' ? '至少 12 个字符，最多 72 个 UTF-8 字节。' : '留空保留当前密码。重置后注销该账号的所有会话。' }}</small>
          </label>
          <label v-if="modal.mode === 'account'" class="checkline">
            <input v-model="draft.enabled" type="checkbox" :disabled="adminAccount"> 启用账号
          </label>
          <fieldset class="choice-group span-full">
            <legend>分配角色</legend>
            <div class="option-list">
              <label v-if="adminAccount" class="checkline">
                <input :checked="true" type="checkbox" disabled> 系统管理员（内置）
              </label>
              <label v-for="role in (roles ?? []).filter(row => row.id !== adminRoleId)" :key="role.id" class="checkline">
                <input v-model="draft.roleIds" type="checkbox" :value="role.id">
                <span>{{ role.name }}{{ role.enabled ? '' : '（停用）' }}</span>
              </label>
              <span v-if="!adminAccount && !roles?.some(row => row.id !== adminRoleId)" class="muted">暂无可分配的角色。</span>
            </div>
          </fieldset>
        </template>
        <p v-if="saveError" class="notice notice-error span-full" role="alert">{{ saveError }}</p>
      </div>
      <div class="form-actions">
        <button type="button" class="btn" :disabled="busy" @click="close">取消</button>
        <button type="submit" class="btn btn-primary" :disabled="busy">{{ busy ? '保存中…' : '保存' }}</button>
      </div>
    </form>
  </EditDialog>
</template>
