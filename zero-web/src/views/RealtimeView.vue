<script setup lang="ts">
import { Client } from '@stomp/stompjs'
import { computed, ref, watch } from 'vue'
import { ApiError, request } from '@/api/client'
import SseDemo from '@/components/SseDemo.vue'
import { errorMessage, formatTime } from '@/hooks/useResourcePage'
import { sessionAuthorization, useAuth } from '@/store/auth'

type MessageKind = 'broadcast' | 'private'
type Connection = 'connecting' | 'connected' | 'retrying' | 'rejected'

interface RealtimeMessage {
  id: string
  senderId: string
  senderName: string
  body: string
  sentAt: number
}

const api = '/api/realtime'
const connectionLabels: Record<Connection, string> = {
  connecting: '正在连接…',
  connected: '已连接',
  retrying: '连接中断，正在重连…',
  rejected: '连接被拒绝，请检查登录状态和实时消息权限。',
}
const messageLabels = { broadcast: '群发', private: '私发' }

const auth = useAuth()
const canRead = computed(() => auth.can('system:message:read'))
const canBroadcast = computed(() => auth.can('system:message:broadcast'))
const canSendPrivate = computed(() => auth.can('system:message:private'))
const selectedKind = ref<MessageKind>('broadcast')
const sendKind = computed<MessageKind>(() =>
  canSendPrivate.value && (!canBroadcast.value || selectedKind.value === 'private') ? 'private' : 'broadcast',
)
const connection = ref<Connection>('connecting')
const retry = ref(0)
const online = ref<number | null>(null)
const messages = ref<(RealtimeMessage & { kind: MessageKind })[]>([])
const recipientId = ref('')
const body = ref('')
const sending = ref(false)
const sendStatus = ref('')
const sendError = ref('')

function receive(kind: MessageKind, text: string) {
  try {
    const message = JSON.parse(text) as RealtimeMessage | null
    if (!message || typeof message.id !== 'string' || typeof message.senderId !== 'string' ||
      typeof message.senderName !== 'string' || typeof message.body !== 'string' || !Number.isFinite(message.sentAt)) return

    messages.value.unshift({ ...message, kind })
    if (messages.value.length > 100) messages.value.pop()
  } catch {
    // 忽略格式无效的代理消息，保留当前订阅。
  }
}

watch([() => auth.user?.id, canRead, retry], ([userId, allowed], previous, onCleanup) => {
  if (userId !== previous?.[0] || !allowed) messages.value = []
  online.value = null
  if (!userId || !allowed) return

  connection.value = 'connecting'
  const { protocol, host } = window.location
  const brokerURL = `${protocol === 'https:' ? 'wss:' : 'ws:'}//${host}${api}/ws`
  const controller = new AbortController()
  const { signal } = controller
  let loadingOnline = false
  const client = new Client({
    brokerURL,
    reconnectDelay: 5000,
    connectionTimeout: 10_000,
    heartbeatIncoming: 15_000,
    heartbeatOutgoing: 15_000,
  })

  function rejectConnection() {
    connection.value = 'rejected'
    online.value = null
    void client.deactivate()
  }

  async function refreshOnline() {
    if (loadingOnline || signal.aborted) return
    loadingOnline = true
    try {
      const presence = await request<{ count: number }>(`${api}/online`, { signal })
      if (!signal.aborted && connection.value === 'connected') online.value = presence.count
    } catch (error) {
      if (signal.aborted) return
      if (error instanceof ApiError && error.status === 403) rejectConnection()
      else online.value = null
    } finally {
      loadingOnline = false
    }
  }

  client.beforeConnect = () => {
    const authorization = sessionAuthorization()
    if (authorization) client.connectHeaders = { Authorization: authorization }
    else rejectConnection()
  }
  client.onConnect = () => {
    if (signal.aborted) return
    client.subscribe('/topic/realtime/broadcast', (frame) => {
      if (!signal.aborted) receive('broadcast', frame.body)
    })
    client.subscribe('/user/queue/realtime/private', (frame) => {
      if (!signal.aborted) receive('private', frame.body)
    })
    connection.value = 'connected'
    void refreshOnline()
  }
  client.onStompError = () => {
    if (signal.aborted || connection.value === 'rejected') return
    rejectConnection()
    void refreshOnline()
  }
  client.onWebSocketClose = () => {
    if (signal.aborted || connection.value === 'rejected') return
    connection.value = 'retrying'
    online.value = null
  }

  const timer = window.setInterval(() => {
    if (connection.value === 'connected') void refreshOnline()
  }, 15_000)
  onCleanup(() => {
    controller.abort()
    window.clearInterval(timer)
    void client.deactivate()
  })
  client.activate()
}, { immediate: true })

