<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, useId } from 'vue'
import { X } from '@lucide/vue'

defineProps<{ title: string; busy?: boolean }>()
const emit = defineEmits<{ close: [] }>()
const id = useId()
const dialog = ref<HTMLDialogElement>()
let opener: HTMLElement | null = null

onMounted(() => {
  opener = document.activeElement instanceof HTMLElement ? document.activeElement : null
  dialog.value?.showModal()
})

onBeforeUnmount(() => {
  dialog.value?.close()
  if (opener?.isConnected) opener.focus()
})
</script>

<template>
  <dialog ref="dialog" class="edit-dialog" :aria-labelledby="id" @cancel.prevent="!busy && emit('close')">
    <header class="dialog-header">
      <h2 :id="id">{{ title }}</h2>
      <button class="btn btn-quiet" type="button" aria-label="关闭" :disabled="busy" @click="emit('close')">
        <X :size="18" aria-hidden="true" />
      </button>
    </header>
    <div class="dialog-body"><slot /></div>
  </dialog>
</template>
