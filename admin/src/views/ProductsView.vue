<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import Modal from '../components/Modal.vue'
import RichTextEditor from '../components/RichTextEditor.vue'
import Select from '../components/Select.vue'
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

/** 下拉选项：分组筛选带「全部」，弹窗里的分组选择不带 */
const groupFilterOptions = computed(() => [
  { value: 0, label: '全部分组' },
  ...groups.value.map((g) => ({ value: g.id, label: g.nameZh })),
])
const groupOptions = computed(() => groups.value.map((g) => ({ value: g.id, label: g.nameZh })))

/** 管理端固定中文：分组显示中文名 */
function groupName(groupId: number): string {
  return groups.value.find((g) => g.id === groupId)?.nameZh ?? String(groupId)
}

/** 弹窗状态：editingId 为空表示新增；价格以美元字符串编辑、提交时转美分 */
const modalOpen = ref(false)
const editingId = ref<number | null>(null)
/**
 * 文案区当前停在哪个语种。中英不再左右并排——并排时每种语言只分到半个弹窗宽，
 * 富文本编辑器和描述框都被挤成一条缝。改成整块切换：一次只看一种语言的全部文案
 * （名称 / 描述 / 角标 / 详情），录入时也本来就是先把中文写完再写英文。
 * 两套字段都常驻 DOM、只切显示，切语言不丢草稿。
 */
const lang = ref<'ZH' | 'EN'>('ZH')
/** 商品图地址取不到图时，预览位给一句人话，而不是一个碎图标 */
const imageError = ref(false)

const form = ref({
  groupId: 0,
  nameZh: '',
  nameEn: '',
  descriptionZh: '',
  descriptionEn: '',
  detailZh: '',
  detailEn: '',
  badgeZh: '',
  badgeEn: '',
  accent: 'MINT',
  priceUsd: '',
  imageUrl: '',
  onSale: true,
})
const saving = ref(false)

// 换了地址就重新试一次，别把上一张的失败状态留在新地址上
watch(
  () => form.value.imageUrl,
  () => {
    imageError.value = false
  },
)

