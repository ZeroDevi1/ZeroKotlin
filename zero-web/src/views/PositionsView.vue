<script setup lang="ts">
import { computed, nextTick, reactive, ref, shallowRef } from 'vue'
import { request } from '@/api/client'
import { useAuth } from '@/store/auth'
import PositionMembers from '@/components/PositionMembers.vue'
import ResourcePager from '@/components/ResourcePager.vue'
import { errorMessage, useResourcePage, type Position } from '@/hooks/useResourcePage'

const auth = useAuth()
const canRead = computed(() => auth.can('system:position:read'))
const canManageMembers = computed(() => canRead.value && auth.user?.administrator === true)
const canWrite = computed(() => canManageMembers.value && auth.can('system:position:write'))
const endpoint = '/api/system/positions'
const { items, total, page, lastPage, loading, error, reload } = useResourcePage<Position>(
  (number, size) => canRead.value ? `${endpoint}?page=${number}&size=${size}` : null,
  '无法加载岗位目录。',
)

const editorOpen = ref(false)
const editing = shallowRef<Position | null>(null)
const memberPosition = shallowRef<Position | null>(null)
const membersBusy = ref(false)
const saving = ref(false)
const busy = computed(() => saving.value || membersBusy.value)
const form = reactive({ code: '', name: '', sortOrder: 0 as number | '', enabled: true })
const formError = ref('')
const feedback = ref('')
const nameInput = ref<HTMLInputElement | null>(null)
const codeInput = ref<HTMLInputElement | null>(null)
const memberPanel = ref<HTMLElement | null>(null)

async function openEditor(position: Position | null) {
  if (!canWrite.value || busy.value) return
  memberPosition.value = null
  editing.value = position
  form.code = position?.code ?? ''
  form.name = position?.name ?? ''
  form.sortOrder = position?.sortOrder ?? 0
  form.enabled = position?.enabled ?? true
  formError.value = ''
  feedback.value = ''
  editorOpen.value = true
  await nextTick()
  if (position) nameInput.value?.focus()
  else codeInput.value?.focus()
}

async function openMembers(position: Position) {
  if (!canManageMembers.value || busy.value) return
  editorOpen.value = false
  feedback.value = ''
  memberPosition.value = position
  await nextTick()
  memberPanel.value?.querySelector<HTMLElement>('h2')?.focus()
}

