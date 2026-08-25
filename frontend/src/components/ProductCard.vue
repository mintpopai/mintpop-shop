<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { accentColors } from '../accent'
import { formatPrice, type Product } from '../api'

const props = defineProps<{ product: Product; buying: boolean }>()
const emit = defineEmits<{ buy: [product: Product] }>()

const accent = computed(() => accentColors(props.product.accent))
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
    <div
      class="thumb"
      :style="{ background: `linear-gradient(135deg, ${accent.from}, ${accent.to})` }"
    >
      <span v-if="product.badge" class="badge">{{ product.badge }}</span>
      <img v-if="product.imageUrl" class="photo" :src="product.imageUrl" :alt="product.name" />
      <span v-else class="placeholder" :style="{ color: accent.ink }" aria-hidden="true">
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
  border-radius: 20px;
  overflow: hidden;
  box-shadow: 0 2px 10px rgba(11, 11, 12, 0.05);
  transition: box-shadow 0.15s ease, transform 0.15s ease;
}

.card:hover {
  box-shadow: 0 10px 28px rgba(11, 11, 12, 0.1);
  transform: translateY(-2px);
}

.card-link {
  position: absolute;
  inset: 0;
  z-index: 1;
  border-radius: inherit;
}

.card-link:focus-visible {
  outline: 2px solid var(--color-brand-deep);
  outline-offset: 2px;
}

/* 图形区：accent 渐变（内联 style）+ 点阵纹理 + 角标 */
.thumb {
  position: relative;
  aspect-ratio: 16 / 10;
  display: flex;
  align-items: center;
  justify-content: center;
}

.thumb::after {
  content: '';
  position: absolute;
  inset: 0 0 0 55%;
  background-image: radial-gradient(rgba(255, 255, 255, 0.5) 1.5px, transparent 1.5px);
  background-size: 14px 14px;
  mask-image: linear-gradient(to right, transparent, #000 60%);
  pointer-events: none;
}

/* 有真实商品图时不叠点阵纹理 */
.thumb:has(.photo)::after {
  content: none;
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

/* 无图占位：白色大圆 + accent 色商品首字 */
.placeholder {
  width: 108px;
  height: 108px;
  border-radius: var(--radius-pill);
  background: var(--color-bg);
  font-family: 'Fredoka', 'Inter', sans-serif;
  font-size: 40px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 20px rgba(11, 11, 12, 0.08);
}

.body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 20px;
  flex: 1;
}

.name {
  font-size: 17px;
  font-weight: 700;
}

.desc {
  font-size: 13px;
  color: var(--color-ink-secondary);
  flex: 1;
}

.footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.price {
  font-size: 20px;
  font-weight: 700;
}

.buy-btn {
  /* 抬到铺满整卡的链接之上，否则点购买会被链接接走变成跳转 */
  position: relative;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  border: none;
  border-radius: 12px;
  background: var(--color-brand);
  color: #ffffff;
  font-size: 14px;
  font-weight: 500;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.15s ease;
  box-shadow: 0 6px 16px rgba(23, 209, 167, 0.35);
}

.buy-icon {
  width: 15px;
  height: 15px;
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
</style>
