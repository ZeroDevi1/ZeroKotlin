<script setup lang="ts">
import type { UserListEntry } from '@/api/system'
import StatusChip from './StatusChip.vue'

defineProps<{
  entries: UserListEntry[]
  departmentNames?: Record<string, string>
  showDepartment?: boolean
}>()
defineSlots<{ actions?(props: { entry: UserListEntry }): unknown }>()
</script>

<template>
  <div class="table-wrap">
    <table class="data-table" aria-label="用户列表">
      <thead>
        <tr>
          <th scope="col">用户</th>
          <th v-if="showDepartment" scope="col">部门</th>
          <th scope="col">角色</th>
          <th scope="col">状态</th>
          <th v-if="$slots.actions" scope="col">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="entry in entries" :key="entry.user.id">
          <td><strong>{{ entry.user.displayName }}</strong><span class="cell-meta">{{ entry.user.username }}</span></td>
          <td v-if="showDepartment">
            {{ entry.user.departmentId ? (departmentNames?.[entry.user.departmentId] ?? entry.user.departmentId) : '无部门' }}
          </td>
          <td>
            <ul v-if="entry.roles.length" class="tags" aria-label="已分配角色">
              <li v-for="role in entry.roles" :key="role.id">{{ role.name }}</li>
            </ul>
            <span v-else class="muted">无角色</span>
          </td>
          <td><StatusChip :enabled="entry.user.enabled" /></td>
          <td v-if="$slots.actions"><div class="row-actions"><slot name="actions" :entry="entry" /></div></td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
