<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { cancelOrder, fetchMyOrders, formatPrice, UnauthorizedError, type OrderItem } from '../api'
import { gotoLogin } from '../auth'
import { locale, t } from '../i18n'
import { showToast } from '../toast'

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

/** 待支付/支付失败的订单可去支付、可取消 */
function isPayable(order: OrderItem): boolean {
  return order.status === 'PENDING' || order.status === 'FAILED'
}

async function onCancel(order: OrderItem) {
  if (!window.confirm(t('payment.cancelConfirm'))) {
    return
  }
  try {
    await cancelOrder(order.orderNo)
    showToast('success', t('payment.cancelled'))
    orders.value = await fetchMyOrders()
  } catch (e) {
    showToast('error', e instanceof Error ? e.message : t('api.requestFailed'))
  }
}

/** 后端 UTC 时刻（ISO-8601 带 Z）按浏览器时区、当前语言渲染到分钟 */
const timeFormatter = new Intl.DateTimeFormat(locale, {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})

function formatTime(iso: string): string {
  return timeFormatter.format(new Date(iso))
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
          <div v-if="isPayable(order)" class="order-actions">
            <RouterLink :to="`/pay/${order.orderNo}`" class="pay-link">
              {{ $t('orders.goPay') }}
            </RouterLink>
            <button type="button" class="cancel-link" @click="onCancel(order)">
              {{ $t('orders.cancel') }}
            </button>
          </div>
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

.order-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.pay-link {
  padding: 6px 16px;
  border-radius: var(--radius-button);
  background: var(--color-brand);
  color: #ffffff;
  font-size: 13px;
}

.pay-link:hover {
  background: var(--color-brand-deep);
}

.cancel-link {
  border: none;
  background: none;
  color: var(--color-ink-secondary);
  font-family: inherit;
  font-size: 13px;
  cursor: pointer;
}

.cancel-link:hover {
  color: #b91c1c;
}
</style>
