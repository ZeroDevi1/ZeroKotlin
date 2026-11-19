<script setup lang="ts">
import { computed, nextTick, reactive, ref, shallowRef } from 'vue'
import { Plus, RefreshCw } from '@lucide/vue'
import { request } from '@/api/client'
import EditDialog from '@/components/EditDialog.vue'
import ListState from '@/components/ListState.vue'
import ResourcePager from '@/components/ResourcePager.vue'
import StatusChip from '@/components/StatusChip.vue'
import { useAction, useResource } from '@/hooks/useQueries'
import { useResourcePage } from '@/hooks/useResourcePage'
import { useAuth } from '@/store/auth'

const endpoint = '/api/system/jobs'
const clockUnits = { SECONDS: '秒', MINUTES: '分钟', HOURS: '小时' }
const calendarUnits = { DAYS: '日', WEEKS: '周', MONTHS: '月', YEARS: '年' }
const calendarLimits = { DAYS: 366, WEEKS: 52, MONTHS: 12, YEARS: 10 }
const clockSeconds = { SECONDS: 1, MINUTES: 60, HOURS: 3600 }
const weekdays = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
const scheduleLabels = { manual: '仅手动', cron: 'Cron', simple: '固定间隔', calendar: '日历间隔', daily: '日内时段' }
const runLabels = { QUEUED: '待执行', SUCCESS: '成功', FAILED: '失败', CANCELLED: '已取消' }
const dateTime = new Intl.DateTimeFormat('zh-CN', { dateStyle: 'short', timeStyle: 'medium' })

type ClockUnit = keyof typeof clockUnits
type CalendarUnit = keyof typeof calendarUnits
type ScheduleKind = keyof typeof scheduleLabels
type JobSchedule = {
  cron?: { expression: string; timeZone: string } | null
  simple?: { interval: number; unit: ClockUnit; repeatCount: number | null } | null
  calendar?: { interval: number; unit: CalendarUnit; timeZone: string } | null
  daily?: { interval: number; unit: ClockUnit; daysOfWeek: number[]; startTime: string; endTime: string } | null
}
type Job = {
  id: string
  name: string
  handler: string
  schedule: JobSchedule | null
  paused: boolean
  nextFireAt: number | null
}
type JobRun = {
  id: string
  jobName: string
  handler: string
  source: 'SCHEDULED' | 'MANUAL'
  status: keyof typeof runLabels
  requestedAt: number
  startedAt: number | null
  finishedAt: number | null
  message: string | null
}
type Handler = { key: string; name: string }
type Dialog =
  | { kind: 'edit'; job?: Job }
  | { kind: 'delete'; job: Job }
  | { kind: 'run'; job: Job; requestId: string }

const auth = useAuth()
const canRead = computed(() => auth.can('system:job:read'))
const canCreate = computed(() => auth.can('system:job:create'))
const canUpdate = computed(() => auth.can('system:job:update'))
const canDelete = computed(() => auth.can('system:job:delete'))
const canPause = computed(() => auth.can('system:job:pause'))
const canResume = computed(() => auth.can('system:job:resume'))
const canRun = computed(() => auth.can('system:job:run'))
const canLogs = computed(() => auth.can('system:job:log:read'))
const showActions = computed(() => canUpdate.value || canDelete.value || canPause.value ||
  canResume.value || canRun.value || canLogs.value)

const { items: jobs, total, page, lastPage, loading, error, reload } = useResourcePage<Job>(
  (number, size) => canRead.value ? `${endpoint}?page=${number}&size=${size}` : null,
  '无法读取定时任务。',
)
const logsOpen = ref(false)
const logFilter = shallowRef<Job | null>(null)
const logHeading = ref<HTMLElement | null>(null)
const {
  items: logs, total: logTotal, page: logPage, lastPage: logLastPage,
  loading: logLoading, error: logError, reload: reloadLogs,
} = useResourcePage<JobRun>((number, size) => {
  if (!logsOpen.value || !canLogs.value) return null
  const params = new URLSearchParams({ page: String(number), size: String(size) })
  if (logFilter.value) params.set('jobId', logFilter.value.id)
  return `${endpoint}/logs?${params}`
}, '无法读取任务执行日志。')

