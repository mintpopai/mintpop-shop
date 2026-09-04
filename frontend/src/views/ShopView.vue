<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  createOrder,
  fetchGroups,
  UnauthorizedError,
  type GroupWithProducts,
  type Product,
} from '../api'
import { currentUser, gotoLogin } from '../auth'
import { showToast } from '../toast'
import { t } from '../i18n'
import ProductCard from '../components/ProductCard.vue'

const router = useRouter()
const groups = ref<GroupWithProducts[]>([])
const activeGroupId = ref<number | null>(null)
const loading = ref(true)
const loadError = ref('')
const buyingProductId = ref<number | null>(null)

const activeGroup = computed(
  () => groups.value.find((g) => g.id === activeGroupId.value) ?? null,
)

onMounted(async () => {
  try {
    groups.value = await fetchGroups()
    activeGroupId.value = groups.value[0]?.id ?? null
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : t('common.loadFailed')
  } finally {
    loading.value = false
  }
})

async function buy(product: Product) {
  // 下单必须登录：游客直接引导去统一登录
  if (!currentUser.value) {
    showToast('error', t('shop.loginRequired'))
    gotoLogin()
    return
  }
  buyingProductId.value = product.id
  try {
    const result = await createOrder(product.id)
    await router.push(`/pay/${result.orderNo}`)
  } catch (e) {
    if (e instanceof UnauthorizedError) {
      showToast('error', t('shop.sessionExpired'))
      gotoLogin()
      return
    }
    showToast('error', e instanceof Error ? e.message : t('shop.orderFailed'))
  } finally {
    buyingProductId.value = null
  }
}
</script>

<template>
  <main class="page">
    <p v-if="loading" class="hint">{{ $t('common.loading') }}</p>
    <p v-else-if="loadError" class="hint error">{{ loadError }}</p>

    <template v-else>
      <section class="hero">
        <div class="hero-glow" aria-hidden="true"></div>
        <div class="hero-body">
          <span class="hero-badge">{{ $t('shop.heroBadge') }}</span>
          <h2 class="hero-title">
            {{ $t('shop.heroTitle1') }}<br />{{ $t('shop.heroTitle2') }}
          </h2>
          <p class="hero-desc">{{ $t('shop.heroDesc') }}</p>
        </div>
      </section>

      <div class="group-row">
        <nav class="group-nav" :aria-label="$t('shop.groupNav')">
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
        <span v-if="activeGroup" class="group-count">
          {{ $t('shop.productCount', { n: activeGroup.products.length }) }}
        </span>
      </div>

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
        {{ $t('shop.emptyGroup') }}
      </p>
    </template>
  </main>
</template>

<style scoped>
.page {
  max-width: 1152px;
  margin: 0 auto;
  padding: 40px 32px 64px;
}

.hint {
  color: var(--color-ink-secondary);
  padding: 24px 0;
}

.hint.error {
  color: var(--color-danger);
}

/* Hero：Cloud 平面，唯一的氛围手法是右上角那颗模糊薄荷光球 */
.hero {
  position: relative;
  overflow: hidden;
  border-radius: var(--radius-panel);
  padding: 64px 48px;
  margin-bottom: 40px;
  background: var(--color-bg-cloud);
}

.hero-glow {
  position: absolute;
  width: 520px;
  height: 520px;
  right: -120px;
  top: -220px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(31, 227, 173, 0.3), rgba(23, 209, 167, 0) 68%);
  filter: blur(28px);
  pointer-events: none;
}

/* 文字压在光球之上 */
.hero-body {
  position: relative;
}

.hero-badge {
  display: inline-block;
  padding: 6px 14px;
  border-radius: var(--radius-pill);
  background: var(--color-bg);
  color: var(--color-brand-ink);
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 24px;
  box-shadow: 0 1px 3px rgba(11, 11, 12, 0.06);
}

.hero-title {
  font-family: 'Fredoka', 'Inter', sans-serif;
  font-size: 48px;
  line-height: 1.2;
  font-weight: 600;
  letter-spacing: -0.015em;
  text-wrap: balance;
  max-width: 640px;
  margin-bottom: 16px;
  color: var(--color-ink);
}

.hero-desc {
  max-width: 560px;
  font-size: 15px;
  line-height: 1.7;
  color: var(--color-ink-secondary);
}

/* 分组行：左 pill 导航，右商品计数 */
.group-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}

.group-nav {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.pill {
  padding: 10px 20px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  background: var(--color-bg);
  color: var(--color-ink-secondary);
  font-size: 14px;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.pill:hover {
  border-color: var(--color-brand);
  color: var(--color-ink);
}

/* 焦点用薄荷软环，不用 outline：与基线其它可聚焦控件一致 */
.pill:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px rgba(23, 209, 167, 0.28);
}

.pill.active {
  background: var(--color-brand);
  border-color: var(--color-brand);
  color: var(--color-ink);
  font-weight: 600;
}

.group-count {
  flex-shrink: 0;
  font-size: 13px;
  color: var(--color-ink-secondary);
  font-variant-numeric: tabular-nums;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 24px;
}

@media (max-width: 720px) {
  .hero {
    padding: 36px 24px;
  }

  .hero-title {
    font-size: 30px;
  }
}
</style>
