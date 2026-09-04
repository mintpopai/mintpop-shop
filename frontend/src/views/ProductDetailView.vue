<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import DOMPurify from 'dompurify'
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

      <div class="layout">
        <figure class="media">
          <div class="media-glow" aria-hidden="true"></div>
          <img v-if="product.imageUrl" class="logo" :src="product.imageUrl" :alt="product.name" />
          <span v-else class="placeholder" aria-hidden="true">{{ product.name.charAt(0) }}</span>
        </figure>

        <aside class="tag">
          <div class="tag-head">
            <span v-if="product.badge" class="badge">{{ product.badge }}</span>
            <h2 class="name">{{ product.name }}</h2>
            <p v-if="product.description" class="summary">{{ product.description }}</p>
          </div>

          <p class="price-row">
            <span class="price">{{ formatPrice(product.priceCents) }}</span>
            <span class="currency">USD</span>
          </p>

          <button class="buy-btn" type="button" :disabled="buying" @click="buy">
            {{ buying ? $t('product.buying') : $t('product.buy') }}
          </button>
          <p class="checkout-note">{{ $t('productDetail.checkoutNote') }}</p>
        </aside>

        <section v-if="safeDetail" class="detail">
          <h3 class="detail-title">{{ $t('productDetail.detailTitle') }}</h3>
          <!-- v-html 的内容经后端白名单净化 + 前端 DOMPurify 二次净化，见上方 safeDetail -->
          <div class="rich-content" v-html="safeDetail"></div>
        </section>
      </div>
    </template>
  </main>
</template>

<style scoped>
.page {
  max-width: 1120px;
  margin: 0 auto;
  padding: 28px 32px 72px;
}

.hint {
  color: var(--color-ink-secondary);
  padding: 24px 0;
}

.hint.error {
  color: var(--color-danger);
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

.back:focus-visible {
  outline: none;
  border-radius: 4px;
  box-shadow: 0 0 0 3px rgba(23, 209, 167, 0.28);
}

/* 上排是商品图与货签，详情整条横跨两栏——详情比货签长得多，让它独占一行才不会在右下留空 */
.layout {
  display: grid;
  grid-template-columns: minmax(0, 1.28fr) minmax(0, 1fr);
  grid-template-areas:
    'media tag'
    'detail detail';
  gap: 28px 32px;
  align-items: start;
}

/* —— 商品图 ——
   align-self: start 是为了守住 aspect-ratio：这一行万一被货签撑高，
   商品图也不该被拉伸变形 */
.media {
  grid-area: media;
  align-self: start;
  position: relative;
  aspect-ratio: 4 / 3;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-panel);
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-bg-cloud);
}

.media-glow {
  position: absolute;
  width: 460px;
  height: 460px;
  left: 50%;
  top: 50%;
  margin: -230px 0 0 -230px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(31, 227, 173, 0.26), rgba(23, 209, 167, 0) 68%);
  filter: blur(26px);
  pointer-events: none;
}

/* 与商品卡同一套语言：商品图是 logo，等比放进方框，不裁切 */
.logo {
  position: relative;
  width: 176px;
  height: 176px;
  object-fit: contain;
  border-radius: 20px;
}

.placeholder {
  position: relative;
  font-family: 'Fredoka', 'Inter', sans-serif;
  font-size: 68px;
  font-weight: 600;
  color: var(--color-ink-secondary);
}

/* —— 货签 —— */
/* 货签拉满整行（行高来自商品图的比例），价格与购买按钮因此永远贴在同一位置，
   不随描述长短上下浮动 */
.tag {
  grid-area: tag;
  display: flex;
  flex-direction: column;
  background: var(--color-bg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-panel);
  box-shadow: 0 1px 3px rgba(11, 11, 12, 0.06);
  padding: 28px 28px 24px;
}

.badge {
  display: inline-block;
  margin-bottom: 14px;
  padding: 4px 10px;
  border-radius: var(--radius-pill);
  background: rgba(23, 209, 167, 0.14);
  color: var(--color-brand-ink);
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.tag-head {
  margin-bottom: 28px;
}

/* 名称与短描述都封顶行数：商家填多长都不该把版面顶变形，完整说明归详情区 */
.name {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  font-size: 28px;
  font-weight: 600;
  line-height: 1.3;
  letter-spacing: -0.015em;
  text-wrap: balance;
}

.summary {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  overflow: hidden;
  margin-top: 12px;
  color: var(--color-ink-secondary);
  font-size: 14px;
  line-height: 1.7;
}

.price-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  /* 吸掉货签里可能富余的高度，把价格与按钮压到底部 */
  margin-top: auto;
  padding-top: 20px;
  border-top: 1px solid var(--color-border);
}

.price {
  font-family: 'Fredoka', 'Inter', sans-serif;
  font-size: 34px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.02em;
}

.currency {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-ink-secondary);
}

.buy-btn {
  width: 100%;
  margin-top: 18px;
  padding: 13px 24px;
  border: none;
  border-radius: var(--radius-button);
  background: var(--color-brand);
  color: var(--color-ink);
  font-size: 15px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.15s ease;
}

.buy-btn:hover:not(:disabled) {
  background: var(--color-brand-bright);
}

.buy-btn:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px rgba(23, 209, 167, 0.28);
}

.buy-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.checkout-note {
  margin-top: 12px;
  color: var(--color-ink-secondary);
  font-size: 12px;
  line-height: 1.6;
  text-align: center;
}

/* —— 详情 —— */
.detail {
  grid-area: detail;
  background: var(--color-bg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-panel);
  padding: 28px 32px 32px;
  box-shadow: 0 1px 3px rgba(11, 11, 12, 0.06);
}

/* 「商品详情」是区块指示牌，不是内容标题：做成小字标签靠分隔线划界，
   免得跟商家在富文本里写的 h1/h2 争层级 */
.detail-title {
  margin-bottom: 20px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--color-border);
  color: var(--color-ink-secondary);
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0.08em;
}

/* 富文本区：内容由管理端配置，这里给它一套可预期的排版，避免继承出奇怪的间距 */
.rich-content {
  max-width: 76ch;
  line-height: 1.8;
  color: var(--color-ink);
  overflow-wrap: break-word;
}

.rich-content :deep(h1),
.rich-content :deep(h2),
.rich-content :deep(h3) {
  margin: 24px 0 10px;
  font-weight: 600;
  line-height: 1.4;
}

/* 富文本的标题一律比「商品详情」这个区块标题小一号，免得内容盖过它所属的区块 */
.rich-content :deep(h1) {
  font-size: 19px;
}

.rich-content :deep(h2) {
  font-size: 17px;
}

.rich-content :deep(h3) {
  font-size: 15px;
}

.rich-content :deep(> :first-child) {
  margin-top: 0;
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

/* 富文本编辑器把每个列表项包成 <li><p>…</p></li>，p 的上下 margin 会把列表撑得老远 */
.rich-content :deep(li > p) {
  margin: 0;
}

.rich-content :deep(a) {
  color: var(--color-brand-ink);
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
  border-top: 1px solid var(--color-border);
}

@media (max-width: 900px) {
  .layout {
    grid-template-columns: minmax(0, 1fr);
    grid-template-areas:
      'media'
      'tag'
      'detail';

    gap: 20px;
  }

  .tag {
    position: static;
  }
}

@media (max-width: 720px) {
  .page {
    padding: 16px 20px 48px;
  }

  .media {
    aspect-ratio: 5 / 4;
  }

  .logo {
    width: 96px;
    height: 96px;
  }

  .placeholder {
    font-size: 52px;
  }

  .detail {
    padding: 22px 20px 24px;
  }
}
</style>
