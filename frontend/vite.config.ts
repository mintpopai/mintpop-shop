import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发期将后端路径代理到本地 8080，避免跨域：
// /api 业务接口；/auth 登录回调登出；/oauth2 是 Spring Security 的授权发起端点（/auth/login 会 302 过去）
export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/auth': 'http://localhost:8080',
      '/oauth2': 'http://localhost:8080',
    },
  },
})
