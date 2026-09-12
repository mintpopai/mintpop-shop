<script setup lang="ts">
// 选本地图片上传到 R2 的按钮：商品表单的「商品图」与富文本的「插入图片」共用。
// 自己只管「选文件 → 上传 → 抛 URL」，失败走全局 toast；拿到 URL 之后怎么用由父组件决定。
import { ref } from 'vue'
import { uploadAdminImage } from '../api-admin'
import { showToast } from '../toast'

/** 与后端 ImageUploadService.MAX_BYTES、spring.servlet.multipart.max-file-size 一致 */
const MAX_BYTES = 5 * 1024 * 1024
/** 与后端 ImageTypeEnum 一致；这只是文件选择框的过滤，真正的判型在后端按魔数做 */
const ACCEPT = 'image/jpeg,image/png,image/webp,image/gif'

withDefaults(defineProps<{ label?: string }>(), { label: '上传图片' })
const emit = defineEmits<{ uploaded: [url: string] }>()

const input = ref<HTMLInputElement | null>(null)
const uploading = ref(false)

function pick() {
  input.value?.click()
}

async function onChange(event: Event) {
  const el = event.target as HTMLInputElement
  const file = el.files?.[0]
  // 立刻清空：同一个文件再选一次浏览器才会再触发 change
  el.value = ''
  if (!file) {
    return
  }
  if (file.size > MAX_BYTES) {
    showToast('error', '图片不能超过 5 MB')
    return
  }
  uploading.value = true
  try {
    emit('uploaded', await uploadAdminImage(file))
  } catch (err) {
    showToast('error', err instanceof Error ? err.message : '图片上传失败')
  } finally {
    uploading.value = false
  }
}
</script>

<template>
  <span class="upload">
    <button type="button" class="admin-btn-ghost" :disabled="uploading" @click="pick">
      {{ uploading ? '上传中…' : label }}
    </button>
    <input ref="input" class="upload-input" type="file" :accept="ACCEPT" @change="onChange" />
  </span>
</template>

<style scoped>
.upload {
  display: inline-flex;
  flex-shrink: 0;
}

/* 文件框只负责弹系统选择器，界面上只露那个按钮 */
.upload-input {
  display: none;
}
</style>
