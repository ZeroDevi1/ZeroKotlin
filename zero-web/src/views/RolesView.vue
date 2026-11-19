<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Plus } from '@lucide/vue'
import { request } from '@/api/client'
import { adminRoleId, apiPath, codePattern, dataScopes, type DataScope, type PermissionSummary, type RoleDetails, type RoleSummary } from '@/api/system'
import { useAuth } from '@/store/auth'
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
    </div>
    <ListState :loading="loading" :error="error" :empty="!data?.items.length" empty-text="暂无符合条件的角色。" @retry="reload">
      <div class="table-wrap">
        <table class="data-table" aria-label="角色列表">
          <thead><tr><th scope="col">角色</th><th scope="col">数据范围</th><th scope="col">状态</th><th scope="col">操作</th></tr></thead>
          <tbody>
            <tr v-for="role in data?.items" :key="role.id">
              <td><strong>{{ role.name }}</strong><code class="cell-meta">{{ role.code }}</code></td>
              <td>{{ dataScopes[role.dataScope] }}</td>
              <td><StatusChip :enabled="role.enabled" /></td>
              <td><div class="row-actions">
                <button class="btn btn-quiet" type="button" @click="open('detail', role.id)">详情</button>
                <button v-if="mayWrite && role.id !== adminRoleId" class="btn btn-quiet" type="button"
                  @click="open('edit', role.id)">编辑</button>
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
</template>
