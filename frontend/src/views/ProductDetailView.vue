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

/** 主题色以 CSS 变量下发，样式表里统一引用，省得每个用色的节点各挂一份内联 style */
const accentVars = computed(() => {
  const accent = accentColors(product.value?.accent ?? 'MINT')
  return {
    '--accent-from': accent.from,
    '--accent-to': accent.to,
    '--accent-ink': accent.ink,
  }
})

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

      <div class="layout" :style="accentVars">
        <figure class="media">
          <img v-if="product.imageUrl" class="photo" :src="product.imageUrl" :alt="product.name" />
          <span v-else class="placeholder" aria-hidden="true">{{ product.name.charAt(0) }}</span>
        </figure>

        <!-- 货签：主题色带 + 吊牌孔，与左侧商品图同色，把两栏缝成一件东西 -->
        <aside class="tag">
          <div class="tag-band">
            <span class="punch" aria-hidden="true"></span>
            <span v-if="product.badge" class="badge">{{ product.badge }}</span>
          </div>

          <div class="tag-body">
            <div class="tag-head">
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
          </div>
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
  padding: 24px 32px 72px;
}

.hint {
  color: var(--color-ink-secondary);
  padding: 24px 0;
}

.hint.error {
  color: #b91c1c;
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
  outline: 2px solid var(--color-brand-deep);
  outline-offset: 3px;
  border-radius: 4px;
}

/* 上排是商品图与货签，详情整条横跨两栏——详情比货签长得多，让它独占一行才不会在右下留空 */
.layout {
  display: grid;
  grid-template-columns: minmax(0, 1.28fr) minmax(0, 1fr);
  grid-template-areas:
    'media tag'
    'detail detail';
  gap: 28px 36px;
}

/* —— 商品图 ——
   align-self: start 是为了守住 aspect-ratio：这一行万一被货签撑高，
   商品图也不该被拉伸变形 */
.media {
  grid-area: media;
  align-self: start;
  position: relative;
  aspect-ratio: 4 / 3;
  border-radius: 24px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--accent-from), var(--accent-to));
  animation: rise 0.4s ease both;
}

/* 与商品卡同一张点阵纹理，详情页放大一档 */
.media::after {
  content: '';
  position: absolute;
  inset: 0 0 0 55%;
  background-image: radial-gradient(rgba(255, 255, 255, 0.5) 1.5px, transparent 1.5px);
  background-size: 18px 18px;
  mask-image: linear-gradient(to right, transparent, #000 60%);
  pointer-events: none;
}

.media:has(.photo)::after {
  content: none;
}

.photo {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* 无图占位：白色大圆 + 主题色商品首字，与商品卡同一套语言 */
.placeholder {
  width: 172px;
  height: 172px;
  border-radius: var(--radius-pill);
  background: var(--color-bg);
  color: var(--accent-ink);
  font-family: 'Fredoka', 'Inter', sans-serif;
  font-size: 68px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 12px 32px rgba(11, 11, 12, 0.1);
}

/* —— 货签 —— */
/* 货签拉满整行（行高来自商品图的比例），价格与购买按钮因此永远贴在同一位置，
   不随描述长短上下浮动 */
.tag {
  grid-area: tag;
  display: flex;
  flex-direction: column;
  background: var(--color-bg);
  border-radius: 20px;
  overflow: hidden;
  box-shadow: 0 2px 10px rgba(11, 11, 12, 0.05);
  animation: rise 0.4s ease 0.06s both;
}

.tag-band {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  height: 52px;
  padding: 0 18px;
  background: linear-gradient(120deg, var(--accent-from), var(--accent-to));
}

/* 吊牌孔：挖成页面底色，靠内阴影做出穿透感 */
.punch {
  flex-shrink: 0;
  width: 14px;
  height: 14px;
  border-radius: var(--radius-pill);
  background: var(--color-bg-page);
  box-shadow: inset 0 1px 2px rgba(11, 11, 12, 0.22);
}

.badge {
  padding: 5px 12px;
  border-radius: var(--radius-pill);
  background: var(--color-bg);
  color: var(--accent-ink);
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

.tag-body {
  display: flex;
  flex-direction: column;
  flex: 1;
  padding: 22px 24px 24px;
}

.tag-head {
  margin-bottom: 24px;
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
  letter-spacing: -0.01em;
}

.summary {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  overflow: hidden;
  margin-top: 10px;
  color: var(--color-ink-secondary);
  font-size: 14px;
  line-height: 1.7;
}

.price-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  /* 吸掉货签里的富余高度，把价格与按钮压到底部 */
  margin-top: auto;
  padding-top: 20px;
  border-top: 1px solid var(--color-border);
}

.price {
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
  padding: 14px 24px;
  border: none;
  border-radius: 14px;
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
  box-shadow: none;
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
  border-radius: 20px;
  padding: 28px 32px 32px;
  box-shadow: 0 2px 10px rgba(11, 11, 12, 0.05);
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
  border-top: 1px solid var(--color-border);
}

@keyframes rise {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: none;
  }
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
    border-radius: 20px;
  }

  .placeholder {
    width: 132px;
    height: 132px;
    font-size: 52px;
  }

  .detail {
    padding: 22px 20px 24px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .media,
  .tag {
    animation: none;
  }
}
</style>
