import {
  MessageCircle,
  FolderOpen,
  BookOpen, Briefcase, ClipboardList, Inbox, KeyRound, LayoutGrid, Megaphone, Network, Shield, Users,
} from '@lucide/vue'
import type { Component } from 'vue'
import type { MenuNode } from '@/api/system'

interface Page {
  path: string
  label: string
  icon: Component
  authority?: string
  view: () => Promise<{ default: Component }>
}

export const inbox: Page = {
  path: '/inbox',
  label: '收件箱',
  icon: Inbox,
  view: () => import('@/views/InboxView.vue'),
}

const systemPages: Page[] = [
  { path: '/users', label: '用户', icon: Users, authority: 'system:user:read', view: () => import('@/views/UsersView.vue') },
  { path: '/roles', label: '角色', icon: Shield, authority: 'system:role:read', view: () => import('@/views/RolesView.vue') },
  { path: '/permissions', label: '权限点', icon: KeyRound, authority: 'system:permission:read', view: () => import('@/views/PermissionsView.vue') },
  { path: '/menus', label: '菜单', icon: LayoutGrid, authority: 'system:menu:read', view: () => import('@/views/MenusView.vue') },
  { path: '/departments', label: '部门', icon: Network, authority: 'system:department:read', view: () => import('@/views/DepartmentsView.vue') },
  { path: '/dictionaries', label: '字典', icon: BookOpen, authority: 'system:dictionary:read', view: () => import('@/views/DictionariesView.vue') },
  { path: '/audits', label: '审计', icon: ClipboardList, authority: 'system:audit:read', view: () => import('@/views/AuditsView.vue') },
  { path: '/positions', label: '岗位', icon: Briefcase, authority: 'system:position:read', view: () => import('@/views/PositionsView.vue') },
  { path: '/announcements', label: '公告管理', icon: Megaphone, authority: 'system:announcement:read', view: () => import('@/views/AnnouncementsView.vue') },
  { path: '/files', label: '文件管理', icon: FolderOpen, authority: 'system:file:read', view: () => import('@/views/FilesView.vue') },
  { path: '/realtime', label: '实时消息', icon: MessageCircle, authority: 'system:message:read', view: () => import('@/views/RealtimeView.vue') },
]

// 扩展页面须先注册固定路由，再通过服务端菜单发布。
const extensionPages: Page[] = []
export const menuPages = [...systemPages, ...extensionPages]
const pagesByPath = new Map(menuPages.map((page) => [page.path, page]))

export interface NavItem {
  id: string
  label: string
  page?: Page
  children: NavItem[]
}

export function getNavigation(nodes: MenuNode[], can: (authority: string) => boolean): NavItem[] {
  const seenPaths = new Set<string>()
  function branch(entries: MenuNode[]) {
    const items: NavItem[] = []
    for (const node of entries) {
      if (!node.enabled || (node.permissionCode && !can(node.permissionCode))) continue
      const page = node.path ? pagesByPath.get(node.path) : undefined
      const allowed = page && (!page.authority || can(page.authority)) && !seenPaths.has(page.path)
      if (page && allowed) seenPaths.add(page.path)

      const children = branch(node.children)
      if (allowed || children.length) {
        items.push({ id: node.id, label: node.name, page: allowed ? page : undefined, children })
      }
    }
    return items
  }
  return branch(nodes)
}

export function findNavigation(items: NavItem[], path: string): NavItem | undefined {
  for (const item of items) {
    if (item.page?.path === path) return item
    const child = findNavigation(item.children, path)
    if (child) return child
  }
}
