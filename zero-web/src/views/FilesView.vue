<script setup lang="ts">
import { computed, onScopeDispose, reactive, ref, shallowRef } from 'vue'
import { Upload } from '@lucide/vue'
import { request, requestBlob } from '@/api/client'
import { apiPath, codePattern, formatTime } from '@/api/system'
import EditDialog from '@/components/EditDialog.vue'
import ListState from '@/components/ListState.vue'
import ResourcePager from '@/components/ResourcePager.vue'
import { useAction } from '@/hooks/useQueries'
import { errorMessage, useResourcePage } from '@/hooks/useResourcePage'
import { useAuth } from '@/store/auth'

type Backend = 'LOCAL' | 'MINIO' | 'ALIYUN'
type FileState = 'UPLOADING' | 'AVAILABLE' | 'DELETING'
type FileEntry = {
  id: string
  fileName: string
  bizType: string | null
  contentType: string | null
  sizeBytes: number
  storageBackend: Backend
  state: FileState
  uploadedBy: string
  createdAt: number
}

const backends: Record<Backend, string> = { LOCAL: '本地', MINIO: 'MinIO', ALIYUN: '阿里云 OSS' }
const states: Record<FileState, string> = { UPLOADING: '上传中', AVAILABLE: '可用', DELETING: '待删除' }
const auth = useAuth()
const canRead = computed(() => auth.can('system:file:read'))
const canUpload = computed(() => auth.can('system:file:upload'))
const canDownload = computed(() => auth.can('system:file:download'))
const canDelete = computed(() => auth.can('system:file:delete'))
const filters = reactive({ name: '', bizType: '' })
const applied = ref({ ...filters })
const { items, page, total, lastPage, loading, error, reload } = useResourcePage<FileEntry>(
  (page, size) => canRead.value ? apiPath('/files', { page, size, ...applied.value }) : null,
  '无法加载文件列表。',
)

type Dialog = { kind: 'upload' } | { kind: 'delete'; file: FileEntry }
const dialog = shallowRef<Dialog | null>(null)
const uploadInput = ref<HTMLInputElement>()
const bizType = ref('')
const feedback = ref('')
const { busy, error: dialogError, run } = useAction()
const downloadError = ref('')
const downloading = ref<string>()
let downloadController: AbortController | undefined
onScopeDispose(() => downloadController?.abort())

function applyFilters() {
  const next = { name: filters.name.trim(), bizType: filters.bizType.trim() }
  if (page.value === 1 && applied.value.name === next.name && applied.value.bizType === next.bizType) reload()
  page.value = 1
  applied.value = next
}

function openDialog(next: Dialog) {
  dialogError.value = ''
  feedback.value = ''
  bizType.value = ''
  dialog.value = next
}

async function upload() {
  if (dialog.value?.kind !== 'upload' || !canUpload.value || busy.value) return
  const file = uploadInput.value?.files?.[0]
  if (!file) return
  const body = new FormData()
  body.append('file', file)
  if (bizType.value.trim()) body.append('bizType', bizType.value.trim())
  const succeeded = await run(async () => {
    const saved = await request<FileEntry>(apiPath('/files'), { method: 'POST', body })
    feedback.value = `已上传 ${saved.fileName}。`
  })
  if (succeeded) {
    dialog.value = null
    page.value = 1
  }
  reload()
}

async function removeFile() {
  if (dialog.value?.kind !== 'delete' || !canDelete.value || busy.value) return
  const { file } = dialog.value
  const succeeded = await run(async () => {
    await request<void>(apiPath(`/files/${encodeURIComponent(file.id)}`), { method: 'DELETE' })
    feedback.value = `已删除 ${file.fileName}。`
  })
  if (succeeded) dialog.value = null
  reload()
}

async function download(file: FileEntry) {
  if (!canDownload.value || downloading.value || file.state !== 'AVAILABLE') return
  const controller = new AbortController()
  downloadController = controller
  downloading.value = file.id
  downloadError.value = ''
  try {
    const blob = await requestBlob(apiPath(`/files/${encodeURIComponent(file.id)}/download`), { signal: controller.signal })
    if (controller.signal.aborted) return
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = file.fileName
    link.hidden = true
    document.body.append(link)
    link.click()
    link.remove()
    window.setTimeout(() => URL.revokeObjectURL(url), 30_000)
  } catch (cause) {
    if (!controller.signal.aborted) downloadError.value = errorMessage(cause, '无法下载文件。')
  } finally {
    downloading.value = undefined
    downloadController = undefined
  }
}

const sizeFormat = new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 1 })
const sizeUnits = ['B', 'KiB', 'MiB', 'GiB', 'TiB', 'PiB', 'EiB']
function formatSize(bytes: number) {
  const unit = bytes === 0 ? 0 : Math.min(sizeUnits.length - 1, Math.floor(Math.log2(bytes) / 10))
  return `${sizeFormat.format(bytes / 1024 ** unit)} ${sizeUnits[unit]}`
}
</script>

