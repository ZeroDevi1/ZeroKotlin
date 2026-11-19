<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { ArrowRight } from '@lucide/vue'
import { useRoute } from 'vue-router'
import { ApiError } from '@/api/client'
import { getLoginCaptcha, session, signIn, useAuth, type LoginCaptcha } from '@/store/auth'
import BrandMark from '@/components/BrandMark.vue'
import { destination, router } from '@/router'

const route = useRoute()
const username = ref('')
const password = ref('')
const busy = ref(false)
const error = ref('')
const captcha = ref<LoginCaptcha | null>()
const captchaCode = ref('')
const captchaError = ref('')
const captchaRequest = new AbortController()

onMounted(() => void refreshCaptcha())
onUnmounted(() => captchaRequest.abort())

async function refreshCaptcha() {
  if (captchaRequest.signal.aborted) return
  captcha.value = undefined
  captchaCode.value = ''
  captchaError.value = ''
  try {
    captcha.value = await getLoginCaptcha(captchaRequest.signal) ?? null
  } catch (cause) {
    if (!captchaRequest.signal.aborted) {
      captchaError.value = cause instanceof Error ? cause.message : '无法获取验证码，请重试。'
    }
  }
}

async function submit() {
  if (busy.value || captcha.value === undefined) return
  const challenge = captcha.value ? { id: captcha.value.id, code: captchaCode.value } : undefined
  busy.value = true
  error.value = ''
  try {
    await signIn(username.value.trim(), password.value, challenge)
    if (!useAuth().user) {
      error.value = '登录会话已失效，请重试。'
      if (challenge) await refreshCaptcha()
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
      if (challenge && !(cause instanceof ApiError && cause.status === 429)) await refreshCaptcha()
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
          <div v-if="captcha" class="field">
            <span><label for="login-captcha">图形验证码</label></span>
            <div class="captcha-controls">
              <input id="login-captcha" v-model="captchaCode" required name="captchaCode" autocomplete="off" minlength="6"
                maxlength="6" pattern="[A-Za-z0-9]{6}" autocapitalize="characters" spellcheck="false">
              <button type="button" class="btn captcha-image" aria-label="换一张验证码" :disabled="busy" @click="refreshCaptcha">
                <img :src="`data:image/png;base64,${captcha.imageBase64}`" width="160" height="46" alt="图形验证码">
              </button>
            </div>
            <small>看不清时点击图片换一张。</small>
          </div>
          <p v-if="captcha === undefined && !captchaError" class="field-help" role="status">正在获取验证码状态…</p>
          <div v-else-if="captchaError">
            <p class="notice notice-error" role="alert">{{ captchaError }}</p>
            <button type="button" class="btn btn-quiet" @click="refreshCaptcha">重新获取验证码</button>
          </div>
          <p v-if="error" class="notice notice-error" role="alert">{{ error }}</p>
          <button type="submit" class="btn btn-primary login-submit" :disabled="busy || captcha === undefined">
            {{ busy ? '正在登录…' : '登录' }} <ArrowRight :size="17" aria-hidden="true" />
          </button>
        </form>
      </section>
    </div>
  </main>
</template>

<style scoped>
.captcha-controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.captcha-controls input { flex: 1 1 6rem; }
.captcha-image { padding: 0; overflow: hidden; }
.captcha-image img { display: block; }
</style>
