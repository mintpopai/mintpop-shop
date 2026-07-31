<script setup lang="ts">
defineProps<{ title: string }>()
const emit = defineEmits<{ close: [] }>()
</script>

<template>
  <Teleport to="body">
    <div class="overlay" @click.self="emit('close')">
      <div class="dialog" role="dialog" aria-modal="true" :aria-label="title">
        <header class="head">
          <h3 class="head-title">{{ title }}</h3>
          <button type="button" class="close" aria-label="关闭" @click="emit('close')">
            ×
          </button>
        </header>
        <div class="content">
          <slot />
        </div>
        <footer class="foot">
          <slot name="footer" />
        </footer>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 26, 22, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  z-index: 30;
}

.dialog {
  width: 100%;
  max-width: 560px;
  max-height: calc(100vh - 48px);
  display: flex;
  flex-direction: column;
  background: var(--color-bg);
  border-radius: var(--radius-card);
  box-shadow: 0 18px 56px rgba(15, 26, 22, 0.28);
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--color-border);
}

.head-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-ink);
}

.close {
  border: none;
  background: none;
  font-size: 22px;
  line-height: 1;
  color: var(--color-ink-secondary);
  cursor: pointer;
}

.close:hover {
  color: var(--color-ink);
}

.content {
  padding: 20px;
  overflow-y: auto;
}

.foot {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 20px;
  border-top: 1px solid var(--color-border);
}
</style>
