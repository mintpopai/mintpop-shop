<script setup lang="ts">
import { onMounted, ref } from 'vue'
import AdminModal from '../../components/AdminModal.vue'
import {
  createAdminGroup,
  deleteAdminGroup,
  fetchAdminGroups,
  updateAdminGroup,
  type AdminGroup,
} from '../../api-admin'
import { t } from '../../i18n'
import { showToast } from '../../toast'

const groups = ref<AdminGroup[]>([])
const loading = ref(true)
const loadError = ref('')

/** 弹窗状态：null=关闭；editingId 为空表示新增 */
const modalOpen = ref(false)
const editingId = ref<number | null>(null)
const form = ref({ nameZh: '', nameEn: '', sortOrder: 0 })
const saving = ref(false)

async function reload() {
  try {
    groups.value = await fetchAdminGroups()
    loadError.value = ''
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : t('common.loadFailed')
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
    showToast('error', t('admin.form.invalid'))
    return
  }
  saving.value = true
  try {
    if (editingId.value === null) {
      await createAdminGroup(form.value)
    } else {
      await updateAdminGroup(editingId.value, form.value)
    }
    showToast('success', t('admin.groups.saved'))
    modalOpen.value = false
    await reload()
  } catch (e) {
    showToast('error', e instanceof Error ? e.message : t('api.requestFailed'))
  } finally {
    saving.value = false
  }
}

async function onDelete(group: AdminGroup) {
  if (!window.confirm(t('admin.groups.deleteConfirm', { name: group.nameZh }))) {
    return
  }
  try {
    await deleteAdminGroup(group.id)
    showToast('success', t('admin.groups.deleted'))
    await reload()
  } catch (e) {
    showToast('error', e instanceof Error ? e.message : t('api.requestFailed'))
  }
}
</script>

<template>
  <h2 class="admin-title">{{ $t('admin.groups.title') }}</h2>

  <div class="admin-toolbar">
    <span class="spacer"></span>
    <button type="button" class="admin-btn" @click="openCreate">{{ $t('admin.groups.add') }}</button>
  </div>

  <p v-if="loading" class="admin-hint">{{ $t('common.loading') }}</p>
  <p v-else-if="loadError" class="admin-hint error">{{ loadError }}</p>

  <div v-else class="admin-card">
    <p v-if="groups.length === 0" class="admin-hint">{{ $t('admin.table.empty') }}</p>
    <table v-else class="admin-table">
      <thead>
        <tr>
          <th>{{ $t('admin.table.id') }}</th>
          <th>{{ $t('admin.groups.nameZh') }}</th>
          <th>{{ $t('admin.groups.nameEn') }}</th>
          <th>{{ $t('admin.groups.sortOrder') }}</th>
          <th>{{ $t('admin.groups.productCount') }}</th>
          <th>{{ $t('admin.table.actions') }}</th>
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
            <button type="button" class="admin-link" @click="openEdit(group)">
              {{ $t('admin.groups.editAction') }}
            </button>
            <button
              type="button"
              class="admin-link danger"
              :disabled="group.productCount > 0"
              :title="group.productCount > 0 ? $t('admin.groups.notEmptyHint') : ''"
              @click="onDelete(group)"
            >
              {{ $t('admin.groups.delete') }}
            </button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>

  <AdminModal
    v-if="modalOpen"
    :title="editingId === null ? $t('admin.groups.add') : $t('admin.groups.edit')"
    @close="modalOpen = false"
  >
    <form class="admin-form" @submit.prevent="onSave">
      <div class="admin-form-row">
        <div class="admin-field">
          <label for="group-name-zh">{{ $t('admin.groups.nameZh') }}</label>
          <input id="group-name-zh" v-model="form.nameZh" class="admin-input" required />
        </div>
        <div class="admin-field">
          <label for="group-name-en">{{ $t('admin.groups.nameEn') }}</label>
          <input id="group-name-en" v-model="form.nameEn" class="admin-input" />
        </div>
      </div>
      <div class="admin-field">
        <label for="group-sort">{{ $t('admin.groups.sortOrder') }}</label>
        <input id="group-sort" v-model.number="form.sortOrder" class="admin-input" type="number" required />
      </div>
    </form>
    <template #footer>
      <button type="button" class="admin-btn-ghost" @click="modalOpen = false">
        {{ $t('admin.form.cancel') }}
      </button>
      <button type="button" class="admin-btn" :disabled="saving" @click="onSave">
        {{ saving ? $t('admin.form.saving') : $t('admin.form.save') }}
      </button>
    </template>
  </AdminModal>
</template>

<style scoped>
.actions {
  display: flex;
  gap: 12px;
  white-space: nowrap;
}
</style>
