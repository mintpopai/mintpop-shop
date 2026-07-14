<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { fetchMyOrders, formatPrice, UnauthorizedError, type OrderItem } from '../api'
import { gotoLogin } from '../auth'
import { t } from '../i18n'

const orders = ref<OrderItem[]>([])
const loading = ref(true)
const loadError = ref('')

onMounted(async () => {
  try {
    orders.value = await fetchMyOrders()
  } catch (e) {
    if (e instanceof UnauthorizedError) {
      gotoLogin()
      return
    }
    loadError.value = e instanceof Error ? e.message : t('common.loadFailed')
  } finally {
    loading.value = false
  }
})

/** 后端 ISO 时间（如 2026-07-13T12:00:00）转「2026-07-13 12:00」 */
function formatTime(iso: string): string {
  return iso.replace('T', ' ').slice(0, 16)
}
</script>

<template>
  <main class="page">
    <h2 class="title">{{ $t('orders.title') }}</h2>

    <p v-if="loading" class="hint">{{ $t('common.loading') }}</p>
    <p v-else-if="loadError" class="hint error">{{ loadError }}</p>
    <p v-else-if="orders.length === 0" class="hint">
      {{ $t('orders.empty') }}<RouterLink to="/" class="link">{{ $t('orders.goShopping') }}</RouterLink>
    </p>

    <ul v-else class="order-list">
      <li v-for="order in orders" :key="order.orderNo" class="order-card">
        <div class="order-main">
          <span class="product-name">{{ order.productName }}</span>
          <span class="order-no">{{ $t('orders.orderNo', { orderNo: order.orderNo }) }}</span>
        </div>
        <div class="order-side">
          <span class="amount">{{ formatPrice(order.amountCents) }}</span>
          <span class="meta">
            {{ $t('orders.quantity', { n: order.quantity }) }} · {{ order.statusLabel }} · {{ formatTime(order.createdAt) }}
          </span>
        </div>
      </li>
    </ul>
  </main>
</template>

<style scoped>
.page {
  max-width: 1080px;
  margin: 0 auto;
  padding: 24px 32px 48px;
}

.title {
  font-size: 20px;
  color: var(--color-ink);
  margin-bottom: 16px;
}

.hint {
  color: var(--color-ink-secondary);
  padding: 24px 0;
}

.hint.error {
  color: #b91c1c;
}

.link {
  color: var(--color-brand-deep);
}

.order-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.order-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 20px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-bg);
}

.order-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.product-name {
  font-weight: 500;
  color: var(--color-ink);
}

.order-no {
  font-size: 13px;
  color: var(--color-ink-secondary);
}

.order-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  flex-shrink: 0;
}

.amount {
  font-weight: 600;
  color: var(--color-brand-deep);
}

.meta {
  font-size: 13px;
  color: var(--color-ink-secondary);
}
</style>
