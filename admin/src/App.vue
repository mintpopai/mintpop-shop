<script setup lang="ts">
// 管理端外壳：顶栏（品牌 + 当前用户）+ 左侧竖导航 + 内容区。
// 管理端不做双语，文案一律写死中文。
// 权限在此统一裁决：未登录引导登录、非管理员显示无权限页——但这只是 UX，
// 真正的安全边界是后端 AdminInterceptor 对 /api/admin/** 的逐请求校验。
import { currentUser, gotoLogin, gotoLogout } from './auth'
import { toast } from './toast'
import './styles/layout.css'
</script>

<template>
  <header class="admin-header">
    <span class="wordmark">MintPop</span>
    <span class="admin-badge">管理后台</span>
    <div class="admin-header-right">
      <template v-if="currentUser">
        <span class="admin-user">{{ currentUser.nickname ?? currentUser.email }}</span>
        <button type="button" class="admin-text-btn" @click="gotoLogout">登出</button>
      </template>
    </div>
  </header>

  <!-- 未登录 -->
  <div v-if="!currentUser" class="admin-gate">
    <h1 class="admin-gate-title">MintPop Shop 管理后台</h1>
    <p class="admin-gate-text">请使用管理员账号登录。</p>
    <button type="button" class="admin-gate-btn" @click="gotoLogin">登录</button>
  </div>

  <!-- 已登录但不是管理员 -->
  <div v-else-if="!currentUser.admin" class="admin-gate">
    <h1 class="admin-gate-title">无访问权限</h1>
    <p class="admin-gate-text">
      当前账号（{{ currentUser.email }}）不是管理员。如需访问请联系店主，或换个账号登录。
    </p>
    <div class="admin-gate-actions">
      <button type="button" class="admin-gate-btn" @click="gotoLogout">退出登录</button>
      <a class="admin-text-btn" href="https://mintpop.ai">返回商城</a>
    </div>
  </div>

  <!-- 管理员 -->
  <div v-else class="admin-shell">
    <nav class="admin-nav" aria-label="管理后台">
      <RouterLink to="/" class="admin-nav-item">概览</RouterLink>
      <RouterLink to="/products" class="admin-nav-item">商品</RouterLink>
      <RouterLink to="/groups" class="admin-nav-item">分组</RouterLink>
      <RouterLink to="/orders" class="admin-nav-item">订单</RouterLink>
      <RouterLink to="/users" class="admin-nav-item">用户</RouterLink>
    </nav>
    <main class="admin-main">
      <RouterView />
    </main>
  </div>

  <Transition name="toast">
    <div v-if="toast" class="toast" :class="toast.type" role="status">
      {{ toast.text }}
    </div>
  </Transition>
</template>

<style scoped>
.admin-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 32px;
  background: var(--color-bg);
  border-bottom: 1px solid var(--color-border);
}

.wordmark {
  font-size: 20px;
  font-weight: 600;
  color: var(--color-brand-deep);
}

.admin-badge {
  padding: 2px 10px;
  border-radius: var(--radius-pill);
  background: var(--color-bg-cloud);
  color: var(--color-ink-secondary);
  font-size: 12px;
}

.admin-header-right {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-left: auto;
}

.admin-user {
  font-size: 14px;
  color: var(--color-ink-secondary);
}

.admin-text-btn {
  border: none;
  background: none;
  padding: 0;
  font-family: inherit;
  font-size: 14px;
  color: var(--color-ink-secondary);
  text-decoration: none;
  cursor: pointer;
}

.admin-text-btn:hover {
  color: var(--color-ink);
}

.admin-gate {
  max-width: 520px;
  margin: 96px auto;
  padding: 32px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-bg);
  text-align: center;
}

.admin-gate-title {
  font-size: 20px;
  font-weight: 600;
}

.admin-gate-text {
  margin-top: 12px;
  color: var(--color-ink-secondary);
  font-size: 14px;
  line-height: 1.6;
}

.admin-gate-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 20px;
  margin-top: 24px;
}

.admin-gate-btn {
  margin-top: 24px;
  padding: 8px 24px;
  border: none;
  border-radius: var(--radius-pill);
  background: var(--color-brand);
  color: #ffffff;
  font-family: inherit;
  font-size: 14px;
  cursor: pointer;
}

.admin-gate-actions .admin-gate-btn {
  margin-top: 0;
}

.admin-gate-btn:hover {
  background: var(--color-brand-deep);
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
