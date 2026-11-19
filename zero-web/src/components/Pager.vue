<script setup lang="ts">
import { computed } from 'vue'
import { ChevronLeft, ChevronRight } from '@lucide/vue'

const props = defineProps<{ page: number; size: number; total: number; loading?: boolean }>()
const emit = defineEmits<{ 'update:page': [page: number]; 'update:size': [size: number] }>()
const last = computed(() => Math.max(1, Math.ceil(props.total / props.size)))
</script>

<template>
  <div class="pager">
    <span>共 {{ total }} 条</span>
    <label class="inline-field">
      <span class="sr-only">每页条数</span>
      <select :value="size" :disabled="loading"
        @change="emit('update:size', Number(($event.target as HTMLSelectElement).value))">
        <option :value="20">20 条/页</option>
        <option :value="50">50 条/页</option>
        <option :value="100">100 条/页</option>
      </select>
    </label>
    <span class="page-indicator">{{ page }} / {{ last }}</span>
    <button class="btn btn-quiet" type="button" aria-label="上一页" :disabled="loading || page <= 1"
      @click="emit('update:page', page - 1)"><ChevronLeft :size="17" aria-hidden="true" /></button>
    <button class="btn btn-quiet" type="button" aria-label="下一页" :disabled="loading || page >= last"
      @click="emit('update:page', page + 1)"><ChevronRight :size="17" aria-hidden="true" /></button>
  </div>
</template>
