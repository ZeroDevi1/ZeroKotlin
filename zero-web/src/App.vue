<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { LogOut, Menu, PanelTop, X } from '@lucide/vue'
import { useRoute } from 'vue-router'
import { session, signOut, useAuth } from '@/store/auth'
import BrandMark from '@/components/BrandMark.vue'
import NavBranch from '@/layouts/NavBranch.vue'
import { findNavigation, inbox } from '@/router/navigation'
import { navigation, router } from '@/router'

const auth = useAuth()
const route = useRoute()
const navOpen = ref(false)
const navToggle = ref<HTMLButtonElement>()
const sidebar = ref<HTMLElement>()
const main = ref<HTMLElement>()
const exiting = ref(false)
const exitError = ref('')
const title = computed(() => findNavigation(navigation.value, route.path)?.label ?? route.meta.title)
const workspace = computed(() => auth.user && route.meta.private)

watch(() => session.active, (active) => {
  if (!active && (route.meta.private || route.path === '/session')) {
    void router.replace({ path: '/login', query: { next: route.fullPath } })
  }
})

watch(navigation, () => {
  if (session.active && route.meta.menu && !findNavigation(navigation.value, route.path)) {
    void router.replace(inbox.path)
  }
})

watch(() => route.path, async (path, previous) => {
  navOpen.value = false
  exitError.value = ''
  if (path !== previous && previous !== '/') {
    await nextTick()
    main.value?.focus()
  }
})

function closeNavigation() {
  navOpen.value = false
  navToggle.value?.focus()
}

async function toggleNavigation() {
  if (navOpen.value) {
    closeNavigation()
  } else {
    navOpen.value = true
    await nextTick()
    sidebar.value?.querySelector<HTMLElement>('a[href]')?.focus()
  }
}

function navigate(event: MouseEvent) {
  if (event.target instanceof Element && event.target.closest('a[href]') && navOpen.value) {
    navOpen.value = false
    main.value?.focus()
  }
}

async function logout() {
  exiting.value = true
  exitError.value = ''
  try {
    await signOut()
    await router.replace('/login')
  } catch (error) {
    exitError.value = error instanceof Error ? error.message : '退出失败，请重试。'
  } finally {
    exiting.value = false
  }
}
</script>

<template>
  <div v-if="workspace" class="workspace" @keydown.esc="navOpen && closeNavigation()">
    <a href="#main-content" class="skip-link">跳至内容</a>
    <aside id="workspace-navigation" ref="sidebar" class="sidebar" :class="{ 'sidebar-open': navOpen }">
      <div class="sidebar-brand"><BrandMark /></div>
      <nav aria-label="工作台导航" @click="navigate">
        <div class="nav-label">个人</div>
        <RouterLink :to="inbox.path" class="nav-link">
          <component :is="inbox.icon" :size="17" aria-hidden="true" /> {{ inbox.label }}
        </RouterLink>
        <NavBranch v-if="navigation.length" :items="navigation" class="system-navigation" />
      </nav>
    </aside>

    <div class="work-area">
      <header class="topbar">
        <div class="topbar-context">
          <button ref="navToggle" class="btn btn-quiet nav-toggle" type="button"
            aria-controls="workspace-navigation" :aria-expanded="navOpen" @click="toggleNavigation">
            <X v-if="navOpen" :size="19" aria-hidden="true" />
            <Menu v-else :size="19" aria-hidden="true" />
            <span class="sr-only">导航</span>
          </button>
          <PanelTop class="context-icon" :size="17" aria-hidden="true" />
          <span>工作台</span><span aria-hidden="true" class="context-divider">/</span><strong>{{ title }}</strong>
        </div>
        <div v-if="auth.user" class="account">
          <span class="avatar" aria-hidden="true">{{ Array.from(auth.user.displayName || auth.user.username)[0] }}</span>
          <span class="account-name" :title="auth.user.username">{{ auth.user.displayName }}</span>
          <button class="btn btn-quiet logout" type="button" :disabled="exiting" @click="logout">
            <LogOut :size="16" aria-hidden="true" /> <span>{{ exiting ? '退出中' : '退出' }}</span>
          </button>
        </div>
      </header>
      <main id="main-content" ref="main" class="workspace-content" tabindex="-1">
        <p v-if="exitError" class="notice notice-error" role="alert">{{ exitError }}</p>
        <RouterView />
      </main>
    </div>
  </div>
  <RouterView v-else-if="route.matched.length && !route.meta.private" />
  <main v-else class="standalone"><p class="notice" role="status">正在连接工作台…</p></main>
</template>