/** 主题色的实际颜色，供弹窗里的预览位取用（未知枚举回落薄荷绿） */
const accentColor = computed(() => ACCENTS[form.value.accent] ?? ACCENTS.MINT)

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
  lang.value = 'ZH'
  imageError.value = false
  form.value = {
    groupId: groupFilter.value || groups.value[0]?.id || 0,
    nameZh: '',
    nameEn: '',
    descriptionZh: '',
    descriptionEn: '',
    detailZh: '',
    detailEn: '',
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
  lang.value = 'ZH'
  imageError.value = false
  form.value = {
    groupId: product.groupId,
    nameZh: product.nameZh,
    nameEn: product.nameEn ?? '',
    descriptionZh: product.descriptionZh ?? '',
    descriptionEn: product.descriptionEn ?? '',
    detailZh: product.detailZh ?? '',
    detailEn: product.detailEn ?? '',
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
  // 中文名藏在语言开关后面，为空时得先把中文面板翻回来，否则人只看到一句报错、找不到那个框
  if (!form.value.nameZh.trim()) {
    lang.value = 'ZH'
    showToast('error', '请填写商品的中文名称')
    return
  }
  if (!form.value.groupId) {
    showToast('error', '请选择商品所在的分组')
    return
  }
  if (!Number.isFinite(priceCents) || priceCents < 1) {
    showToast('error', '价格至少是 $0.01')
    return
  }
  saving.value = true
  const body = {
    groupId: form.value.groupId,
    nameZh: form.value.nameZh,
    nameEn: form.value.nameEn,
    descriptionZh: form.value.descriptionZh,
    descriptionEn: form.value.descriptionEn,
    detailZh: form.value.detailZh,
    detailEn: form.value.detailEn,
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
    <Select v-model="groupFilter" :options="groupFilterOptions" aria-label="按分组筛选" />
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
          <th>详情</th>
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
          <td class="col-detail">{{ product.detailZh ? '✓' : '—' }}</td>
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

  <!--
    商品弹窗按「柜台上的一张商品卡」来排，而不是一长条表单：
    左边是人写的文案（名称 / 描述 / 角标 / 详情），一次只写一种语言；
    右边是这件货的货签（分组 / 主题色 / 价格 / 状态 / 图），全程可见、不随语言切换。
    分栏也正是全站那条排版规则的空间版：Inter 的内容归左，系统事实归右。
  -->
  <Modal
    v-if="modalOpen"
    :title="editingId === null ? '新增商品' : '编辑商品'"
    size="lg"
    flush
    @close="modalOpen = false"
  >
    <form class="product-editor" @submit.prevent="onSave">
      <section class="copy-pane">
        <div class="lang-bar">
          <div class="lang-switch" role="tablist" aria-label="文案语言">
            <button
              type="button"
              class="lang-tab"
              :class="{ active: lang === 'ZH' }"
              role="tab"
              :aria-selected="lang === 'ZH'"
              @click="lang = 'ZH'"
            >
              中文
            </button>
            <button
              type="button"
              class="lang-tab"
              :class="{ active: lang === 'EN' }"
              role="tab"
              :aria-selected="lang === 'EN'"
              @click="lang = 'EN'"
            >
              English
            </button>
          </div>
          <p class="lang-note">
            {{
              lang === 'ZH'
                ? '中文顾客看到的文案。'
                : '英文顾客看到的文案，留空的字段回退显示中文。'
            }}
          </p>
        </div>

        <!-- 两套字段都常驻、只切显示：v-if 会把没在看的那一份连同草稿一起销毁 -->
        <div v-show="lang === 'ZH'" class="lang-panel">
          <div class="admin-field">
            <label for="p-name-zh">名称 <span class="required">必填</span></label>
            <input id="p-name-zh" v-model="form.nameZh" class="admin-input" required />
          </div>
          <div class="admin-form-row">
            <div class="admin-field">
              <label for="p-desc-zh">描述</label>
              <textarea id="p-desc-zh" v-model="form.descriptionZh" class="admin-textarea"></textarea>
              <p class="field-note">商品卡上名称下面的那行小字。</p>
            </div>
            <div class="admin-field">
              <label for="p-badge-zh">角标</label>
              <input id="p-badge-zh" v-model="form.badgeZh" class="admin-input" />
              <p class="field-note">贴在商品图角上的一句话，留空则不显示。</p>
            </div>
          </div>
          <div class="admin-field detail-field">
            <label for="p-detail-zh">商品详情</label>
            <RichTextEditor id="p-detail-zh" v-model="form.detailZh" fill />
          </div>
        </div>

        <div v-show="lang === 'EN'" class="lang-panel">
          <div class="admin-field">
            <label for="p-name-en">名称</label>
            <input
              id="p-name-en"
              v-model="form.nameEn"
              class="admin-input"
              :placeholder="form.nameZh"
            />
          </div>
          <div class="admin-form-row">
            <div class="admin-field">
              <label for="p-desc-en">描述</label>
              <textarea
                id="p-desc-en"
                v-model="form.descriptionEn"
                class="admin-textarea"
                :placeholder="form.descriptionZh"
              ></textarea>
              <p class="field-note">商品卡上名称下面的那行小字。</p>
            </div>
            <div class="admin-field">
              <label for="p-badge-en">角标</label>
              <input
                id="p-badge-en"
                v-model="form.badgeEn"
                class="admin-input"
                :placeholder="form.badgeZh"
              />
              <p class="field-note">贴在商品图角上的一句话，留空则不显示。</p>
            </div>
          </div>
          <div class="admin-field detail-field">
            <label for="p-detail-en">商品详情</label>
            <RichTextEditor id="p-detail-en" v-model="form.detailEn" fill />
          </div>
        </div>
      </section>

      <aside class="tag-pane" :style="{ '--accent': accentColor }">
        <div class="tag-head">
          <h4 class="tag-title">货签</h4>
          <span v-if="editingId !== null" class="fact tag-id">ID {{ editingId }}</span>
        </div>

        <div class="admin-field">
          <label for="p-group">分组</label>
          <Select id="p-group" v-model="form.groupId" :options="groupOptions" />
        </div>

        <div class="admin-field">
          <div class="field-head">
            <label id="p-accent-label">主题色</label>
            <!-- 色板选中与否不能只靠颜色，当前值同时用等宽名字写出来 -->
            <span class="fact accent-name">{{ form.accent }}</span>
          </div>
          <div class="swatches" role="radiogroup" aria-labelledby="p-accent-label">
            <button
              v-for="(color, name) in ACCENTS"
              :key="name"
              type="button"
              class="swatch"
              role="radio"
              :aria-checked="form.accent === name"
              :aria-label="name"
              :title="name"
              :class="{ active: form.accent === name }"
              :style="{ '--swatch': color }"
              @click="form.accent = name"
            >
              <span class="swatch-dot" aria-hidden="true"></span>
            </button>
          </div>
        </div>

        <div class="admin-field">
          <label for="p-price">价格</label>
          <div class="price-field">
            <span class="price-unit" aria-hidden="true">$</span>
            <input
              id="p-price"
              v-model="form.priceUsd"
              class="admin-input price-input"
              type="number"
              min="0.01"
              step="0.01"
              placeholder="0.00"
              required
            />
            <span class="price-currency">USD</span>
          </div>
        </div>

        <div class="admin-field">
          <label id="p-on-sale-label">状态</label>
          <div class="segmented" role="radiogroup" aria-labelledby="p-on-sale-label">
            <button
              type="button"
              class="segment"
              data-state="ON_SALE"
              role="radio"
              :aria-checked="form.onSale"
              :class="{ active: form.onSale }"
              @click="form.onSale = true"
            >
              上架
            </button>
            <button
              type="button"
              class="segment"
              data-state="OFF_SALE"
              role="radio"
              :aria-checked="!form.onSale"
              :class="{ active: !form.onSale }"
              @click="form.onSale = false"
            >
              下架
            </button>
          </div>
          <p class="field-note">
            {{ form.onSale ? '商城首页会展示这件商品。' : '商城里看不到，链接也打不开。' }}
          </p>
        </div>

        <div class="admin-field">
          <label for="p-image">商品图</label>
          <input
            id="p-image"
            v-model="form.imageUrl"
            class="admin-input"
            type="url"
            placeholder="https://…"
          />
          <!-- 预览位铺一层主题色：商城的商品卡就是这个底色，在这儿先看一眼配得上配不上 -->
          <div class="image-preview">
            <img
              v-if="form.imageUrl && !imageError"
              :src="form.imageUrl"
              alt=""
              @error="imageError = true"
            />
            <p v-else class="image-note">
              {{ imageError ? '这个地址取不到图片。' : '填了地址就能在这里看到效果。' }}
            </p>
          </div>
        </div>
      </aside>
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
/* —— 商品弹窗：左「文案」右「货签」两栏，各自滚动 —— */
.product-editor {
  display: flex;
  flex: 1;
  min-height: 0;
}

.copy-pane {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  min-height: 0;
}

/* 语言开关钉在文案栏顶部：它管着下面所有字段，跟着内容滚走就找不着了 */
.lang-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  padding: 14px 24px;
  border-bottom: 1px solid var(--color-border);
}

.lang-switch {
  display: inline-flex;
  gap: 2px;
  padding: 2px;
  border-radius: var(--radius-button);
  background: var(--color-bg-cloud);
}

.lang-tab {
  padding: 6px 16px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--color-ink-secondary);
  font-family: inherit;
  font-size: 13px;
  line-height: 18px;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}

.lang-tab:hover {
  color: var(--color-ink);
}

/* 当前语种：白面浮起来 + 墨色加粗，两重表达，不靠单一颜色 */
.lang-tab.active {
  background: var(--color-bg);
  color: var(--color-ink);
  font-weight: 600;
  box-shadow: 0 1px 3px rgba(15, 26, 22, 0.12);
}

.lang-note {
  font-size: 12px;
  color: var(--color-ink-secondary);
}

.lang-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
  flex: 1;
  min-height: 0;
  padding: 20px 24px 24px;
  overflow-y: auto;
}

