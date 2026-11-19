<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Plus } from '@lucide/vue'
import { request } from '@/api/client'
import { apiPath, codePattern, type DictionaryItem, type DictionarySummary } from '@/api/system'
import { useAuth } from '@/store/auth'
import EditDialog from '@/components/EditDialog.vue'
import ListState from '@/components/ListState.vue'
import StatusChip from '@/components/StatusChip.vue'
import { useAction, useResource } from '@/hooks/useQueries'

const auth = useAuth()
const mayWrite = computed(() => auth.can('system:dictionary:write'))
const { data, loading, error, reload } = useResource<DictionarySummary[]>(() => apiPath('/dictionaries'))
const selected = ref<DictionarySummary | null>(null)
const activeOnly = ref(false)
const {
  data: items, loading: itemLoading, error: itemError, reload: reloadItems,
} = useResource<DictionaryItem[]>(() => {
  const dictionary = selected.value
  if (!dictionary) return null
  return activeOnly.value
    ? apiPath(`/dictionaries/by-code/${encodeURIComponent(dictionary.code)}/items`)
    : apiPath(`/dictionaries/${encodeURIComponent(dictionary.id)}/items`)
})

watch(data, (rows) => {
  const fresh = rows?.find((row) => row.id === selected.value?.id)
  if (!fresh) return
  selected.value = fresh
  if (!fresh.enabled) activeOnly.value = false
})

function openItems(row: DictionarySummary) {
  activeOnly.value = false
  selected.value = row
}

type Modal = { kind: 'dictionary'; id?: string } | { kind: 'item'; dictionaryId: string; id?: string }
const modal = ref<Modal | null>(null)
const draft = reactive({ code: '', name: '', enabled: true, value: '', label: '', sortOrder: 0 })
const feedback = ref('')
const { busy, error: saveError, run } = useAction()
const modalTitle = computed(() => {
  if (modal.value?.kind === 'item') return modal.value.id ? '编辑字典项' : '创建字典项'
  return modal.value?.id ? '编辑字典' : '创建字典'
})

function editDictionary(row?: DictionarySummary) {
  Object.assign(draft, { code: row?.code ?? '', name: row?.name ?? '', enabled: row?.enabled ?? true })
  saveError.value = ''
  modal.value = { kind: 'dictionary', id: row?.id }
}

function editItem(row?: DictionaryItem) {
  if (!selected.value) return
  Object.assign(draft, { value: row?.value ?? '', label: row?.label ?? '', sortOrder: row?.sortOrder ?? 0, enabled: row?.enabled ?? true })
  saveError.value = ''
  modal.value = { kind: 'item', dictionaryId: selected.value.id, id: row?.id }
}

async function save() {
  const editing = modal.value
  if (!editing) return
  const method = editing.id ? 'PUT' : 'POST'
  const completed = await run(async () => {
    if (editing.kind === 'dictionary') {
      const path = editing.id ? `/dictionaries/${encodeURIComponent(editing.id)}` : '/dictionaries'
      const body = editing.id ? { name: draft.name, enabled: draft.enabled } : { code: draft.code, name: draft.name }
      await request(apiPath(path), { method, body })
    } else {
      const collection = `/dictionaries/${encodeURIComponent(editing.dictionaryId)}/items`
      await request(apiPath(editing.id ? `${collection}/${encodeURIComponent(editing.id)}` : collection), {
        method, body: { value: draft.value, label: draft.label, sortOrder: draft.sortOrder, enabled: draft.enabled },
      })
    }
  })
  if (!completed) return
  feedback.value = `${editing.kind === 'dictionary' ? '字典' : '字典项'}${editing.id ? '已保存' : '已创建'}。`
  modal.value = null
  if (editing.kind === 'dictionary') await reload()
  else await reloadItems()
}
</script>

