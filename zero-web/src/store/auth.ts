import { reactive } from 'vue'
import { ApiError, onUnauthorized, request, setAccessToken } from '@/api/client'
import type { MenuNode } from '@/api/system'

interface CurrentUser {
  id: string
  username: string
  displayName: string
  departmentId: string | null
  administrator: boolean
  permissions: Record<string, string>
}

interface SessionToken {
  accessToken: string
  expiresAt: number
}

export interface LoginCaptcha {
  id: string
  imageBase64: string
  expiresAt: number
}

const storageKey = 'zerokotlin.session'

function loadToken(): SessionToken | undefined {
  try {
    const stored = JSON.parse(sessionStorage.getItem(storageKey) ?? 'null')
    if (typeof stored?.accessToken === 'string' && Number.isFinite(stored.expiresAt) && stored.expiresAt > Date.now()) {
      return stored
    }
    sessionStorage.removeItem(storageKey)
  } catch {
    // 禁止 Web Storage 时仍可使用当前页面的会话。
  }
}

let token: SessionToken | undefined
let expiresTimer: number | undefined
let pending: { token: SessionToken; promise: Promise<void> } | undefined

const auth = reactive({
  user: null as CurrentUser | null,
  can(authority: string) {
    return !!auth.user && Object.hasOwn(auth.user.permissions, authority)
  },
})

export const session = reactive({
  active: false,
  menus: [] as MenuNode[],
  expired: false,
})

export function useAuth() {
  return auth
}

export function sessionAuthorization(): string | undefined {
  if (token && token.expiresAt > Date.now()) return `Bearer ${token.accessToken}`
}

function clearSession(expired = false) {
  storeToken(undefined)
  auth.user = null
  session.menus = []
  session.expired = expired
}

function storeToken(value: SessionToken | undefined) {
  token = value
  setAccessToken(value?.accessToken)
  session.active = !!value
  window.clearTimeout(expiresTimer)
  if (value) {
    expiresTimer = window.setTimeout(() => clearSession(true), Math.max(0, value.expiresAt - Date.now()))
  }
  try {
    if (value) sessionStorage.setItem(storageKey, JSON.stringify(value))
    else sessionStorage.removeItem(storageKey)
  } catch {
    // 内存会话不依赖保存成功。
  }
}

storeToken(loadToken())
onUnauthorized(() => clearSession(true))

export function refreshSession(): Promise<void> {
  const current = token
  if (!current) return Promise.resolve()
  if (pending?.token === current) return pending.promise

  const promise = Promise.all([
    request<CurrentUser>('/api/auth/me'),
    request<MenuNode[]>('/api/auth/me/menus'),
  ]).then(([user, menus]) => {
    if (token === current) {
      auth.user = user
      session.menus = menus
    }
  }).catch((error: unknown) => {
    if (error instanceof ApiError && error.status === 401) return
    throw error
  }).finally(() => {
    if (pending?.token === current) pending = undefined
  })
  pending = { token: current, promise }
  return promise
}

export function getLoginCaptcha(signal: AbortSignal) {
  return request<LoginCaptcha | undefined>('/api/auth/captcha', { signal })
}

export async function signIn(username: string, password: string, captcha?: { id: string; code: string }) {
  const value = await request<SessionToken>('/api/auth/login', {
    method: 'POST',
    body: { username, password, captchaId: captcha?.id, captchaCode: captcha?.code },
  })
  session.expired = false
  storeToken(value)
  await refreshSession()
}

export async function changeOwnPassword(oldPassword: string, newPassword: string) {
  const current = token
  await request<void>('/api/auth/me/password', { method: 'PUT', body: { oldPassword, newPassword } })
  if (current === token) clearSession()
}

export async function signOut() {
  try {
    await request<void>('/api/auth/logout', { method: 'POST' })
    clearSession()
  } catch (error) {
    if (!(error instanceof ApiError && error.status === 401)) throw error
    clearSession()
  }
}
