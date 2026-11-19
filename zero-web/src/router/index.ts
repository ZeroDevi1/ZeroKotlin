import { computed } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import { refreshSession, session, useAuth } from '@/store/auth'
import { findNavigation, getNavigation, inbox, menuPages } from '@/router/navigation'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    private?: boolean
    menu?: boolean
  }
}

export const navigation = computed(() => getNavigation(session.menus, useAuth().can))

function home() {
  return findNavigation(navigation.value, '/users') ? '/users' : inbox.path
}

export const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/login', component: () => import('@/views/LoginView.vue').then((module) => module.default), meta: { title: '登录' } },
    { path: '/session', component: () => import('@/views/SessionView.vue').then((module) => module.default), meta: { title: '连接会话' } },
    { path: '/', component: { render: () => null }, meta: { private: true } },
    ...[inbox, ...menuPages].map((page) => ({
      path: page.path,
      component: () => page.view().then((module) => module.default),
      meta: { private: true, menu: page !== inbox, title: page.label },
    })),
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async (to) => {
  if (to.path === '/session') return session.active ? true : '/login'

  if (session.active) {
    try {
      await refreshSession()
    } catch {
      return { path: '/session', query: { next: to.fullPath } }
    }
  }
  if (to.path === '/login') return useAuth().user ? home() : true
  if (!useAuth().user) return { path: '/login', query: { next: to.fullPath } }
  if (to.path === '/') return home()
  if (to.meta.menu && !findNavigation(navigation.value, to.path)) return inbox.path
})

router.afterEach((to) => {
  const title = findNavigation(navigation.value, to.path)?.label ?? to.meta.title
  document.title = title ? `${title} · ZeroKotlin` : 'ZeroKotlin 管理工作台'
})

export function destination(next: unknown) {
  if (typeof next === 'string' && /^\/(?!\/)[^\\\u0000-\u001f\u007f]*$/.test(next)) {
    const route = router.resolve(next)
    if (route.path === inbox.path || findNavigation(navigation.value, route.path)) return route.fullPath
  }
  return home()
}