/* 详情编辑器吃掉文案栏剩下的全部高度——它是这个弹窗里真正要干活的地方；
   min-height 兜住矮屏：挤不下时由 .lang-panel 自己滚，而不是把编辑区压成一条缝 */
.detail-field {
  flex: 1;
  min-height: 240px;
}

.field-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.field-note {
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-ink-secondary);
}

/* 并排两列里控件高矮不一（多行的描述 vs 单行的角标），说明文字统一沉到行底对齐，
   否则两句小字一高一低，看着像没排齐 */
.admin-form-row .field-note {
  margin-top: auto;
}

/* 必填不用红星：星号得靠一句「* 为必填」的注释才能读懂，直接写字省掉这一跳 */
.required {
  margin-left: 6px;
  padding: 1px 6px;
  border-radius: var(--radius-pill);
  background: var(--color-bg-cloud);
  font-size: 11px;
  color: var(--color-ink-secondary);
}

/* —— 货签栏：这件货的参数，全程可见，不随语言切换 —— */
.tag-pane {
  display: flex;
  flex-direction: column;
  gap: 20px;
  flex-shrink: 0;
  width: 296px;
  padding: 20px 24px 24px;
  border-left: 1px solid var(--color-border);
  background: var(--color-bg-cloud);
  overflow-y: auto;
}

