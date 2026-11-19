<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { requestEventStream } from '@/api/client'
import { errorMessage } from '@/hooks/useResourcePage'

const frames = ref('')
const status = ref('')
const active = ref(false)
let connection: AbortController | undefined

async function start() {
  if (active.value) return
  const controller = new AbortController()
  connection = controller
  active.value = true
  frames.value = ''
  status.value = '正在接收事件…'
  try {
    const stream = await requestEventStream('/api/realtime/sse/demo', controller.signal)
    const reader = stream.getReader()
    try {
      while (!controller.signal.aborted) {
        const chunk = await reader.read()
        if (chunk.done) break
        frames.value += chunk.value
      }
    } finally {
      reader.releaseLock()
    }
    status.value = controller.signal.aborted ? '已断开。' : '事件流已结束。'
  } catch (cause) {
    status.value = controller.signal.aborted ? '已断开。' : errorMessage(cause, '接收事件失败。')
  } finally {
    active.value = false
    connection = undefined
  }
}

onBeforeUnmount(() => connection?.abort())
</script>

<template>
  <section class="panel" aria-labelledby="sse-demo-heading">
    <h2 id="sse-demo-heading">SSE 通信演示</h2>
    <button v-if="active" type="button" class="btn" @click="connection?.abort()">断开连接</button>
    <button v-else type="button" class="btn" @click="start">接收示例事件</button>
    <span role="status">{{ status }}</span>
    <pre v-if="frames" aria-label="收到的 SSE 事件">{{ frames }}</pre>
  </section>
</template>

<style scoped>
.panel { padding: 1rem; }
h2 { margin: 0 0 .75rem; font-size: 1rem; }
[role="status"] { margin-inline-start: .75rem; }
pre { overflow-x: auto; }
</style>
