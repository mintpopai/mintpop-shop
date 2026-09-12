<script setup lang="ts">
// 自绘弹窗：不用 window.confirm / alert——那是操作系统画的框，字体、圆角、按钮次序都不归这套设计管，
// 也没法本地化按钮文案。aria-modal 只是个声明，真正让它成立的是「焦点进得来、出不去、关得掉」。
import { onBeforeUnmount, onMounted, ref } from 'vue'

defineProps<{ title: string }>()
const emit = defineEmits<{ close: [] }>()

const dialog = ref<HTMLElement | null>(null)
/** 打开弹窗前焦点在哪，关闭后还回去，不把人丢回页首 */
let restoreTo: HTMLElement | null = null

const FOCUSABLE =
  'a[href], button:not([disabled]), input:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'

function focusables(): HTMLElement[] {
  return Array.from(dialog.value?.querySelectorAll<HTMLElement>(FOCUSABLE) ?? [])
}

onMounted(() => {
  restoreTo = document.activeElement instanceof HTMLElement ? document.activeElement : null
  // 落在第一个可聚焦元素（关闭按钮）上，而不是危险操作上
  ;(focusables()[0] ?? dialog.value)?.focus()
})

onBeforeUnmount(() => restoreTo?.focus())

/** Esc 关闭；Tab 在弹窗内首尾相接地循环，不跑到背后的页面去 */
function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    emit('close')
    return
  }
  if (event.key !== 'Tab') {
    return
  }
  const items = focusables()
  if (items.length === 0) {
    return
  }
  const first = items[0]
  const last = items[items.length - 1]
  if (
    event.shiftKey &&
    (document.activeElement === first || document.activeElement === dialog.value)
  ) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}
</script>

<template>
  <Teleport to="body">
    <!-- 遮罩不响应点击：确认类弹窗误点空白处关掉会让人以为操作已完成，只留 Esc 与按钮 -->
    <div class="overlay">
      <!-- tabindex="-1"：弹窗里万一没有可聚焦元素时，焦点也有地方落，键盘事件才收得到 -->
      <div
        ref="dialog"
        class="dialog"
        role="dialog"
        aria-modal="true"
        :aria-label="title"
        tabindex="-1"
        @keydown="onKeydown"
      >
        <header class="head">
          <h3 class="head-title">{{ title }}</h3>
          <button
            type="button"
            class="close"
            :aria-label="$t('common.close')"
            @click="emit('close')"
          >
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
  background: rgba(11, 11, 12, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  z-index: 30;
}

.dialog {
  width: 100%;
  max-width: 440px;
  max-height: calc(100vh - 48px);
  display: flex;
  flex-direction: column;
  background: var(--color-bg);
  border-radius: var(--radius-panel);
  box-shadow: 0 18px 56px rgba(11, 11, 12, 0.28);
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 20px 0;
}

.head-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-ink);
}

.close {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: var(--radius-button);
  background: none;
  font-size: 20px;
  line-height: 1;
  color: var(--color-ink-secondary);
  cursor: pointer;
}

.close:hover {
  background: var(--color-bg-cloud);
  color: var(--color-ink);
}

.close:focus-visible {
  outline: 2px solid var(--color-brand-deep);
  outline-offset: 1px;
}

.content {
  padding: 12px 20px 20px;
  overflow-y: auto;
}

.foot {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 0 20px 20px;
}

@media (max-width: 640px) {
  .overlay {
    padding: 16px;
    align-items: flex-end;
  }
}
</style>
