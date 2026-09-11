<script setup lang="ts">
// 管理端外壳：全高导航轨（品牌 + 页面 + 当前用户）+ 右侧工作区。
// 管理端不做双语，文案一律写死中文。
// 权限在此统一裁决：未登录引导登录、非管理员显示无权限页——但这只是 UX，
// 真正的安全边界是后端 AdminInterceptor 对 /api/admin/** 的逐请求校验。
import { computed } from 'vue'
import { currentUser, gotoLogin, gotoLogout } from './auth'
import { toast } from './toast'
import GateShell from './components/GateShell.vue'
import './styles/layout.css'

/** 头像兜底字母：优先昵称首字，其次邮箱首字 */
const initial = computed(() => (currentUser.value?.nickname ?? currentUser.value?.email ?? '?').slice(0, 1))
</script>

<template>
  <!-- 未登录 / 无权限：分屏闸门页，骨架与样式见 GateShell 与 layout.css 的 gate 段 -->
  <GateShell v-if="!currentUser">
    <h1 class="gate-title">用管理员账号登录</h1>
    <p class="gate-text">登录后可以查看订单与营收、维护商品目录。</p>
    <div class="gate-actions">
      <button type="button" class="gate-btn" @click="gotoLogin">登录</button>
    </div>
  </GateShell>

  <GateShell v-else-if="!currentUser.admin">
    <h1 class="gate-title">这个账号没有后台权限</h1>
    <p class="gate-text">
      当前登录的是 <span class="fact">{{ currentUser.email }}</span
      >。找店主把它设为管理员，或换个账号登录。
    </p>
    <div class="gate-actions">
      <button type="button" class="gate-btn" @click="gotoLogout">退出登录</button>
      <a class="gate-link" href="https://mintpop.ai">返回商城</a>
    </div>
  </GateShell>

  <!-- 管理员 -->
  <template v-else>
    <nav class="admin-rail" aria-label="管理后台">
      <p class="rail-brand">
        <img
          class="rail-wordmark"
          src="https://standards.mintpop.ai/assets/brand/wordmark/mintpop-wordmark-dark.png"
          alt="MintPop"
        />
        <span class="rail-kind">管理后台</span>
      </p>

      <div class="rail-nav">
        <RouterLink to="/" class="rail-link">概览</RouterLink>
        <RouterLink to="/products" class="rail-link">商品</RouterLink>
        <RouterLink to="/groups" class="rail-link">分组</RouterLink>
        <RouterLink to="/orders" class="rail-link">订单</RouterLink>
        <RouterLink to="/users" class="rail-link">用户</RouterLink>
      </div>

      <div class="rail-foot">
        <div class="rail-user">
          <img v-if="currentUser.avatarUrl" class="rail-avatar" :src="currentUser.avatarUrl" alt="" />
          <span v-else class="rail-avatar">{{ initial }}</span>
          <span class="rail-user-name">{{ currentUser.nickname ?? currentUser.email }}</span>
        </div>
        <button type="button" class="rail-signout" @click="gotoLogout">退出登录</button>
      </div>
    </nav>

    <main class="admin-desk">
      <div class="admin-page">
        <RouterView />
      </div>
    </main>
  </template>

  <Transition name="toast">
    <div v-if="toast" class="toast" :class="toast.type" role="status">
      {{ toast.text }}
    </div>
  </Transition>
</template>

<style scoped>
.toast {
  position: fixed;
  top: 24px;
  left: 50%;
  transform: translateX(-50%);
  padding: 12px 24px;
  border-radius: var(--radius-card);
  background: var(--counter-deep);
  color: #ffffff;
  font-size: 14px;
  box-shadow: 0 10px 30px rgba(15, 26, 22, 0.28);
  z-index: 40;
}

/* 成功用品牌绿，但绿只做底、字换深墨——沿用主按钮那对经过校验的配色（9.1:1）。
   原先是绿底白字，只有 2.7:1，正是 layout.css 开头写明要避开的用法 */
.toast.success {
  background: var(--color-brand);
  color: var(--counter-deep);
}

.toast.error {
  background: var(--counter-danger);
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
