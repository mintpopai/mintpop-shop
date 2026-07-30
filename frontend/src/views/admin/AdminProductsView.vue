<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AdminModal from '../../components/AdminModal.vue'
import { formatPrice } from '../../api'
import {
  createAdminProduct,
  fetchAdminGroups,
  fetchAdminProducts,
  setAdminProductOnSale,
  updateAdminProduct,
  type AdminGroup,
  type AdminProduct,
} from '../../api-admin'
import { locale, t } from '../../i18n'
import { showToast } from '../../toast'

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

/** 分组名按当前语言展示（英文缺失回退中文，与后端口径一致） */
function groupName(groupId: number): string {
  const group = groups.value.find((g) => g.id === groupId)
  if (!group) {
    return String(groupId)
  }
  return locale === 'en-US' && group.nameEn ? group.nameEn : group.nameZh
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
    loadError.value = e instanceof Error ? e.message : t('common.loadFailed')
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
    showToast('error', t('admin.form.invalid'))
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
    showToast('success', t('admin.products.saved'))
    modalOpen.value = false
    await reload()
  } catch (e) {
    showToast('error', e instanceof Error ? e.message : t('api.requestFailed'))
  } finally {
    saving.value = false
  }
}

async function onToggleSale(product: AdminProduct) {
  try {
    const updated = await setAdminProductOnSale(product.id, !product.onSale)
    products.value = products.value.map((p) => (p.id === updated.id ? updated : p))
    showToast('success', t('admin.products.statusUpdated'))
  } catch (e) {
    showToast('error', e instanceof Error ? e.message : t('api.requestFailed'))
  }
}
</script>

