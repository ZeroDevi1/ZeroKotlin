<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { Plus } from '@lucide/vue'
import { request } from '@/api/client'
import { apiPath, codePattern, flattenTree, type MenuNode, type PermissionSummary } from '@/api/system'
import { refreshSession, useAuth } from '@/store/auth'
import EditDialog from '@/components/EditDialog.vue'
import ListState from '@/components/ListState.vue'
import StatusChip from '@/components/StatusChip.vue'
import { menuPages } from '@/router/navigation'
import { useAction, useResource } from '@/hooks/useQueries'

const auth = useAuth()
const mayWrite = computed(() => auth.can('system:menu:write'))
const { data, loading, error, reload } = useResource<MenuNode[]>(() => apiPath('/menus'))
const rows = computed(() => flattenTree(data.value ?? []))
const editing = ref(false)
const existing = ref<string | null>(null)
const draft = reactive({ name: '', parentId: '', path: '', permissionCode: '', sortOrder: 0, enabled: true })
const parents = computed(() => rows.value.filter(({ node, ancestors }) => node.id !== existing.value
  && (existing.value === null || !ancestors.includes(existing.value))))
const { data: permissions } = useResource<PermissionSummary[]>(
  () => editing.value && auth.can('system:permission:read') ? apiPath('/permissions') : null,
)
const feedback = ref('')
const navWarning = ref('')
const { busy, error: saveError, run } = useAction()

function open(node?: MenuNode, parentId?: string | null) {
  existing.value = node?.id ?? null
  Object.assign(draft, {
    name: node?.name ?? '', parentId: parentId ?? '', path: node?.path ?? '',
    permissionCode: node?.permissionCode ?? '', sortOrder: node?.sortOrder ?? 0, enabled: node?.enabled ?? true,
  })
  saveError.value = ''
  editing.value = true
}

async function refreshNavigation() {
  navWarning.value = ''
  try {
    await refreshSession()
  } catch {
    navWarning.value = '菜单已保存。当前导航刷新失败，请重试。'
  }
}

async function save() {
  const id = existing.value
  const completed = await run(async () => {
    if (draft.path && !/^\/(?!\/)[^\\\u0000-\u001f\u007f]*$/.test(draft.path)) {
      throw new Error('路径须以单个 / 开头，并且是站内路径。')
    }
    await request(apiPath(id ? `/menus/${encodeURIComponent(id)}` : '/menus'), {
      method: id ? 'PUT' : 'POST',
      body: {
        name: draft.name, parentId: draft.parentId || null, path: draft.path || null,
        permissionCode: draft.permissionCode || null, sortOrder: draft.sortOrder, enabled: draft.enabled,
      },
    })
  })
  if (!completed) return
  feedback.value = id ? '菜单已保存。' : '菜单已创建。'
  editing.value = false
  await Promise.all([reload(), refreshNavigation()])
}
</script>

<template>
  <header class="page-header">
    <div><h1>菜单</h1><p>配置导航层级、站内路径和菜单所需权限。</p></div>
    <button v-if="mayWrite" class="btn btn-primary" type="button" @click="open()">
      <Plus :size="17" aria-hidden="true" /> 创建菜单
    </button>
  </header>
  <p v-if="feedback && !navWarning" class="notice notice-success" role="status">{{ feedback }}</p>
  <div v-if="navWarning" class="notice" role="status">
    {{ navWarning }} <button class="btn btn-quiet" type="button" @click="refreshNavigation">刷新导航</button>
  </div>
  <section class="panel" aria-label="菜单树">
    <ListState :loading="loading" :error="error" :empty="!rows.length" empty-text="暂无菜单。" @retry="reload">
      <div class="table-wrap">
        <table class="data-table" aria-label="菜单树">
          <thead><tr><th scope="col">菜单</th><th scope="col">路径</th><th scope="col">权限点</th>
            <th scope="col">排序</th><th scope="col">状态</th><th v-if="mayWrite" scope="col">操作</th>
          </tr></thead>
          <tbody>
            <tr v-for="{ node, parentId, ancestors } in rows" :key="node.id">
              <td><div class="tree-cell" :style="{ '--tree-depth': Math.min(ancestors.length, 10) }"><strong>{{ node.name }}</strong></div></td>
              <td><code v-if="node.path">{{ node.path }}</code><span v-else class="muted">分组</span></td>
              <td><code v-if="node.permissionCode">{{ node.permissionCode }}</code><span v-else class="muted">无</span></td>
              <td>{{ node.sortOrder }}</td>
              <td><StatusChip :enabled="node.enabled" /></td>
              <td v-if="mayWrite"><div class="row-actions">
                <button class="btn btn-quiet" type="button" @click="open(node, parentId)">编辑</button>
                <button class="btn btn-quiet" type="button" @click="open(undefined, node.id)">添加下级</button>
              </div></td>
            </tr>
          </tbody>
        </table>
      </div>
    </ListState>
  </section>

  <EditDialog v-if="editing" :title="existing ? '编辑菜单' : '创建菜单'" :busy="busy" @close="editing = false">
    <form @submit.prevent="save">
      <div class="form-grid">
        <label class="field"><span>菜单名称</span><input v-model.trim="draft.name" required maxlength="120"></label>
        <label class="field">
          <span>上级菜单</span>
          <select v-model="draft.parentId">
            <option value="">根层级</option>
            <option v-for="{ node, trail } in parents" :key="node.id" :value="node.id">{{ trail }}</option>
          </select>
        </label>
        <label class="field">
          <span>路径</span>
          <input v-model.trim="draft.path" list="registered-menu-paths" maxlength="255" spellcheck="false">
          <datalist id="registered-menu-paths">
            <option v-for="page in menuPages" :key="page.path" :value="page.path">{{ page.label }}</option>
          </datalist>
          <small>留空创建分组。当前工作台渲染已注册的站内页面。</small>
        </label>
        <label class="field">
          <span>菜单所需权限点</span>
          <input v-model.trim="draft.permissionCode" list="menu-permission-codes" :pattern="codePattern" maxlength="120" spellcheck="false">
          <datalist id="menu-permission-codes">
            <option v-for="item in permissions" :key="item.code" :value="item.code">{{ item.name }}</option>
          </datalist>
          <small>可留空。页面所需权限仍需满足。</small>
        </label>
        <label class="field">
          <span>排序</span><input v-model.number="draft.sortOrder" type="number" required min="-2147483648" max="2147483647" step="1">
        </label>
        <label class="checkline"><input v-model="draft.enabled" type="checkbox"> 启用菜单</label>
        <p v-if="saveError" class="notice notice-error span-full" role="alert">{{ saveError }}</p>
      </div>
      <div class="form-actions">
        <button class="btn" type="button" :disabled="busy" @click="editing = false">取消</button>
        <button class="btn btn-primary" type="submit" :disabled="busy">{{ busy ? '保存中…' : '保存' }}</button>
      </div>
    </form>
  </EditDialog>
</template>
