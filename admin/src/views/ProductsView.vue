<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import Modal from '../components/Modal.vue'
import { formatPrice } from '../api'
import {
  createAdminProduct,
  fetchAdminGroups,
  fetchAdminProducts,
  setAdminProductOnSale,
  updateAdminProduct,
  type AdminGroup,
  type AdminProduct,
} from '../api-admin'
import { showToast } from '../toast'

/** accent 枚举与商品卡的预览色（点缀色一致，仅作后台辨识） */
const ACCENTS: Record<string, string> = {
  MINT: '#17d1a7',
  VIOLET: '#6d5bd0',
  SKY: '#2f7fd1',
  AMBER: '#c07f1f',
  ROSE: '#d04a68',
}

const products = ref<AdminProduct[]>([])
const groups = ref<AdminGroup[]>([])
const loading = ref(true)
const loadError = ref('')

/** 分组筛选：0 = 全部（select 的 number 绑定用 0 兜底） */
const groupFilter = ref(0)

const filteredProducts = computed(() =>
  groupFilter.value === 0
    ? products.value
    : products.value.filter((p) => p.groupId === groupFilter.value),
)

/** 页头那行事实：当前筛选下的上下架构成 */
const onSaleCount = computed(() => filteredProducts.value.filter((p) => p.onSale).length)

/** 管理端固定中文：分组显示中文名 */
function groupName(groupId: number): string {
  return groups.value.find((g) => g.id === groupId)?.nameZh ?? String(groupId)
}

/** 弹窗状态：editingId 为空表示新增；价格以美元字符串编辑、提交时转美分 */
const modalOpen = ref(false)
const editingId = ref<number | null>(null)
const form = ref({
  groupId: 0,
  nameZh: '',
  nameEn: '',
  descriptionZh: '',
  descriptionEn: '',
  badgeZh: '',
  badgeEn: '',
  accent: 'MINT',
  priceUsd: '',
  imageUrl: '',
  onSale: true,
})
const saving = ref(false)

