<script setup lang="ts">
import { apiPath } from '@/api/system'
import ListState from '@/components/ListState.vue'
import StatusChip from '@/components/StatusChip.vue'
import { useResource } from '@/hooks/useQueries'

interface MonitorSnapshot {
  sampledAt: number
  cpu: { processors: number; systemLoad: number | null; processLoad: number | null }
  memory: { totalBytes: number; freeBytes: number } | null
  jvm: {
    version: string
    heapUsedBytes: number
    heapCommittedBytes: number
    heapMaxBytes: number
    uptimeMs: number | null
  }
  disk: { totalBytes: number; usableBytes: number } | null
  redis: {
    status: 'DISABLED' | 'AVAILABLE' | 'UNAVAILABLE'
    version: string | null
    connectedClients: number | null
    usedMemoryBytes: number | null
    uptimeSeconds: number | null
  }
}

const { data, error, loading, reload } = useResource<MonitorSnapshot>(() => apiPath('/monitor'))
const time = new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'medium' })
const decimal = new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 1 })
const percent = new Intl.NumberFormat('zh-CN', { style: 'percent', maximumFractionDigits: 1 })
const units = ['B', 'KiB', 'MiB', 'GiB', 'TiB']

function bytes(value: number | null) {
  if (value == null) return '—'
  const exponent = value < 1024 ? 0 : Math.min(units.length - 1, Math.floor(Math.log2(value) / 10))
  return `${decimal.format(value / 1024 ** exponent)} ${units[exponent]}`
}

function load(value: number | null) {
  return value == null ? '—' : percent.format(value)
}

function uptime(seconds: number | null) {
  if (seconds == null) return '—'
  if (seconds < 60) return `${Math.floor(seconds)} 秒`
  const minutes = Math.floor(seconds / 60)
  const days = Math.floor(minutes / 1440)
  const hours = Math.floor((minutes % 1440) / 60)
  return [days ? `${days} 天` : '', days || hours ? `${hours} 小时` : '', `${minutes % 60} 分钟`]
    .filter(Boolean).join(' ')
}
</script>

<template>
  <header class="page-header">
    <div>
      <h1>运行监控</h1>
      <p v-if="data">
        采样于 <time :datetime="new Date(data.sampledAt).toISOString()">{{ time.format(data.sampledAt) }}</time>
      </p>
    </div>
    <button class="btn" type="button" :disabled="loading" @click="reload">刷新</button>
  </header>
  <ListState :loading="loading" :error="error" @retry="reload">
    <div v-if="data" class="ops-grid">
      <section class="panel ops-card">
        <h2>CPU</h2>
        <dl class="details-list">
          <dt>可用逻辑处理器</dt><dd>{{ data.cpu.processors }}</dd>
          <dt>系统使用率</dt><dd>{{ load(data.cpu.systemLoad) }}</dd>
          <dt>进程使用率</dt><dd>{{ load(data.cpu.processLoad) }}</dd>
        </dl>
      </section>
      <section class="panel ops-card">
        <h2>系统内存</h2>
        <dl v-if="data.memory" class="details-list">
          <dt>总内存</dt><dd>{{ bytes(data.memory.totalBytes) }}</dd>
          <dt>空闲内存</dt><dd>{{ bytes(data.memory.freeBytes) }}</dd>
        </dl>
        <p v-else class="muted">当前环境未提供系统内存数据。</p>
      </section>
      <section class="panel ops-card">
        <h2>JVM</h2>
        <dl class="details-list">
          <dt>Java 版本</dt><dd>{{ data.jvm.version }}</dd>
          <dt>已用堆</dt><dd>{{ bytes(data.jvm.heapUsedBytes) }}</dd>
          <dt>已分配堆</dt><dd>{{ bytes(data.jvm.heapCommittedBytes) }}</dd>
          <dt>最大堆</dt><dd>{{ bytes(data.jvm.heapMaxBytes) }}</dd>
          <dt>运行时长</dt><dd>{{ uptime(data.jvm.uptimeMs == null ? null : data.jvm.uptimeMs / 1000) }}</dd>
        </dl>
      </section>
      <section class="panel ops-card">
        <h2>磁盘</h2>
        <template v-if="data.disk">
          <p class="muted">应用工作目录所在的文件系统</p>
          <dl class="details-list">
            <dt>总容量</dt><dd>{{ bytes(data.disk.totalBytes) }}</dd>
            <dt>可用空间</dt><dd>{{ bytes(data.disk.usableBytes) }}</dd>
          </dl>
        </template>
        <p v-else class="muted">暂时无法读取磁盘容量。</p>
      </section>
      <section class="panel ops-card ops-redis">
        <div class="ops-heading">
          <h2>Redis</h2>
          <StatusChip :enabled="data.redis.status === 'AVAILABLE'" active-label="可用"
            :inactive-label="data.redis.status === 'DISABLED' ? '未启用' : '暂不可读'" />
        </div>
        <dl v-if="data.redis.status === 'AVAILABLE'" class="details-list">
          <dt>版本</dt><dd>{{ data.redis.version ?? '—' }}</dd>
          <dt>连接客户端</dt><dd>{{ data.redis.connectedClients ?? '—' }}</dd>
          <dt>已用内存</dt><dd>{{ bytes(data.redis.usedMemoryBytes) }}</dd>
          <dt>运行时长</dt><dd>{{ uptime(data.redis.uptimeSeconds) }}</dd>
        </dl>
      </section>
    </div>
  </ListState>
</template>

<style scoped>
.ops-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 260px), 1fr));
  gap: 14px;
}

.ops-card {
  padding: 18px 20px;
}

.ops-grid .ops-card {
  margin-top: 0;
}

.ops-card > * + * {
  margin-top: 12px;
}

.ops-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.ops-redis {
  grid-column: 1 / -1;
}
</style>
