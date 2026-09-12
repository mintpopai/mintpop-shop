<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { currentUser, gotoLogin, gotoLogout } from './auth'
import { showToast, toast } from './toast'
import { locale, setLocale, t } from './i18n'

const menuOpen = ref(false)
const userMenu = ref<HTMLElement | null>(null)
const route = useRoute()

/** 点到菜单外面就收起：下拉不是弹窗，不该逼人再点一次头像才能关 */
function onDocumentPointerDown(event: PointerEvent) {
  if (menuOpen.value && !userMenu.value?.contains(event.target as Node)) {
    menuOpen.value = false
  }
}

function onDocumentKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    menuOpen.value = false
  }
}

/** 主导航高亮按路径前缀判定：商品详情归「商店」，订单详情归「我的订单」（设计稿：详情页顶栏「商店」仍高亮） */
const shopActive = computed(() => route.path === '/' || route.path.startsWith('/products'))
const ordersActive = computed(() => route.path.startsWith('/orders'))

/** 官网首页与联系方式页，按当前语言指向对应路径 */
const siteUrl = locale === 'zh-CN' ? 'https://mintpop.ai/zh/' : 'https://mintpop.ai/'
const contactUrl = locale === 'zh-CN' ? 'https://mintpop.ai/zh/contact/' : 'https://mintpop.ai/contact/'

/** 游客的中英互切（切了立即刷新生效）；登录用户的语言偏好统一在设置页里改 */
function toggleLocale() {
  setLocale(locale === 'zh-CN' ? 'en-US' : 'zh-CN')
}

onMounted(() => {
  document.addEventListener('pointerdown', onDocumentPointerDown)
  document.addEventListener('keydown', onDocumentKeydown)
  // OIDC 握手失败会回跳 ?login_error=1：提示后清掉参数
  const params = new URLSearchParams(window.location.search)
  if (params.get('login_error')) {
    showToast('error', t('app.loginFailed'))
    params.delete('login_error')
    const query = params.toString()
    history.replaceState(null, '', window.location.pathname + (query ? `?${query}` : ''))
  }
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocumentPointerDown)
  document.removeEventListener('keydown', onDocumentKeydown)
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
        <RouterLink to="/" class="nav-item" :class="{ active: shopActive }">{{ $t('app.shop') }}</RouterLink>
        <RouterLink to="/orders" class="nav-item" :class="{ active: ordersActive }">{{ $t('app.myOrders') }}</RouterLink>
      </nav>
    </div>

    <nav class="auth-area">
      <a class="site-link" :href="siteUrl" target="_blank" rel="noopener">
        {{ $t('app.website') }}
      </a>
      <a class="site-link" :href="contactUrl" target="_blank" rel="noopener">
        {{ $t('app.contact') }}
      </a>
      <!-- 语言按钮只给游客：登录用户改语言走设置页（点保存才生效） -->
      <button v-if="!currentUser" type="button" class="lang-btn" @click="toggleLocale">
        {{ locale === 'zh-CN' ? 'EN' : '中文' }}
      </button>
      <button v-if="!currentUser" type="button" class="login-btn" @click="gotoLogin">
        {{ $t('app.login') }}
      </button>
      <div v-else ref="userMenu" class="user-menu">
        <button
          type="button"
          class="user-trigger"
          :class="{ open: menuOpen }"
          aria-haspopup="menu"
          :aria-expanded="menuOpen"
          @click="menuOpen = !menuOpen"
        >
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
          <svg class="chevron" viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 6l4 4 4-4" /></svg>
        </button>
        <Transition name="menu">
          <div v-if="menuOpen" class="menu" role="menu" @click="menuOpen = false">
            <!-- 身份区：让人确认「现在登着的是谁」，昵称缺省时只显一行邮箱，不重复 -->
            <div class="menu-identity">
              <img v-if="currentUser.avatarUrl" class="avatar avatar-lg" :src="currentUser.avatarUrl" alt="" />
              <span v-else class="avatar avatar-lg avatar-fallback">
                {{ (currentUser.nickname ?? currentUser.email).slice(0, 1) }}
              </span>
              <div class="identity-text">
                <span class="identity-name">{{ currentUser.nickname ?? currentUser.email }}</span>
                <span v-if="currentUser.nickname" class="identity-email">{{ currentUser.email }}</span>
              </div>
            </div>
            <div class="menu-divider" />
            <RouterLink to="/settings" class="menu-item" role="menuitem">
              <svg class="menu-icon" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1Z" /></svg>
              {{ $t('app.settings') }}
            </RouterLink>
            <button type="button" class="menu-item menu-item--logout" role="menuitem" @click="gotoLogout">
              <svg class="menu-icon" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" /><path d="M16 17l5-5-5-5" /><path d="M21 12H9" /></svg>
              {{ $t('app.logout') }}
            </button>
          </div>
        </Transition>
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
/* 页头常驻顶部：半透明 + 背景模糊，滚动时内容从下方透过去 */
.header {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 32px;
  background: rgba(255, 255, 255, 0.82);
  -webkit-backdrop-filter: blur(10px);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--color-border);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 32px;
}

