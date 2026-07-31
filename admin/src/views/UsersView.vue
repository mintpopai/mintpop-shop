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
  <h2 class="admin-title">用户管理</h2>

  <p v-if="loading" class="admin-hint">加载中……</p>
  <p v-else-if="loadError" class="admin-hint error">{{ loadError }}</p>

  <template v-else>
    <div class="admin-card">
      <p v-if="records.length === 0" class="admin-hint">暂无数据</p>
      <table v-else class="admin-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>用户</th>
            <th>邮箱</th>
            <th>角色</th>
            <th>订单数</th>
            <th>注册时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="user in records" :key="user.id">
            <td>{{ user.id }}</td>
            <td>
              <div class="user-cell">
                <img v-if="user.avatarUrl" class="avatar" :src="user.avatarUrl" alt="" />
                <span v-else class="avatar avatar-fallback">
                  {{ (user.nickname ?? user.email).slice(0, 1) }}
                </span>
                <span>{{ user.nickname ?? '—' }}</span>
              </div>
            </td>
            <td>{{ user.email }}</td>
            <td>
              <span v-if="user.role === 'ADMIN'" class="role-badge">管理员</span>
              <span v-else class="secondary">普通用户</span>
            </td>
            <td>{{ user.orderCount }}</td>
            <td class="secondary">{{ formatDateTime(user.createdAt) }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="admin-pager">
      <button type="button" class="admin-btn-ghost" :disabled="page <= 1" @click="gotoPage(page - 1)">
        上一页
      </button>
      <span class="info">第 {{ page }} 页 · 共 {{ total }} 条</span>
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
  background: var(--color-brand);
  color: #ffffff;
  font-weight: 600;
  font-size: 13px;
}

.secondary {
  color: var(--color-ink-secondary);
  font-size: 13px;
  white-space: nowrap;
}

/* 管理员标记：只读展示，角色由管理员直接改库维护 */
.role-badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: var(--radius-pill);
  background: var(--color-brand);
  color: #ffffff;
  font-size: 12px;
  white-space: nowrap;
}
</style>
