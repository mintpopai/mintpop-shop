<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import Modal from './Modal.vue'
import {
  fetchAdminShipments,
  shipAdminOrder,
  type AdminShipmentItem,
} from '../api-admin'
import { formatDateTime } from '../datetime'
import { showToast } from '../toast'

const props = defineProps<{ orderNo: string }>()
const emit = defineEmits<{ close: []; shipped: [] }>()

const history = ref<AdminShipmentItem[]>([])
const historyLoading = ref(true)
const historyError = ref('')

const content = ref('')
const reason = ref('')
const submitting = ref(false)
const formError = ref('')

/** 发过货就是「重新发货」：必须说明原因，避免无痕覆盖 */
const isReship = computed(() => history.value.length > 0)

onMounted(async () => {
  try {
    history.value = await fetchAdminShipments(props.orderNo)
  } catch (e) {
    historyError.value = e instanceof Error ? e.message : '发货历史加载失败'
  } finally {
    historyLoading.value = false
  }
})

async function onSubmit() {
  const trimmed = content.value.trim()
  if (!trimmed) {
    formError.value = '发货内容不能为空'
    return
  }
  if (trimmed.length > 2000) {
    formError.value = '发货内容最多 2000 字'
    return
  }
  if (isReship.value && !reason.value.trim()) {
    formError.value = '重新发货必须填写原因'
    return
  }
  formError.value = ''
  submitting.value = true
  try {
    const result = await shipAdminOrder(props.orderNo, {
      content: trimmed,
      reason: isReship.value ? reason.value.trim() : undefined,
    })
    if (result.emailStatus === 'SENT') {
      showToast('success', '已发货，发货邮件已发送')
    } else {
      // 发货本身已落库，只是邮件没发出去——文案必须说清，别让人以为整件事失败了
      showToast('error', `已发货，但邮件发送失败：${result.emailError ?? '未知原因'}`)
    }
    emit('shipped')
    emit('close')
  } catch (e) {
    formError.value = e instanceof Error ? e.message : '发货失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <Modal :title="isReship ? '重新发货' : '发货'" @close="emit('close')">
    <p class="order-line">
      订单号 <span class="fact">{{ orderNo }}</span>
    </p>

    <section class="history">
      <h4 class="section-title">发货历史</h4>
      <p v-if="historyLoading" class="admin-hint">加载中……</p>
      <p v-else-if="historyError" class="admin-hint error">{{ historyError }}</p>
      <p v-else-if="history.length === 0" class="admin-hint">还没有发货记录。</p>
      <ul v-else class="history-list">
        <li v-for="item in history" :key="item.id" class="history-item">
          <div class="history-head">
            <span class="fact muted">{{ formatDateTime(item.shippedAt) }}</span>
            <span class="mail-state" :data-sent="item.emailStatus === 'SENT'">
              {{ item.emailStatus === 'SENT' ? '邮件已发送' : '邮件发送失败' }}
            </span>
          </div>
          <p class="history-meta">
            {{ item.operatorEmail ?? '未知操作人' }} → {{ item.emailTo }}
            <template v-if="item.reason">· 原因：{{ item.reason }}</template>
          </p>
          <pre class="history-content">{{ item.content }}</pre>
          <p v-if="item.emailError" class="history-error">{{ item.emailError }}</p>
        </li>
      </ul>
    </section>

    <section class="form">
      <h4 class="section-title">{{ isReship ? '新的发货内容' : '发货内容' }}</h4>
      <textarea
        v-model="content"
        class="admin-input textarea"
        rows="6"
        placeholder="填写给买家的发货信息，如兑换码、下载地址、账号密码等"
      ></textarea>
      <template v-if="isReship">
        <h4 class="section-title">重新发货原因</h4>
        <input v-model="reason" class="admin-input" placeholder="如：上次发错卡密" />
      </template>
      <p v-if="formError" class="admin-hint error">{{ formError }}</p>
    </section>

    <template #footer>
      <button type="button" class="admin-btn-ghost" @click="emit('close')">取消</button>
      <button type="button" class="admin-btn" :disabled="submitting" @click="onSubmit">
        {{ submitting ? '发货中…' : '确认发货并发邮件' }}
      </button>
    </template>
  </Modal>
</template>

<style scoped>
.order-line {
  font-size: 13px;
  color: var(--color-ink-secondary);
  margin-bottom: 16px;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-ink);
  margin: 0 0 8px;
}

.history {
  margin-bottom: 20px;
}

.history-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 220px;
  overflow-y: auto;
}

.history-item {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  padding: 10px 12px;
}

.history-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.mail-state {
  font-size: 12px;
  color: #b45309;
}

.mail-state[data-sent='true'] {
  color: var(--color-brand-deep);
}

.history-meta {
  font-size: 12px;
  color: var(--color-ink-secondary);
  margin: 4px 0;
}

.history-content {
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: 13px;
  margin: 0;
  color: var(--color-ink);
}

.history-error {
  font-size: 12px;
  color: #b91c1c;
  margin: 6px 0 0;
}

.textarea {
  width: 100%;
  resize: vertical;
  margin-bottom: 12px;
}
</style>
