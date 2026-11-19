<script setup lang="ts">
defineProps<{ loading: boolean; error: string; empty?: boolean; emptyText?: string }>()
defineEmits<{ retry: [] }>()
</script>

<template>
  <div v-if="loading" class="empty" role="status">正在读取…</div>
  <div v-else-if="error" class="empty" role="alert">
    <p>{{ error }}</p>
    <button class="btn" type="button" @click="$emit('retry')">重试</button>
  </div>
  <div v-else-if="empty" class="empty">{{ emptyText || '暂无记录。' }}</div>
  <slot v-else />
</template>
