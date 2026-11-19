<script setup lang="ts">
import type { NavItem } from '@/router/navigation'

defineProps<{ items: NavItem[] }>()
</script>

<template>
  <ul class="nav-list">
    <li v-for="item in items" :key="item.id">
      <RouterLink v-if="item.page" :to="item.page.path" class="nav-link">
        <component :is="item.page.icon" :size="17" aria-hidden="true" />
        {{ item.label }}
      </RouterLink>
      <div v-else class="nav-label">{{ item.label }}</div>
      <NavBranch v-if="item.children.length" :items="item.children" />
    </li>
  </ul>
</template>
