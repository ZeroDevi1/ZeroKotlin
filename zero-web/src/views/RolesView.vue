<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Plus } from '@lucide/vue'
import { request } from '@/api/client'
import {
  adminRoleId, apiPath, codePattern, dataScopes, flattenTree,
  type DataScope, type MenuNode, type PermissionSummary, type RoleDetails, type RoleMenus, type RoleSummary,
} from '@/api/system'
import { useAuth } from '@/store/auth'
import DeleteDialog from '@/components/DeleteDialog.vue'
import EditDialog from '@/components/EditDialog.vue'
import ListState from '@/components/ListState.vue'
import Pager from '@/components/Pager.vue'
import StatusChip from '@/components/StatusChip.vue'
import { useAction, usePage, useResource } from '@/hooks/useQueries'

const auth = useAuth()
const mayWrite = computed(() => auth.user?.administrator && auth.can('system:role:write'))
const enabled = ref('')
const { data, loading, error, reload, page, size, total, setSize } = usePage<RoleSummary>(
  (page, size) => apiPath('/roles', { page, size, enabled: enabled.value }),
)
watch(enabled, () => { page.value = 1 }, { flush: 'sync' })

const modal = ref<{ mode: 'detail' | 'edit' | 'create'; id?: string } | null>(null)
const draft = reactive({ code: '', name: '', dataScope: 'SELF' as DataScope, enabled: true, permissionCodes: [] as string[] })
const draftTitle = computed(() => ({ create: '创建角色', edit: '编辑角色', detail: '角色详情' })[modal.value?.mode ?? 'detail'])
const feedback = ref('')
const { busy, error: saveError, run } = useAction()
const selectedIds = ref<string[]>([])
const removing = ref<{ ids: string[]; name?: string } | null>(null)
watch(data, () => { selectedIds.value = [] })

function openDelete(role?: RoleSummary) {
  removing.value = role ? { ids: [role.id], name: role.name } : { ids: [...selectedIds.value] }
}

function deleted(ids: string[]) {
  removing.value = null
  feedback.value = `已删除 ${ids.length} 个角色。`
  const lastPage = Math.max(1, Math.ceil((total.value - ids.length) / size.value))
  if (page.value > lastPage) page.value = lastPage
  else void reload()
}

const { data: detail, loading: detailLoading, error: detailError, reload: reloadDetail } = useResource<RoleDetails>(
  () => modal.value?.id ? apiPath(`/roles/${encodeURIComponent(modal.value.id)}`) : null,
)
const {
  data: permissions, loading: permissionsLoading, error: permissionsError, reload: reloadPermissions,
} = useResource<PermissionSummary[]>(
  () => modal.value && modal.value.mode !== 'detail' && auth.can('system:permission:read') ? apiPath('/permissions') : null,
)
const reservedPermissions = new Set([
  'system:user:manage', 'system:role:write', 'system:permission:write', 'system:department:write', 'system:position:write',
])
const permissionChoices = computed(() => (permissions.value ?? []).filter(({ code }) => !reservedPermissions.has(code)))

watch(detail, (result) => {
  if (result && modal.value?.mode === 'edit') {
    Object.assign(draft, result.role, { permissionCodes: [...result.permissionCodes] })
  }
})

function open(mode: 'detail' | 'edit' | 'create', id?: string) {
  Object.assign(draft, { code: '', name: '', dataScope: 'SELF', enabled: true, permissionCodes: [] })
  saveError.value = ''
  modal.value = { mode, id }
}

function close() {
  modal.value = null
}

async function save() {
  const item = modal.value
  if (!item || item.mode === 'detail') return
  const body = { name: draft.name, dataScope: draft.dataScope, permissionCodes: draft.permissionCodes }
  const completed = await run(async () => {
    if (item.mode === 'create') {
      await request(apiPath('/roles'), { method: 'POST', body: { ...body, code: draft.code } })
    } else if (item.id) {
      await request(apiPath(`/roles/${encodeURIComponent(item.id)}`), {
        method: 'PUT', body: { ...body, enabled: draft.enabled },
      })
    }
  })
  if (!completed) return
  feedback.value = item.mode === 'create' ? '角色已创建。' : '角色已保存。'
  close()
  await reload()
}

const menuRole = ref<RoleSummary | null>(null)
const menuDraft = reactive<RoleMenus>({ mode: 'LEGACY', menuIds: [] })
const menuSave = useAction()
const menuSettings = useResource<RoleMenus>(() =>
  menuRole.value ? apiPath(`/roles/${encodeURIComponent(menuRole.value.id)}/menus`) : null,
)
const menuOptions = useResource<MenuNode[]>(() => menuRole.value ? apiPath('/menus') : null)
const menuRows = computed(() => flattenTree(menuOptions.data.value ?? []))
const menuLoading = computed(() => menuSettings.loading.value || menuOptions.loading.value)
const menuError = computed(() => menuSettings.error.value || menuOptions.error.value)

