import { createApp } from 'vue'
// 字体自托管（Fontsource），禁止外链 Google Fonts
import '@fontsource/inter/400.css'
import '@fontsource/inter/500.css'
import '@fontsource/inter/600.css'
import '@fontsource/fredoka/600.css'
import './styles/base.css'
import App from './App.vue'
import { router } from './router'
import { loadCurrentUser } from './auth'

// 先取登录态再挂载，避免页面闪烁（失败也照常挂载，由 App.vue 决定渲染登录引导还是无权限页）
loadCurrentUser().finally(() => {
  createApp(App).use(router).mount('#app')
})