async function reload() {
  try {
    ;[groups.value, products.value] = await Promise.all([fetchAdminGroups(), fetchAdminProducts()])
    loadError.value = ''
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

onMounted(reload)

function openCreate() {
  editingId.value = null
  form.value = {
    groupId: groupFilter.value || groups.value[0]?.id || 0,
    nameZh: '',
    nameEn: '',
    descriptionZh: '',
    descriptionEn: '',
    badgeZh: '',
    badgeEn: '',
    accent: 'MINT',
    priceUsd: '',
    imageUrl: '',
    onSale: true,
  }
  modalOpen.value = true
}

function openEdit(product: AdminProduct) {
  editingId.value = product.id
  form.value = {
    groupId: product.groupId,
    nameZh: product.nameZh,
    nameEn: product.nameEn ?? '',
    descriptionZh: product.descriptionZh ?? '',
    descriptionEn: product.descriptionEn ?? '',
    badgeZh: product.badgeZh ?? '',
    badgeEn: product.badgeEn ?? '',
    accent: product.accent,
    priceUsd: (product.priceCents / 100).toFixed(2),
    imageUrl: product.imageUrl ?? '',
    onSale: product.onSale,
  }
  modalOpen.value = true
}

async function onSave() {
  const priceCents = Math.round(Number(form.value.priceUsd) * 100)
  if (!form.value.nameZh.trim() || !form.value.groupId || !Number.isFinite(priceCents) || priceCents < 1) {
    showToast('error', '请完整填写必填项')
    return
  }
  saving.value = true
  const body = {
    groupId: form.value.groupId,
    nameZh: form.value.nameZh,
    nameEn: form.value.nameEn,
    descriptionZh: form.value.descriptionZh,
    descriptionEn: form.value.descriptionEn,
    badgeZh: form.value.badgeZh,
    badgeEn: form.value.badgeEn,
    accent: form.value.accent,
    priceCents,
    imageUrl: form.value.imageUrl,
    onSale: form.value.onSale,
  }
  try {
    if (editingId.value === null) {
      await createAdminProduct(body)
    } else {
      await updateAdminProduct(editingId.value, body)
    }
    showToast('success', '已保存')
    modalOpen.value = false
    await reload()
  } catch (e) {
    showToast('error', e instanceof Error ? e.message : '请求失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

async function onToggleSale(product: AdminProduct) {
  try {
    const updated = await setAdminProductOnSale(product.id, !product.onSale)
    products.value = products.value.map((p) => (p.id === updated.id ? updated : p))
    showToast('success', '已更新上架状态')
  } catch (e) {
    showToast('error', e instanceof Error ? e.message : '请求失败，请稍后重试')
  }
}
</script>

<template>
  <header class="page-head">
    <h2 class="page-title">商品</h2>
    <p class="page-facts">
      共 <span class="fact">{{ filteredProducts.length }}</span> 件 · 在售
      <span class="fact">{{ onSaleCount }}</span> · 已下架
      <span class="fact">{{ filteredProducts.length - onSaleCount }}</span>
    </p>
  </header>

  <div class="admin-toolbar">
    <select v-model.number="groupFilter" class="admin-select" aria-label="按分组筛选">
      <option :value="0">全部分组</option>
      <option v-for="group in groups" :key="group.id" :value="group.id">{{ group.nameZh }}</option>
    </select>
    <span class="spacer"></span>
    <button type="button" class="admin-btn" @click="openCreate">新增商品</button>
  </div>

  <p v-if="loading" class="admin-hint">加载中……</p>
  <p v-else-if="loadError" class="admin-hint error">{{ loadError }}</p>

  <div v-else class="admin-card">
    <p v-if="filteredProducts.length === 0" class="admin-hint">
      {{ groupFilter === 0 ? '还没有商品。新增的商品会出现在商城首页。' : '这个分组下还没有商品。' }}
    </p>
    <table v-else class="admin-table">
      <thead>
        <tr>
          <th>ID</th>
          <th>名称</th>
          <th>分组</th>
          <th class="col-amount">价格</th>
          <th>角标</th>
          <th>主题色</th>
          <th>状态</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="product in filteredProducts" :key="product.id">
          <td class="fact muted">{{ product.id }}</td>
          <td>
            <div class="name-cell">
              <span>{{ product.nameZh }}</span>
              <span v-if="product.nameEn" class="name-en">{{ product.nameEn }}</span>
            </div>
          </td>
          <td>{{ groupName(product.groupId) }}</td>
          <td class="fact col-amount">{{ formatPrice(product.priceCents) }}</td>
          <td>{{ product.badgeZh ?? '—' }}</td>
          <td class="fact">
            <span class="accent-dot" :style="{ background: ACCENTS[product.accent] ?? ACCENTS.MINT }"></span
            >{{ product.accent }}
          </td>
          <td>
            <span class="state" :data-state="product.onSale ? 'ON_SALE' : 'OFF_SALE'">
              {{ product.onSale ? '上架中' : '已下架' }}
            </span>
          </td>
          <td class="actions">
            <button type="button" class="admin-link" @click="openEdit(product)">编辑</button>
            <button type="button" class="admin-link" :class="{ danger: product.onSale }" @click="onToggleSale(product)">
              {{ product.onSale ? '下架' : '上架' }}
            </button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>

  <Modal v-if="modalOpen" :title="editingId === null ? '新增商品' : '编辑商品'" @close="modalOpen = false">
    <form class="admin-form" @submit.prevent="onSave">
      <div class="admin-form-row">
        <div class="admin-field">
          <label for="p-name-zh">名称（中文）</label>
          <input id="p-name-zh" v-model="form.nameZh" class="admin-input" required />
        </div>
        <div class="admin-field">
          <label for="p-name-en">名称（英文）</label>
          <input id="p-name-en" v-model="form.nameEn" class="admin-input" />
        </div>
      </div>
      <div class="admin-form-row">
        <div class="admin-field">
          <label for="p-desc-zh">描述（中文）</label>
          <textarea id="p-desc-zh" v-model="form.descriptionZh" class="admin-textarea"></textarea>
        </div>
        <div class="admin-field">
          <label for="p-desc-en">描述（英文）</label>
          <textarea id="p-desc-en" v-model="form.descriptionEn" class="admin-textarea"></textarea>
        </div>
      </div>
      <div class="admin-form-row">
        <div class="admin-field">
          <label for="p-badge-zh">角标（中文，留空不显示）</label>
          <input id="p-badge-zh" v-model="form.badgeZh" class="admin-input" />
        </div>
        <div class="admin-field">
          <label for="p-badge-en">角标（英文）</label>
          <input id="p-badge-en" v-model="form.badgeEn" class="admin-input" />
        </div>
      </div>
      <div class="admin-form-row">
        <div class="admin-field">
          <label for="p-group">分组</label>
          <select id="p-group" v-model.number="form.groupId" class="admin-select" required>
            <option v-for="group in groups" :key="group.id" :value="group.id">{{ group.nameZh }}</option>
          </select>
        </div>
        <div class="admin-field">
          <label for="p-accent">主题色</label>
          <select id="p-accent" v-model="form.accent" class="admin-select">
            <option v-for="(color, name) in ACCENTS" :key="name" :value="name">{{ name }}</option>
          </select>
        </div>
      </div>
      <div class="admin-form-row">
        <div class="admin-field">
          <label for="p-price">价格（美元）</label>
          <input
            id="p-price"
            v-model="form.priceUsd"
            class="admin-input"
            type="number"
            min="0.01"
            step="0.01"
            required
          />
        </div>
        <div class="admin-field">
          <label for="p-on-sale">是否上架</label>
          <select id="p-on-sale" v-model="form.onSale" class="admin-select">
            <option :value="true">上架</option>
            <option :value="false">下架</option>
          </select>
        </div>
      </div>
      <div class="admin-field">
        <label for="p-image">商品图 URL（可空）</label>
        <input id="p-image" v-model="form.imageUrl" class="admin-input" type="url" />
      </div>
    </form>
    <template #footer>
      <button type="button" class="admin-btn-ghost" @click="modalOpen = false">取消</button>
      <button type="button" class="admin-btn" :disabled="saving" @click="onSave">
        {{ saving ? '保存中…' : '保存' }}
      </button>
    </template>
  </Modal>
</template>

<style scoped>
.name-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.name-en {
  font-size: 12px;
  color: var(--color-ink-secondary);
}
</style>
