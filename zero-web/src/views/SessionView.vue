<script setup lang="ts">
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import { refreshSession, session, signOut } from '@/store/auth'
import BrandMark from '@/components/BrandMark.vue'
import { destination, router } from '@/router'

const route = useRoute()
const busy = ref(false)
const error = ref('')

async function run(action: 'retry' | 'logout') {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    if (action === 'logout') await signOut()
    else await refreshSession()
    await router.replace(session.active ? destination(route.query.next) : '/login')
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '请求失败，请重试。'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="standalone">
    <section class="panel recovery">
      <BrandMark />
      <h1>暂时无法连接会话</h1>
      <p>工作台需要从服务端读取当前账号的权限和菜单。</p>
      <p v-if="error" class="notice notice-error" role="alert">{{ error }}</p>
      <div class="toolbar">
        <button class="btn btn-primary" type="button" :disabled="busy" @click="run('retry')">重试</button>
        <button class="btn" type="button" :disabled="busy" @click="run('logout')">退出</button>
      </div>
    </section>
  </main>
</template>