const dialog = shallowRef<Dialog | null>(null)
const { data: handlers, loading: handlersLoading, error: handlersError, reload: reloadHandlers } =
  useResource<Handler[]>(() => dialog.value?.kind === 'edit' ? `${endpoint}/handlers` : null)
const draft = reactive({
  name: '', handler: '', kind: 'manual' as ScheduleKind, expression: '', timeZone: 'UTC',
  interval: 1 as number | '', clockUnit: 'MINUTES' as ClockUnit, calendarUnit: 'DAYS' as CalendarUnit,
  repeatCount: '' as number | '', daysOfWeek: [1, 2, 3, 4, 5, 6, 7], startTime: '09:00', endTime: '18:00',
})
const maxInterval = computed(() => {
  if (draft.kind === 'calendar') return calendarLimits[draft.calendarUnit]
  if (draft.kind === 'daily') return 86400 / clockSeconds[draft.clockUnit]
  return 86400
})
const { busy, error: actionError, run } = useAction()
const feedback = ref('')
const dialogTitle = computed(() => {
  switch (dialog.value?.kind) {
    case 'edit': return dialog.value.job ? '编辑任务' : '新建任务'
    case 'delete': return '删除任务'
    case 'run': return '立即执行任务'
    default: return ''
  }
})

function scheduleKind(schedule: JobSchedule | null): ScheduleKind {
  if (schedule?.cron) return 'cron'
  if (schedule?.simple) return 'simple'
  if (schedule?.calendar) return 'calendar'
  if (schedule?.daily) return 'daily'
  return 'manual'
}

function scheduleDetail(schedule: JobSchedule | null) {
  if (schedule?.cron) return `${schedule.cron.expression} · ${schedule.cron.timeZone}`
  if (schedule?.simple) {
    const { interval, unit, repeatCount } = schedule.simple
    return `每 ${interval} ${clockUnits[unit]} · ${repeatCount === null ? '持续重复' : `共 ${repeatCount + 1} 次`}`
  }
  if (schedule?.calendar) return `每 ${schedule.calendar.interval} ${calendarUnits[schedule.calendar.unit]} · ${schedule.calendar.timeZone}`
  if (schedule?.daily) {
    const { daysOfWeek, startTime, endTime, interval, unit } = schedule.daily
    return `${daysOfWeek.map(day => weekdays[day - 1]).join('、')} ${startTime}–${endTime} · 每 ${interval} ${clockUnits[unit]}`
  }
  return ''
}

function openEditor(job?: Job) {
  const schedule = job?.schedule ?? null
  Object.assign(draft, {
    name: job?.name ?? '',
    handler: job?.handler ?? '',
    kind: scheduleKind(schedule),
    expression: schedule?.cron?.expression ?? '',
    timeZone: schedule?.cron?.timeZone ?? schedule?.calendar?.timeZone ?? 'UTC',
    interval: schedule?.simple?.interval ?? schedule?.calendar?.interval ?? schedule?.daily?.interval ?? 1,
    clockUnit: schedule?.simple?.unit ?? schedule?.daily?.unit ?? 'MINUTES',
    calendarUnit: schedule?.calendar?.unit ?? 'DAYS',
    repeatCount: schedule?.simple?.repeatCount ?? '',
    daysOfWeek: [...(schedule?.daily?.daysOfWeek ?? [1, 2, 3, 4, 5, 6, 7])],
    startTime: schedule?.daily?.startTime ?? '09:00',
    endTime: schedule?.daily?.endTime ?? '18:00',
  })
  feedback.value = ''
  actionError.value = ''
  dialog.value = { kind: 'edit', job }
}

function buildSchedule(): JobSchedule | null {
  const interval = Number(draft.interval)
  switch (draft.kind) {
    case 'cron': return { cron: { expression: draft.expression, timeZone: draft.timeZone } }
    case 'simple': return { simple: {
      interval, unit: draft.clockUnit, repeatCount: draft.repeatCount === '' ? null : draft.repeatCount,
    } }
    case 'calendar': return { calendar: { interval, unit: draft.calendarUnit, timeZone: draft.timeZone } }
    case 'daily': return { daily: {
      interval, unit: draft.clockUnit, daysOfWeek: draft.daysOfWeek, startTime: draft.startTime, endTime: draft.endTime,
    } }
    default: return null
  }
}

