import { computed, onScopeDispose, ref, shallowRef, watch, type WatchSource } from 'vue'
import { request } from '@/api/client'
import type { PageResult } from '@/api/system'

export function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : '请求失败，请重试。'
}

export function useQuery<T>(load: (signal: AbortSignal) => Promise<T | null>, key: WatchSource<unknown> = () => true) {
  const data = shallowRef<T | null>(null)
  const error = ref('')
  const loading = ref(false)
  let controller: AbortController | undefined
  let disposed = false

  async function reload() {
    if (disposed) return
    controller?.abort()
    const current = new AbortController()
    controller = current
    data.value = null
    error.value = ''
    loading.value = true
    try {
      const response = await load(current.signal)
      if (!current.signal.aborted) data.value = response
    } catch (cause) {
      if (!current.signal.aborted) error.value = errorMessage(cause)
    } finally {
      if (!current.signal.aborted) loading.value = false
    }
  }

  watch(key, () => void reload(), { immediate: true })
  onScopeDispose(() => {
    disposed = true
    controller?.abort()
  })
  return { data, error, loading, reload }
}

export function useResource<T>(url: () => string | null) {
  return useQuery<T>((signal) => {
    const path = url()
    return path ? request<T>(path, { signal }) : Promise.resolve(null)
  }, url)
}

export function usePage<T>(path: (page: number, size: number) => string | null) {
  const page = ref(1)
  const size = ref(20)
  const result = useResource<PageResult<T>>(() => path(page.value, size.value))
  const total = computed(() => result.data.value?.total ?? 0)

  function setSize(value: number) {
    page.value = 1
    size.value = value
  }

  return { ...result, page, size, total, setSize }
}

export function useAction() {
  const busy = ref(false)
  const error = ref('')

  async function run(action: () => Promise<void>) {
    if (busy.value) return false
    busy.value = true
    error.value = ''
    try {
      await action()
      return true
    } catch (cause) {
      error.value = errorMessage(cause)
      return false
    } finally {
      busy.value = false
    }
  }

  return { busy, error, run }
}
