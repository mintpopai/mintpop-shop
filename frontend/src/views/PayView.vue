<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { Stripe, StripeElements } from '@stripe/stripe-js'
import {
  cancelOrder,
  createPaymentIntent,
  fetchCheckoutInfo,
  formatPrice,
  UnauthorizedError,
  verifyOrder,
  type PaymentIntentInfo,
} from '../api'
import { buildPayOptions, isPaidStatus, type PayOption, type StripeSubMethod } from '../payment'
import {
  confirmCardPayment,
  createCardElements,
  getStripe,
  startAlipay,
  startWechatPay,
} from '../stripe'
import { gotoLogin } from '../auth'
import { locale, t } from '../i18n'
import { showToast } from '../toast'

/** 二维码有效期（秒）：Stripe 未在 next_action 中下发过期时间，按微信码常见时效取 15 分钟 */
const QR_TTL_SECONDS = 15 * 60

const route = useRoute()
const router = useRouter()
const orderNo = route.params.orderNo as string
const returnUrl = `${location.origin}/payment/result?order_no=${orderNo}`

const loading = ref(true)
const loadError = ref('')
const intentInfo = ref<PaymentIntentInfo | null>(null)
const payOptions = ref<PayOption[]>([])
const selectedKey = ref('')
const submitting = ref(false)
const polling = ref(false)

// 订单级支付时限（区别于二维码有效期）
const orderSecondsLeft = ref(0)
const orderExpired = ref(false)

// 二维码状态
const qrFor = ref<StripeSubMethod | null>(null)
const qrCanvas = ref<HTMLCanvasElement | null>(null)
const qrSecondsLeft = ref(0)

// 银行卡 Payment Element
const cardMount = ref<HTMLDivElement | null>(null)
let stripe: Stripe | null = null
let cardElements: StripeElements | null = null
let pollTimer: ReturnType<typeof setInterval> | undefined
let qrTimer: ReturnType<typeof setInterval> | undefined
let orderTimer: ReturnType<typeof setInterval> | undefined

const selectedOption = computed(
  () => payOptions.value.find((o) => o.key === selectedKey.value) ?? null,
)
const isCardSelected = computed(() => selectedOption.value?.subMethod === 'card')

