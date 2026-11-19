<script setup lang="ts">
defineProps<{ enabled: boolean; activeLabel?: string; inactiveLabel?: string }>()
</script>

<template>
  <span class="status-chip" :class="{ 'status-active': enabled }">
    {{ enabled ? (activeLabel ?? '启用') : (inactiveLabel ?? '停用') }}
  </span>
</template>
