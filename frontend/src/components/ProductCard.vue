<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { formatPrice, type Product } from '../api'

defineProps<{ product: Product; buying: boolean }>()
const emit = defineEmits<{ buy: [product: Product] }>()
</script>

<template>
  <article class="card">
    <!-- 铺满整卡的链接（stretched link）：整张卡可点、可 Tab、可右键新开，
         又不至于把 <button> 塞进 <a> 里造出非法嵌套 -->
    <RouterLink
      class="card-link"
      :to="`/products/${product.id}`"
      :aria-label="$t('product.viewDetail', { name: product.name })"
    />
    <div class="thumb">
      <div class="thumb-glow" aria-hidden="true"></div>
      <span v-if="product.badge" class="badge">{{ product.badge }}</span>
      <img v-if="product.imageUrl" class="logo" :src="product.imageUrl" :alt="product.name" />
      <span v-else class="placeholder" aria-hidden="true">
        {{ product.name.charAt(0) }}
      </span>
    </div>
    <div class="body">
      <h3 class="name">{{ product.name }}</h3>
      <p class="desc">{{ product.description ?? '' }}</p>
      <div class="footer">
        <span class="price">{{ formatPrice(product.priceCents) }}</span>
        <button
          class="buy-btn"
          type="button"
          :disabled="buying"
          @click="emit('buy', product)"
        >
          <svg
            class="buy-icon"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          >
            <circle cx="9" cy="21" r="1" />
            <circle cx="20" cy="21" r="1" />
            <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6" />
          </svg>
          {{ buying ? $t('product.buying') : $t('product.buy') }}
        </button>
      </div>
    </div>
  </article>
</template>

<style scoped>
.card {
  position: relative;
  display: flex;
  flex-direction: column;
  background: var(--color-bg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-panel);
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(11, 11, 12, 0.06);
  transition: transform 0.15s ease, border-color 0.15s ease;
}

.card:hover {
  transform: translateY(-2px);
  border-color: var(--color-brand);
}

.card-link {
  position: absolute;
  inset: 0;
  z-index: 1;
  border-radius: inherit;
}

.card-link:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px rgba(23, 209, 167, 0.28);
}

/* 图形区：Cloud 底 + 一颗模糊薄荷光球，中间放商品 logo */
.thumb {
  position: relative;
  overflow: hidden;
  aspect-ratio: 16 / 10;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-bg-cloud);
  border-bottom: 1px solid var(--color-border);
}

.thumb-glow {
  position: absolute;
  width: 320px;
  height: 320px;
  left: 50%;
  top: 50%;
  margin: -160px 0 0 -160px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(31, 227, 173, 0.26), rgba(23, 209, 167, 0) 68%);
  filter: blur(22px);
  pointer-events: none;
}

.badge {
  position: absolute;
  top: 14px;
  left: 14px;
  padding: 4px 10px;
  border-radius: var(--radius-pill);
  background: var(--color-bg);
  color: var(--color-ink);
  font-size: 12px;
  font-weight: 500;
  box-shadow: 0 1px 3px rgba(11, 11, 12, 0.08);
  /* 角标压在整卡链接之下也不该吃掉点击 */
  pointer-events: none;
}

/* 商品图是品牌 logo 不是照片：等比放进方形框，不裁切不拉伸 */
.logo {
  position: relative;
  width: 132px;
  height: 132px;
  object-fit: contain;
  border-radius: 16px;
}

/* 无 logo 时回落商品首字，不画灰框 */
.placeholder {
  position: relative;
  font-family: 'Fredoka', 'Inter', sans-serif;
  font-size: 40px;
  font-weight: 600;
  color: var(--color-ink-secondary);
}

.body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 20px;
  flex: 1;
}

.name {
  font-size: 16px;
  font-weight: 600;
}

.desc {
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-ink-secondary);
  flex: 1;
}

.footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
}

.price {
  font-family: 'Fredoka', 'Inter', sans-serif;
  font-size: 22px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.buy-btn {
  /* 抬到铺满整卡的链接之上，否则点购买会被链接接走变成跳转 */
  position: relative;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 9px 18px;
  border: none;
  border-radius: var(--radius-button);
  background: var(--color-brand);
  color: var(--color-ink);
  font-size: 14px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.15s ease;
}

.buy-icon {
  width: 15px;
  height: 15px;
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
</style>