<template>
  <header class="page-header">
    <h1>文件管理</h1>
    <button v-if="canRead && canUpload" type="button" class="btn btn-primary" @click="openDialog({ kind: 'upload' })">
      <Upload :size="17" aria-hidden="true" /> 上传文件
    </button>
  </header>

  <p v-if="!canRead" class="empty">当前账号没有查看文件的权限。</p>
  <template v-else>
    <p v-if="feedback" class="notice notice-success" role="status">{{ feedback }}</p>
    <p v-if="downloadError" class="notice notice-error" role="alert">{{ downloadError }}</p>
    <section class="panel" aria-label="文件列表" :aria-busy="loading">
      <form class="toolbar" @submit.prevent="applyFilters">
        <label class="field"><span>文件名</span><input v-model="filters.name" type="search" maxlength="255"></label>
        <label class="field">
          <span>业务分类</span>
          <input v-model.trim="filters.bizType" :pattern="codePattern" maxlength="120" autocomplete="off">
        </label>
        <button type="submit" class="btn">筛选</button>
      </form>
      <ListState :loading="loading" :error="error" :empty="items.length === 0" empty-text="暂无符合条件的文件。" @retry="reload">
        <div class="table-wrap">
          <table class="data-table" aria-label="文件列表">
            <thead>
              <tr>
                <th scope="col">文件名</th>
                <th scope="col">业务分类</th>
                <th scope="col">大小</th>
                <th scope="col">存储</th>
                <th scope="col">状态</th>
                <th scope="col">上传时间</th>
                <th v-if="canDownload || canDelete" scope="col">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="file in items" :key="file.id">
                <td class="file-name"><strong>{{ file.fileName }}</strong><span v-if="file.contentType" class="cell-meta">{{ file.contentType }}</span></td>
                <td><code v-if="file.bizType">{{ file.bizType }}</code><span v-else>—</span></td>
                <td class="file-size" :title="`${file.sizeBytes} 字节`">{{ formatSize(file.sizeBytes) }}</td>
                <td>{{ backends[file.storageBackend] }}</td>
                <td><span class="status-chip" :class="{ 'status-active': file.state === 'AVAILABLE' }">{{ states[file.state] }}</span></td>
                <td><time :datetime="new Date(file.createdAt).toISOString()">{{ formatTime(file.createdAt) }}</time></td>
                <td v-if="canDownload || canDelete">
                  <div class="row-actions">
                    <button v-if="canDownload && file.state === 'AVAILABLE'" type="button" class="btn btn-quiet"
                      :disabled="!!downloading" :aria-label="`下载 ${file.fileName}`" @click="download(file)">
                      {{ downloading === file.id ? '正在下载…' : '下载' }}
                    </button>
                    <button v-if="canDelete && file.state !== 'UPLOADING'" type="button" class="btn btn-quiet"
                      :aria-label="`${file.state === 'DELETING' ? '重试删除' : '删除'} ${file.fileName}`"
                      @click="openDialog({ kind: 'delete', file })">
                      {{ file.state === 'DELETING' ? '重试删除' : '删除' }}
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </ListState>
      <ResourcePager v-if="total > 0" v-model:page="page" :last-page="lastPage" :total="total" :disabled="loading" label="文件列表分页" />
    </section>

    <EditDialog v-if="dialog?.kind === 'upload' && canUpload" title="上传文件" :busy="busy" @close="dialog = null">
      <form class="upload-form" @submit.prevent="upload">
        <label class="field"><span>文件</span><input ref="uploadInput" type="file" required :disabled="busy"></label>
        <label class="field">
          <span>业务分类（可选）</span>
          <input v-model.trim="bizType" :pattern="codePattern" maxlength="120" autocomplete="off" :disabled="busy">
          <small>以小写字母或数字开头，可用冒号、下划线和连字符。</small>
        </label>
        <p v-if="dialogError" class="notice notice-error" role="alert">{{ dialogError }}</p>
        <div class="form-actions">
          <button type="button" class="btn" :disabled="busy" @click="dialog = null">取消</button>
          <button type="submit" class="btn btn-primary" :disabled="busy">{{ busy ? '正在上传…' : '上传' }}</button>
        </div>
      </form>
    </EditDialog>

    <EditDialog v-if="dialog?.kind === 'delete' && canDelete" title="删除文件" :busy="busy" @close="dialog = null">
      <p class="file-name">确定删除「{{ dialog.file.fileName }}」？</p>
      <p v-if="dialogError" class="notice notice-error" role="alert">{{ dialogError }}</p>
      <div class="form-actions">
        <button type="button" class="btn" autofocus :disabled="busy" @click="dialog = null">取消</button>
        <button type="button" class="btn delete-action" :disabled="busy" @click="removeFile">{{ busy ? '正在删除…' : '确认删除' }}</button>
      </div>
    </EditDialog>
  </template>
</template>

<style scoped>
.file-name { max-width: 26rem; overflow-wrap: anywhere; }
.file-size, time { white-space: nowrap; }
.upload-form { display: grid; gap: 12px; }
.upload-form .form-actions { margin-top: 0; }
.notice-error { margin-top: 12px; }
.delete-action { color: var(--danger); }
</style>
