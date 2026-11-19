<script setup lang="ts">
defineProps<{
  page: number
  lastPage: number
  total: number
  label: string
  disabled?: boolean
}>()

defineEmits<{
  'update:page': [page: number]
}>()
</script>

<template>
  <nav class="pager" :aria-label="label">
    <span class="page-count">共 {{ total }} 条 · 第 {{ page }} / {{ lastPage }} 页</span>
    <div class="page-buttons">
      <button type="button" class="btn btn-quiet" :disabled="disabled || page <= 1" @click="$emit('update:page', page - 1)">
        上一页
      </button>
      <button type="button" class="btn btn-quiet" :disabled="disabled || page >= lastPage" @click="$emit('update:page', page + 1)">
        下一页
      </button>
    </div>
  </nav>
</template>

<style scoped>
.pager,
.page-buttons {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: .5rem;
}

.pager { justify-content: space-between; }
.page-count { font-size: .875rem; }
</style>
