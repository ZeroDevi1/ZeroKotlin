<script setup lang="ts">
import EditDialog from '@/components/EditDialog.vue'
import { request } from '@/api/client'
import { apiPath } from '@/api/system'
import { useAction } from '@/hooks/useQueries'

const props = defineProps<{
  collection: string
  ids: string[]
  resource: string
  name?: string
  description: string
}>()
const emit = defineEmits<{ close: []; deleted: [ids: string[]] }>()
const { busy, error, run } = useAction()

async function confirm() {
  const { ids, collection } = props
  const [id] = ids
  if (!id) return
  const ok = await run(async () => {
    if (ids.length === 1) {
      await request(apiPath(`${collection}/${encodeURIComponent(id)}`), { method: 'DELETE' })
    } else {
      await request(apiPath(`${collection}/batch-delete`), { method: 'POST', body: { ids } })
    }
  })
  if (ok) emit('deleted', ids)
}
</script>

<template>
  <EditDialog :title="`删除${resource}`" :busy="busy" @close="emit('close')">
    <form @submit.prevent="confirm">
      <p v-if="name">确定删除{{ resource }}「{{ name }}」？</p>
      <p v-else>确定删除选中的 {{ ids.length }} 个{{ resource }}？</p>
      <p class="muted">{{ description }}</p>
      <p v-if="error" class="notice notice-error" role="alert">{{ error }}</p>
      <div class="form-actions">
        <button class="btn" type="button" autofocus :disabled="busy" @click="emit('close')">取消</button>
        <button class="btn btn-primary" type="submit" :disabled="busy">{{ busy ? '删除中…' : '确认删除' }}</button>
      </div>
    </form>
  </EditDialog>
</template>
