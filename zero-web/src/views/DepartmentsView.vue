<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Plus } from '@lucide/vue'
import { request } from '@/api/client'
import { apiPath, fetchRoles, flattenTree, type DepartmentNode, type UserListEntry } from '@/api/system'
import { useAuth } from '@/auth'
import EditDialog from '@/components/EditDialog.vue'
import ListState from '@/components/ListState.vue'
import Pager from '@/components/Pager.vue'
import UserTable from '@/components/UserTable.vue'
import { useAction, usePage, useQuery, useResource } from '@/queries'

const auth = useAuth()
const mayWrite = computed(() => auth.user?.administrator && auth.can('system:department:write'))
const canReadRoles = computed(() => auth.can('system:role:read'))
const { data, loading, error, reload } = useResource<DepartmentNode[]>(() => apiPath('/departments/tree'))
const rows = computed(() => flattenTree(data.value ?? []))

const editing = ref(false)
const existing = ref<string | null>(null)
const draft = reactive({ name: '', parentId: '', sortOrder: 0 })
const feedback = ref('')
const { busy, error: saveError, run } = useAction()
const parents = computed(() => rows.value.filter(({ node, ancestors }) => node.id !== existing.value
  && (existing.value === null || !ancestors.includes(existing.value))))

function open(department?: DepartmentNode, parentId?: string | null) {
  existing.value = department?.id ?? null
  Object.assign(draft, { name: department?.name ?? '', parentId: parentId ?? '', sortOrder: department?.sortOrder ?? 0 })
  saveError.value = ''
  editing.value = true
}

async function save() {
  const id = existing.value
  const completed = await run(async () => {
    await request(apiPath(id ? `/departments/${encodeURIComponent(id)}` : '/departments'), {
      method: id ? 'PUT' : 'POST',
      body: { name: draft.name, parentId: draft.parentId || null, sortOrder: draft.sortOrder },
    })
  })
  if (!completed) return
  feedback.value = id ? '部门已保存。' : '部门已创建。'
  editing.value = false
  await reload()
}

const selected = ref<{ id: string; name: string } | null>(null)
const roleId = ref('')
const appliedRole = ref('')
const canLoadRoles = computed(() => !!selected.value && canReadRoles.value)
const { data: roles, error: roleError, reload: reloadRoles } = useQuery(
  (signal) => canLoadRoles.value ? fetchRoles(signal) : Promise.resolve(null), canLoadRoles,
)
const members = usePage<UserListEntry>((page, size) => selected.value
  ? apiPath(`/departments/${encodeURIComponent(selected.value.id)}/members`, { page, size, roleId: appliedRole.value })
  : null)
const { data: memberData, loading: memberLoading, error: memberError, reload: reloadMembers, page, size, total, setSize } = members

function openMembers(department: DepartmentNode) {
  selected.value = { id: department.id, name: department.name }
  roleId.value = ''
  appliedRole.value = ''
  page.value = 1
}

function filterMembers() {
  page.value = 1
  const next = roleId.value.trim()
  if (next !== appliedRole.value) appliedRole.value = next
  else if (!memberLoading.value) void reloadMembers()
}

watch(rows, (entries) => {
  if (!selected.value || !data.value) return
  const department = entries.find(({ node }) => node.id === selected.value?.id)
  if (department) selected.value.name = department.node.name
})
</script>

<template>
  <header class="page-header">
    <div><h1>部门</h1><p>维护组织层级，查看各部门的成员。</p></div>
    <button v-if="mayWrite" class="btn btn-primary" type="button" @click="open()">
      <Plus :size="17" aria-hidden="true" /> 创建部门
    </button>
  </header>
  <p v-if="feedback" class="notice notice-success" role="status">{{ feedback }}</p>
  <section class="panel" aria-label="部门树">
    <ListState :loading="loading" :error="error" :empty="!rows.length" empty-text="暂无部门。" @retry="reload">
      <div class="table-wrap">
        <table class="data-table" aria-label="部门树">
          <thead><tr><th scope="col">部门</th><th scope="col">排序</th>
            <th v-if="auth.can('system:user:read') || mayWrite" scope="col">操作</th>
          </tr></thead>
          <tbody>
            <tr v-for="{ node, parentId, ancestors } in rows" :key="node.id">
              <td>
                <div class="tree-cell" :style="{ '--tree-depth': Math.min(ancestors.length, 10) }">
                  <strong>{{ node.name }}</strong><code class="cell-meta">{{ node.id }}</code>
                </div>
              </td>
              <td>{{ node.sortOrder }}</td>
              <td v-if="auth.can('system:user:read') || mayWrite"><div class="row-actions">
                <button v-if="auth.can('system:user:read')" class="btn btn-quiet" type="button" @click="openMembers(node)">成员</button>
                <button v-if="mayWrite" class="btn btn-quiet" type="button" @click="open(node, parentId)">编辑</button>
                <button v-if="mayWrite" class="btn btn-quiet" type="button" @click="open(undefined, node.id)">添加下级</button>
              </div></td>
            </tr>
          </tbody>
        </table>
      </div>
    </ListState>
  </section>

  <EditDialog v-if="editing" :title="existing ? '编辑部门' : '创建部门'" :busy="busy" @close="editing = false">
    <form @submit.prevent="save">
      <div class="form-grid">
        <label class="field"><span>部门名称</span><input v-model.trim="draft.name" required maxlength="120"></label>
        <label class="field">
          <span>上级部门</span>
          <select v-model="draft.parentId">
            <option value="">根层级</option>
            <option v-for="{ node, trail } in parents" :key="node.id" :value="node.id">{{ trail }}</option>
          </select>
        </label>
        <label class="field"><span>排序</span>
          <input v-model.number="draft.sortOrder" type="number" required min="-2147483648" max="2147483647" step="1">
        </label>
        <p v-if="saveError" class="notice notice-error span-full" role="alert">{{ saveError }}</p>
      </div>
      <div class="form-actions">
        <button class="btn" type="button" :disabled="busy" @click="editing = false">取消</button>
        <button class="btn btn-primary" type="submit" :disabled="busy">{{ busy ? '保存中…' : '保存' }}</button>
      </div>
    </form>
  </EditDialog>

  <EditDialog v-if="selected" :title="`${selected.name} · 部门成员`" @close="selected = null">
    <section class="panel" aria-label="部门成员目录">
      <form class="toolbar" @submit.prevent="filterMembers">
        <label class="field">
          <span>{{ roles ? '角色' : '角色 ID' }}</span>
          <select v-if="roles" v-model="roleId">
            <option value="">全部角色</option>
            <option v-for="role in roles" :key="role.id" :value="role.id">{{ role.name }}{{ role.enabled ? '' : '（停用）' }}</option>
          </select>
          <input v-else v-model="roleId" maxlength="36">
        </label>
        <button class="btn" type="submit">筛选</button>
        <button v-if="roleError" class="btn btn-quiet" type="button" @click="reloadRoles">重读角色</button>
      </form>
      <ListState :loading="memberLoading" :error="memberError" :empty="!memberData?.items.length"
        empty-text="当前授权范围和筛选条件下暂无成员。" @retry="reloadMembers">
        <UserTable :entries="memberData?.items ?? []" />
      </ListState>
      <Pager v-if="memberData" v-model:page="page" :size="size" :total="total" @update:size="setSize" />
    </section>
  </EditDialog>
</template>
