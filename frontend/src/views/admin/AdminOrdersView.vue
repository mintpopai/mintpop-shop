<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { formatPrice } from '../../api'
import { fetchAdminOrders, type AdminOrderItem } from '../../api-admin'
import { formatDateTime } from '../../datetime'

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
  <h2 class="admin-title">订单管理</h2>

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

  <p v-if="loading" class="admin-hint">加载中……</p>
  <p v-else-if="loadError" class="admin-hint error">{{ loadError }}</p>

  <template v-else>
    <div class="admin-card">
      <p v-if="records.length === 0" class="admin-hint">暂无数据</p>
      <table v-else class="admin-table">
        <thead>
          <tr>
            <th>订单号</th>
            <th>商品</th>
            <th>买家</th>
            <th>数量</th>
            <th>金额</th>
            <th>状态</th>
            <th>支付方式</th>
            <th>下单时间</th>
            <th>支付时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="order in records" :key="order.orderNo">
            <td class="mono">{{ order.orderNo }}</td>
            <td>{{ order.productName }}</td>
            <td>{{ order.buyerEmail ?? '游客' }}</td>
            <td>{{ order.quantity }}</td>
            <td class="amount">{{ formatPrice(order.amountCents) }}</td>
            <td>
              <span class="status-tag" :class="`status-tag--${order.status}`">{{ order.statusLabel }}</span>
            </td>
            <td>{{ order.paymentProvider ?? '—' }}</td>
            <td class="secondary">{{ formatDateTime(order.createdAt) }}</td>
            <td class="secondary">{{ order.paidAt ? formatDateTime(order.paidAt) : '—' }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="admin-pager">
      <button type="button" class="admin-btn-ghost" :disabled="page <= 1" @click="gotoPage(page - 1)">
        上一页
      </button>
      <span class="info">第 {{ page }} 页 · 共 {{ total }} 条</span>
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

.mono {
  font-size: 13px;
  white-space: nowrap;
}

.amount {
  font-weight: 600;
  color: var(--color-brand-deep);
  white-space: nowrap;
}

.secondary {
  color: var(--color-ink-secondary);
  font-size: 13px;
  white-space: nowrap;
}
</style>
