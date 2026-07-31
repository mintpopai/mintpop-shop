<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { formatPrice } from '../api'
import { fetchAdminOrders, type AdminOrderItem } from '../api-admin'
import { formatDateTime } from '../datetime'

/** 筛选条状态与中文标签（顺序与后端 OrderStatusEnum 对齐） */
const STATUS_FILTERS: Array<{ status: string; label: string }> = [
  { status: 'PENDING', label: '待支付' },
  { status: 'PAID', label: '已支付' },
  { status: 'COMPLETED', label: '已完成' },
  { status: 'FAILED', label: '支付失败' },
  { status: 'CANCELLED', label: '已取消' },
  { status: 'EXPIRED', label: '已过期' },
]

const PAGE_SIZE = 20

const records = ref<AdminOrderItem[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(true)
const loadError = ref('')

/** 'ALL' 表示不过滤；服务端筛选 */
const activeStatus = ref('ALL')
/** 输入框草稿与已生效的搜索词分开，点搜索/回车才生效 */
const keywordDraft = ref('')
const keyword = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))

/** 页头那行事实：当前筛选下有多少笔 */
const scopeLabel = computed(
  () => STATUS_FILTERS.find((item) => item.status === activeStatus.value)?.label ?? '全部',
)

async function reload() {
  loading.value = true
  try {
    const result = await fetchAdminOrders({
      page: page.value,
      size: PAGE_SIZE,
      status: activeStatus.value === 'ALL' ? undefined : activeStatus.value,
      keyword: keyword.value || undefined,
    })
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

// 切筛选/搜索回第一页重查
watch([activeStatus, keyword], () => {
  page.value = 1
  reload()
})

function onSearch() {
  keyword.value = keywordDraft.value.trim()
}

function gotoPage(next: number) {
  if (next < 1 || next > totalPages.value || next === page.value) {
    return
  }
  page.value = next
  reload()
}
</script>

<template>
  <header class="page-head">
    <h2 class="page-title">订单</h2>
    <p class="page-facts">
      {{ activeStatus === 'ALL' ? '全部' : scopeLabel }}共 <span class="fact">{{ total }}</span> 笔<template
        v-if="keyword"
        >，订单号含「{{ keyword }}」</template
      >
    </p>
  </header>

  <div class="admin-toolbar">
    <button
      type="button"
      class="admin-chip"
      :class="{ active: activeStatus === 'ALL' }"
      @click="activeStatus = 'ALL'"
    >
      全部
    </button>
    <button
      v-for="item in STATUS_FILTERS"
      :key="item.status"
      type="button"
      class="admin-chip"
      :class="{ active: activeStatus === item.status }"
      :data-state="item.status"
      @click="activeStatus = item.status"
    >
      {{ item.label }}
    </button>
    <span class="spacer"></span>
    <form class="search" @submit.prevent="onSearch">
      <input v-model="keywordDraft" class="admin-input" placeholder="按订单号搜索" />
      <button type="submit" class="admin-btn-ghost">搜索</button>
    </form>
  </div>

  <p v-if="loading" class="admin-hint loading">加载中……</p>
  <p v-else-if="loadError" class="admin-hint error">{{ loadError }}</p>

  <template v-else>
    <div class="admin-card">
      <p v-if="records.length === 0" class="admin-hint">
        {{ keyword || activeStatus !== 'ALL' ? '没有符合条件的订单，换个状态或搜索词试试。' : '还没有订单。' }}
      </p>
      <table v-else class="admin-table">
        <thead>
          <tr>
            <th>订单号</th>
            <th>商品</th>
            <th>买家</th>
            <th class="col-amount">数量</th>
            <th class="col-amount">金额</th>
            <th>状态</th>
            <th>支付方式</th>
            <th>下单时间</th>
            <th>支付时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="order in records" :key="order.orderNo">
            <td class="fact">{{ order.orderNo }}</td>
            <td>{{ order.productName }}</td>
            <td>{{ order.buyerEmail ?? '游客' }}</td>
            <td class="fact col-amount">{{ order.quantity }}</td>
            <td class="fact col-amount">{{ formatPrice(order.amountCents) }}</td>
            <td>
              <span class="state" :data-state="order.status">{{ order.statusLabel }}</span>
            </td>
            <td class="fact muted">{{ order.paymentProvider ?? '—' }}</td>
            <td class="fact muted">{{ formatDateTime(order.createdAt) }}</td>
            <td class="fact muted">{{ order.paidAt ? formatDateTime(order.paidAt) : '—' }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="admin-pager">
      <button type="button" class="admin-btn-ghost" :disabled="page <= 1" @click="gotoPage(page - 1)">
        上一页
      </button>
      <span class="info">
        第 <span class="fact">{{ page }}</span> / <span class="fact">{{ totalPages }}</span> 页 · 共
        <span class="fact">{{ total }}</span> 条
      </span>
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
.search {
  display: flex;
  gap: 8px;
}
</style>