function confirmAction(kind: 'run' | 'delete', job: Job) {
  feedback.value = ''
  actionError.value = ''
  if (kind === 'delete') {
    dialog.value = { kind, job }
  } else {
    const requestId = Array.from(crypto.getRandomValues(new Uint8Array(16)), byte => byte.toString(16).padStart(2, '0')).join('')
    dialog.value = { kind, job, requestId }
  }
}

async function showLogs(job: Job | null) {
  if (logsOpen.value && logFilter.value?.id === job?.id) reloadLogs()
  logFilter.value = job
  logPage.value = 1
  logsOpen.value = true
  await nextTick()
  logHeading.value?.focus()
}

async function submit() {
  const active = dialog.value
  if (!active) return
  if (active.kind === 'edit' && draft.kind === 'daily' && draft.daysOfWeek.length === 0) {
    actionError.value = '请至少选择一个执行日期。'
    return
  }

  const path = active.job ? `${endpoint}/${encodeURIComponent(active.job.id)}` : endpoint
  const completed = await run(async () => {
    switch (active.kind) {
      case 'edit':
        await request(path, {
          method: active.job ? 'PUT' : 'POST',
          body: { name: draft.name, handler: draft.handler, schedule: buildSchedule() },
        })
        break
      case 'delete':
        await request(path, { method: 'DELETE' })
        break
      case 'run':
        await request(`${path}/run`, { method: 'POST', body: { requestId: active.requestId } })
        break
    }
  })
  if (!completed) return

  feedback.value = active.kind === 'run' ? '手动执行请求已受理。'
    : active.kind === 'delete' ? '任务已删除。' : '任务已保存。'
  dialog.value = null
  reload()
  if (active.kind === 'run' && canLogs.value) await showLogs(active.job)
  else reloadLogs()
}

async function togglePause(job: Job) {
  feedback.value = ''
  const action = job.paused ? 'resume' : 'pause'
  const completed = await run(async () => {
    await request(`${endpoint}/${encodeURIComponent(job.id)}/${action}`, { method: 'POST' })
  })
  if (!completed) return
  feedback.value = job.paused ? '自动计划已恢复。' : '自动计划已暂停。'
  reload()
}
</script>

