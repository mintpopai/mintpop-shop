import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发期将 /api 代理到本地后端，避免跨域
export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
