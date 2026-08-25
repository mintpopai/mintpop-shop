<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import DOMPurify from 'dompurify'
import { accentColors } from '../accent'
import {
  createOrder,
  fetchProduct,
  formatPrice,
  UnauthorizedError,
  type ProductDetail,
} from '../api'
import { currentUser, gotoLogin } from '../auth'
import { showToast } from '../toast'
import { t } from '../i18n'

const route = useRoute()
const router = useRouter()
const product = ref<ProductDetail | null>(null)
const loading = ref(true)
const loadError = ref('')
const buying = ref(false)

const accent = computed(() => accentColors(product.value?.accent ?? 'MINT'))

/**
 * 详情 HTML 在渲染前再过一遍 DOMPurify。
 * 后端入库时已按白名单净化过，这里是纵深防御：历史脏数据、或哪天多了别的写入口，都不至于直达 v-html。
 */
const safeDetail = computed(() =>
  product.value?.detail ? DOMPurify.sanitize(product.value.detail) : '',
)

onMounted(async () => {
  try {
    product.value = await fetchProduct(Number(route.params.id))
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : t('common.loadFailed')
  } finally {
    loading.value = false
  }
})

async function buy() {
  const current = product.value
  if (!current) {
    return
  }
  // 与商城首页同一套规矩：下单必须登录，游客直接引导去统一登录
  if (!currentUser.value) {
    showToast('error', t('shop.loginRequired'))
    gotoLogin()
    return
  }
  buying.value = true
  try {
    const result = await createOrder(current.id)
    await router.push(`/pay/${result.orderNo}`)
  } catch (e) {
    if (e instanceof UnauthorizedError) {
      showToast('error', t('shop.sessionExpired'))
      gotoLogin()
      return
    }
    showToast('error', e instanceof Error ? e.message : t('shop.orderFailed'))
  } finally {
    buying.value = false
  }
}
</script>

<template>
  <main class="page">
    <p v-if="loading" class="hint">{{ $t('common.loading') }}</p>
    <p v-else-if="loadError" class="hint error">{{ loadError }}</p>

    <template v-else-if="product">
      <RouterLink class="back" to="/">← {{ $t('productDetail.back') }}</RouterLink>

      <section class="hero">
        <div
          class="media"
          :style="{ background: `linear-gradient(135deg, ${accent.from}, ${accent.to})` }"
        >
          <span v-if="product.badge" class="badge">{{ product.badge }}</span>
          <img v-if="product.imageUrl" class="photo" :src="product.imageUrl" :alt="product.name" />
          <span v-else class="placeholder" :style="{ color: accent.ink }" aria-hidden="true">
            {{ product.name.charAt(0) }}
          </span>
        </div>

        <div class="info">
          <h2 class="name">{{ product.name }}</h2>
          <p v-if="product.description" class="summary">{{ product.description }}</p>
          <p class="price">{{ formatPrice(product.priceCents) }}</p>
          <button class="buy-btn" type="button" :disabled="buying" @click="buy">
            {{ buying ? $t('product.buying') : $t('product.buy') }}
          </button>
        </div>
      </section>

      <section class="detail">
        <h3 class="detail-title">{{ $t('productDetail.detailTitle') }}</h3>
        <!-- v-html 的内容经后端白名单净化 + 前端 DOMPurify 二次净化，见上方 safeDetail -->
        <div v-if="safeDetail" class="rich-content" v-html="safeDetail"></div>
        <p v-else class="detail-fallback">
          {{ product.description || $t('productDetail.noDetail') }}
        </p>
      </section>
    </template>
  </main>
</template>

<style scoped>
.page {
  max-width: 1000px;
  margin: 0 auto;
  padding: 24px 32px 64px;
}

.hint {
  color: var(--color-ink-secondary);
  padding: 24px 0;
}

