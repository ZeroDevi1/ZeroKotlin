import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig, loadEnv } from 'vite'

const contextPath = ''

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'ZERO_WEB_')
  const target = env.ZERO_WEB_API_TARGET || 'http://127.0.0.1:8080'
  const proxy = {
    [`^${contextPath}/api/realtime/ws$`]: {
      target,
      ws: true,
      // 保留浏览器访问的 Host，供后端执行同源检查。
      changeOrigin: false,
    },
    [`^${contextPath}/api(?:/|$)`]: {
      target,
      changeOrigin: true,
    },
  }

  return {
    base: `${contextPath}/`,
    plugins: [vue()],
    resolve: {
      alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
    },
    server: { proxy },
    preview: { proxy },
  }
})