async function savePosition() {
  if (!canWrite.value || !editorOpen.value || busy.value) return
  const name = form.name.trim()
  const code = form.code.trim()
  const sortOrder = form.sortOrder
  if (!name) {
    formError.value = '请输入岗位名称。'
    return
  }
  if (!editing.value && !/^[a-z0-9][a-z0-9:_-]{0,119}$/.test(code)) {
    formError.value = '岗位编码须以小写字母或数字开头，只能含小写字母、数字、冒号、下划线或连字符，最长 120 字符。'
    return
  }
  if (typeof sortOrder !== 'number' || !Number.isInteger(sortOrder) || sortOrder < -2147483648 || sortOrder > 2147483647) {
    formError.value = '排序值须为 -2147483648 到 2147483647 之间的整数。'
    return
  }

  saving.value = true
  formError.value = ''
  const position = editing.value
  try {
    if (position) {
      await request<void>(`${endpoint}/${encodeURIComponent(position.id)}`, {
        method: 'PUT', body: { name, enabled: form.enabled, sortOrder },
      })
    } else {
      await request<{ id: string }>(endpoint, { method: 'POST', body: { code, name, sortOrder } })
    }
    feedback.value = position ? `已保存岗位：${name}。` : `已新建岗位：${name}。`
    editorOpen.value = false
    reload()
  } catch (cause) {
    formError.value = errorMessage(cause, '无法保存岗位。')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section aria-labelledby="positions-title">
    <header class="page-header">
      <div>
        <h1 id="positions-title">岗位目录</h1>
        <p>查看组织岗位及其启用状态。</p>
      </div>
      <button v-if="canWrite" type="button" class="btn btn-primary" :disabled="busy" @click="openEditor(null)">新建岗位</button>
    </header>

    <p v-if="!canRead" class="empty">当前账号没有查看岗位的权限。</p>
    <div v-else class="page-sections">
      <p v-if="feedback" class="notice notice-success" role="status">{{ feedback }}</p>
      <section v-if="editorOpen && canWrite" class="panel editor-panel" aria-labelledby="position-editor-heading" :aria-busy="saving">
        <div class="toolbar"><h2 id="position-editor-heading">{{ editing ? '编辑岗位' : '新建岗位' }}</h2></div>
        <form @submit.prevent="savePosition">
          <fieldset :disabled="saving" class="position-fields">
            <div class="form-grid position-grid">
              <label class="field">
                <span>岗位编码</span>
                <input v-model="form.code" ref="codeInput" required maxlength="120" :readonly="editing !== null" autocomplete="off" spellcheck="false">
              </label>
              <label class="field">
                <span>岗位名称</span>
                <input v-model="form.name" ref="nameInput" required maxlength="120" autocomplete="off">
              </label>
              <label class="field">
                <span>排序值</span>
                <input v-model.number="form.sortOrder" type="number" min="-2147483648" max="2147483647" step="1" required>
              </label>
              <label v-if="editing" class="enabled-field">
                <input v-model="form.enabled" type="checkbox">
                <span>启用此岗位</span>
              </label>
            </div>
            <p v-if="formError" class="notice notice-error" role="alert">保存岗位失败：{{ formError }}</p>
            <div class="actions form-actions">
              <button type="submit" class="btn btn-primary">{{ saving ? '正在保存…' : '保存岗位' }}</button>
              <button type="button" class="btn btn-quiet" @click="editorOpen = false">取消</button>
            </div>
          </fieldset>
        </form>
      </section>

      <div v-if="memberPosition && canManageMembers" ref="memberPanel">
        <PositionMembers :key="memberPosition.id" :position="memberPosition" @busy="membersBusy = $event" @close="memberPosition = null" />
      </div>

      <section class="panel" aria-label="岗位列表" :aria-busy="loading">
        <p v-if="loading" class="empty" role="status">正在加载岗位…</p>
        <div v-else-if="error" class="notice notice-error list-error" role="alert">
          <p>{{ error }}</p>
          <button type="button" class="btn btn-quiet" @click="reload">重新加载</button>
        </div>
        <p v-else-if="items.length === 0" class="empty">尚未建立岗位。</p>
        <div v-else class="table-wrap">
          <table class="data-table positions-table">
            <thead>
              <tr>
                <th scope="col">岗位名称</th>
                <th scope="col">编码</th>
                <th scope="col">状态</th>
                <th scope="col">排序值</th>
                <th v-if="canManageMembers" scope="col">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="position in items" :key="position.id">
                <th scope="row">{{ position.name }}</th>
                <td><code>{{ position.code }}</code></td>
                <td><span class="status" :class="{ enabled: position.enabled }">{{ position.enabled ? '已启用' : '已停用' }}</span></td>
                <td>{{ position.sortOrder }}</td>
                <td v-if="canManageMembers">
                  <div class="actions">
                    <button v-if="canWrite" type="button" class="btn btn-quiet" :disabled="busy" :aria-label="`编辑岗位：${position.name}`" @click="openEditor(position)">编辑</button>
                    <button type="button" class="btn btn-quiet" :disabled="busy" :aria-label="`查看岗位成员：${position.name}`" @click="openMembers(position)">成员</button>
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
          label="岗位目录分页"
        />
      </section>
    </div>
  </section>
</template>

<style scoped>
.page-sections { display: grid; grid-template-columns: minmax(0, 1fr); gap: 1rem; }
.page-sections > .panel + .panel, .page-sections > .notice { margin: 0; }
.page-sections h2 { margin: 0; }
.editor-panel > form { padding: 0 1rem 1rem; }
.list-error { margin: 1rem; }
.position-fields { min-width: 0; border: 0; padding: 0; margin: 0; }
.position-fields > .notice { margin-top: .75rem; }
.position-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 13rem), 1fr)); gap: .75rem 1rem; }
.enabled-field, .actions { display: flex; align-items: center; flex-wrap: wrap; gap: .5rem; }
.enabled-field { min-height: 2.75rem; align-self: end; }
.enabled-field input { accent-color: var(--accent, #4d3bb2); }
.form-actions { margin-top: 1rem; }
.positions-table tbody th, .positions-table td code { overflow-wrap: anywhere; white-space: normal; }
.positions-table tbody th { background: none; color: inherit; text-align: start; border-top: 1px solid var(--line, #e5e4e9); font-size: inherit; }
.status { display: inline-block; padding: .125rem .5rem; border-radius: 99px; background: #edecef; color: #4b475a; font-size: .8125rem; white-space: nowrap; }
.status.enabled { background: #e1f3eb; color: #166045; }
</style>
