<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { createOrder, fetchGroups, type GroupWithProducts, type Product } from './api'
import ProductCard from './components/ProductCard.vue'

const groups = ref<GroupWithProducts[]>([])
const activeGroupId = ref<number | null>(null)
const loading = ref(true)
const loadError = ref('')
const buyingProductId = ref<number | null>(null)

interface Toast {
  type: 'success' | 'error'
  text: string
}
const toast = ref<Toast | null>(null)
let toastTimer: ReturnType<typeof setTimeout> | undefined

function showToast(type: Toast['type'], text: string) {
  toast.value = { type, text }
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => {
    toast.value = null
  }, 3000)
}

const activeGroup = computed(
  () => groups.value.find((g) => g.id === activeGroupId.value) ?? null,
)

onMounted(async () => {
  try {
    groups.value = await fetchGroups()
    activeGroupId.value = groups.value[0]?.id ?? null
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
})

async function buy(product: Product) {
  buyingProductId.value = product.id
  try {
    const result = await createOrder(product.id)
    showToast('success', `下单成功，订单号 ${result.orderNo}`)
  } catch (e) {
    showToast('error', e instanceof Error ? e.message : '下单失败，请稍后重试')
  } finally {
    buyingProductId.value = null
  }
}
</script>

<template>
  <header class="header">
    <h1 class="wordmark">MintPop <span class="wordmark-sub">Shop</span></h1>
  </header>

  <main class="page">
    <p v-if="loading" class="hint">加载中……</p>
    <p v-else-if="loadError" class="hint error">{{ loadError }}</p>

    <template v-else>
      <nav class="group-nav" aria-label="商品分组">
        <button
          v-for="group in groups"
          :key="group.id"
          type="button"
          class="pill"
          :class="{ active: group.id === activeGroupId }"
          @click="activeGroupId = group.id"
        >
          {{ group.name }}
        </button>
      </nav>

      <section v-if="activeGroup" class="grid" aria-live="polite">
        <ProductCard
          v-for="product in activeGroup.products"
          :key="product.id"
          :product="product"
          :buying="buyingProductId === product.id"
          @buy="buy"
        />
      </section>
      <p v-if="activeGroup && activeGroup.products.length === 0" class="hint">
        该分组暂无上架商品
      </p>
    </template>
  </main>

  <Transition name="toast">
    <div v-if="toast" class="toast" :class="toast.type" role="status">
      {{ toast.text }}
    </div>
  </Transition>
</template>

<style scoped>
.header {
  padding: 16px 32px;
  border-bottom: 1px solid var(--color-border);
}

.wordmark {
  font-size: 24px;
  color: var(--color-brand-deep);
}

.wordmark-sub {
  color: var(--color-ink);
  font-weight: 500;
}

.page {
  max-width: 1080px;
  margin: 0 auto;
  padding: 24px 32px 48px;
}

.hint {
  color: var(--color-ink-secondary);
  padding: 24px 0;
}

.hint.error {
  color: #b91c1c;
}

.group-nav {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 24px;
}

.pill {
  padding: 8px 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  background: var(--color-bg);
  color: var(--color-ink);
  font-size: 14px;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.15s ease;
}

.pill:hover {
  border-color: var(--color-brand);
  color: var(--color-brand-deep);
}

.pill:focus-visible {
  outline: 2px solid var(--color-brand-deep);
  outline-offset: 2px;
}

.pill.active {
  background: var(--color-brand);
  border-color: var(--color-brand);
  color: #ffffff;
  font-weight: 500;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
}

.toast {
  position: fixed;
  top: 24px;
  left: 50%;
  transform: translateX(-50%);
  padding: 12px 24px;
  border-radius: var(--radius-card);
  background: var(--color-ink);
  color: #ffffff;
  font-size: 14px;
  box-shadow: 0 8px 24px rgba(11, 11, 12, 0.16);
  z-index: 10;
}

.toast.success {
  background: var(--color-brand-deep);
}

.toast.error {
  background: #b91c1c;
}

.toast-enter-active,
.toast-leave-active {
  transition: all 0.2s ease;
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(-8px);
}
</style>
