<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { request } from '@/api/client'
import { passwordError, type OwnProfile } from '@/api/system'
import ListState from '@/components/ListState.vue'
import { useAction, useResource } from '@/hooks/useQueries'
import { changeOwnPassword, refreshSession } from '@/store/auth'

const profilePath = '/api/auth/me/profile'
const { data: profile, loading, error, reload } = useResource<OwnProfile>(() => profilePath)
const details = reactive({ displayName: '', email: '', mobile: '' })
const passwords = reactive({ old: '', next: '', confirm: '' })
const saved = ref(false)
const saveProfile = useAction()
const savePassword = useAction()

function fill(profile: OwnProfile) {
  Object.assign(details, { displayName: profile.displayName, email: profile.email ?? '', mobile: profile.mobile ?? '' })
}

watch(profile, (value) => { if (value) fill(value) })

async function updateProfile() {
  saved.value = false
  const completed = await saveProfile.run(async () => {
    const updated = await request<OwnProfile>(profilePath, {
      method: 'PUT',
      body: { displayName: details.displayName, email: details.email || null, mobile: details.mobile || null },
    })
    fill(updated)
  })
  if (completed) {
    saved.value = true
    await refreshSession().catch(() => undefined)
  }
}

async function updatePassword() {
  const completed = await savePassword.run(async () => {
    const invalid = passwordError(passwords.next)
    if (invalid) throw new Error(invalid)
    if (passwords.next !== passwords.confirm) throw new Error('两次输入的新密码不一致。')
    await changeOwnPassword(passwords.old, passwords.next)
  })
  if (completed) Object.assign(passwords, { old: '', next: '', confirm: '' })
}
</script>

<template>
  <header class="page-header"><h1>个人中心</h1></header>
  <section class="panel" aria-labelledby="profile-title">
    <header class="panel-title"><h2 id="profile-title">个人资料</h2></header>
    <ListState :loading="loading" :error="error" @retry="reload">
      <form v-if="profile" class="profile-form" @submit.prevent="updateProfile">
        <div class="form-grid">
          <label class="field"><span>账号</span><input :value="profile.username" readonly autocomplete="username"></label>
          <label class="field">
            <span>显示名</span><input v-model.trim="details.displayName" required maxlength="120" autocomplete="nickname">
          </label>
          <label class="field">
            <span>邮箱</span><input v-model.trim="details.email" type="email" maxlength="254" autocomplete="email">
          </label>
          <label class="field">
            <span>手机号</span>
            <input v-model.trim="details.mobile" type="tel" maxlength="16" :pattern="'\\+?[0-9]{6,15}'" autocomplete="tel">
            <small>可带国际前缀 +。</small>
          </label>
          <p v-if="saveProfile.error.value" class="notice notice-error span-full" role="alert">{{ saveProfile.error.value }}</p>
          <p v-else-if="saved" class="notice notice-success span-full" role="status">资料已保存。</p>
        </div>
        <div class="form-actions">
          <button class="btn btn-primary" type="submit" :disabled="saveProfile.busy.value || savePassword.busy.value">
            {{ saveProfile.busy.value ? '保存中…' : '保存资料' }}
          </button>
        </div>
      </form>
    </ListState>
  </section>

  <section class="panel" aria-labelledby="profile-password-title">
    <header class="panel-title"><h2 id="profile-password-title">修改密码</h2></header>
    <form class="profile-form" @submit.prevent="updatePassword">
      <div class="form-grid">
        <label class="field span-full">
          <span>旧密码</span>
          <input v-model="passwords.old" type="password" required maxlength="72" autocomplete="current-password">
        </label>
        <label class="field">
          <span>新密码</span><input v-model="passwords.next" type="password" required maxlength="72" autocomplete="new-password">
          <small>至少 12 个字符，最多 72 个 UTF-8 字节。</small>
        </label>
        <label class="field">
          <span>确认新密码</span><input v-model="passwords.confirm" type="password" required maxlength="72" autocomplete="new-password">
        </label>
        <p v-if="savePassword.error.value" class="notice notice-error span-full" role="alert">{{ savePassword.error.value }}</p>
      </div>
      <div class="form-actions">
        <p class="muted">修改后所有设备均需重新登录。</p>
        <button class="btn btn-primary" type="submit" :disabled="saveProfile.busy.value || savePassword.busy.value">
          {{ savePassword.busy.value ? '修改中…' : '修改密码' }}
        </button>
      </div>
    </form>
  </section>
</template>

<style scoped>
.profile-form { padding: 0 20px 20px; }
.profile-form .form-actions { align-items: center; }
</style>
