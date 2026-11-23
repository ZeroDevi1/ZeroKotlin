<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { baseUrl, getHealth, sendGreeting } from './api'

const projectName = document.title
const health = ref<Awaited<ReturnType<typeof getHealth>>>()
const checking = ref(false)
const healthError = ref('')
const name = ref('')
const greeting = ref('')
const submitting = ref(false)
const greetingError = ref('')
const healthy = computed(() => !checking.value && health.value?.status === 'UP')

async function checkHealth() {
  if (checking.value) return
  checking.value = true
  healthError.value = ''
  try {
    health.value = await getHealth()
  } catch (error) {
    health.value = undefined
    healthError.value = error instanceof Error ? error.message : '请检查后端是否已启动。'
  } finally {
    checking.value = false
  }
}

async function greet() {
  const submittedName = name.value.trim()
  if (!submittedName || submitting.value) return

  submitting.value = true
  greetingError.value = ''
  greeting.value = ''
  try {
    greeting.value = (await sendGreeting(submittedName)).message
  } catch (error) {
    greetingError.value = error instanceof Error ? error.message : '请稍后重试。'
  } finally {
    submitting.value = false
  }
}

onMounted(checkHealth)
</script>

<template>
  <main class="workspace">
    <header class="page-heading">
      <p class="context">{{ baseUrl }}</p>
      <h1>{{ projectName }}</h1>
    </header>

    <div class="panels">
      <section class="panel" aria-labelledby="health-heading">
        <h2 id="health-heading">后端状态</h2>
        <code>GET {{ baseUrl }}health</code>
        <p class="status" :class="{ online: healthy }" role="status">
          {{ checking ? '检查中' : healthy ? '运行正常' : '服务不可用' }}
        </p>
        <dl v-if="health">
          <dt>服务名</dt>
          <dd>{{ health.name }}</dd>
        </dl>
        <p v-if="healthError" class="error" role="alert">{{ healthError }}</p>
        <button type="button" class="secondary" :disabled="checking" @click="checkHealth">重新检查</button>
      </section>

      <section class="panel" aria-labelledby="greeting-heading">
        <h2 id="greeting-heading">发送问候</h2>
        <code>POST {{ baseUrl }}api/hello</code>
        <form @submit.prevent="greet">
          <label for="name">姓名</label>
          <div class="form-actions">
            <input id="name" v-model="name" required maxlength="80" autocomplete="name" placeholder="例如 Lin">
            <button :disabled="submitting || !name.trim()">{{ submitting ? '发送中…' : '发送' }}</button>
          </div>
        </form>
        <p v-if="greeting" class="response" role="status">{{ greeting }}</p>
        <p v-if="greetingError" class="error" role="alert">{{ greetingError }}</p>
      </section>
    </div>
  </main>
</template>
