<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { fetchAdminUsers, type AdminUser } from '../api-admin'
import { formatDateTime } from '../datetime'

const PAGE_SIZE = 20

const records = ref<AdminUser[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(true)
const loadError = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))

async function reload() {
  loading.value = true
  try {
    const result = await fetchAdminUsers(page.value, PAGE_SIZE)
    records.value = result.records
    total.value = result.total
    loadError.value = ''
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

onMounted(reload)

function gotoPage(next: number) {
  if (next < 1 || next > totalPages.value || next === page.value) {
    return
  }
  page.value = next
  reload()
}
</script>

<template>
  <header class="page-head">
    <h2 class="page-title">用户</h2>
    <p class="page-facts">
      共 <span class="fact">{{ total }}</span> 人。角色只读，改管理员权限要直接改库。
    </p>
  </header>

  <p v-if="loading" class="admin-hint loading">加载中……</p>
  <p v-else-if="loadError" class="admin-hint error">{{ loadError }}</p>

  <template v-else>
    <div class="admin-card">
      <p v-if="records.length === 0" class="admin-hint">还没有用户。有人在商城登录后会出现在这里。</p>
      <table v-else class="admin-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>用户</th>
            <th>邮箱</th>
            <th>角色</th>
            <th class="col-amount">订单数</th>
            <th>注册时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="user in records" :key="user.id">
            <td class="fact muted">{{ user.id }}</td>
            <td>
              <div class="user-cell">
                <img v-if="user.avatarUrl" class="avatar" :src="user.avatarUrl" alt="" />
                <span v-else class="avatar avatar-fallback">
                  {{ (user.nickname ?? user.email).slice(0, 1) }}
                </span>
                <span>{{ user.nickname ?? '—' }}</span>
              </div>
            </td>
            <td class="fact">{{ user.email }}</td>
            <td>
              <span v-if="user.role === 'ADMIN'" class="role-badge">管理员</span>
              <span v-else class="muted">普通用户</span>
            </td>
            <td class="fact col-amount">{{ user.orderCount }}</td>
            <td class="fact muted">{{ formatDateTime(user.createdAt) }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="admin-pager">
      <button type="button" class="admin-btn-ghost" :disabled="page <= 1" @click="gotoPage(page - 1)">
        上一页
      </button>
      <span class="info">
        第 <span class="fact">{{ page }}</span> / <span class="fact">{{ totalPages }}</span> 页 · 共
        <span class="fact">{{ total }}</span> 人
      </span>
      <button
        type="button"
        class="admin-btn-ghost"
        :disabled="page >= totalPages"
        @click="gotoPage(page + 1)"
      >
        下一页
      </button>
    </div>
  </template>
</template>

<style scoped>
.user-cell {
  display: flex;
  align-items: center;
  gap: 8px;
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
  background: var(--color-bg-cloud);
  color: var(--color-ink-secondary);
  font-weight: 600;
  font-size: 13px;
  text-transform: uppercase;
}

/* 管理员标记：只读展示，角色由管理员直接改库维护 */
.role-badge {
  display: inline-block;
  padding: 4px 12px;
  border-radius: var(--radius-pill);
  background: var(--counter-rail);
  color: #ffffff;
  font-size: 12px;
  white-space: nowrap;
}
</style>