.main-nav {
  display: flex;
  align-items: center;
  gap: 4px;
}

.nav-item {
  padding: 8px 16px;
  border-radius: var(--radius-pill);
  font-size: 14px;
  color: var(--color-ink-secondary);
  text-decoration: none;
  transition: background 0.15s ease, color 0.15s ease;
}

.nav-item:hover {
  background: var(--color-bg-cloud);
  color: var(--color-ink);
}

/* 当前板块高亮为 Cloud 胶囊（板块归属见 shopActive / ordersActive） */
.nav-item.active {
  background: var(--color-bg-cloud);
  color: var(--color-ink);
  font-weight: 600;
}

.wordmark-link {
  text-decoration: none;
}

.wordmark {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 22px;
  font-weight: 600;
  letter-spacing: -0.015em;
  color: var(--color-ink);
}

.wordmark-img {
  height: 26px;
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
  gap: 16px;
}

/* 官网 / 联系方式：外链到 mintpop.ai，弱化为次级文字 */
.site-link {
  font-size: 14px;
  color: var(--color-ink-secondary);
  text-decoration: none;
}

.site-link:hover {
  color: var(--color-brand-ink);
}

.lang-btn {
  padding: 6px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  background: transparent;
  color: var(--color-ink-secondary);
  font-size: 13px;
  font-family: inherit;
  cursor: pointer;
  transition: border-color 0.15s ease, color 0.15s ease;
}

.lang-btn:hover {
  border-color: var(--color-brand);
  color: var(--color-ink);
}

/* 薄荷填充上一律 ink 字：#17d1a7 配白字对比度不过 AA */
.login-btn {
  padding: 9px 20px;
  border: none;
  border-radius: var(--radius-pill);
  background: var(--color-brand);
  color: var(--color-ink);
  font-size: 14px;
  font-family: inherit;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s ease;
}

.login-btn:hover {
  background: var(--color-brand-bright);
}

.user-menu {
  position: relative;
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 10px 4px 4px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  background: var(--color-bg);
  font-family: inherit;
  font-size: 14px;
  color: var(--color-ink);
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease;
}

.user-trigger:hover,
.user-trigger.open {
  background: var(--color-bg-cloud);
  border-color: #d5dbd8;
}

.chevron {
  color: var(--color-ink-secondary);
  transition: transform 0.2s ease;
}

.user-trigger.open .chevron {
  transform: rotate(180deg);
}

.avatar {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  object-fit: cover;
}

.avatar-fallback {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--color-brand);
  color: var(--color-ink);
  font-size: 13px;
  font-weight: 600;
}

/* 下拉面板：圆角与商品卡同级、双层柔和投影，从触发按钮右上角「长出来」而不是硬切出现 */
.menu {
  position: absolute;
  right: 0;
  top: calc(100% + 8px);
  min-width: 224px;
  padding: 6px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-panel);
  background: #ffffff;
  box-shadow:
    0 12px 32px rgba(11, 11, 12, 0.1),
    0 2px 6px rgba(11, 11, 12, 0.05);
  display: flex;
  flex-direction: column;
  z-index: 20;
  transform-origin: top right;
}

.menu-enter-active,
.menu-leave-active {
  transition: opacity 0.16s ease, transform 0.16s ease;
}

.menu-enter-from,
.menu-leave-to {
  opacity: 0;
  transform: translateY(-6px) scale(0.97);
}

.menu-identity {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px 10px;
}

.avatar-lg {
  width: 36px;
  height: 36px;
  font-size: 16px;
  flex-shrink: 0;
}

.identity-text {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.identity-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-ink);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.identity-email {
  font-size: 12px;
  color: var(--color-ink-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.menu-divider {
  height: 1px;
  margin: 0 6px 6px;
  background: var(--color-border);
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 9px 10px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--color-ink);
  font-size: 14px;
  font-family: inherit;
  text-align: left;
  text-decoration: none;
  cursor: pointer;
  transition: background 0.12s ease, color 0.12s ease;
}

.menu-icon {
  color: var(--color-ink-secondary);
  flex-shrink: 0;
  transition: color 0.12s ease;
}

.menu-item:hover {
  background: var(--color-bg-cloud);
}

.menu-item:hover .menu-icon {
  color: var(--color-ink);
}

/* 退出是离开动作：悬停时用危险色提示，但平时不刺眼 */
.menu-item--logout:hover {
  background: #fef2f2;
  color: var(--color-danger);
}

.menu-item--logout:hover .menu-icon {
  color: var(--color-danger);
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
  box-shadow: 0 1px 3px rgba(11, 11, 12, 0.06);
  z-index: 20;
}

/* 成功/失败用语义色，不用品牌绿：薄荷是强调色，不承担状态含义 */
.toast.success {
  background: #17b26a;
}

.toast.error {
  background: var(--color-danger);
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
