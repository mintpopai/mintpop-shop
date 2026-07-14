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
</style>
