import vue from '@vitejs/plugin-vue'
import { defineConfig, loadEnv } from 'vite'

const contextPath = ''

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'ZERO_WEB_')
  const proxy = {
    [`^${contextPath}/(?:api(?:/|[?]|$)|health(?:[?]|$))`]: {
      target: env.ZERO_WEB_API_TARGET || 'http://127.0.0.1:8080',
      changeOrigin: true,
    },
  }

  return {
    base: `${contextPath}/`,
    plugins: [vue()],
    server: { host: '127.0.0.1', proxy },
    preview: { host: '127.0.0.1', proxy },
  }
})
