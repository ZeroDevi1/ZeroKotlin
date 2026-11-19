<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { Plus } from '@lucide/vue'
import { request } from '@/api/client'
import { apiPath, codePattern, type PermissionSummary } from '@/api/system'
import { useAuth } from '@/store/auth'
import EditDialog from '@/components/EditDialog.vue'
import ListState from '@/components/ListState.vue'
import StatusChip from '@/components/StatusChip.vue'
import { useAction, useResource } from '@/hooks/useQueries'

const auth = useAuth()
const mayWrite = computed(() => auth.user?.administrator && auth.can('system:permission:write'))
const { data, loading, error, reload } = useResource<PermissionSummary[]>(() => apiPath('/permissions'))
const keyword = ref('')
const rows = computed(() => {
  const match = keyword.value.toLowerCase()
  return (data.value ?? []).filter(({ name, code }) => name.toLowerCase().includes(match) || code.toLowerCase().includes(match))
})

const editing = ref(false)
const existing = ref<string | null>(null)
const draft = reactive({ code: '', name: '', enabled: true })
const feedback = ref('')
const { busy, error: saveError, run } = useAction()

function open(permission?: PermissionSummary) {
  existing.value = permission?.code ?? null
  Object.assign(draft, permission ?? { code: '', name: '', enabled: true })
  saveError.value = ''
  editing.value = true
}

async function save() {
  const code = existing.value
  const completed = await run(async () => {
    if (draft.code.startsWith('system:')) throw new Error('system: 命名空间由系统维护，请使用业务模块编码。')
    await request(apiPath(code ? `/permissions/${encodeURIComponent(code)}` : '/permissions'), {
      method: code ? 'PUT' : 'POST',
      body: code ? { name: draft.name, enabled: draft.enabled } : { code: draft.code, name: draft.name },
    })
  })
  if (!completed) return
  feedback.value = code ? '权限点已保存。' : '权限点已创建。'
  editing.value = false
  await reload()
}
</script>

<template>
  <header class="page-header">
    <div><h1>权限点</h1><p>查看系统与业务模块声明的访问权限。</p></div>
    <button v-if="mayWrite" class="btn btn-primary" type="button" @click="open()">
      <Plus :size="17" aria-hidden="true" /> 创建权限点
    </button>
  </header>
  <p v-if="feedback" class="notice notice-success" role="status">{{ feedback }}</p>
  <section class="panel" aria-label="权限点列表">
    <div class="toolbar">
      <label class="field"><span>搜索名称或编码</span><input v-model.trim="keyword" type="search"></label>
    </div>
    <ListState :loading="loading" :error="error" :empty="!rows.length" empty-text="暂无符合条件的权限点。" @retry="reload">
      <div class="table-wrap">
        <table class="data-table" aria-label="权限点列表">
          <thead><tr><th scope="col">名称</th><th scope="col">权限编码</th><th scope="col">状态</th><th v-if="mayWrite" scope="col">操作</th></tr></thead>
          <tbody>
            <tr v-for="row in rows" :key="row.code">
              <td><strong>{{ row.name }}</strong></td>
              <td><code>{{ row.code }}</code></td>
              <td><StatusChip :enabled="row.enabled" /></td>
              <td v-if="mayWrite">
                <span v-if="row.code.startsWith('system:')" class="muted">系统维护</span>
                <button v-else class="btn btn-quiet" type="button" @click="open(row)">编辑</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </ListState>
  </section>

  <EditDialog v-if="editing" :title="existing ? '编辑权限点' : '创建权限点'" :busy="busy" @close="editing = false">
    <form @submit.prevent="save">
      <div class="form-grid">
        <label class="field">
          <span>权限编码</span>
          <input v-model="draft.code" :disabled="!!existing" :pattern="codePattern" required maxlength="120" spellcheck="false">
        </label>
        <label class="field"><span>权限名称</span><input v-model.trim="draft.name" required maxlength="120"></label>
        <label v-if="existing" class="checkline"><input v-model="draft.enabled" type="checkbox"> 启用权限点</label>
        <p v-if="saveError" class="notice notice-error span-full" role="alert">{{ saveError }}</p>
      </div>
      <div class="form-actions">
        <button class="btn" type="button" :disabled="busy" @click="editing = false">取消</button>
        <button class="btn btn-primary" type="submit" :disabled="busy">{{ busy ? '保存中…' : '保存' }}</button>
      </div>
    </form>
  </EditDialog>
</template>
