<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { cancelOrder, fetchMyOrders, formatPrice, UnauthorizedError, type OrderItem } from '../api'
import { gotoLogin } from '../auth'
import { formatDateTime } from '../datetime'
import { t } from '../i18n'
import { showToast } from '../toast'

const orders = ref<OrderItem[]>([])
const loading = ref(true)
const loadError = ref('')

/** 筛选条的状态展示顺序（与后端 OrderStatusEnum 对齐，未知状态追加在尾部） */
const STATUS_ORDER = ['PENDING', 'PAID', 'COMPLETED', 'FAILED', 'CANCELLED', 'EXPIRED']

/** 当前筛选状态，'ALL' 表示不过滤 */
const activeStatus = ref('ALL')

/** 列表里实际出现的状态及数量，标签复用后端本地化的 statusLabel */
const availableStatuses = computed(() => {
  const counts = new Map<string, { label: string; count: number }>()
  for (const order of orders.value) {
    const entry = counts.get(order.status)
    if (entry) {
      entry.count += 1
    } else {
      counts.set(order.status, { label: order.statusLabel, count: 1 })
    }
  }
  const known = STATUS_ORDER.filter((s) => counts.has(s))
  const extras = [...counts.keys()].filter((s) => !STATUS_ORDER.includes(s))
  return [...known, ...extras].map((s) => ({ status: s, ...counts.get(s)! }))
})

const filteredOrders = computed(() =>
  activeStatus.value === 'ALL' ? orders.value : orders.value.filter((o) => o.status === activeStatus.value),
)

/** 重新拉取后，若当前筛选的状态已不存在（如取消了唯一的待支付单），退回「全部」 */
function resetFilterIfStale() {
  if (activeStatus.value !== 'ALL' && !orders.value.some((o) => o.status === activeStatus.value)) {
    activeStatus.value = 'ALL'
  }
}

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

/** 只有付款成功的订单才有详情可看（发货信息、支付时间）；未支付/已过期的详情页是空壳，不给入口 */
function hasDetail(order: OrderItem): boolean {
  return order.status === 'PAID' || order.status === 'COMPLETED'
}

async function onCancel(order: OrderItem) {
  if (!window.confirm(t('payment.cancelConfirm'))) {
    return
  }
  try {
    await cancelOrder(order.orderNo)
    showToast('success', t('payment.cancelled'))
    orders.value = await fetchMyOrders()
    resetFilterIfStale()
  } catch (e) {
    showToast('error', e instanceof Error ? e.message : t('api.requestFailed'))
  }
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

    <template v-else>
      <div class="filter-bar" role="group" :aria-label="$t('orders.filterLabel')">
        <button
          type="button"
          class="filter-chip"
          :class="{ active: activeStatus === 'ALL' }"
          @click="activeStatus = 'ALL'"
        >
          {{ $t('orders.filterAll') }}
          <span class="chip-count">{{ orders.length }}</span>
        </button>
        <button
          v-for="s in availableStatuses"
          :key="s.status"
          type="button"
          class="filter-chip"
          :class="{ active: activeStatus === s.status }"
          @click="activeStatus = s.status"
        >
          {{ s.label }}
          <span class="chip-count">{{ s.count }}</span>
        </button>
      </div>

      <ul class="order-list">
        <li v-for="order in filteredOrders" :key="order.orderNo" class="order-card">
          <div class="order-main">
            <div class="name-row">
              <span class="product-name">{{ order.productName }}</span>
              <span class="status-tag" :class="`status-tag--${order.status}`">{{ order.statusLabel }}</span>
            </div>
            <span class="order-no">{{ $t('orders.orderNo', { orderNo: order.orderNo }) }}</span>
          </div>
          <div class="order-side">
            <span class="amount">{{ formatPrice(order.amountCents) }}</span>
            <span class="meta">
              {{ $t('orders.quantity', { n: order.quantity }) }} · {{ formatDateTime(order.createdAt) }}
            </span>
            <div v-if="isPayable(order) || hasDetail(order)" class="order-actions">
              <template v-if="isPayable(order)">
                <button type="button" class="cancel-link" @click="onCancel(order)">
                  {{ $t('orders.cancel') }}
                </button>
                <RouterLink :to="`/pay/${order.orderNo}`" class="pay-link">
                  {{ $t('orders.goPay') }}
                </RouterLink>
              </template>
              <RouterLink v-if="hasDetail(order)" :to="`/orders/${order.orderNo}`" class="detail-link">
                {{ $t('orders.viewDetail') }}
              </RouterLink>
            </div>
          </div>
        </li>
      </ul>
    </template>
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

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.filter-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  background: var(--color-bg);
  color: var(--color-ink-secondary);
  font-family: inherit;
  font-size: 13px;
  cursor: pointer;
}

.filter-chip:hover {
  border-color: var(--color-brand);
  color: var(--color-brand-deep);
}

.filter-chip.active {
  border-color: var(--color-brand);
  background: var(--color-brand);
  color: #ffffff;
}

.chip-count {
  font-size: 12px;
  opacity: 0.75;
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

.name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.product-name {
  font-weight: 500;
  color: var(--color-ink);
}

/* 状态 tag 样式在 base.css（与管理端共用） */

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
  gap: 8px;
  align-items: center;
  margin-top: 4px;
}

/* 详情入口是次级操作：描边按钮，与主色「去支付」区分轻重 */
.detail-link {
  padding: 6px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-button);
  color: var(--color-ink-secondary);
  font-size: 13px;
}

.detail-link:hover {
  border-color: var(--color-brand);
  color: var(--color-brand-deep);
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
  padding: 6px 8px;
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