function formatCountdown(totalSeconds: number) {
  const m = Math.floor(totalSeconds / 60)
  const s = totalSeconds % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

const qrCountdown = computed(() => formatCountdown(qrSecondsLeft.value))
const orderCountdown = computed(() => formatCountdown(orderSecondsLeft.value))

onMounted(async () => {
  try {
    const [checkout, intent] = await Promise.all([
      fetchCheckoutInfo(),
      createPaymentIntent(orderNo),
    ])
    intentInfo.value = intent
    payOptions.value = buildPayOptions(checkout.methods)
    selectedKey.value = payOptions.value[0]?.key ?? ''
    if (checkout.stripePublishableKey) {
      stripe = await getStripe(checkout.stripePublishableKey)
    }
    if (!stripe || payOptions.value.length === 0) {
      loadError.value = t('payment.notPayable')
    } else {
      startOrderCountdown(intent.expireRemainingSeconds)
    }
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

onUnmounted(() => {
  clearInterval(pollTimer)
  clearInterval(qrTimer)
  clearInterval(orderTimer)
})

/** 订单级支付时限倒计时：到点主动触发后端过期，而不是被动等下一个入口 */
function startOrderCountdown(seconds: number) {
  orderSecondsLeft.value = Math.max(0, Math.floor(seconds))
  if (orderSecondsLeft.value === 0) {
    void onOrderDeadline()
    return
  }
  orderTimer = setInterval(() => {
    orderSecondsLeft.value -= 1
    if (orderSecondsLeft.value <= 0) {
      clearInterval(orderTimer)
      void onOrderDeadline()
    }
  }, 1000)
}

/**
 * 倒计时归零：调 verify 主动触发后端懒惰过期（置 EXPIRED 并撤 Stripe 侧凭据）。
 * 竞态兜底——若这一问发现其实已支付成功，直接去结果页而不是判过期。
 */
async function onOrderDeadline() {
  try {
    const result = await verifyOrder(orderNo)
    if (isPaidStatus(result.status)) {
      goResult()
      return
    }
  } catch {
    // 触发失败不影响前端切过期态：后端任一入口读到该单时仍会过期它
  }
  showExpiredState()
}

/** 切到订单过期态：停掉所有计时器与轮询，收起二维码 */
function showExpiredState() {
  clearInterval(orderTimer)
  clearInterval(qrTimer)
  clearInterval(pollTimer)
  polling.value = false
  qrFor.value = null
  orderExpired.value = true
}

/** 选卡即重置确认区：切换方式时收起二维码；选中银行卡时挂 Payment Element */
watch(selectedKey, async () => {
  qrFor.value = null
  clearInterval(qrTimer)
  if (isCardSelected.value && intentInfo.value && stripe && !cardElements) {
    cardElements = createCardElements(stripe, {
      amount: intentInfo.value.amountCents,
      currency: intentInfo.value.currency,
      locale,
    })
    cardElements.create('payment', { layout: 'tabs' })
    // 等 v-show 生效后再挂载
    await Promise.resolve()
    if (cardMount.value) {
      cardElements.getElement('payment')?.mount(cardMount.value)
    }
  }
})

/** radiogroup 无障碍：roving tabindex + 方向键循环 */
function onRadioKeydown(event: KeyboardEvent) {
  const keys = ['ArrowRight', 'ArrowDown', 'ArrowLeft', 'ArrowUp']
  if (!keys.includes(event.key)) {
    return
  }
  event.preventDefault()
  const idx = payOptions.value.findIndex((o) => o.key === selectedKey.value)
  const delta = event.key === 'ArrowRight' || event.key === 'ArrowDown' ? 1 : -1
  const next = (idx + delta + payOptions.value.length) % payOptions.value.length
  selectedKey.value = payOptions.value[next].key
  document.getElementById(`pay-option-${payOptions.value[next].key}`)?.focus()
}

async function confirm() {
  const option = selectedOption.value
  if (!option || !stripe || !intentInfo.value || submitting.value) {
    return
  }
  submitting.value = true
  try {
    // 支付前先核实订单状态：页面可能已挂很久，client_secret 虽在手，超时/已取消单不放行
    try {
      const check = await verifyOrder(orderNo)
      if (isPaidStatus(check.status)) {
        goResult()
        return
      }
      if (check.status === 'EXPIRED') {
        showExpiredState()
        return
      }
      if (check.status === 'CANCELLED') {
        showToast('error', t('payment.notPayable'))
        router.push('/orders')
        return
      }
    } catch {
      // 核实失败（网络抖动）不阻断支付：后端与 Stripe 侧仍有各自的拦截
    }
    const clientSecret = intentInfo.value.clientSecret
    if (option.subMethod === 'wxpay') {
      const qr = await startWechatPay(stripe, clientSecret)
      await showQr('wxpay', qr)
      startPolling()
    } else if (option.subMethod === 'alipay') {
      const { qrUrl } = await startAlipay(stripe, clientSecret, returnUrl)
      if (qrUrl) {
        await showQr('alipay', qrUrl)
        startPolling()
      }
      // 移动端整页跳转中，无需轮询
    } else {
      if (!cardElements) {
        return
      }
      const status = await confirmCardPayment(stripe, cardElements, clientSecret, returnUrl)
      if (status === 'succeeded') {
        goResult()
      } else {
        startPolling()
      }
    }
  } catch (e) {
    showToast('error', e instanceof Error ? (e.message ?? t('api.requestFailed')) : t('api.requestFailed'))
  } finally {
    submitting.value = false
  }
}

/** 本地渲染二维码（200×200）并启动有效期倒计时 */
async function showQr(kind: StripeSubMethod, content: string) {
  qrFor.value = kind
  qrSecondsLeft.value = QR_TTL_SECONDS
  clearInterval(qrTimer)
  qrTimer = setInterval(() => {
    if (qrSecondsLeft.value > 0) {
      qrSecondsLeft.value -= 1
    } else {
      clearInterval(qrTimer)
    }
  }, 1000)
  // 等二维码容器渲染
  await Promise.resolve()
  if (qrCanvas.value) {
    const { toCanvas } = await import('qrcode')
    await toCanvas(qrCanvas.value, content, { width: 200, margin: 1 })
  }
}

/** 每 2s 核实一次：成功跳结果页；过期/取消也是终态，停止轮询 */
function startPolling() {
  if (polling.value) {
    return
  }
  polling.value = true
  pollTimer = setInterval(async () => {
    try {
      const result = await verifyOrder(orderNo)
      if (isPaidStatus(result.status)) {
        clearInterval(pollTimer)
        polling.value = false
        goResult()
      } else if (result.status === 'EXPIRED') {
        showExpiredState()
      } else if (result.status === 'CANCELLED') {
        clearInterval(pollTimer)
        polling.value = false
        showToast('error', t('payment.notPayable'))
        router.push('/orders')
      }
    } catch {
      // 轮询失败不打断（网络抖动下一轮再试）
    }
  }, 2000)
}

function goResult() {
  router.push({ path: '/payment/result', query: { order_no: orderNo } })
}

async function onCancel() {
  if (!window.confirm(t('payment.cancelConfirm'))) {
    return
  }
  try {
    await cancelOrder(orderNo)
    showToast('success', t('payment.cancelled'))
    router.push('/orders')
  } catch (e) {
    showToast('error', e instanceof Error ? e.message : t('api.requestFailed'))
  }
}
</script>

<template>
  <main class="page">
    <h2 class="title">{{ $t('payment.title') }}</h2>

    <p v-if="loading" class="hint">{{ $t('common.loading') }}</p>
    <div v-else-if="loadError" class="hint error">
      {{ loadError }}
      <RouterLink to="/orders" class="link">{{ $t('payment.viewOrders') }}</RouterLink>
    </div>

    <template v-else-if="intentInfo">
      <!-- 订单摘要 -->
      <section class="summary">
        <h3 class="section-label">{{ $t('payment.orderSummary') }}</h3>
        <div class="summary-row">
          <span class="product">{{ intentInfo.productName }} × {{ intentInfo.quantity }}</span>
          <span class="amount-label">{{ $t('payment.payAmount') }}</span>
        </div>
        <div class="summary-row">
          <span class="order-no">{{ $t('orders.orderNo', { orderNo: intentInfo.orderNo }) }}</span>
          <span class="amount">{{ formatPrice(intentInfo.amountCents) }}</span>
        </div>
        <p v-if="!orderExpired && orderSecondsLeft > 0" class="pay-deadline">
          {{ $t('payment.payDeadline', { time: orderCountdown }) }}
        </p>
      </section>

      <!-- 订单已过期：替换支付面板，引导重新下单 -->
      <div v-if="orderExpired" class="hint error">
        {{ $t('payment.orderExpired') }}
        <RouterLink to="/orders" class="link">{{ $t('payment.viewOrders') }}</RouterLink>
      </div>

      <!-- 支付方式（拍平三卡，INVARIANT） -->
      <section v-else class="pay-panel">
        <div class="panel-head">
          <h3 class="section-label">{{ $t('payment.paymentMethod') }}</h3>
          <i18n-t keypath="payment.poweredBy" tag="span" class="powered-by">
            <template #provider><b class="stripe-word">Stripe</b></template>
          </i18n-t>
        </div>

        <div
          class="method-grid"
          role="radiogroup"
          :aria-label="$t('payment.paymentMethod')"
          @keydown="onRadioKeydown"
        >
          <button
            v-for="option in payOptions"
            :id="`pay-option-${option.key}`"
            :key="option.key"
            type="button"
            role="radio"
            class="method-card"
            :class="{ selected: option.key === selectedKey }"
            :aria-checked="option.key === selectedKey"
            :tabindex="option.key === selectedKey ? 0 : -1"
            @click="selectedKey = option.key"
          >
            <!-- 图标：一律内联 SVG（含「支」字），不引外链图片 -->
            <span v-if="option.subMethod === 'wxpay'" class="icon icon-wxpay" aria-hidden="true">
              <svg viewBox="0 0 24 24" width="22" height="22" fill="#ffffff"><path d="M9.5 4C5.9 4 3 6.5 3 9.6c0 1.8 1 3.4 2.5 4.4l-.6 2 2.2-1.2c.6.2 1.3.3 2 .3h.3A5.3 5.3 0 0 1 9 13c0-2.9 2.8-5.2 6.2-5.2h.4C15 5.6 12.5 4 9.5 4Zm-2.2 2.9a.9.9 0 1 1 0 1.8.9.9 0 0 1 0-1.8Zm4.6 0a.9.9 0 1 1 0 1.8.9.9 0 0 1 0-1.8ZM15.2 9c-3 0-5.4 1.9-5.4 4.3 0 2.4 2.4 4.3 5.4 4.3.6 0 1.2-.1 1.7-.2l1.9 1-.5-1.7c1.4-.8 2.3-2 2.3-3.4C20.6 10.9 18.2 9 15.2 9Zm-1.9 2.4a.8.8 0 1 1 0 1.6.8.8 0 0 1 0-1.6Zm3.8 0a.8.8 0 1 1 0 1.6.8.8 0 0 1 0-1.6Z"/></svg>
            </span>
            <span v-else-if="option.subMethod === 'alipay'" class="icon icon-alipay" aria-hidden="true">支</span>
            <span v-else class="icon icon-card" aria-hidden="true">
              <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="#ffffff" stroke-width="2" stroke-linecap="round"><rect x="2.5" y="5" width="19" height="14" rx="2.5"/><path d="M2.5 9.5h19"/><path d="M6 15h4"/></svg>
            </span>
            <span class="method-text">
              <span class="method-name">{{
                option.subMethod === 'wxpay'
                  ? $t('payment.methodWxpay')
                  : option.subMethod === 'alipay'
                    ? $t('payment.methodAlipay')
                    : $t('payment.methodCard')
              }}</span>
              <span class="method-desc">{{
                option.subMethod === 'card' ? $t('payment.methodCardDesc') : $t('payment.methodScanDesc')
              }}</span>
            </span>
            <span class="radio-dot" aria-hidden="true"></span>
          </button>
        </div>

        <!-- 银行卡：Payment Element（deferred，只渲染 card 一种） -->
        <div v-show="isCardSelected && !qrFor" ref="cardMount" class="card-element"></div>

        <!-- 二维码（微信/支付宝桌面端） -->
        <div v-if="qrFor" class="qr-box">
          <canvas ref="qrCanvas" width="200" height="200" class="qr-canvas"></canvas>
          <p class="qr-hint">
            {{ qrFor === 'wxpay' ? $t('payment.scanWithWechat') : $t('payment.scanWithAlipay') }}
          </p>
          <p v-if="qrSecondsLeft > 0" class="qr-expiry">
            {{ $t('payment.qrExpiry', { time: qrCountdown }) }}
          </p>
          <p v-else class="qr-expiry expired">
            {{ $t('payment.qrExpired') }}
            <button type="button" class="link-btn" @click="confirm">
              {{ $t('payment.regenerateQr') }}
            </button>
          </p>
          <p v-if="polling" class="qr-hint">{{ $t('payment.processing') }}…</p>
        </div>

        <div class="actions">
          <button
            v-if="!qrFor"
            type="button"
            class="pay-btn"
            :disabled="submitting || !selectedOption"
            @click="confirm"
          >
            {{
              submitting
                ? $t('payment.submitting')
                : isCardSelected
                  ? $t('payment.stripePay')
                  : $t('payment.confirmPay')
            }}
          </button>
          <button type="button" class="cancel-btn" @click="onCancel">
            {{ $t('payment.cancelOrder') }}
          </button>
        </div>

        <!-- 底部安全提示（盾牌 + 虚线分隔，不可省） -->
        <div class="security-note">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 3l7 3v5c0 4.4-3 8.3-7 9.5C8 19.3 5 15.4 5 11V6l7-3Z"/><path d="M9 11.5l2 2 4-4"/></svg>
          <i18n-t keypath="payment.securityNote" tag="span">
            <template #provider><b class="stripe-word">Stripe</b></template>
          </i18n-t>
        </div>
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
  margin-left: 8px;
}

.section-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-ink);
}

.summary {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  padding: 16px 20px;
  margin-bottom: 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.summary-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}

.product {
  color: var(--color-ink);
  font-weight: 500;
}

.order-no,
.amount-label {
  font-size: 13px;
  color: var(--color-ink-secondary);
}

.amount {
  font-size: 20px;
  font-weight: 600;
  color: var(--color-brand-deep);
}

/* 订单级支付时限提示（区别于二维码有效期） */
.pay-deadline {
  font-size: 13px;
  color: #b45309;
}

.pay-panel {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  padding: 16px 20px 20px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.powered-by {
  font-size: 12px;
  color: var(--color-ink-secondary);
}

.stripe-word {
  color: #635bff;
}

/* 三卡平级并列：移动端单列、桌面端三列（INVARIANT） */
.method-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
}

@media (min-width: 640px) {
  .method-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

.method-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-bg);
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease;
}

