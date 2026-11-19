<script setup lang="ts">
import { ref } from 'vue'
import { request, requestBlob } from '@/api/client'
import { useAction } from '@/hooks/useQueries'
import EditDialog from '@/components/EditDialog.vue'

const props = defineProps<{
  name: string
  basePath: string
  exportPath?: string
  canImport?: boolean
  maxRows: number
}>()

const emit = defineEmits<{ imported: [] }>()
const { busy, error, run } = useAction()
const importing = ref(false)
const selectedFile = ref<File>()
const feedback = ref('')

function openImport() {
  error.value = ''
  feedback.value = ''
  selectedFile.value = undefined
  importing.value = true
}

function closeImport() {
  selectedFile.value = undefined
  importing.value = false
  error.value = ''
}

function selectFile(event: Event) {
  selectedFile.value = (event.target as HTMLInputElement).files?.[0]
  error.value = ''
}

async function upload() {
  const file = selectedFile.value
  if (!file) return
  const completed = await run(async () => {
    if (!/\.xlsx$/i.test(file.name) || !file.size) throw new Error('请选择非空的 .xlsx 文件。')
    if (file.size > 1024 * 1024) throw new Error('文件大小不能超过 1 MiB。')

    const body = new FormData()
    body.append('file', file)
    const result = await request<{ imported: number }>(`${props.basePath}/import`, { method: 'POST', body })
    feedback.value = `已导入 ${result.imported} 条${props.name}记录。`
  })
  if (completed) {
    closeImport()
    emit('imported')
  }
}

async function download(path: string, filename: string) {
  feedback.value = ''
  await run(async () => {
    const blob = await requestBlob(path)
    if (blob.type !== 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet') {
      throw new Error('服务返回了无法识别的 Excel 文件。')
    }
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = filename
    document.body.append(link)
    link.click()
    link.remove()
    window.setTimeout(() => URL.revokeObjectURL(url), 1000)
  })
}
</script>

<template>
  <div class="toolbar" role="group" :aria-label="`${name} Excel 操作`">
    <button v-if="canImport" type="button" class="btn" :disabled="busy"
      @click="download(`${basePath}/template`, `${name}导入模板.xlsx`)">下载模板</button>
    <button v-if="canImport" type="button" class="btn" :disabled="busy" @click="openImport">导入 Excel</button>
    <button v-if="exportPath" type="button" class="btn" :disabled="busy"
      @click="download(exportPath, `${name}.xlsx`)">导出 Excel</button>
    <p v-if="feedback" class="excel-feedback notice notice-success" role="status">{{ feedback }}</p>
    <p v-if="error && !importing" class="excel-feedback notice notice-error" role="alert">{{ error }}</p>
  </div>

  <EditDialog v-if="importing" :title="`导入${name}`" :busy="busy" @close="closeImport">
    <form @submit.prevent="upload">
      <label class="field">
        <span>Excel 文件</span>
        <input type="file" accept=".xlsx" required :disabled="busy" @change="selectFile">
        <small>请按下载模板填写。每次最多 {{ maxRows }} 条，文件不超过 1 MiB。</small>
      </label>
      <p v-if="error" class="notice notice-error" role="alert">{{ error }}</p>
      <div class="form-actions">
        <button class="btn" type="button" :disabled="busy" @click="closeImport">取消</button>
        <button class="btn btn-primary" type="submit" :disabled="busy || !selectedFile">
          {{ busy ? '导入中…' : '导入' }}
        </button>
      </div>
    </form>
  </EditDialog>
</template>

<style scoped>
.excel-feedback { flex-basis: 100%; margin: 0; }
</style>
