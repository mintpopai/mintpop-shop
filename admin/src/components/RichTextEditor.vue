<script setup lang="ts">
// 商品详情的富文本编辑器：TipTap（ProseMirror 内核），对外就是一个 v-model 绑 HTML 字符串的输入框。
// 产出的 HTML 后端还会用白名单再净化一遍，这里不负责安全，只负责好用。
import { onBeforeUnmount, watch } from 'vue'
import { EditorContent, useEditor } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Image from '@tiptap/extension-image'

const props = defineProps<{ id: string; modelValue: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

/** 空文档时 TipTap 给的是 <p></p> 这类空壳，对外一律归一成空串，别把空壳存进库 */
function toModelValue(html: string): string {
  return html === '<p></p>' ? '' : html
}

const editor = useEditor({
  content: props.modelValue,
  extensions: [
    StarterKit.configure({
      // 外链固定新标签页打开，rel 交给 TipTap 默认的 noopener noreferrer nofollow
      link: { HTMLAttributes: { target: '_blank' } },
    }),
    // 详情里的配图仍是填 URL（与商品主图一致），本项目没有图床，不开 base64 内联
    Image,
  ],
  editorProps: { attributes: { class: 'editor-surface', id: props.id } },
  onUpdate: ({ editor }) => emit('update:modelValue', toModelValue(editor.getHTML())),
})

// 外部换内容（切到另一个商品、或打开编辑弹窗回填）时同步进编辑器；
// 内容与当前一致时必须跳过——重建文档会把光标打回开头，正在打字的人会当场跳字
watch(
  () => props.modelValue,
  (value) => {
    if (!editor.value || toModelValue(editor.value.getHTML()) === value) {
      return
    }
    editor.value.commands.setContent(value, { emitUpdate: false })
  },
)

onBeforeUnmount(() => editor.value?.destroy())

/** 暴露编辑器实例：父组件（或用例）需要主动聚焦、设选区时用 */
defineExpose({ editor })

interface Tool {
  title: string
  label: string
  run: () => void
  active?: () => boolean
}

/** 链接与图片要问一句地址，其余都是无参命令 */
function promptSetLink() {
  const previous = editor.value?.getAttributes('link').href ?? ''
  const href = window.prompt('链接地址（留空则取消链接）', previous)
  if (href === null) {
    return
  }
  if (href.trim() === '') {
    editor.value?.chain().focus().unsetLink().run()
    return
  }
  editor.value?.chain().focus().extendMarkRange('link').setLink({ href: href.trim() }).run()
}

function promptInsertImage() {
  const src = window.prompt('图片地址（https://…）', '')
  if (src === null || src.trim() === '') {
    return
  }
  editor.value?.chain().focus().setImage({ src: src.trim() }).run()
}

const tools: Tool[] = [
  { title: '加粗', label: 'B', run: () => editor.value?.chain().focus().toggleBold().run(), active: () => !!editor.value?.isActive('bold') },
  { title: '斜体', label: 'I', run: () => editor.value?.chain().focus().toggleItalic().run(), active: () => !!editor.value?.isActive('italic') },
  { title: '下划线', label: 'U', run: () => editor.value?.chain().focus().toggleUnderline().run(), active: () => !!editor.value?.isActive('underline') },
  { title: '删除线', label: 'S', run: () => editor.value?.chain().focus().toggleStrike().run(), active: () => !!editor.value?.isActive('strike') },
  { title: '二级标题', label: 'H2', run: () => editor.value?.chain().focus().toggleHeading({ level: 2 }).run(), active: () => !!editor.value?.isActive('heading', { level: 2 }) },
  { title: '三级标题', label: 'H3', run: () => editor.value?.chain().focus().toggleHeading({ level: 3 }).run(), active: () => !!editor.value?.isActive('heading', { level: 3 }) },
  { title: '无序列表', label: '• 列表', run: () => editor.value?.chain().focus().toggleBulletList().run(), active: () => !!editor.value?.isActive('bulletList') },
  { title: '有序列表', label: '1. 列表', run: () => editor.value?.chain().focus().toggleOrderedList().run(), active: () => !!editor.value?.isActive('orderedList') },
  { title: '引用', label: '❝', run: () => editor.value?.chain().focus().toggleBlockquote().run(), active: () => !!editor.value?.isActive('blockquote') },
  { title: '分隔线', label: '—', run: () => editor.value?.chain().focus().setHorizontalRule().run() },
  { title: '链接', label: '🔗', run: promptSetLink, active: () => !!editor.value?.isActive('link') },
  { title: '图片', label: '🖼', run: promptInsertImage },
  { title: '清除格式', label: '⌫', run: () => editor.value?.chain().focus().unsetAllMarks().clearNodes().run() },
  { title: '撤销', label: '↶', run: () => editor.value?.chain().focus().undo().run() },
  { title: '重做', label: '↷', run: () => editor.value?.chain().focus().redo().run() },
  { title: '清空内容', label: '清空', run: () => editor.value?.chain().focus().clearContent(true).run() },
]
</script>

<template>
  <div class="rich-text-editor">
    <div class="toolbar" role="toolbar" aria-label="富文本工具栏">
      <button
        v-for="tool in tools"
        :key="tool.title"
        type="button"
        class="tool"
        :class="{ active: tool.active?.() }"
        :title="tool.title"
        :aria-label="tool.title"
        @click="tool.run()"
      >
        {{ tool.label }}
      </button>
    </div>
    <EditorContent class="editor-content" :editor="editor" />
  </div>
</template>

<style scoped>
.rich-text-editor {
  border: 1px solid var(--admin-line);
  border-radius: 8px;
  overflow: hidden;
  background: var(--admin-bg);
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
  padding: 6px;
  border-bottom: 1px solid var(--admin-line);
  background: var(--admin-bg-subtle, rgba(11, 11, 12, 0.03));
}

.tool {
  min-width: 30px;
  height: 28px;
  padding: 0 8px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: var(--admin-ink);
  font-family: inherit;
  font-size: 13px;
  cursor: pointer;
}

.tool:hover {
  background: rgba(11, 11, 12, 0.06);
}

.tool.active {
  border-color: var(--admin-line);
  background: rgba(11, 11, 12, 0.1);
  font-weight: 600;
}

.tool:focus-visible {
  outline: 2px solid var(--admin-brand, #17d1a7);
  outline-offset: 1px;
}

.editor-content {
  max-height: 320px;
  overflow-y: auto;
}

/* 编辑区在 TipTap 内部渲染，作用域样式够不到，用 :deep 穿进去 */
.editor-content :deep(.editor-surface) {
  min-height: 160px;
  padding: 12px 14px;
  outline: none;
  font-size: 14px;
  line-height: 1.7;
}

.editor-content :deep(h2) {
  margin: 16px 0 8px;
  font-size: 17px;
  font-weight: 700;
}

.editor-content :deep(h3) {
  margin: 14px 0 8px;
  font-size: 15px;
  font-weight: 700;
}

.editor-content :deep(p) {
  margin: 8px 0;
}

.editor-content :deep(ul),
.editor-content :deep(ol) {
  margin: 8px 0;
  padding-left: 22px;
  list-style: revert;
}

.editor-content :deep(blockquote) {
  margin: 10px 0;
  padding-left: 12px;
  border-left: 3px solid var(--admin-line);
  color: var(--admin-ink-secondary);
}

.editor-content :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 8px;
}

.editor-content :deep(a) {
  color: var(--admin-brand, #17d1a7);
  text-decoration: underline;
}
</style>