.method-card:hover {
  border-color: var(--color-brand);
}

.method-card:focus-visible {
  outline: 2px solid var(--color-brand-deep);
  outline-offset: 2px;
}

/* 选中态：强调色边框 + 6% 底 + 右侧单选点 */
.method-card.selected {
  border-color: var(--color-brand-deep);
  background: rgba(23, 209, 167, 0.06);
}

.icon {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.icon-wxpay {
  background: #09bb07;
}

.icon-alipay {
  background: #1677ff;
  color: #ffffff;
  font-size: 18px;
  font-weight: 700;
}

.icon-card {
  background: #635bff;
}

.method-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.method-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-ink);
}

.method-desc {
  font-size: 12px;
  color: var(--color-ink-secondary);
}

.radio-dot {
  width: 16px;
  height: 16px;
  border: 2px solid var(--color-border);
  border-radius: 50%;
  flex-shrink: 0;
}

.method-card.selected .radio-dot {
  border-color: var(--color-brand-deep);
  background: radial-gradient(circle, var(--color-brand-deep) 45%, transparent 50%);
}

.card-element {
  margin-top: 16px;
}

.qr-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 20px 0 4px;
}

.qr-canvas {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
}

.qr-hint {
  font-size: 14px;
  color: var(--color-ink);
}

.qr-expiry {
  font-size: 13px;
  color: var(--color-ink-secondary);
}

.qr-expiry.expired {
  color: #b91c1c;
}

.link-btn {
  border: none;
  background: none;
  color: var(--color-brand-deep);
  font-family: inherit;
  font-size: 13px;
  cursor: pointer;
  text-decoration: underline;
}

.actions {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 16px;
}

.pay-btn {
  padding: 10px 32px;
  border: none;
  border-radius: var(--radius-button);
  background: var(--color-brand);
  color: #ffffff;
  font-size: 15px;
  font-weight: 500;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.15s ease;
}

.pay-btn:hover:not(:disabled) {
  background: var(--color-brand-deep);
}

.pay-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.cancel-btn {
  border: none;
  background: none;
  color: var(--color-ink-secondary);
  font-family: inherit;
  font-size: 14px;
  cursor: pointer;
}

.cancel-btn:hover {
  color: #b91c1c;
}

/* 虚线分隔的底部安全提示 */
.security-note {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px dashed var(--color-border);
  font-size: 12px;
  color: var(--color-ink-secondary);
}
</style>