<template>
  <header class="page-header">
    <div><h1>字典</h1><p>维护业务共用的编码、显示值和排序。</p></div>
    <button v-if="mayWrite" type="button" class="btn btn-primary" @click="editDictionary()">
      <Plus :size="17" aria-hidden="true" /> 创建字典
    </button>
  </header>
  <p v-if="feedback" class="notice notice-success" role="status">{{ feedback }}</p>
  <section class="panel" aria-label="字典列表">
    <ListState :loading="loading" :error="error" :empty="!data?.length" empty-text="暂无字典。" @retry="reload">
      <div class="table-wrap">
        <table class="data-table" aria-label="字典列表">
          <thead><tr><th scope="col">字典</th><th scope="col">状态</th><th scope="col">操作</th></tr></thead>
          <tbody>
            <tr v-for="row in data" :key="row.id">
              <td><strong>{{ row.name }}</strong><code class="cell-meta">{{ row.code }}</code></td>
              <td><StatusChip :enabled="row.enabled" /></td>
              <td><div class="row-actions">
                <button type="button" class="btn btn-quiet" aria-controls="dictionary-items"
                  :aria-expanded="row.id === selected?.id" @click="selected?.id === row.id ? selected = null : openItems(row)">
                  {{ selected?.id === row.id ? '收起字典项' : '字典项' }}
                </button>
                <button v-if="mayWrite" type="button" class="btn btn-quiet" @click="editDictionary(row)">编辑字典</button>
              </div></td>
            </tr>
          </tbody>
        </table>
      </div>
    </ListState>
  </section>

  <section v-if="selected" id="dictionary-items" class="panel" aria-labelledby="dictionary-items-title">
    <header class="panel-title">
      <h2 id="dictionary-items-title">{{ selected.name }} · 字典项</h2>
      <button v-if="mayWrite && !activeOnly" type="button" class="btn" @click="editItem()">
        <Plus :size="16" aria-hidden="true" /> 创建字典项
      </button>
    </header>
    <div v-if="selected.enabled" class="toolbar" role="group" aria-label="字典项视图">
      <button type="button" class="btn btn-quiet" :aria-pressed="!activeOnly" @click="activeOnly = false">全部字典项</button>
      <button type="button" class="btn btn-quiet" :aria-pressed="activeOnly" @click="activeOnly = true">预览使用中的字典项</button>
    </div>
    <ListState :loading="itemLoading" :error="itemError" :empty="!items?.length" empty-text="暂无字典项。" @retry="reloadItems">
      <div class="table-wrap">
        <table class="data-table" aria-label="字典项列表">
          <thead><tr><th scope="col">标签</th><th scope="col">值</th><th scope="col">排序</th>
            <th scope="col">状态</th><th v-if="mayWrite && !activeOnly" scope="col">操作</th>
          </tr></thead>
          <tbody>
            <tr v-for="item in items" :key="item.id">
              <td><strong>{{ item.label }}</strong></td>
              <td><code>{{ item.value }}</code></td>
              <td>{{ item.sortOrder }}</td>
              <td><StatusChip :enabled="item.enabled" /></td>
              <td v-if="mayWrite && !activeOnly">
                <button type="button" class="btn btn-quiet" @click="editItem(item)">编辑</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </ListState>
  </section>

  <EditDialog v-if="modal" :title="modalTitle" :busy="busy" @close="modal = null">
    <form @submit.prevent="save">
      <div class="form-grid">
        <template v-if="modal.kind === 'dictionary'">
          <label class="field"><span>字典编码</span>
            <input v-model="draft.code" required :disabled="!!modal.id" :pattern="codePattern" maxlength="120" spellcheck="false">
          </label>
          <label class="field"><span>字典名称</span><input v-model.trim="draft.name" required maxlength="120"></label>
        </template>
        <template v-else>
          <label class="field"><span>值</span><input v-model.trim="draft.value" required maxlength="120" spellcheck="false"></label>
          <label class="field"><span>标签</span><input v-model.trim="draft.label" required maxlength="120"></label>
          <label class="field"><span>排序</span>
            <input v-model.number="draft.sortOrder" type="number" required min="-2147483648" max="2147483647" step="1">
          </label>
        </template>
        <label v-if="modal.id || modal.kind === 'item'" class="checkline">
          <input v-model="draft.enabled" type="checkbox"> {{ modal.kind === 'item' ? '启用字典项' : '启用字典' }}
        </label>
        <p v-if="saveError" class="notice notice-error span-full" role="alert">{{ saveError }}</p>
      </div>
      <div class="form-actions">
        <button class="btn" type="button" :disabled="busy" @click="modal = null">取消</button>
        <button class="btn btn-primary" type="submit" :disabled="busy">{{ busy ? '保存中…' : '保存' }}</button>
      </div>
    </form>
  </EditDialog>
</template>
