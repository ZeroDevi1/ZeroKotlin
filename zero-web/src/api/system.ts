import { request } from './client'

export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  size: number
}

export interface UserSummary {
  id: string
  username: string
  displayName: string
  departmentId: string | null
  enabled: boolean
}

export interface AssignedRole {
  id: string
  code: string
  name: string
}

export interface UserListEntry {
  user: UserSummary
  roles: AssignedRole[]
}

export interface UserDetails {
  user: UserSummary
  roleIds: string[]
}

export interface OwnProfile {
  username: string
  displayName: string
  email: string | null
  mobile: string | null
}

export const dataScopes = {
  SELF: '仅本人',
  DEPARTMENT: '本部门',
  DEPARTMENT_AND_DESCENDANTS: '本部门及下级',
  ALL: '全部用户',
} as const

export type DataScope = keyof typeof dataScopes

export interface RoleSummary extends AssignedRole {
  dataScope: DataScope
  enabled: boolean
}

export interface RoleDetails {
  role: RoleSummary
  permissionCodes: string[]
}

export interface RoleMenus {
  mode: 'LEGACY' | 'DIRECT'
  menuIds: string[]
}

export interface DepartmentNode {
  id: string
  name: string
  sortOrder: number
  children: DepartmentNode[]
}

export interface PermissionSummary {
  code: string
  name: string
  enabled: boolean
}

export interface MenuNode {
  id: string
  name: string
  path: string | null
  permissionCode: string | null
  sortOrder: number
  enabled: boolean
  children: MenuNode[]
}

export interface DictionarySummary {
  id: string
  code: string
  name: string
  enabled: boolean
}

export interface DictionaryItem {
  id: string
  value: string
  label: string
  sortOrder: number
  enabled: boolean
}

export function dictionaryValues(codes: string[], signal?: AbortSignal) {
  return request<Record<string, DictionaryItem[]>>(apiPath('/dictionaries/by-codes/items'), {
    method: 'POST', body: { codes }, signal,
  })
}

export interface LoginAuditEntry {
  id: string
  userId: string | null
  username: string | null
  successful: boolean
  remoteAddress: string
  occurredAt: number
  ipRegion: string | null
}

export interface OperationAuditEntry {
  id: string
  actorId: string
  action: string
  targetId: string
  occurredAt: number
}

export interface RequestAuditEntry {
  id: string
  actorId: string | null
  action: string
  httpMethod: string
  routePattern: string
  httpStatus: number
  durationMs: number
  exceptionType: string | null
  remoteAddress: string
  ipRegion: string | null
  userAgent: string | null
  occurredAt: number
}

export const adminRoleId = 'system-admin'
export const codePattern = '[a-z0-9][a-z0-9:_\\-]{0,119}'
export const usernamePattern = '[A-Za-z0-9][A-Za-z0-9._\\-]{2,63}'

export function apiPath(path: string, params: Record<string, string | number | boolean | null | undefined> = {}) {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value != null && value !== '') query.set(key, String(value))
  }
  return `/api/system${path}${query.size ? `?${query}` : ''}`
}

export async function fetchRoles(signal: AbortSignal) {
  const rows: RoleSummary[] = []
  const size = 100
  for (let page = 1; ; page++) {
    const result = await request<PageResult<RoleSummary>>(apiPath('/roles', { page, size }), { signal })
    rows.push(...result.items)
    if (result.items.length < size || page * size >= result.total) return rows
  }
}

interface TreeNode<T> {
  id: string
  name: string
  children: T[]
}

export function flattenTree<T extends TreeNode<T>>(tree: T[]) {
  const rows: Array<{ node: T; ancestors: string[]; parentId: string | null; trail: string }> = []
  function visit(nodes: T[], ancestors: string[], names: string[]) {
    for (const node of nodes) {
      const nextNames = [...names, node.name]
      rows.push({ node, ancestors, parentId: ancestors.at(-1) ?? null, trail: nextNames.join(' / ') })
      visit(node.children, [...ancestors, node.id], nextNames)
    }
  }
  visit(tree, [], [])
  return rows
}

export function passwordError(password: string): string | undefined {
  if (Array.from(password).length < 12 || new TextEncoder().encode(password).length > 72) {
    return '密码至少为 12 个字符，最多为 72 个 UTF-8 字节。'
  }
}

const dateTime = new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'medium', hour12: false })

export function formatTime(timestamp: number) {
  return dateTime.format(timestamp)
}
