<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { formatPrice } from '../../api'
import { fetchAdminDashboard, type AdminDashboard } from '../../api-admin'
import { formatDateTime } from '../../datetime'
import { t } from '../../i18n'

const dashboard = ref<AdminDashboard | null>(null)
const loading = ref(true)
const loadError = ref('')

onMounted(async () => {
  try {
    dashboard.value = await fetchAdminDashboard()
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : t('common.loadFailed')
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <h2 class="admin-title">{{ $t('admin.nav.dashboard') }}</h2>

  <p v-if="loading" class="admin-hint">{{ $t('common.loading') }}</p>
  <p v-else-if="loadError" class="admin-hint error">{{ loadError }}</p>

  <template v-else-if="dashboard">
    <div class="stats">
      <div class="stat-card">
        <span class="stat-label">{{ $t('admin.dashboard.totalRevenue') }}</span>
        <span class="stat-value highlight">{{ formatPrice(dashboard.totalRevenueCents) }}</span>
      </div>
      <div class="stat-card">
        <span class="stat-label">{{ $t('admin.dashboard.totalOrders') }}</span>
        <span class="stat-value">{{ dashboard.totalOrderCount }}</span>
      </div>
      <div class="stat-card">
        <span class="stat-label">{{ $t('admin.dashboard.todayOrders') }}</span>
        <span class="stat-value">{{ dashboard.todayOrderCount }}</span>
      </div>
      <div class="stat-card">
        <span class="stat-label">{{ $t('admin.dashboard.todayRevenue') }}</span>
        <span class="stat-value highlight">{{ formatPrice(dashboard.todayRevenueCents) }}</span>
      </div>
      <div class="stat-card">
        <span class="stat-label">{{ $t('admin.dashboard.users') }}</span>
        <span class="stat-value">{{ dashboard.userCount }}</span>
      </div>
      <div class="stat-card">
        <span class="stat-label">{{ $t('admin.dashboard.onSaleProducts') }}</span>
        <span class="stat-value">{{ dashboard.onSaleProductCount }}</span>
      </div>
    </div>

    <h3 class="section-title">{{ $t('admin.dashboard.recentOrders') }}</h3>
    <div class="admin-card">
      <p v-if="dashboard.recentOrders.length === 0" class="admin-hint">
        {{ $t('admin.dashboard.empty') }}
      </p>
      <table v-else class="admin-table">
        <thead>
          <tr>
            <th>{{ $t('admin.orders.orderNo') }}</th>
            <th>{{ $t('admin.orders.product') }}</th>
            <th>{{ $t('admin.orders.buyer') }}</th>
            <th>{{ $t('admin.orders.amount') }}</th>
            <th>{{ $t('admin.orders.status') }}</th>
            <th>{{ $t('admin.orders.createdAt') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="order in dashboard.recentOrders" :key="order.orderNo">
            <td class="mono">{{ order.orderNo }}</td>
            <td>{{ order.productName }}</td>
            <td>{{ order.buyerEmail ?? $t('admin.orders.guest') }}</td>
            <td class="amount">{{ formatPrice(order.amountCents) }}</td>
            <td>
              <span class="status-tag" :class="`status-tag--${order.status}`">{{ order.statusLabel }}</span>
            </td>
            <td class="secondary">{{ formatDateTime(order.createdAt) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </template>
</template>

<style scoped>
.stats {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
  margin-bottom: 28px;
}

.stat-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px 20px;
  background: var(--color-bg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
}

.stat-label {
  font-size: 13px;
  color: var(--color-ink-secondary);
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
  color: var(--color-ink);
}

.stat-value.highlight {
  color: var(--color-brand-deep);
}

.section-title {
  font-size: 16px;
  color: var(--color-ink);
  margin-bottom: 12px;
}

.mono {
  font-size: 13px;
}

.amount {
  font-weight: 600;
  color: var(--color-brand-deep);
}

.secondary {
  color: var(--color-ink-secondary);
  font-size: 13px;
  white-space: nowrap;
}
</style>
