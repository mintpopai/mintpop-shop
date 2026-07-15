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
        <span class="hero-badge">✦ {{ $t('shop.heroBadge') }}</span>
        <h2 class="hero-title">
          {{ $t('shop.heroTitle1') }}<br />{{ $t('shop.heroTitle2') }}
        </h2>
        <p class="hero-desc">{{ $t('shop.heroDesc') }}</p>
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
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px 32px 64px;
}

.hint {
  color: var(--color-ink-secondary);
  padding: 24px 0;
}

.hint.error {
  color: #b91c1c;
}

/* Hero：品牌绿渐变 + 右侧点阵纹理（纯 CSS，不引外部资源） */
.hero {
  position: relative;
  overflow: hidden;
  border-radius: 24px;
  padding: 56px 56px 64px;
  margin-bottom: 32px;
  background: linear-gradient(120deg, #1fd8a4 0%, #0ec98f 55%, #0abf85 100%);
  color: #ffffff;
}

.hero::after {
  content: '';
  position: absolute;
  inset: 0 0 0 55%;
  background-image: radial-gradient(rgba(255, 255, 255, 0.28) 1.5px, transparent 1.5px);
  background-size: 16px 16px;
  mask-image: linear-gradient(to right, transparent, #000 60%);
  pointer-events: none;
}

.hero-badge {
  display: inline-block;
  padding: 8px 16px;
  border-radius: var(--radius-pill);
  background: rgba(255, 255, 255, 0.18);
  font-size: 13px;
  font-weight: 500;
  margin-bottom: 20px;
}

.hero-title {
  font-family: 'Fredoka', 'Inter', sans-serif;
  font-size: 44px;
  line-height: 1.25;
  font-weight: 600;
  margin-bottom: 16px;
}

.hero-desc {
  max-width: 560px;
  font-size: 15px;
  line-height: 1.7;
  color: rgba(255, 255, 255, 0.92);
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
  gap: 10px;
}

.pill {
  padding: 12px 22px;
  border: 1px solid transparent;
  border-radius: var(--radius-pill);
  background: var(--color-bg);
  color: var(--color-ink);
  font-size: 14px;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.15s ease;
}

.pill:hover {
  color: var(--color-brand-deep);
}

.pill:focus-visible {
  outline: 2px solid var(--color-brand-deep);
  outline-offset: 2px;
}

.pill.active {
  background: linear-gradient(120deg, #1fd8a4, #0abf85);
  color: #ffffff;
  font-weight: 500;
  box-shadow: 0 6px 16px rgba(15, 179, 137, 0.35);
}

.group-count {
  flex-shrink: 0;
  font-size: 13px;
  color: var(--color-ink-secondary);
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 24px;
}

@media (max-width: 720px) {
  .hero {
    padding: 36px 28px 44px;
  }

  .hero-title {
    font-size: 30px;
  }
}
</style>