<template>
  <h2 class="admin-title">{{ $t('admin.products.title') }}</h2>

  <div class="admin-toolbar">
    <select v-model.number="groupFilter" class="admin-select" :aria-label="$t('admin.products.group')">
      <option :value="0">{{ $t('admin.products.allGroups') }}</option>
      <option v-for="group in groups" :key="group.id" :value="group.id">
        {{ groupName(group.id) }}
      </option>
    </select>
    <span class="spacer"></span>
    <button type="button" class="admin-btn" @click="openCreate">{{ $t('admin.products.add') }}</button>
  </div>

  <p v-if="loading" class="admin-hint">{{ $t('common.loading') }}</p>
  <p v-else-if="loadError" class="admin-hint error">{{ loadError }}</p>

  <div v-else class="admin-card">
    <p v-if="filteredProducts.length === 0" class="admin-hint">{{ $t('admin.table.empty') }}</p>
    <table v-else class="admin-table">
      <thead>
        <tr>
          <th>{{ $t('admin.table.id') }}</th>
          <th>{{ $t('admin.products.name') }}</th>
          <th>{{ $t('admin.products.group') }}</th>
          <th>{{ $t('admin.products.price') }}</th>
          <th>{{ $t('admin.products.badge') }}</th>
          <th>{{ $t('admin.products.accent') }}</th>
          <th>{{ $t('admin.products.status') }}</th>
          <th>{{ $t('admin.table.actions') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="product in filteredProducts" :key="product.id">
          <td>{{ product.id }}</td>
          <td>
            <div class="name-cell">
              <span>{{ product.nameZh }}</span>
              <span v-if="product.nameEn" class="name-en">{{ product.nameEn }}</span>
            </div>
          </td>
          <td>{{ groupName(product.groupId) }}</td>
          <td class="price">{{ formatPrice(product.priceCents) }}</td>
          <td>{{ product.badgeZh ?? '—' }}</td>
          <td>
            <span class="accent-dot" :style="{ background: ACCENTS[product.accent] ?? ACCENTS.MINT }"></span
            >{{ product.accent }}
          </td>
          <td>
            <span class="sale-tag" :class="{ off: !product.onSale }">
              {{ product.onSale ? $t('admin.products.onSale') : $t('admin.products.offSale') }}
            </span>
          </td>
          <td class="actions">
            <button type="button" class="admin-link" @click="openEdit(product)">
              {{ $t('admin.products.editAction') }}
            </button>
            <button type="button" class="admin-link" :class="{ danger: product.onSale }" @click="onToggleSale(product)">
              {{ product.onSale ? $t('admin.products.putOffSale') : $t('admin.products.putOnSale') }}
            </button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>

  <AdminModal
    v-if="modalOpen"
    :title="editingId === null ? $t('admin.products.add') : $t('admin.products.edit')"
    @close="modalOpen = false"
  >
    <form class="admin-form" @submit.prevent="onSave">
      <div class="admin-form-row">
        <div class="admin-field">
          <label for="p-name-zh">{{ $t('admin.products.nameZh') }}</label>
          <input id="p-name-zh" v-model="form.nameZh" class="admin-input" required />
        </div>
        <div class="admin-field">
          <label for="p-name-en">{{ $t('admin.products.nameEn') }}</label>
          <input id="p-name-en" v-model="form.nameEn" class="admin-input" />
        </div>
      </div>
      <div class="admin-form-row">
        <div class="admin-field">
          <label for="p-desc-zh">{{ $t('admin.products.descZh') }}</label>
          <textarea id="p-desc-zh" v-model="form.descriptionZh" class="admin-textarea"></textarea>
        </div>
        <div class="admin-field">
          <label for="p-desc-en">{{ $t('admin.products.descEn') }}</label>
          <textarea id="p-desc-en" v-model="form.descriptionEn" class="admin-textarea"></textarea>
        </div>
      </div>
      <div class="admin-form-row">
        <div class="admin-field">
          <label for="p-badge-zh">{{ $t('admin.products.badgeZh') }}</label>
          <input id="p-badge-zh" v-model="form.badgeZh" class="admin-input" />
        </div>
        <div class="admin-field">
          <label for="p-badge-en">{{ $t('admin.products.badgeEn') }}</label>
          <input id="p-badge-en" v-model="form.badgeEn" class="admin-input" />
        </div>
      </div>
      <div class="admin-form-row">
        <div class="admin-field">
          <label for="p-group">{{ $t('admin.products.group') }}</label>
          <select id="p-group" v-model.number="form.groupId" class="admin-select" required>
            <option v-for="group in groups" :key="group.id" :value="group.id">
              {{ groupName(group.id) }}
            </option>
          </select>
        </div>
        <div class="admin-field">
          <label for="p-accent">{{ $t('admin.products.accent') }}</label>
          <select id="p-accent" v-model="form.accent" class="admin-select">
            <option v-for="(color, name) in ACCENTS" :key="name" :value="name">{{ name }}</option>
          </select>
        </div>
      </div>
      <div class="admin-form-row">
        <div class="admin-field">
          <label for="p-price">{{ $t('admin.products.priceUsd') }}</label>
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
          <label for="p-on-sale">{{ $t('admin.products.onSaleField') }}</label>
          <select id="p-on-sale" v-model="form.onSale" class="admin-select">
            <option :value="true">{{ $t('admin.products.onSale') }}</option>
            <option :value="false">{{ $t('admin.products.offSale') }}</option>
          </select>
        </div>
      </div>
      <div class="admin-field">
        <label for="p-image">{{ $t('admin.products.imageUrl') }}</label>
        <input id="p-image" v-model="form.imageUrl" class="admin-input" type="url" />
      </div>
    </form>
    <template #footer>
      <button type="button" class="admin-btn-ghost" @click="modalOpen = false">
        {{ $t('admin.form.cancel') }}
      </button>
      <button type="button" class="admin-btn" :disabled="saving" @click="onSave">
        {{ saving ? $t('admin.form.saving') : $t('admin.form.save') }}
      </button>
    </template>
  </AdminModal>
</template>

<style scoped>
.name-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.name-en {
  font-size: 12px;
  color: var(--color-ink-secondary);
}

.price {
  font-weight: 600;
  color: var(--color-brand-deep);
  white-space: nowrap;
}

.actions {
  display: flex;
  gap: 12px;
  white-space: nowrap;
}
</style>
