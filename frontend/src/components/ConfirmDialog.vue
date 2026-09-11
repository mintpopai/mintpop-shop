<script setup lang="ts">
// 确认弹窗：说清后果、按钮直接写动作（「确认取消」而不是「确定」），
// 主按钮是破坏性动作时用红底，次按钮是「留下来」的安全出口。
import Modal from './Modal.vue'

withDefaults(
  defineProps<{
    title: string
    text: string
    confirmLabel: string
    cancelLabel: string
    /** 请求进行中：主按钮禁用并换成 busyLabel，防止连点 */
    busy?: boolean
    busyLabel?: string
  }>(),
  { busy: false, busyLabel: '' },
)
const emit = defineEmits<{ confirm: []; close: [] }>()
</script>

<template>
  <Modal :title="title" @close="emit('close')">
    <p class="confirm-text">{{ text }}</p>
    <template #footer>
      <button type="button" class="btn-ghost" @click="emit('close')">{{ cancelLabel }}</button>
      <button type="button" class="btn-danger" :disabled="busy" @click="emit('confirm')">
        {{ busy && busyLabel ? busyLabel : confirmLabel }}
      </button>
    </template>
  </Modal>
</template>

<style scoped>
.confirm-text {
  font-size: 14px;
  line-height: 1.75;
  color: var(--color-ink-secondary);
  overflow-wrap: anywhere;
}

.btn-ghost,
.btn-danger {
  padding: 8px 18px;
  border-radius: var(--radius-button);
  font-family: inherit;
  font-size: 14px;
  font-weight: 500;
  line-height: 20px;
  cursor: pointer;
}

.btn-ghost {
  border: 1px solid var(--color-border);
  background: var(--color-bg);
  color: var(--color-ink);
  transition: border-color 0.15s ease;
}

.btn-ghost:hover {
  border-color: var(--color-ink-secondary);
}

.btn-danger {
  border: 1px solid transparent;
  background: var(--color-danger);
  color: #ffffff;
  transition: background 0.15s ease;
}

.btn-danger:hover:not(:disabled) {
  background: #c23a3f;
}

.btn-danger:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-ghost:focus-visible,
.btn-danger:focus-visible {
  outline: 2px solid var(--color-brand-deep);
  outline-offset: 2px;
}
</style>
