<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { apiPath, formatTime, type LoginAuditEntry, type OperationAuditEntry } from '@/api/system'
import ListState from '@/components/ListState.vue'
import Pager from '@/components/Pager.vue'
import StatusChip from '@/components/StatusChip.vue'
import { usePage } from '@/hooks/useQueries'

const tab = ref<'logins' | 'operations'>('logins')
const successful = ref('')
const operationFilters = reactive({ actorId: '', action: '' })
const applied = ref({ ...operationFilters })

const {
  data: loginData, loading: loginLoading, error: loginError, reload: reloadLogins,
  page: loginPage, size: loginSize, total: loginTotal, setSize: setLoginSize,
} = usePage<LoginAuditEntry>((page, size) => tab.value === 'logins'
  ? apiPath('/audits/logins', { page, size, successful: successful.value })
  : null)
const {
  data: opData, loading: opLoading, error: opError, reload: reloadOperations,
  page: opPage, size: opSize, total: opTotal, setSize: setOpSize,
} = usePage<OperationAuditEntry>((page, size) => tab.value === 'operations'
  ? apiPath('/audits/operations', { page, size, ...applied.value })
  : null)
watch(successful, () => { loginPage.value = 1 }, { flush: 'sync' })

function filterOperations() {
  opPage.value = 1
  const filter = { actorId: operationFilters.actorId.trim(), action: operationFilters.action.trim() }
  if (filter.actorId !== applied.value.actorId || filter.action !== applied.value.action) applied.value = filter
  else if (!opLoading.value) void reloadOperations()
}

function resetOperations() {
  operationFilters.actorId = ''
  operationFilters.action = ''
  filterOperations()
}
</script>

<template>
  <header class="page-header">
    <div><h1>审计</h1><p>查询登录结果和已记录的系统操作。</p></div>
  </header>
  <section class="panel" aria-label="审计记录">
    <div class="panel-title">
      <div class="toolbar" role="group" aria-label="审计类型">
        <button type="button" class="btn btn-quiet" :aria-pressed="tab === 'logins'" @click="tab = 'logins'">登录审计</button>
        <button type="button" class="btn btn-quiet" :aria-pressed="tab === 'operations'" @click="tab = 'operations'">操作审计</button>
      </div>
    </div>
    <template v-if="tab === 'logins'">
      <div class="toolbar">
        <label class="field">
          <span>登录结果</span>
          <select v-model="successful">
            <option value="">全部结果</option><option value="true">成功</option><option value="false">失败</option>
          </select>
        </label>
      </div>
      <ListState :loading="loginLoading" :error="loginError" :empty="!loginData?.items.length"
        empty-text="暂无符合条件的登录记录。" @retry="reloadLogins">
        <div class="table-wrap">
          <table class="data-table" aria-label="登录审计记录">
            <thead><tr><th scope="col">时间</th><th scope="col">账号</th><th scope="col">结果</th><th scope="col">来源地址</th></tr></thead>
            <tbody>
              <tr v-for="entry in loginData?.items" :key="entry.id">
                <td><time :datetime="new Date(entry.occurredAt).toISOString()">{{ formatTime(entry.occurredAt) }}</time></td>
                <td>
                  <strong>{{ entry.username ?? '未知账号' }}</strong>
                  <code v-if="entry.userId" class="cell-meta">{{ entry.userId }}</code>
                </td>
                <td><StatusChip :enabled="entry.successful" active-label="成功" inactive-label="失败" /></td>
                <td><code>{{ entry.remoteAddress }}</code></td>
              </tr>
            </tbody>
          </table>
        </div>
      </ListState>
      <Pager v-if="loginData" v-model:page="loginPage" :size="loginSize" :total="loginTotal" @update:size="setLoginSize" />
    </template>
    <template v-else>
      <form class="toolbar" @submit.prevent="filterOperations">
        <label class="field"><span>操作者 ID</span><input v-model="operationFilters.actorId" maxlength="36"></label>
        <label class="field"><span>操作类型</span><input v-model="operationFilters.action" maxlength="120"></label>
        <button type="submit" class="btn">筛选</button>
        <button type="button" class="btn btn-quiet" @click="resetOperations">重置</button>
      </form>
      <ListState :loading="opLoading" :error="opError" :empty="!opData?.items.length"
        empty-text="暂无符合条件的操作记录。" @retry="reloadOperations">
        <div class="table-wrap">
          <table class="data-table" aria-label="操作审计记录">
            <thead><tr><th scope="col">时间</th><th scope="col">操作者 ID</th><th scope="col">操作类型</th><th scope="col">目标 ID</th></tr></thead>
            <tbody>
              <tr v-for="entry in opData?.items" :key="entry.id">
                <td><time :datetime="new Date(entry.occurredAt).toISOString()">{{ formatTime(entry.occurredAt) }}</time></td>
                <td><code>{{ entry.actorId }}</code></td>
                <td><code>{{ entry.action }}</code></td>
                <td><code>{{ entry.targetId }}</code></td>
              </tr>
            </tbody>
          </table>
        </div>
      </ListState>
      <Pager v-if="opData" v-model:page="opPage" :size="opSize" :total="opTotal" @update:size="setOpSize" />
    </template>
  </section>
</template>