.hint.error {
  color: var(--color-danger, #d04a68);
}

.back {
  display: inline-block;
  margin-bottom: 20px;
  color: var(--color-ink-secondary);
  font-size: 14px;
  text-decoration: none;
}

.back:hover {
  color: var(--color-ink);
}

.hero {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr);
  gap: 32px;
  align-items: start;
}

.media {
  position: relative;
  aspect-ratio: 16 / 10;
  border-radius: 20px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}

.badge {
  position: absolute;
  top: 16px;
  left: 16px;
  padding: 5px 12px;
  border-radius: var(--radius-pill);
  background: var(--color-bg);
  color: var(--color-ink);
  font-size: 12px;
  font-weight: 500;
  box-shadow: 0 2px 8px rgba(11, 11, 12, 0.08);
}

.photo {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.placeholder {
  width: 132px;
  height: 132px;
  border-radius: var(--radius-pill);
  background: var(--color-bg);
  font-family: 'Fredoka', 'Inter', sans-serif;
  font-size: 52px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 20px rgba(11, 11, 12, 0.08);
}

.info {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-top: 8px;
}

.name {
  font-size: 28px;
  font-weight: 700;
}

.summary {
  color: var(--color-ink-secondary);
  font-size: 15px;
}

.price {
  font-size: 32px;
  font-weight: 700;
}

.buy-btn {
  align-self: flex-start;
  padding: 12px 32px;
  border: none;
  border-radius: 12px;
  background: var(--color-brand);
  color: #ffffff;
  font-size: 15px;
  font-weight: 500;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.15s ease;
  box-shadow: 0 6px 16px rgba(23, 209, 167, 0.35);
}

.buy-btn:hover:not(:disabled) {
  background: var(--color-brand-deep);
}

.buy-btn:focus-visible {
  outline: 2px solid var(--color-brand-deep);
  outline-offset: 2px;
}

.buy-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.detail {
  margin-top: 48px;
}

.detail-title {
  font-size: 18px;
  font-weight: 700;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--color-line, rgba(11, 11, 12, 0.08));
}

.detail-fallback {
  padding-top: 16px;
  color: var(--color-ink-secondary);
}

/* 富文本区：内容由管理端配置，这里给它一套可预期的排版，避免继承出奇怪的间距 */
.rich-content {
  padding-top: 16px;
  line-height: 1.8;
  color: var(--color-ink);
  overflow-wrap: break-word;
}

.rich-content :deep(h1),
.rich-content :deep(h2),
.rich-content :deep(h3) {
  margin: 28px 0 12px;
  font-weight: 700;
  line-height: 1.4;
}

.rich-content :deep(h1) {
  font-size: 22px;
}

.rich-content :deep(h2) {
  font-size: 19px;
}

.rich-content :deep(h3) {
  font-size: 16px;
}

.rich-content :deep(p) {
  margin: 12px 0;
}

.rich-content :deep(ul),
.rich-content :deep(ol) {
  margin: 12px 0;
  padding-left: 24px;
  list-style: revert;
}

.rich-content :deep(li) {
  margin: 6px 0;
}

.rich-content :deep(a) {
  color: var(--color-brand-deep);
}

.rich-content :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 12px;
}

.rich-content :deep(blockquote) {
  margin: 16px 0;
  padding: 8px 16px;
  border-left: 3px solid var(--color-brand);
  color: var(--color-ink-secondary);
}

.rich-content :deep(pre) {
  margin: 16px 0;
  padding: 12px 16px;
  border-radius: 12px;
  background: rgba(11, 11, 12, 0.05);
  overflow-x: auto;
}

.rich-content :deep(hr) {
  margin: 24px 0;
  border: none;
  border-top: 1px solid var(--color-line, rgba(11, 11, 12, 0.08));
}

@media (max-width: 720px) {
  .page {
    padding: 16px 20px 48px;
  }

  .hero {
    grid-template-columns: 1fr;
    gap: 20px;
  }
}
</style>
