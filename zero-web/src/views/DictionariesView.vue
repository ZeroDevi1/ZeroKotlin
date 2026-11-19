<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Plus } from '@lucide/vue'
import { request } from '@/api/client'
import { apiPath, codePattern, dictionaryValues, type DictionaryItem, type DictionarySummary } from '@/api/system'
import { useAuth } from '@/store/auth'
import DeleteDialog from '@/components/DeleteDialog.vue'
import EditDialog from '@/components/EditDialog.vue'
import ExcelActions from '@/components/ExcelActions.vue'
import ListState from '@/components/ListState.vue'
import StatusChip from '@/components/StatusChip.vue'
import { useAction, useQuery, useResource } from '@/hooks/useQueries'

const auth = useAuth()
const mayWrite = computed(() => auth.can('system:dictionary:write'))
const mayExport = computed(() => auth.can('system:dictionary:read'))
const mayDelete = computed(() => mayWrite.value && auth.user?.administrator)
const { data, loading, error, reload } = useResource<DictionarySummary[]>(() => apiPath('/dictionaries'))
const selected = ref<DictionarySummary | null>(null)
const activeOnly = ref(false)
const itemExcelPath = computed(() => selected.value
  ? apiPath(`/dictionaries/${encodeURIComponent(selected.value.id)}/items/excel`)
  : '')
const dictionaryIds = ref<string[]>([])
const itemIds = ref<string[]>([])
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
  dictionaryIds.value = []
  const fresh = rows?.find((row) => row.id === selected.value?.id)
  if (!rows) return
  if (!fresh) {
    selected.value = null
    return
  }
  selected.value = fresh
  if (!fresh.enabled) activeOnly.value = false
})
watch(items, () => { itemIds.value = [] })

