<script setup lang="ts">
import { formatPrice, type Product } from '../api'

defineProps<{ product: Product; buying: boolean }>()
const emit = defineEmits<{ buy: [product: Product] }>()
</script>

<template>
  <article class="card">
    <div class="thumb">
      <img v-if="product.imageUrl" :src="product.imageUrl" :alt="product.name" />
      <span v-else class="placeholder" aria-hidden="true">{{ product.name.charAt(0) }}</span>
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
          {{ buying ? '下单中…' : '购买' }}
        </button>
      </div>
    </div>
  </article>
</template>

<style scoped>
.card {
  display: flex;
  flex-direction: column;
  background: var(--color-bg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  overflow: hidden;
  transition: box-shadow 0.15s ease;
}

.card:hover {
  box-shadow: 0 4px 16px rgba(11, 11, 12, 0.08);
}

.thumb {
  aspect-ratio: 4 / 3;
  background: var(--color-bg-cloud);
  display: flex;
  align-items: center;
  justify-content: center;
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  max-width: 100%;
}

/* 无图占位：薄荷色圆底 + 商品首字 */
.placeholder {
  width: 72px;
  height: 72px;
  border-radius: var(--radius-pill);
  background: var(--color-brand);
  color: #ffffff;
  font-size: 32px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}

.body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px;
  flex: 1;
}

.name {
  font-size: 16px;
  font-weight: 600;
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
  font-size: 16px;
  font-weight: 600;
}

.buy-btn {
  padding: 8px 20px;
  border: none;
  border-radius: var(--radius-button);
  background: var(--color-brand);
  color: #ffffff;
  font-size: 14px;
  font-weight: 500;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.15s ease;
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
