// defineConfig 取自 vitest/config（vite 版本的超集，多带 test 字段类型）：
// 测试与开发构建共用同一份插件配置，不另起 vitest.config.ts 造成 vue 插件重复定义
import { defineConfig } from 'vitest/config'
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
  test: {
    // 被测代码大量触碰 localStorage / navigator / location / document，一律跑在 DOM 环境
    environment: 'happy-dom',
    // 用例源码与被测模块同目录，后缀 .test.ts 区分
    include: ['src/**/*.test.ts'],
    // 固定时区：datetime.ts 在模块加载时就建好 Intl 格式化器，不钉 TZ 则断言随机器时区漂移
    env: { TZ: 'UTC' },
    // 每个用例前复位 mock 调用记录，避免跨用例互相看见对方的调用
    clearMocks: true,
  },
})
