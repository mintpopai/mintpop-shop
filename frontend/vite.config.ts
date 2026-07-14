import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发期将后端路径代理到本地 8080，避免跨域：
// /api 业务接口；/auth 登录回调登出；/oauth2 是 Spring Security 的授权发起端点（/auth/login 会 302 过去）。
// 必须用对象形式：字符串简写会默认开启 changeOrigin，把 Host 头改写成 localhost:8080，
// 后端据 Host 展开 {baseUrl} 时会把 redirect_uri 拼成 8080，与账号中心登记的 5173 不符而被拒。
const backendProxy = { target: 'http://localhost:8080' }

export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': backendProxy,
      '/auth': backendProxy,
      '/oauth2': backendProxy,
    },
  },
})
