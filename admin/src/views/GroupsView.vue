<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import Modal from '../components/Modal.vue'
import {
  createAdminGroup,
  deleteAdminGroup,
  fetchAdminGroups,
  updateAdminGroup,
  type AdminGroup,
} from '../api-admin'
import { showToast } from '../toast'

const groups = ref<AdminGroup[]>([])
const loading = ref(true)
const loadError = ref('')

/** 页头那行事实：分组一共装了多少件商品 */
const coveredProducts = computed(() => groups.value.reduce((sum, g) => sum + g.productCount, 0))

/** 弹窗状态：editingId 为空表示新增 */
const modalOpen = ref(false)
const editingId = ref<number | null>(null)
const form = ref({ nameZh: '', nameEn: '', sortOrder: 0 })
const saving = ref(false)

async function reload() {
  try {
    groups.value = await fetchAdminGroups()
    loadError.value = ''
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

onMounted(reload)

function openCreate() {
  editingId.value = null
  // 新分组默认排到最后
  const maxSort = groups.value.reduce((max, g) => Math.max(max, g.sortOrder), 0)
  form.value = { nameZh: '', nameEn: '', sortOrder: maxSort + 10 }
  modalOpen.value = true
}

function openEdit(group: AdminGroup) {
  editingId.value = group.id
  form.value = { nameZh: group.nameZh, nameEn: group.nameEn ?? '', sortOrder: group.sortOrder }
  modalOpen.value = true
}

async function onSave() {
  if (!form.value.nameZh.trim()) {
    showToast('error', '请完整填写必填项')
    return
  }
  saving.value = true
  try {
    if (editingId.value === null) {
      await createAdminGroup(form.value)
    } else {
      await updateAdminGroup(editingId.value, form.value)
    }
    showToast('success', '已保存')
    modalOpen.value = false
    await reload()
  } catch (e) {
    showToast('error', e instanceof Error ? e.message : '请求失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

/** 待确认删除的分组；非空即打开确认弹窗。
    不用 window.confirm：那是操作系统画的框，字体、圆角、按钮次序都不归这套设计管，
    而且它把「删掉哪一个」压成一行系统文案，说不清后果 */
const deleting = ref<AdminGroup | null>(null)
const deletingBusy = ref(false)

async function onDelete() {
  const group = deleting.value
  if (!group) {
    return
  }
  deletingBusy.value = true
  try {
    await deleteAdminGroup(group.id)
    showToast('success', '已删除')
    deleting.value = null
    await reload()
  } catch (e) {
    showToast('error', e instanceof Error ? e.message : '请求失败，请稍后重试')
  } finally {
    deletingBusy.value = false
  }
}
</script>

<template>
  <header class="page-head">
    <h2 class="page-title">分组</h2>
    <p class="page-facts">
      共 <span class="fact">{{ groups.length }}</span> 组，装着
      <span class="fact">{{ coveredProducts }}</span> 件商品。排序号小的排在商城前面。
    </p>
  </header>

  <div class="admin-toolbar">
    <span class="spacer"></span>
    <button type="button" class="admin-btn" @click="openCreate">新增分组</button>
  </div>

  <p v-if="loading" class="admin-hint">加载中……</p>
  <p v-else-if="loadError" class="admin-hint error">{{ loadError }}</p>

  <div v-else class="admin-card">
    <p v-if="groups.length === 0" class="admin-hint">还没有分组。商品必须归到某个分组下，先建一个。</p>
    <table v-else class="admin-table">
      <thead>
        <tr>
          <th>ID</th>
          <th>名称（中文）</th>
          <th>名称（英文）</th>
          <th class="col-amount">排序号</th>
          <th class="col-amount">商品数</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="group in groups" :key="group.id">
          <td class="fact muted">{{ group.id }}</td>
          <td>{{ group.nameZh }}</td>
          <td>{{ group.nameEn || '—' }}</td>
          <td class="fact col-amount">{{ group.sortOrder }}</td>
          <td class="fact col-amount">{{ group.productCount }}</td>
          <td class="actions">
            <button type="button" class="admin-link" @click="openEdit(group)">编辑</button>
            <button
              type="button"
              class="admin-link danger"
              :disabled="group.productCount > 0"
              :title="group.productCount > 0 ? '组内有商品，不可删除' : ''"
              @click="deleting = group"
            >
              删除
            </button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>

  <Modal v-if="modalOpen" :title="editingId === null ? '新增分组' : '编辑分组'" @close="modalOpen = false">
    <form class="admin-form" @submit.prevent="onSave">
      <div class="admin-form-row">
        <div class="admin-field">
          <label for="group-name-zh">名称（中文）</label>
          <input id="group-name-zh" v-model="form.nameZh" class="admin-input" required />
        </div>
        <div class="admin-field">
          <label for="group-name-en">名称（英文）</label>
          <input id="group-name-en" v-model="form.nameEn" class="admin-input" />
        </div>
      </div>
      <div class="admin-field">
        <label for="group-sort">排序号（小的在前）</label>
        <input id="group-sort" v-model.number="form.sortOrder" class="admin-input" type="number" required />
      </div>
    </form>
    <template #footer>
      <button type="button" class="admin-btn-ghost" @click="modalOpen = false">取消</button>
      <button type="button" class="admin-btn" :disabled="saving" @click="onSave">
        {{ saving ? '保存中…' : '保存' }}
      </button>
    </template>
  </Modal>

  <!-- 删除确认：说清删的是哪一个、以及删掉之后会怎样，按钮直接写动作而不是「确定」 -->
  <Modal v-if="deleting" title="删除分组" @close="deleting = null">
    <p class="confirm-text">
      分组「{{ deleting.nameZh }}」将从商城下架，这个操作无法撤销。组里现在没有商品，删除不影响任何商品。
    </p>
    <template #footer>
      <button type="button" class="admin-btn-ghost" @click="deleting = null">取消</button>
      <button type="button" class="admin-btn danger" :disabled="deletingBusy" @click="onDelete">
        {{ deletingBusy ? '删除中…' : '删除分组' }}
      </button>
    </template>
  </Modal>
</template>

<style scoped>
.confirm-text {
  font-size: 14px;
  line-height: 1.75;
  color: var(--color-ink-secondary);
}
</style>