async function send() {
  const kind = sendKind.value
  const text = body.value.trim()
  const target = recipientId.value.trim()
  if (sending.value || connection.value !== 'connected' || !text ||
    (kind === 'broadcast' ? !canBroadcast.value : !canSendPrivate.value || !target)) return

  sending.value = true
  sendError.value = ''
  sendStatus.value = ''
  try {
    await request<void>(`${api}/${kind}`, {
      method: 'POST',
      body: kind === 'private' ? { body: text, recipientId: target } : { body: text },
    })
    body.value = ''
    sendStatus.value = '已提交实时投递。'
  } catch (error) {
    sendError.value = errorMessage(error, '发送实时消息失败。')
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <section aria-labelledby="realtime-title">
    <header class="page-header">
      <div>
        <h1 id="realtime-title">实时消息</h1>
        <p>接收当前连接期间的群发和个人消息。</p>
      </div>
    </header>

    <p v-if="!canRead" class="empty">没有读取实时消息的权限。</p>
    <template v-else>
      <SseDemo />
      <div class="panel presence">
        <p role="status">{{ connectionLabels[connection] }}</p>
        <p>在线人数 <strong>{{ online ?? '—' }}</strong></p>
        <button v-if="connection === 'rejected'" type="button" class="btn" @click="retry++">重新连接</button>
      </div>

      <section v-if="canBroadcast || canSendPrivate" class="panel realtime-panel" aria-labelledby="realtime-send-title">
        <h2 id="realtime-send-title">发送消息</h2>
        <form class="send-form" @submit.prevent="send">
          <label v-if="canBroadcast && canSendPrivate" class="field">
            <span>发送方式</span>
            <select v-model="selectedKind" :disabled="sending">
              <option value="broadcast">群发</option>
              <option value="private">私发</option>
            </select>
          </label>
          <label v-if="sendKind === 'private'" class="field">
            <span>接收者用户 ID</span>
            <input v-model="recipientId" autocomplete="off" maxlength="36" required :disabled="sending">
            <small>可从用户详情复制用户 ID。</small>
          </label>
          <label class="field">
            <span>消息正文</span>
            <textarea v-model="body" maxlength="2000" required :disabled="sending" />
          </label>
          <div class="send-actions">
            <button type="submit" class="btn btn-primary" :disabled="sending || connection !== 'connected'">
              {{ sending ? '正在发送…' : `发送${messageLabels[sendKind]}消息` }}
            </button>
            <span v-if="sendStatus" role="status">{{ sendStatus }}</span>
          </div>
          <p v-if="sendError" class="notice notice-error" role="alert">{{ sendError }}</p>
        </form>
      </section>

      <section class="panel realtime-panel" aria-labelledby="realtime-messages-title">
        <h2 id="realtime-messages-title">已接收消息</h2>
        <p v-if="messages.length === 0" class="empty">暂无实时消息。</p>
        <ul class="realtime-list" aria-live="polite" aria-relevant="additions">
          <li v-for="message in messages" :key="message.id">
            <div class="message-meta">
              <strong :title="message.senderId">{{ message.senderName }}</strong>
              <span>{{ messageLabels[message.kind] }}</span>
              <span>{{ formatTime(message.sentAt) }}</span>
            </div>
            <p class="message-body">{{ message.body }}</p>
          </li>
        </ul>
      </section>
    </template>
  </section>
</template>

<style scoped>
.presence, .send-actions, .message-meta { display: flex; align-items: center; gap: 0.75rem; flex-wrap: wrap; }
.presence, .realtime-panel { padding: 1rem; }
.presence p, .message-body { margin: 0; }
.realtime-panel h2 { margin: 0 0 0.75rem; }
.send-form, .realtime-list { display: grid; gap: 0.75rem; }
.realtime-list { margin: 0; padding: 0; list-style: none; }
.realtime-list li { border-top: 1px solid var(--line); padding-top: 0.75rem; }
.message-meta { color: var(--muted); font-size: 0.875rem; overflow-wrap: anywhere; }
.message-body { margin-top: 0.25rem; white-space: pre-wrap; overflow-wrap: anywhere; }
</style>
