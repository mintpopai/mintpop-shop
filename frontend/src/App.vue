<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { updateMyLocale } from './api'
import { currentUser, gotoLogin, gotoLogout } from './auth'
import { showToast, toast } from './toast'
import { locale, setLocale, t, type AppLocale } from './i18n'

const menuOpen = ref(false)

/** 联系方式页在官网（mintpop.ai），按当前语言指向对应路径 */
const contactUrl = locale === 'zh-CN' ? 'https://mintpop.ai/zh/contact/' : 'https://mintpop.ai/contact/'

/** 中英互切：登录用户先把偏好写到服务端（失败不挡切换），再写本地偏好并整页刷新 */
async function toggleLocale() {
  const next: AppLocale = locale === 'zh-CN' ? 'en-US' : 'zh-CN'
  // 登录用户把偏好写到服务端；失败不挡切换（本地偏好照样生效）
  if (currentUser.value) {
    await updateMyLocale(next).catch(() => undefined)
  }
  setLocale(next)
}

onMounted(() => {
  // OIDC 握手失败会回跳 ?login_error=1：提示后清掉参数
  const params = new URLSearchParams(window.location.search)
  if (params.get('login_error')) {
    showToast('error', t('app.loginFailed'))
    params.delete('login_error')
    const query = params.toString()
    history.replaceState(null, '', window.location.pathname + (query ? `?${query}` : ''))
  }
})
</script>

<template>
  <header class="header">
    <div class="header-left">
      <RouterLink to="/" class="wordmark-link">
        <h1 class="wordmark">
          <img
            class="wordmark-img"
            src="https://standards.mintpop.ai/assets/brand/wordmark/mintpop-wordmark-dark.png"
            alt="MintPop"
          />
          <span class="wordmark-sub">Shop</span>
        </h1>
      </RouterLink>
      <nav class="main-nav">
        <RouterLink to="/" class="nav-item">{{ $t('app.shop') }}</RouterLink>
        <RouterLink to="/orders" class="nav-item">{{ $t('app.myOrders') }}</RouterLink>
      </nav>
    </div>

    <nav class="auth-area">
      <a class="contact-link" :href="contactUrl" target="_blank" rel="noopener">
        {{ $t('app.contact') }}
      </a>
      <button type="button" class="lang-btn" @click="toggleLocale">
        {{ locale === 'zh-CN' ? 'EN' : '中文' }}
      </button>
      <button v-if="!currentUser" type="button" class="login-btn" @click="gotoLogin">
        {{ $t('app.login') }}
      </button>
      <div v-else class="user-menu">
        <button type="button" class="user-trigger" @click="menuOpen = !menuOpen">
          <img
            v-if="currentUser.avatarUrl"
            class="avatar"
            :src="currentUser.avatarUrl"
            alt=""
          />
          <span v-else class="avatar avatar-fallback">
            {{ (currentUser.nickname ?? currentUser.email).slice(0, 1) }}
          </span>
          <span class="nickname">{{ currentUser.nickname ?? currentUser.email }}</span>
        </button>
        <div v-if="menuOpen" class="menu" @click="menuOpen = false">
          <button type="button" class="menu-item" @click="gotoLogout">{{ $t('app.logout') }}</button>
        </div>
      </div>
    </nav>
  </header>

  <RouterView />

  <Transition name="toast">
    <div v-if="toast" class="toast" :class="toast.type" role="status">
      {{ toast.text }}
    </div>
  </Transition>
</template>

<style scoped>
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 32px;
  background: var(--color-bg);
  border-bottom: 1px solid var(--color-border);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 28px;
}

.main-nav {
  display: flex;
  align-items: center;
  gap: 6px;
}

.nav-item {
  padding: 8px 18px;
  border-radius: var(--radius-pill);
  font-size: 14px;
  color: var(--color-ink);
  text-decoration: none;
  transition: background 0.15s ease;
}

.nav-item:hover {
  background: var(--color-bg-page);
}

/* 当前路由高亮为浅灰胶囊：两项均精确匹配；日后若加 /orders 子路由，需改用 router-link-active 并对 / 单独处理 */
.nav-item.router-link-exact-active {
  background: var(--color-bg-page);
  font-weight: 600;
}

.wordmark-link {
  text-decoration: none;
}

.wordmark {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 24px;
  color: var(--color-brand-deep);
}

.wordmark-img {
  height: 28px;
  width: auto;
  display: block;
}

.wordmark-sub {
  color: var(--color-ink);
  font-weight: 500;
}

.auth-area {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
}

.contact-link {
  font-size: 14px;
  color: var(--color-ink);
  text-decoration: none;
}

.contact-link:hover {
  color: var(--color-brand-deep);
}

.lang-btn {
  padding: 6px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  background: transparent;
  color: var(--color-ink);
  font-size: 13px;
  font-family: inherit;
  cursor: pointer;
}

.lang-btn:hover {
  border-color: var(--color-brand);
  color: var(--color-brand-deep);
}

.login-btn {
  padding: 8px 20px;
  border: none;
  border-radius: var(--radius-pill);
  background: var(--color-brand);
  color: #ffffff;
  font-size: 14px;
  font-family: inherit;
  font-weight: 500;
  cursor: pointer;
}

.login-btn:hover {
  background: var(--color-brand-deep);
}

.user-menu {
  position: relative;
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 12px 4px 4px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  background: var(--color-bg);
  font-family: inherit;
  font-size: 14px;
  color: var(--color-ink);
  cursor: pointer;
}

.user-trigger:hover {
  background: var(--color-bg-page);
}

.avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  object-fit: cover;
}

.avatar-fallback {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--color-brand);
  color: #ffffff;
  font-weight: 600;
}

.menu {
  position: absolute;
  right: 0;
  top: calc(100% + 8px);
  min-width: 140px;
  padding: 6px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: #ffffff;
  box-shadow: 0 8px 24px rgba(11, 11, 12, 0.12);
  display: flex;
  flex-direction: column;
  z-index: 20;
}

.menu-item {
  display: block;
  padding: 8px 12px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--color-ink);
  font-size: 14px;
  font-family: inherit;
  text-align: left;
  text-decoration: none;
  cursor: pointer;
}

.menu-item:hover {
  background: var(--color-bg);
}

.toast {
  position: fixed;
  top: 24px;
  left: 50%;
  transform: translateX(-50%);
  padding: 12px 24px;
  border-radius: var(--radius-card);
  background: var(--color-ink);
  color: #ffffff;
  font-size: 14px;
  box-shadow: 0 8px 24px rgba(11, 11, 12, 0.16);
  z-index: 10;
}

.toast.success {
  background: var(--color-brand-deep);
}

.toast.error {
  background: #b91c1c;
}

.toast-enter-active,
.toast-leave-active {
  transition: all 0.2s ease;
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(-8px);
}
</style>
