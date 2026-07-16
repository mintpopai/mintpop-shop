<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { verifyOrder, UnauthorizedError } from '../api'
import { isPaidStatus } from '../payment'
import { gotoLogin } from '../auth'

/** 整页跳转回流场景：约 15 次 × 2s（~30s）未确认则转「结果待确认」，不报失败 */
const MAX_ATTEMPTS = 15

type Stage = 'confirming' | 'success' | 'pending' | 'failed'

const route = useRoute()
const router = useRouter()
const stage = ref<Stage>('confirming')
const orderNo = String(route.query.order_no ?? '')
let timer: ReturnType<typeof setInterval> | undefined
let attempts = 0

onMounted(() => {
  // 处理完毕清理 Stripe 回跳追加的 query（payment_intent / redirect_status 等）
  router.replace({ path: '/payment/result', query: orderNo ? { order_no: orderNo } : {} })
  if (!orderNo) {
    stage.value = 'pending'
    return
  }
  timer = setInterval(check, 2000)
  void check()
})

onUnmounted(() => clearInterval(timer))

async function check() {
  // 快速短路：已定格后不再重复检查
  if (stage.value !== 'confirming') {
    return
  }
  attempts += 1
  try {
    const result = await verifyOrder(orderNo)
    if (isPaidStatus(result.status)) {
      finish('success')
    } else if (result.status === 'FAILED' || result.status === 'CANCELLED' || result.status === 'EXPIRED') {
      finish('failed')
    } else if (attempts >= MAX_ATTEMPTS) {
      finish('pending')
    }
  } catch (e) {
    if (e instanceof UnauthorizedError) {
      clearInterval(timer)
      gotoLogin()
      return
    }
    if (attempts >= MAX_ATTEMPTS) {
      finish('pending')
    }
  }
}

function finish(next: Stage) {
  // 终态守卫：只允许从 confirming 迁出一次，过期的慢响应不得翻转已定格的结果
  if (stage.value !== 'confirming') {
    return
  }
  clearInterval(timer)
  stage.value = next
}
</script>

<template>
  <main class="page">
    <div class="result-card" aria-live="polite">
      <template v-if="stage === 'confirming'">
        <p class="status">{{ $t('payment.result.confirming') }}</p>
      </template>
      <template v-else-if="stage === 'success'">
        <p class="status success">{{ $t('payment.result.success') }}</p>
        <p class="desc">{{ $t('payment.result.successDesc') }}</p>
      </template>
      <template v-else-if="stage === 'pending'">
        <p class="status">{{ $t('payment.result.pending') }}</p>
        <p class="desc">{{ $t('payment.result.pendingDesc') }}</p>
      </template>
      <template v-else>
        <p class="status failed">{{ $t('payment.result.failed') }}</p>
        <p class="desc">{{ $t('payment.result.failedDesc') }}</p>
      </template>

      <nav v-if="stage !== 'confirming'" class="links">
        <RouterLink to="/orders" class="primary-link">
          {{ $t('payment.result.backToOrders') }}
        </RouterLink>
        <RouterLink to="/" class="secondary-link">
          {{ $t('payment.result.backToShop') }}
        </RouterLink>
      </nav>
    </div>
  </main>
</template>

<style scoped>
.page {
  max-width: 720px;
  margin: 0 auto;
  padding: 48px 32px;
}

.result-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  padding: 40px 24px;
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: center;
}

.status {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-ink);
}

.status.success {
  color: var(--color-brand-deep);
}

.status.failed {
  color: #b91c1c;
}

.desc {
  font-size: 14px;
  color: var(--color-ink-secondary);
}

.links {
  display: flex;
  gap: 16px;
  margin-top: 8px;
}

.primary-link {
  padding: 8px 20px;
  border-radius: var(--radius-button);
  background: var(--color-brand);
  color: #ffffff;
  font-size: 14px;
}

.secondary-link {
  padding: 8px 20px;
  color: var(--color-brand-deep);
  font-size: 14px;
}
</style>