const selectedCodes = computed(() => {
  const ids = new Set(dictionaryIds.value)
  return (data.value ?? []).filter(row => ids.has(row.id)).map(row => row.code)
})
const previewCodes = ref<string[] | null>(null)
const preview = useQuery(
  (signal) => previewCodes.value ? dictionaryValues(previewCodes.value, signal) : Promise.resolve(null),
  previewCodes,
)
const previewGroups = computed(() => {
  const result = preview.data.value
  return (previewCodes.value ?? []).map(code => ({
    code,
    items: result && Object.hasOwn(result, code) ? result[code] : undefined,
  }))
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
const removing = ref<{ ids: string[]; name?: string; dictionaryId?: string } | null>(null)
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

function deleteDictionary(row?: DictionarySummary) {
  removing.value = row ? { ids: [row.id], name: row.name } : { ids: [...dictionaryIds.value] }
}

function deleteItem(row?: DictionaryItem) {
  if (!selected.value) return
  removing.value = { dictionaryId: selected.value.id, ids: row ? [row.id] : [...itemIds.value], name: row?.label }
}

function deleted(ids: string[]) {
  const isItem = !!removing.value?.dictionaryId
  removing.value = null
  feedback.value = `已删除 ${ids.length} 个${isItem ? '字典项' : '字典'}。`
  if (isItem) {
    void reloadItems()
  } else {
    if (selected.value && ids.includes(selected.value.id)) selected.value = null
    void reload()
  }
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
    <ExcelActions v-if="mayWrite || mayExport" name="字典" :base-path="apiPath('/dictionaries/excel')"
      :export-path="mayExport ? apiPath('/dictionaries/excel/export') : undefined"
      :can-import="mayWrite" :max-rows="1000" @imported="reload" />
    <div class="toolbar">
      <button class="btn" type="button" :disabled="!selectedCodes.length || selectedCodes.length > 100"
        @click="previewCodes = [...selectedCodes]">预览选中字典的可用项</button>
      <button v-if="mayDelete" class="btn" type="button" :disabled="!dictionaryIds.length || dictionaryIds.length > 100"
        @click="deleteDictionary()">删除选中字典</button>
      <span v-if="dictionaryIds.length" class="muted">已选 {{ dictionaryIds.length }} 个字典，每批最多 100 个。</span>
    </div>
    <ListState :loading="loading" :error="error" :empty="!data?.length" empty-text="暂无字典。" @retry="reload">
      <div class="table-wrap">
        <table class="data-table" aria-label="字典列表">
          <thead><tr>
            <th scope="col"><span class="sr-only">选择字典</span></th>
            <th scope="col">字典</th><th scope="col">状态</th><th scope="col">操作</th>
          </tr></thead>
          <tbody>
            <tr v-for="row in data" :key="row.id">
              <td><input v-model="dictionaryIds" type="checkbox" :value="row.id" :aria-label="`选择字典 ${row.name}`"></td>
              <td><strong>{{ row.name }}</strong><code class="cell-meta">{{ row.code }}</code></td>
              <td><StatusChip :enabled="row.enabled" /></td>
              <td><div class="row-actions">
                <button type="button" class="btn btn-quiet" aria-controls="dictionary-items"
                  :aria-expanded="row.id === selected?.id" @click="selected?.id === row.id ? selected = null : openItems(row)">
                  {{ selected?.id === row.id ? '收起字典项' : '字典项' }}
                </button>
                <button v-if="mayWrite" type="button" class="btn btn-quiet" @click="editDictionary(row)">编辑字典</button>
                <button v-if="mayDelete" type="button" class="btn btn-quiet" @click="deleteDictionary(row)">删除</button>
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
    <ExcelActions v-if="!activeOnly && (mayWrite || mayExport)" :key="selected.id" name="字典项" :base-path="itemExcelPath"
      :export-path="mayExport ? `${itemExcelPath}/export` : undefined"
      :can-import="mayWrite" :max-rows="1000" @imported="reloadItems" />
    <div v-if="mayDelete && !activeOnly" class="toolbar">
      <button class="btn" type="button" :disabled="!itemIds.length || itemIds.length > 100" @click="deleteItem()">
        删除选中字典项<span v-if="itemIds.length">（{{ itemIds.length }}）</span>
      </button>
      <span v-if="itemIds.length > 100" class="muted">每批最多删除 100 个字典项。</span>
    </div>
    <ListState :loading="itemLoading" :error="itemError" :empty="!items?.length" empty-text="暂无字典项。" @retry="reloadItems">
      <div class="table-wrap">
        <table class="data-table" aria-label="字典项列表">
          <thead><tr>
            <th v-if="mayDelete && !activeOnly" scope="col"><span class="sr-only">选择字典项</span></th>
            <th scope="col">标签</th><th scope="col">值</th><th scope="col">排序</th>
            <th scope="col">状态</th><th v-if="mayWrite && !activeOnly" scope="col">操作</th>
          </tr></thead>
          <tbody>
            <tr v-for="item in items" :key="item.id">
              <td v-if="mayDelete && !activeOnly">
                <input v-model="itemIds" type="checkbox" :value="item.id" :aria-label="`选择字典项 ${item.label}`">
              </td>
              <td><strong>{{ item.label }}</strong></td>
              <td><code>{{ item.value }}</code></td>
              <td>{{ item.sortOrder }}</td>
              <td><StatusChip :enabled="item.enabled" /></td>
              <td v-if="mayWrite && !activeOnly">
                <div class="row-actions">
                  <button type="button" class="btn btn-quiet" @click="editItem(item)">编辑</button>
                  <button v-if="mayDelete" type="button" class="btn btn-quiet" @click="deleteItem(item)">删除</button>
                </div>
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

  <EditDialog v-if="previewCodes" title="预览启用的字典项" @close="previewCodes = null">
    <ListState :loading="preview.loading.value" :error="preview.error.value" @retry="preview.reload">
      <dl v-if="preview.data.value" class="details-list">
        <template v-for="group in previewGroups" :key="group.code">
          <dt><code>{{ group.code }}</code></dt>
          <dd>
            <ul v-if="group.items?.length" class="tags">
              <li v-for="item in group.items" :key="item.id">{{ item.label }} <code>{{ item.value }}</code></li>
            </ul>
            <span v-else>{{ group.items ? '暂无启用的字典项' : '字典不存在或已停用' }}</span>
          </dd>
        </template>
      </dl>
    </ListState>
  </EditDialog>

  <DeleteDialog v-if="removing"
    :collection="removing.dictionaryId ? `/dictionaries/${encodeURIComponent(removing.dictionaryId)}/items` : '/dictionaries'"
    :resource="removing.dictionaryId ? '字典项' : '字典'" :ids="removing.ids" :name="removing.name"
    :description="removing.dictionaryId ? '删除后，该项将从字典中移除。' : '删除前须清空所选字典的全部字典项。'"
    @close="removing = null" @deleted="deleted" />
</template>
