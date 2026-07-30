import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发期把后端路径代理到本地 8080：
// /api 业务接口；/auth 登录回调登出；/oauth2 是 Spring Security 的授权发起端点（/auth/login 会 302 过去）。
// 必须用对象形式：字符串简写会默认开启 changeOrigin，把 Host 头改写成 localhost:8080，
// 后端据 Host 展开 redirect_uri 时会拼成 8080，与账号中心登记的 5174 不符而被拒。
const backendProxy = { target: 'http://localhost:8080' }

export default defineConfig({
  plugins: [vue()],
  server: {
    // 固定 5174：与商城前端的 5173 错开，两者可同时开着调试
    port: 5174,
    proxy: {
      '/api': backendProxy,
      '/auth': backendProxy,
      '/oauth2': backendProxy,
    },
  },
})