.tag-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.tag-title {
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--color-ink);
}

.tag-id {
  font-size: 12px;
  color: var(--color-ink-secondary);
}

/* 色板：色点本身就是选项，比下拉少一次展开；当前值另有等宽名字写在标签行右侧 */
.swatches {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.swatch {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: 1px solid var(--color-border);
  border-radius: 50%;
  background: var(--color-bg);
  cursor: pointer;
  transition: border-color 0.15s ease, transform 0.15s ease;
}

.swatch:hover {
  border-color: var(--color-ink-secondary);
}

.swatch-dot {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--swatch);
  transition: width 0.15s ease, height 0.15s ease;
}

/* 选中态：外圈染成本色 + 内点长大，形状与颜色两重表达 */
.swatch.active {
  border-color: var(--swatch);
  box-shadow: inset 0 0 0 2px var(--color-bg);
}

.swatch.active .swatch-dot {
  width: 22px;
  height: 22px;
}

/* 价格是系统事实，走等宽；单位固定摆在框内，不占一行标签去说明 */
.price-field {
  position: relative;
  display: flex;
  align-items: center;
}

.price-unit,
.price-currency {
  position: absolute;
  font-family: var(--font-fact);
  font-size: 13px;
  color: var(--color-ink-secondary);
  pointer-events: none;
}

.price-unit {
  left: 12px;
}

.price-currency {
  right: 12px;
}

.price-input {
  width: 100%;
  padding-left: 26px;
  padding-right: 46px;
  font-family: var(--font-fact);
  font-variant-numeric: tabular-nums;
}

/* 数字框的上下箭头在这儿只会误触，价格靠键盘敲 */
.price-input::-webkit-outer-spin-button,
.price-input::-webkit-inner-spin-button {
  appearance: none;
  margin: 0;
}

.price-input {
  appearance: textfield;
}

/* 上下架只有两个值，摊开成分段控件：状态一眼可见，改它也只要一次点击。
   材质与上面的语言开关同一套（凹槽 + 浮起的白片），弹窗里只出现一种「分段」长相 */
.segmented {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 2px;
  padding: 2px;
  border-radius: var(--radius-button);
  background: color-mix(in srgb, var(--color-ink) 7%, transparent);
}

.segment {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 6px 0;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--color-ink-secondary);
  font-family: inherit;
  font-size: 13px;
  line-height: 18px;
  cursor: pointer;
}

/* 状态点取的是全站那套语义色（layout.css 里按 data-state 派发），与表格里同一个绿 */
.segment::before {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--state-color, #7a857f);
  opacity: 0.4;
}

.segment:hover {
  color: var(--color-ink);
}

.segment.active {
  background: var(--color-bg);
  color: var(--color-ink);
  font-weight: 600;
  box-shadow: 0 1px 3px rgba(15, 26, 22, 0.12);
}

.segment.active::before {
  opacity: 1;
}

.image-preview {
  display: flex;
  align-items: center;
  justify-content: center;
  aspect-ratio: 4 / 3;
  padding: 8px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  /* 商城的商品卡就是这层主题色底，在这儿先看一眼图配不配得上 */
  background: color-mix(in srgb, var(--accent) 12%, #ffffff);
  overflow: hidden;
}

.image-preview img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

.image-note {
  padding: 0 8px;
  font-size: 12px;
  line-height: 1.6;
  text-align: center;
  color: var(--color-ink-secondary);
}

/* —— 列表页 —— */
.name-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.name-en {
  font-size: 12px;
  color: var(--color-ink-secondary);
}

/* 窄屏：货签栏收到文案下面接着排，不再挤成一条竖缝 */
@media (max-width: 900px) {
  .product-editor {
    flex-direction: column;
    overflow-y: auto;
  }

  .copy-pane {
    flex: none;
  }

  .lang-panel {
    overflow: visible;
  }

  .detail-field {
    min-height: 320px;
  }

  .tag-pane {
    width: auto;
    border-left: none;
    border-top: 1px solid var(--color-border);
    overflow: visible;
  }
}
</style>
