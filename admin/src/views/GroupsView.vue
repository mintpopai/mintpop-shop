<script setup lang="ts">
import { onMounted, ref } from 'vue'
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

async function onDelete(group: AdminGroup) {
  if (!window.confirm(`确定删除分组「${group.nameZh}」吗？`)) {
    return
  }
  try {
    await deleteAdminGroup(group.id)
    showToast('success', '已删除')
    await reload()
  } catch (e) {
    showToast('error', e instanceof Error ? e.message : '请求失败，请稍后重试')
  }
}
</script>

<template>
  <h2 class="admin-title">分组管理</h2>

  <div class="admin-toolbar">
    <span class="spacer"></span>
    <button type="button" class="admin-btn" @click="openCreate">新增分组</button>
  </div>

  <p v-if="loading" class="admin-hint">加载中……</p>
  <p v-else-if="loadError" class="admin-hint error">{{ loadError }}</p>

  <div v-else class="admin-card">
    <p v-if="groups.length === 0" class="admin-hint">暂无数据</p>
    <table v-else class="admin-table">
      <thead>
        <tr>
          <th>ID</th>
          <th>名称（中文）</th>
          <th>名称（英文）</th>
          <th>排序号</th>
          <th>商品数</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="group in groups" :key="group.id">
          <td>{{ group.id }}</td>
          <td>{{ group.nameZh }}</td>
          <td>{{ group.nameEn ?? '—' }}</td>
          <td>{{ group.sortOrder }}</td>
          <td>{{ group.productCount }}</td>
          <td class="actions">
            <button type="button" class="admin-link" @click="openEdit(group)">编辑</button>
            <button
              type="button"
              class="admin-link danger"
              :disabled="group.productCount > 0"
              :title="group.productCount > 0 ? '组内有商品，不可删除' : ''"
              @click="onDelete(group)"
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
</template>

<style scoped>
.actions {
  display: flex;
  gap: 12px;
  white-space: nowrap;
}
</style>