watch(menuSettings.data, (value) => {
  if (value) Object.assign(menuDraft, { mode: value.mode, menuIds: [...value.menuIds] })
})

function openMenus(role: RoleSummary) {
  menuSave.error.value = ''
  menuRole.value = role
}

function retryMenus() {
  if (menuSettings.error.value) void menuSettings.reload()
  if (menuOptions.error.value) void menuOptions.reload()
}

async function saveMenus() {
  const role = menuRole.value
  if (!role) return
  const completed = await menuSave.run(async () => {
    if (menuDraft.mode === 'DIRECT' && menuDraft.menuIds.length > 256) {
      throw new Error('一个角色最多可勾选 256 个菜单。')
    }
    await request(apiPath(`/roles/${encodeURIComponent(role.id)}/menus`), {
      method: 'PUT',
      body: { mode: menuDraft.mode, menuIds: menuDraft.mode === 'DIRECT' ? menuDraft.menuIds : [] },
    })
  })
  if (!completed) return
  feedback.value = '角色菜单已保存。'
  menuRole.value = null
}
</script>

<template>
  <header class="page-header">
    <div><h1>角色</h1><p>为角色配置权限点与可访问的用户范围。</p></div>
    <button v-if="mayWrite" class="btn btn-primary" type="button" @click="open('create')">
      <Plus :size="17" aria-hidden="true" /> 创建角色
    </button>
  </header>
  <p v-if="feedback" class="notice notice-success" role="status">{{ feedback }}</p>
  <section class="panel" aria-label="角色列表">
    <div class="toolbar">
      <label class="field">
        <span>状态</span>
        <select v-model="enabled">
          <option value="">全部状态</option><option value="true">启用</option><option value="false">停用</option>
        </select>
      </label>
      <button v-if="mayWrite" class="btn" type="button" :disabled="!selectedIds.length || loading" @click="openDelete()">
        删除选中<span v-if="selectedIds.length">（{{ selectedIds.length }}）</span>
      </button>
    </div>
    <ListState :loading="loading" :error="error" :empty="!data?.items.length" empty-text="暂无符合条件的角色。" @retry="reload">
      <div class="table-wrap">
        <table class="data-table" aria-label="角色列表">
          <thead><tr>
            <th v-if="mayWrite" scope="col"><span class="sr-only">选择角色</span></th>
            <th scope="col">角色</th><th scope="col">数据范围</th><th scope="col">状态</th><th scope="col">操作</th>
          </tr></thead>
          <tbody>
            <tr v-for="role in data?.items" :key="role.id">
              <td v-if="mayWrite">
                <input v-if="role.id !== adminRoleId" v-model="selectedIds" type="checkbox" :value="role.id"
                  :aria-label="`选择角色 ${role.name}`">
              </td>
              <td><strong>{{ role.name }}</strong><code class="cell-meta">{{ role.code }}</code></td>
              <td>{{ dataScopes[role.dataScope] }}</td>
              <td><StatusChip :enabled="role.enabled" /></td>
              <td><div class="row-actions">
                <button class="btn btn-quiet" type="button" @click="open('detail', role.id)">详情</button>
                <button v-if="mayWrite && role.id !== adminRoleId" class="btn btn-quiet" type="button"
                  @click="open('edit', role.id)">编辑</button>
                <button v-if="mayWrite && role.id !== adminRoleId && auth.can('system:menu:read')" class="btn btn-quiet"
                  type="button" @click="openMenus(role)">分配菜单</button>
                <button v-if="mayWrite && role.id !== adminRoleId" class="btn btn-quiet" type="button"
                  @click="openDelete(role)">删除</button>
              </div></td>
            </tr>
          </tbody>
        </table>
      </div>
    </ListState>
    <Pager v-if="data" v-model:page="page" :size="size" :total="total" @update:size="setSize" />
  </section>

  <EditDialog v-if="modal" :title="draftTitle" :busy="busy" @close="close">
    <ListState v-if="modal.id && !detail" :loading="detailLoading" :error="detailError" @retry="reloadDetail" />
    <dl v-else-if="modal.mode === 'detail' && detail" class="details-list">
      <dt>名称</dt><dd>{{ detail.role.name }}</dd>
      <dt>编码</dt><dd><code>{{ detail.role.code }}</code></dd>
      <dt>角色 ID</dt><dd><code>{{ detail.role.id }}</code></dd>
      <dt>数据范围</dt><dd>{{ dataScopes[detail.role.dataScope] }}</dd>
      <dt>状态</dt><dd><StatusChip :enabled="detail.role.enabled" /></dd>
      <dt>权限点</dt>
      <dd>
        <ul v-if="detail.permissionCodes.length" class="tags">
          <li v-for="code in detail.permissionCodes" :key="code"><code>{{ code }}</code></li>
        </ul>
        <span v-else>未分配权限点</span>
      </dd>
    </dl>
    <ListState v-else-if="!permissions" :loading="permissionsLoading" :error="permissionsError" @retry="reloadPermissions" />
    <form v-else @submit.prevent="save">
      <div class="form-grid">
        <label class="field">
          <span>编码</span>
          <input v-model="draft.code" required :disabled="modal.mode === 'edit'" :pattern="codePattern" maxlength="120" spellcheck="false">
        </label>
        <label class="field"><span>名称</span><input v-model.trim="draft.name" required maxlength="120"></label>
        <label class="field">
          <span>数据范围</span>
          <select v-model="draft.dataScope">
            <option v-for="(label, value) in dataScopes" :key="value" :value="value">{{ label }}</option>
          </select>
        </label>
        <label v-if="modal.mode === 'edit'" class="checkline">
          <input v-model="draft.enabled" type="checkbox"> 启用角色
        </label>
        <fieldset class="choice-group span-full">
          <legend>权限点</legend>
          <div class="option-list">
            <label v-for="permission in permissionChoices" :key="permission.code" class="checkline">
              <input v-model="draft.permissionCodes" type="checkbox" :value="permission.code">
              <span>{{ permission.name }}{{ permission.enabled ? '' : '（停用）' }} <code class="cell-meta">{{ permission.code }}</code></span>
            </label>
            <span v-if="!permissionChoices.length" class="muted">暂无可分配的权限点。</span>
          </div>
        </fieldset>
        <p v-if="saveError" class="notice notice-error span-full" role="alert">{{ saveError }}</p>
      </div>
      <div class="form-actions">
        <button class="btn" type="button" :disabled="busy" @click="close">取消</button>
        <button class="btn btn-primary" type="submit" :disabled="busy">{{ busy ? '保存中…' : '保存' }}</button>
      </div>
    </form>
  </EditDialog>

  <EditDialog v-if="menuRole" :title="`分配菜单 · ${menuRole.name}`" :busy="menuSave.busy.value" @close="menuRole = null">
    <ListState :loading="menuLoading" :error="menuError" @retry="retryMenus">
      <form v-if="menuSettings.data.value && menuOptions.data.value" @submit.prevent="saveMenus">
        <label class="field">
          <span>菜单模式</span>
          <select v-model="menuDraft.mode">
            <option value="LEGACY">沿用权限点规则</option>
            <option value="DIRECT">按勾选限制可见菜单</option>
          </select>
        </label>
        <fieldset v-if="menuDraft.mode === 'DIRECT'" class="choice-group">
          <legend>可见菜单</legend>
          <p class="muted">逐项勾选菜单。用户须拥有菜单和页面所需权限。同一用户的直接菜单取各启用角色的勾选并集。</p>
          <div class="option-list">
            <label v-for="{ node, trail } in menuRows" :key="node.id" class="checkline">
              <input v-model="menuDraft.menuIds" type="checkbox" :value="node.id">
              <span>{{ trail }}{{ node.enabled ? '' : '（停用）' }}
                <code v-if="node.permissionCode" class="cell-meta">{{ node.permissionCode }}</code>
              </span>
            </label>
            <span v-if="!menuRows.length" class="muted">暂无可分配的菜单。</span>
          </div>
        </fieldset>
        <p v-if="menuSave.error.value" class="notice notice-error" role="alert">{{ menuSave.error.value }}</p>
        <div class="form-actions">
          <button class="btn" type="button" :disabled="menuSave.busy.value" @click="menuRole = null">取消</button>
          <button class="btn btn-primary" type="submit" :disabled="menuSave.busy.value">
            {{ menuSave.busy.value ? '保存中…' : '保存菜单' }}
          </button>
        </div>
      </form>
    </ListState>
  </EditDialog>

  <DeleteDialog v-if="removing" collection="/roles" resource="角色" :ids="removing.ids" :name="removing.name"
    description="删除前须解除该角色与所有账号的关联。" @close="removing = null" @deleted="deleted" />
</template>