<template>
  <header class="page-header">
    <h1>定时任务</h1>
    <div class="row-actions">
      <button v-if="canLogs" type="button" class="btn" @click="showLogs(null)">执行日志</button>
      <button v-if="canRead && canCreate" type="button" class="btn btn-primary" :disabled="busy" @click="openEditor()">
        <Plus :size="17" aria-hidden="true" /> 新建任务
      </button>
    </div>
  </header>
  <p v-if="feedback" class="notice notice-success" role="status">{{ feedback }}</p>
  <p v-if="actionError && !dialog" class="notice notice-error" role="alert">{{ actionError }}</p>
  <section v-if="canRead" class="panel" aria-label="任务列表" :aria-busy="loading">
    <header class="panel-title">
      <h2>任务列表</h2>
      <button type="button" class="btn btn-quiet" :disabled="loading" @click="reload">
        <RefreshCw :size="16" aria-hidden="true" /> 刷新
      </button>
    </header>
    <ListState :loading="loading" :error="error" :empty="jobs.length === 0" empty-text="暂无定时任务。" @retry="reload">
      <div class="table-wrap">
        <table class="data-table">
          <thead><tr>
            <th scope="col">任务</th><th scope="col">触发器</th><th scope="col">自动计划</th><th scope="col">下次触发</th>
            <th v-if="showActions" scope="col">操作</th>
          </tr></thead>
          <tbody>
            <tr v-for="job in jobs" :key="job.id">
              <td><strong>{{ job.name }}</strong><code class="cell-meta">{{ job.handler }}</code></td>
              <td>
                {{ scheduleLabels[scheduleKind(job.schedule)] }}
                <span v-if="job.schedule" class="cell-meta">{{ scheduleDetail(job.schedule) }}</span>
              </td>
              <td>
                <StatusChip v-if="job.schedule" :enabled="!job.paused && job.nextFireAt !== null"
                  active-label="已启用" :inactive-label="job.paused ? '已暂停' : '已结束'" />
                <span v-else class="muted">—</span>
              </td>
              <td>{{ job.nextFireAt === null ? '—' : dateTime.format(job.nextFireAt) }}</td>
              <td v-if="showActions">
                <div class="row-actions" :aria-label="`${job.name}的操作`">
                  <button v-if="canUpdate" type="button" class="btn btn-quiet" :disabled="busy" @click="openEditor(job)">编辑</button>
                  <button v-if="job.schedule && job.paused && canResume" type="button" class="btn btn-quiet" :disabled="busy" @click="togglePause(job)">恢复</button>
                  <button v-if="job.schedule && !job.paused && job.nextFireAt !== null && canPause" type="button" class="btn btn-quiet" :disabled="busy" @click="togglePause(job)">暂停</button>
                  <button v-if="canRun" type="button" class="btn btn-quiet" :disabled="busy" @click="confirmAction('run', job)">立即执行</button>
                  <button v-if="canLogs" type="button" class="btn btn-quiet" @click="showLogs(job)">日志</button>
                  <button v-if="canDelete" type="button" class="btn btn-quiet danger-text" :disabled="busy" @click="confirmAction('delete', job)">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </ListState>
    <ResourcePager v-if="total > 0" v-model:page="page" :total="total" :last-page="lastPage" :disabled="loading" label="任务列表分页" />
  </section>
  <p v-else class="empty">当前账号没有查看任务列表的权限。</p>

  <section v-if="canLogs && logsOpen" class="panel logs-panel" aria-labelledby="job-logs-title" :aria-busy="logLoading">
    <header class="panel-title">
      <h2 id="job-logs-title" ref="logHeading" tabindex="-1">{{ logFilter ? `${logFilter.name} · 执行日志` : '全部执行日志' }}</h2>
      <div class="row-actions">
        <button type="button" class="btn btn-quiet" :disabled="logLoading" @click="reloadLogs">
          <RefreshCw :size="16" aria-hidden="true" /> 刷新
        </button>
        <button type="button" class="btn btn-quiet" @click="logsOpen = false">收起</button>
      </div>
    </header>
    <ListState :loading="logLoading" :error="logError" :empty="logs.length === 0" empty-text="暂无执行日志。" @retry="reloadLogs">
      <div class="table-wrap">
        <table class="data-table">
          <thead><tr>
            <th scope="col">任务</th><th scope="col">触发方式</th><th scope="col">状态</th>
            <th scope="col">触发时间</th><th scope="col">开始时间</th><th scope="col">结束时间</th><th scope="col">结果</th>
          </tr></thead>
          <tbody>
            <tr v-for="log in logs" :key="log.id">
              <td><strong>{{ log.jobName }}</strong><code class="cell-meta">{{ log.handler }}</code></td>
              <td>{{ log.source === 'MANUAL' ? '手动' : '自动' }}</td>
              <td><StatusChip :enabled="log.status === 'SUCCESS'" :active-label="runLabels[log.status]" :inactive-label="runLabels[log.status]" /></td>
              <td>{{ dateTime.format(log.requestedAt) }}</td>
              <td>{{ log.startedAt === null ? '—' : dateTime.format(log.startedAt) }}</td>
              <td>{{ log.finishedAt === null ? '—' : dateTime.format(log.finishedAt) }}</td>
              <td>{{ log.message ?? '—' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </ListState>
    <ResourcePager v-if="logTotal > 0" v-model:page="logPage" :total="logTotal" :last-page="logLastPage" :disabled="logLoading" label="执行日志分页" />
  </section>

  <EditDialog v-if="dialog" :title="dialogTitle" :busy="busy" @close="dialog = null">
    <form @submit.prevent="submit">
      <fieldset class="job-fields" :disabled="busy">
        <div v-if="dialog.kind === 'edit'" class="form-grid">
          <label class="field"><span>任务名称</span><input v-model.trim="draft.name" maxlength="120" required></label>
          <label class="field">
            <span>任务实现</span>
            <select v-model="draft.handler" required :disabled="handlersLoading || !!handlersError">
              <option disabled value="">{{ handlersLoading ? '正在读取…' : '请选择' }}</option>
              <option v-if="handlers && draft.handler && !handlers.some(item => item.key === draft.handler)" :value="draft.handler" disabled>
                {{ draft.handler }}（已移除）
              </option>
              <option v-for="handler in handlers" :key="handler.key" :value="handler.key">{{ handler.name }}</option>
            </select>
          </label>
          <div v-if="handlersError" class="notice notice-error span-full" role="alert">
            {{ handlersError }} <button class="btn btn-quiet" type="button" @click="reloadHandlers">重试</button>
          </div>
          <label class="field">
            <span>触发器</span>
            <select v-model="draft.kind">
              <option v-for="(label, kind) in scheduleLabels" :key="kind" :value="kind">{{ label }}</option>
            </select>
          </label>
          <label v-if="draft.kind === 'cron' || draft.kind === 'calendar'" class="field">
            <span>时区</span><input v-model.trim="draft.timeZone" maxlength="64" placeholder="Asia/Shanghai" required>
          </label>
          <label v-if="draft.kind === 'cron'" class="field span-full">
            <span>Quartz Cron 表达式</span><input v-model.trim="draft.expression" maxlength="120" placeholder="0 0 9 ? * MON-FRI" required>
          </label>
          <template v-if="draft.kind === 'simple' || draft.kind === 'calendar' || draft.kind === 'daily'">
            <label class="field">
              <span>间隔</span><input v-model.number="draft.interval" type="number" min="1" :max="maxInterval" step="1" required>
            </label>
            <label class="field">
              <span>间隔单位</span>
              <select v-if="draft.kind === 'calendar'" v-model="draft.calendarUnit">
                <option v-for="(label, unit) in calendarUnits" :key="unit" :value="unit">{{ label }}</option>
              </select>
              <select v-else v-model="draft.clockUnit">
                <option v-for="(label, unit) in clockUnits" :key="unit" :value="unit">{{ label }}</option>
              </select>
            </label>
          </template>
          <label v-if="draft.kind === 'simple'" class="field">
            <span>重复次数</span><input v-model.number="draft.repeatCount" type="number" min="0" max="1000000" step="1" placeholder="无限重复">
            <small>0 表示仅执行一次。</small>
          </label>
          <template v-if="draft.kind === 'daily'">
            <div class="field span-full" role="group" aria-labelledby="job-weekdays">
              <span id="job-weekdays">星期</span>
              <div class="row-actions week-options">
                <label v-for="(day, index) in weekdays" :key="day" class="checkline">
                  <input v-model="draft.daysOfWeek" type="checkbox" :value="index + 1"> {{ day }}
                </label>
              </div>
            </div>
            <label class="field"><span>开始时间</span><input v-model="draft.startTime" type="time" required></label>
            <label class="field"><span>结束时间</span><input v-model="draft.endTime" type="time" required></label>
            <small class="field-help span-full">日内时段按服务器时区解释。</small>
          </template>
        </div>
        <p v-else-if="dialog.kind === 'delete'">删除「{{ dialog.job.name }}」将取消尚未执行的手动请求，并保留执行历史。</p>
        <p v-else>立即提交「{{ dialog.job.name }}」的一次执行请求。</p>
        <p v-if="actionError" class="notice notice-error action-error" role="alert">{{ actionError }}</p>
        <div class="form-actions">
          <button type="button" class="btn" @click="dialog = null">取消</button>
          <button type="submit" class="btn btn-primary"
            :disabled="dialog.kind === 'edit' && !handlers?.some(item => item.key === draft.handler)">
            {{ busy ? '正在提交…' : dialog.kind === 'edit' ? '保存' : dialog.kind === 'delete' ? '删除' : '执行' }}
          </button>
        </div>
      </fieldset>
    </form>
  </EditDialog>
</template>

<style scoped>
.job-fields { border: 0; padding: 0; margin: 0; min-width: 0; }
.action-error { margin-top: 1rem; }
.week-options { gap: .75rem; }
.week-options input { width: auto; min-height: auto; }
.danger-text { color: var(--danger); }
.logs-panel { margin-top: 18px; }
</style>
