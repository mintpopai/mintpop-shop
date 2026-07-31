<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { fetchOrderDetail, formatPrice, UnauthorizedError, type OrderDetail } from '../api'
import { gotoLogin } from '../auth'
import { formatDateTime } from '../datetime'
import { t } from '../i18n'

const route = useRoute()
const detail = ref<OrderDetail | null>(null)
const loading = ref(true)
const loadError = ref('')

onMounted(async () => {
  try {
    detail.value = await fetchOrderDetail(String(route.params.orderNo))
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
</script>

<template>
  <main class="page">
    <RouterLink to="/orders" class="back">← {{ $t('orders.back') }}</RouterLink>
    <h2 class="title">{{ $t('orders.detailTitle') }}</h2>

    <p v-if="loading" class="hint">{{ $t('common.loading') }}</p>
    <p v-else-if="loadError" class="hint error">{{ loadError }}</p>

    <template v-else-if="detail">
      <section class="card">
        <div class="head">
          <span class="product">{{ detail.productName }}</span>
          <span class="status-tag" :class="`status-tag--${detail.status}`">{{ detail.statusLabel }}</span>
        </div>
        <dl class="facts">
          <div class="fact-row">
            <dt>{{ $t('orders.orderNoLabel') }}</dt>
            <dd>{{ detail.orderNo }}</dd>
          </div>
          <div class="fact-row">
            <dt>{{ $t('orders.product') }}</dt>
            <dd>{{ detail.productName }} × {{ detail.quantity }}</dd>
          </div>
          <div class="fact-row">
            <dt>{{ $t('orders.amount') }}</dt>
            <dd>{{ formatPrice(detail.amountCents) }}</dd>
          </div>
          <div class="fact-row">
            <dt>{{ $t('orders.createdAt') }}</dt>
            <dd>{{ formatDateTime(detail.createdAt) }}</dd>
          </div>
          <div v-if="detail.paidAt" class="fact-row">
            <dt>{{ $t('orders.paidAt') }}</dt>
            <dd>{{ formatDateTime(detail.paidAt) }}</dd>
          </div>
        </dl>
      </section>

      <section class="card">
        <h3 class="section-title">{{ $t('orders.shipmentTitle') }}</h3>
        <template v-if="detail.latestShipment">
          <pre class="shipment-content">{{ detail.latestShipment.content }}</pre>
          <p class="shipped-at">
            {{ $t('orders.shippedAt') }}：{{ formatDateTime(detail.latestShipment.shippedAt) }}
          </p>
        </template>
        <p v-else class="hint">{{ $t('orders.notShipped') }}</p>
      </section>
    </template>
  </main>
</template>

<style scoped>
.page {
  max-width: 720px;
  margin: 0 auto;
  padding: 24px 32px 48px;
}

.back {
  font-size: 13px;
  color: var(--color-ink-secondary);
}

.back:hover {
  color: var(--color-brand-deep);
}

.title {
  font-size: 20px;
  color: var(--color-ink);
  margin: 8px 0 16px;
}

.hint {
  color: var(--color-ink-secondary);
  padding: 8px 0;
}

.hint.error {
  color: #b91c1c;
}

.card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-bg);
  padding: 20px;
  margin-bottom: 16px;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.product {
  font-weight: 600;
  color: var(--color-ink);
}

.facts {
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.fact-row {
  display: flex;
  gap: 16px;
  font-size: 14px;
}

.fact-row dt {
  width: 88px;
  flex-shrink: 0;
  color: var(--color-ink-secondary);
}

.fact-row dd {
  margin: 0;
  color: var(--color-ink);
}

.section-title {
  font-size: 15px;
  color: var(--color-ink);
  margin: 0 0 12px;
}

/* 发货文本原样展示：管理员写的换行必须保留 */
.shipment-content {
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.6;
  margin: 0;
  padding: 14px;
  background: var(--color-bg-soft, #f5f7f6);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  color: var(--color-ink);
}

.shipped-at {
  font-size: 13px;
  color: var(--color-ink-secondary);
  margin: 10px 0 0;
}
</style>
