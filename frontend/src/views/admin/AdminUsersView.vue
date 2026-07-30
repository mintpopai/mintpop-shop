<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { fetchAdminUsers, type AdminUser } from '../../api-admin'
import { formatDateTime } from '../../datetime'
import { t } from '../../i18n'

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
    loadError.value = e instanceof Error ? e.message : t('common.loadFailed')
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
  <h2 class="admin-title">{{ $t('admin.users.title') }}</h2>

  <p v-if="loading" class="admin-hint">{{ $t('common.loading') }}</p>
  <p v-else-if="loadError" class="admin-hint error">{{ loadError }}</p>

  <template v-else>
    <div class="admin-card">
      <p v-if="records.length === 0" class="admin-hint">{{ $t('admin.table.empty') }}</p>
      <table v-else class="admin-table">
        <thead>
          <tr>
            <th>{{ $t('admin.table.id') }}</th>
            <th>{{ $t('admin.users.user') }}</th>
            <th>{{ $t('admin.users.email') }}</th>
            <th>{{ $t('admin.users.orderCount') }}</th>
            <th>{{ $t('admin.users.createdAt') }}</th>
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
            <td>{{ user.orderCount }}</td>
            <td class="secondary">{{ formatDateTime(user.createdAt) }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="admin-pager">
      <button type="button" class="admin-btn-ghost" :disabled="page <= 1" @click="gotoPage(page - 1)">
        {{ $t('admin.table.prev') }}
      </button>
      <span class="info">{{ $t('admin.table.pageInfo', { page, total }) }}</span>
      <button
        type="button"
        class="admin-btn-ghost"
        :disabled="page >= totalPages"
        @click="gotoPage(page + 1)"
      >
        {{ $t('admin.table.next') }}
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
</style>
