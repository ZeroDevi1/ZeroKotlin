<script setup lang="ts">
import { ref } from 'vue'
import { ArrowRight } from '@lucide/vue'
import { useRoute } from 'vue-router'
import { ApiError } from '@/api/client'
import { session, signIn, useAuth } from '@/store/auth'
import BrandMark from '@/components/BrandMark.vue'
import { destination, router } from '@/router'

const route = useRoute()
const username = ref('')
const password = ref('')
const busy = ref(false)
const error = ref('')

async function submit() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    await signIn(username.value.trim(), password.value)
    if (!useAuth().user) {
      error.value = '登录会话已失效，请重试。'
      return
    }
    await router.replace(destination(route.query.next))
  } catch (cause) {
    if (session.active) {
      await router.replace({ path: '/session', query: { next: route.query.next } })
    } else {
      error.value = cause instanceof ApiError && cause.status === 401
        ? '账号或密码有误，或该账号已停用。'
        : cause instanceof Error ? cause.message : '登录失败，请重试。'
    }
  } finally {
    password.value = ''
    busy.value = false
  }
}
</script>

<template>
  <main class="login">
    <div class="login-frame">
      <section class="login-identity" aria-label="工作台">
        <BrandMark />
        <div>
          <div class="identity-symbol" aria-hidden="true"><i class="brand-z"></i></div>
          <h1>平台管理工作台</h1>
        </div>
      </section>
      <section class="login-form" aria-labelledby="login-title">
        <h2 id="login-title">账号登录</h2>
        <p class="login-help">使用管理员分配的账号进入工作台。</p>
        <p v-if="session.expired && !error" class="notice" role="status">登录已失效，请重新登录。</p>
        <form @submit.prevent="submit">
          <label class="field">
            <span>账号</span>
            <input v-model="username" required name="username" autocomplete="username" maxlength="64" autocapitalize="none" spellcheck="false">
          </label>
          <label class="field">
            <span>密码</span>
            <input v-model="password" required type="password" name="password" autocomplete="current-password" maxlength="72">
          </label>
          <p v-if="error" class="notice notice-error" role="alert">{{ error }}</p>
          <button type="submit" class="btn btn-primary login-submit" :disabled="busy">
            {{ busy ? '正在登录…' : '登录' }} <ArrowRight :size="17" aria-hidden="true" />
          </button>
        </form>
      </section>
    </div>
  </main>
</template>
