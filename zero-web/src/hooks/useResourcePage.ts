import { computed, onScopeDispose, ref, shallowRef, watch } from 'vue'
import { request } from '@/api/client'

export type PageResult<Item> = {
  items: Item[]
  total: number
  page: number
  size: number
}

export type Position = {
  id: string
  code: string
  name: string
  enabled: boolean
  sortOrder: number
}

export type AnnouncementStatus = 'DRAFT' | 'PUBLISHED' | 'REVOKED'

export type Announcement = {
  id: string
  title: string
  body: string
  status: AnnouncementStatus
  createdAt: number
  updatedAt: number
  publishedAt: number | null
}

export type InboxItem = {
  id: string
  title: string
  body: string
  publishedAt: number
  readAt: number | null
}

const dateTime = new Intl.DateTimeFormat('zh-CN', {
  dateStyle: 'medium',
  timeStyle: 'short',
})

export const formatTime = (timestamp: number) => dateTime.format(timestamp)

export function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

export function useResourcePage<Item>(
  getPath: (page: number, size: number) => string | null,
  fallbackError: string,
  size = 20,
) {
  const items = shallowRef<Item[]>([])
  const total = ref(0)
  const page = ref(1)
  const lastPage = computed(() => Math.max(1, Math.ceil(total.value / size)))
  const loading = ref(false)
  const error = ref('')
  const revision = ref(0)
  let disposed = false

  watch([() => getPath(page.value, size), revision], async ([path, version], _, onCleanup) => {
    items.value = []
    error.value = ''
    loading.value = path !== null
    if (path === null) {
      total.value = 0
      return
    }

    const controller = new AbortController()
    const isCurrent = () => !controller.signal.aborted && version === revision.value && path === getPath(page.value, size)
    onCleanup(() => controller.abort())
    try {
      const result = await request<PageResult<Item>>(path, { signal: controller.signal })
      if (!isCurrent()) return
      total.value = result.total
      if (page.value > lastPage.value) {
        page.value = lastPage.value
        return
      }
      items.value = result.items
    } catch (cause) {
      if (isCurrent()) error.value = errorMessage(cause, fallbackError)
    } finally {
      if (isCurrent()) loading.value = false
    }
  }, { immediate: true })

  onScopeDispose(() => { disposed = true })

  function reload() {
    if (!disposed) revision.value++
  }

  return { items, total, page, lastPage, loading, error, reload }
}
