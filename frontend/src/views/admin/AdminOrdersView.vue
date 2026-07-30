<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { formatPrice } from '../../api'
import { fetchAdminOrders, type AdminOrderItem } from '../../api-admin'
import { formatDateTime } from '../../datetime'
import { t } from '../../i18n'

/** 筛选条状态顺序（与后端 OrderStatusEnum 对齐） */
const STATUS_ORDER = ['PENDING', 'PAID', 'COMPLETED', 'FAILED', 'CANCELLED', 'EXPIRED']

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
    loadError.value = e instanceof Error ? e.message : t('common.loadFailed')
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
  <h2 class="admin-title">{{ $t('admin.orders.title') }}</h2>

  <div class="admin-toolbar">
    <button
      type="button"
      class="admin-chip"
      :class="{ active: activeStatus === 'ALL' }"
      @click="activeStatus = 'ALL'"
    >
      {{ $t('admin.orders.filterAll') }}
    </button>
    <button
      v-for="status in STATUS_ORDER"
      :key="status"
      type="button"
      class="admin-chip"
      :class="{ active: activeStatus === status }"
      @click="activeStatus = status"
    >
      {{ $t(`admin.status.${status}`) }}
    </button>
    <span class="spacer"></span>
    <form class="search" @submit.prevent="onSearch">
      <input
        v-model="keywordDraft"
        class="admin-input"
        :placeholder="$t('admin.orders.searchPlaceholder')"
      />
      <button type="submit" class="admin-btn-ghost">{{ $t('admin.orders.search') }}</button>
    </form>
  </div>

  <p v-if="loading" class="admin-hint">{{ $t('common.loading') }}</p>
  <p v-else-if="loadError" class="admin-hint error">{{ loadError }}</p>

  <template v-else>
    <div class="admin-card">
      <p v-if="records.length === 0" class="admin-hint">{{ $t('admin.table.empty') }}</p>
      <table v-else class="admin-table">
        <thead>
          <tr>
            <th>{{ $t('admin.orders.orderNo') }}</th>
            <th>{{ $t('admin.orders.product') }}</th>
            <th>{{ $t('admin.orders.buyer') }}</th>
            <th>{{ $t('admin.orders.quantity') }}</th>
            <th>{{ $t('admin.orders.amount') }}</th>
            <th>{{ $t('admin.orders.status') }}</th>
            <th>{{ $t('admin.orders.provider') }}</th>
            <th>{{ $t('admin.orders.createdAt') }}</th>
            <th>{{ $t('admin.orders.paidAt') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="order in records" :key="order.orderNo">
            <td class="mono">{{ order.orderNo }}</td>
            <td>{{ order.productName }}</td>
            <td>{{ order.buyerEmail ?? $t('admin.orders.guest') }}</td>
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
        {{ $t('admin.table.prev') }}
      </button>
      <span class="info">{{ $t('admin.table.pageInfo', { page, total }) }}</span>
      <button
        type="button"
        class="admin-btn-ghost"
        :disabled="page >= totalPages"
        @click="gotoPage(page + 1)"
      >
        {{ $t('admin.table.next') }}
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
