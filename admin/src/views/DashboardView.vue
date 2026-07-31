<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { formatPrice } from '../api'
import { fetchAdminDashboard, type AdminDashboard, type AdminOrderItem } from '../api-admin'
import { formatUtcDateTime, formatUtcTime, utcDate, utcDayProgress } from '../datetime'

const dashboard = ref<AdminDashboard | null>(null)
const loading = ref(true)
const loadError = ref('')
/** 打开页面那一刻的 UTC 时间，用于纸带的「此刻」标线与今日判定 */
const openedAt = ref(new Date())

onMounted(async () => {
  try {
    dashboard.value = await fetchAdminDashboard()
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
})

const today = computed(() => utcDate(openedAt.value))

/** 最近订单里属于今天（UTC）的部分，按时间正序——纸带只画得出手上有的这些 */
const todayOrders = computed(() =>
  (dashboard.value?.recentOrders ?? [])
    .filter((o) => utcDate(o.createdAt) === today.value)
    .sort((a, b) => a.createdAt.localeCompare(b.createdAt)),
)

/** 一笔订单一根竖条：横轴是 UTC 一天的时刻，高度按金额相对当天最大额 */
const bars = computed(() => {
  const orders = todayOrders.value
  const maxCents = orders.reduce((max, o) => Math.max(max, o.amountCents), 0) || 1
  return orders.map((order: AdminOrderItem, index: number) => ({
    order,
    left: utcDayProgress(order.createdAt) * 100,
    height: 20 + (order.amountCents / maxCents) * 80,
    delay: index * 40,
  }))
})

const nowLeft = computed(() => utcDayProgress(openedAt.value) * 100)

/** 后端的今日笔数才是权威值；纸带只取自最近订单，条数对不上时要说明 */
const hiddenCount = computed(() => Math.max(0, (dashboard.value?.todayOrderCount ?? 0) - bars.value.length))

const tapeLabel = computed(() =>
  dashboard.value
    ? `今日（UTC）${dashboard.value.todayOrderCount} 笔订单，营收 ${formatPrice(dashboard.value.todayRevenueCents)}`
    : '',
)
</script>

<template>
  <header class="page-head">
    <h2 class="page-title">概览</h2>
    <p class="page-facts">
      今天是 <span class="fact">{{ today }}</span
      >。这一页的时间全部按 UTC 显示，与后端结算「今日」的口径一致。
    </p>
  </header>

  <p v-if="loading" class="admin-hint loading">加载中……</p>
  <p v-else-if="loadError" class="admin-hint error">{{ loadError }}</p>

  <template v-else-if="dashboard">
    <!-- 今日纸带：左边是今天收了多少，右边是这些钱在一天里什么时候进来的 -->
    <section class="tape-card">
      <div class="tape-lead">
        <p class="tape-lead-label">今日营收</p>
        <p class="tape-lead-value fact">{{ formatPrice(dashboard.todayRevenueCents) }}</p>
        <p class="tape-lead-sub">
          <span class="fact">{{ dashboard.todayOrderCount }}</span> 笔订单
          <template v-if="hiddenCount > 0">
            ·<br />纸带画出其中最近 <span class="fact">{{ bars.length }}</span> 笔
          </template>
        </p>
      </div>

      <div class="tape" role="img" :aria-label="tapeLabel">
        <div class="tape-track">
          <div
            class="tape-now"
            :class="{ flip: nowLeft > 82 }"
            :style="{ left: `${nowLeft}%` }"
            aria-hidden="true"
          >
            <span class="tape-now-label"
              >此刻 <span class="fact">{{ formatUtcTime(openedAt.toISOString()) }}</span></span
            >
          </div>

          <div
            v-for="bar in bars"
            :key="bar.order.orderNo"
            class="tape-bar"
            :data-state="bar.order.status"
            :style="{ left: `${bar.left}%`, height: `${bar.height}%`, animationDelay: `${bar.delay}ms` }"
          >
            <span class="tape-tip">
              <span class="fact">{{ formatUtcTime(bar.order.createdAt) }}</span>
              <span class="fact tape-tip-amount">{{ formatPrice(bar.order.amountCents) }}</span>
              <!-- 柱子的颜色只是辅助，状态在这里始终有文字 -->
              <span class="tape-tip-state">{{ bar.order.statusLabel }}</span>
              <span class="tape-tip-name">{{ bar.order.productName }}</span>
            </span>
          </div>

          <p v-if="bars.length === 0" class="tape-empty">今天还没有订单</p>
        </div>

        <div class="tape-axis fact" aria-hidden="true">
          <span>00</span><span>06</span><span>12</span><span>18</span><span>24</span>
        </div>
      </div>
    </section>

    <!-- 累计数据不抢戏：一行事实，看一眼就够 -->
    <dl class="totals">
      <div class="total">
        <dt>累计营收</dt>
        <dd class="fact">{{ formatPrice(dashboard.totalRevenueCents) }}</dd>
      </div>
      <div class="total">
        <dt>累计订单</dt>
        <dd class="fact">{{ dashboard.totalOrderCount }}</dd>
      </div>
      <div class="total">
        <dt>注册用户</dt>
        <dd class="fact">{{ dashboard.userCount }}</dd>
      </div>
      <div class="total">
        <dt>在售商品</dt>
        <dd class="fact">{{ dashboard.onSaleProductCount }}</dd>
      </div>
    </dl>

    <h3 class="section-title">最近订单</h3>
    <div class="admin-card">
      <p v-if="dashboard.recentOrders.length === 0" class="admin-hint">
        还没有订单。商城下出第一单后，它会出现在这里。
      </p>
      <table v-else class="admin-table">
        <thead>
          <tr>
            <th>订单号</th>
            <th>商品</th>
            <th>买家</th>
            <th class="col-amount">金额</th>
            <th>状态</th>
            <th>下单时间（UTC）</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="order in dashboard.recentOrders" :key="order.orderNo">
            <td class="fact">{{ order.orderNo }}</td>
            <td>{{ order.productName }}</td>
            <td>{{ order.buyerEmail ?? '游客' }}</td>
            <td class="fact col-amount">{{ formatPrice(order.amountCents) }}</td>
            <td>
              <span class="state" :data-state="order.status">{{ order.statusLabel }}</span>
            </td>
            <td class="fact muted">{{ formatUtcDateTime(order.createdAt) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </template>
</template>

<style scoped>
.tape-card {
  display: flex;
  gap: 32px;
  padding: 24px;
  background: var(--color-bg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
}

/* 不写死宽度：营收位数变多时让左栏自己撑开、纸带相应变窄，而不是把数字压到纸带上 */
.tape-lead {
  flex-shrink: 0;
  min-width: 192px;
}

.tape-lead-label {
  font-size: 13px;
  color: var(--color-ink-secondary);
}

/* 页面的焦点数字。不染品牌绿：#0FB389 对白底只有 2.7:1，达不到大字要求的 3:1；
   46px 的墨色本身就够重，染成浅绿反而更轻 */
.tape-lead-value {
  margin-top: 8px;
  font-size: clamp(34px, 4vw, 46px);
  line-height: 1.05;
  letter-spacing: -0.02em;
  color: var(--color-ink);
}

.tape-lead-sub {
  margin-top: 12px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-ink-secondary);
}

.tape {
  flex: 1;
  min-width: 0;
}

.tape-track {
  position: relative;
  height: 104px;
  border-bottom: 1px solid var(--color-border);
}

/* 此刻：一天已经走到哪儿了 */
.tape-now {
  position: absolute;
  top: 0;
  bottom: 0;
  border-left: 1px dashed var(--color-border);
}

.tape-now-label {
  position: absolute;
  top: -2px;
  left: 6px;
  font-size: 11px;
  color: var(--color-ink-secondary);
  white-space: nowrap;
}

/* 临近一天末尾时把「此刻」标签翻到线的左侧，避免溢出 */
.tape-now.flip .tape-now-label {
  left: auto;
  right: 6px;
}

.tape-bar {
  position: absolute;
  bottom: 0;
  width: 6px;
  margin-left: -3px;
  border-radius: 3px 3px 0 0;
  background: var(--state-color, #7a857f);
  animation: bar-rise 0.55s cubic-bezier(0.2, 0.8, 0.2, 1) backwards;
}

@keyframes bar-rise {
  from {
    height: 0;
  }
}

/* 柱子只有 6px 宽，用透明区域把鼠标命中范围撑开 */
.tape-bar::before {
  content: '';
  position: absolute;
  inset: -6px -7px 0;
}

.tape-tip {
  position: absolute;
  bottom: calc(100% + 8px);
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: baseline;
  gap: 12px;
  padding: 8px 12px;
  border-radius: var(--radius-button);
  background: var(--counter-rail);
  color: #ffffff;
  font-size: 12px;
  white-space: nowrap;
  max-width: 320px;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.12s ease;
}

.tape-bar:hover .tape-tip {
  opacity: 1;
}

.tape-tip-amount {
  color: var(--color-brand);
}

.tape-tip-state,
.tape-tip-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--counter-rail-text);
}

.tape-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  color: var(--color-ink-secondary);
}

/* 刻度必须精确落在 0/25/50/75/100%——space-between 会把首末标签整体内缩半个字宽，
   与柱子的百分比定位对不上，所以逐个绝对定位再居中 */
.tape-axis {
  position: relative;
  height: 16px;
  margin-top: 8px;
  font-size: 11px;
  color: var(--color-ink-secondary);
}

.tape-axis span {
  position: absolute;
  transform: translateX(-50%);
}

.tape-axis span:nth-child(1) {
  left: 0;
}

.tape-axis span:nth-child(2) {
  left: 25%;
}

.tape-axis span:nth-child(3) {
  left: 50%;
}

.tape-axis span:nth-child(4) {
  left: 75%;
}

.tape-axis span:nth-child(5) {
  left: 100%;
}

/* —— 累计事实条 —— */
.totals {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 0;
  margin: 16px 0 32px;
}

.total {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding-right: 24px;
}

.total + .total {
  padding-left: 24px;
  border-left: 1px solid var(--color-border);
}

.total dt {
  font-size: 12px;
  color: var(--color-ink-secondary);
}

.total dd {
  font-size: 15px;
  color: var(--color-ink);
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 12px;
}

@media (max-width: 640px) {
  .tape-card {
    flex-direction: column;
    gap: 24px;
  }

  .tape-lead {
    min-width: 0;
  }

  .total + .total {
    padding-left: 0;
    border-left: none;
  }
}
</style>
