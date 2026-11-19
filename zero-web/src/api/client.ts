export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  signal?: AbortSignal
}

export class ApiError extends Error {
  constructor(readonly status: number, message: string) {
    super(message)
    this.name = 'ApiError'
  }
}

let accessToken: string | undefined
let unauthorized: () => void = () => {}

export function setAccessToken(token?: string) {
  accessToken = token
}

export function onUnauthorized(handler: () => void) {
  unauthorized = handler
}

const statusMessages: Record<number, string> = {
  400: '请求参数有误，请检查输入。',
  401: '登录已失效，请重新登录。',
  403: '没有权限执行此操作。',
  404: '记录不存在或超出授权范围。',
  409: '当前记录与请求冲突，请检查后重试。',
  429: '请求过于频繁，请稍后重试。',
}

function problemText(payload: unknown): string | undefined {
  if (!payload || typeof payload !== 'object') return
  for (const key of ['detail', 'message', 'title'] as const) {
    const value = (payload as Record<string, unknown>)[key]
    if (typeof value === 'string' && value.trim()) return value
  }
}

async function send(path: string, options: RequestOptions, accept: string): Promise<Response> {
  const url = new URL(path, window.location.origin)
  if (!path.startsWith('/api/') || url.origin !== window.location.origin || !url.pathname.startsWith('/api/')) {
    throw new Error('API 路径必须位于同源的 /api/ 下。')
  }

  const token = url.pathname === '/api/auth/login' ? undefined : accessToken
  const headers = new Headers({ Accept: accept })
  if (token) headers.set('Authorization', `Bearer ${token}`)
  let body: BodyInit | undefined
  if (options.body instanceof FormData) {
    body = options.body
  } else if (options.body !== undefined) {
    headers.set('Content-Type', 'application/json')
    body = JSON.stringify(options.body)
  }

  let response: Response
  try {
    response = await fetch(url, {
      method: options.method ?? 'GET',
      headers,
      body,
      signal: options.signal,
      cache: 'no-store',
      redirect: 'error',
    })
  } catch (error) {
    if (options.signal?.aborted) throw error
    throw new ApiError(0, '无法连接服务，请检查网络后重试。')
  }

  if (!response.ok) {
    if (response.status === 401 && token && token === accessToken) unauthorized()
    const problem = response.headers.get('Content-Type')?.includes('json')
      ? await response.json().catch(() => undefined)
      : undefined
    const fallback = statusMessages[response.status] ?? `服务请求失败（HTTP ${response.status}）。`
    throw new ApiError(response.status, response.status >= 500 ? fallback : problemText(problem) ?? fallback)
  }

  return response
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await send(path, options, 'application/json, application/problem+json')
  if (response.status === 204 || response.status === 205) return undefined as T
  if (!response.headers.get('Content-Type')?.includes('json')) {
    throw new ApiError(response.status, '服务返回了无法识别的响应。')
  }
  return await response.json() as T
}

/** 下载同源 API 的二进制响应，沿用当前会话和统一的错误处理。 */
export async function requestBlob(path: string, options: RequestOptions = {}): Promise<Blob> {
  const response = await send(path, options, '*/*')
  return response.blob()
}
