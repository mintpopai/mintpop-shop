<script setup lang="ts">
// 商品详情的富文本编辑器：TipTap（ProseMirror 内核），对外就是一个 v-model 绑 HTML 字符串的输入框。
// 产出的 HTML 后端还会用白名单再净化一遍，这里不负责安全，只负责好用。
import { onBeforeUnmount, watch } from 'vue'
import { EditorContent, useEditor } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Image from '@tiptap/extension-image'

const props = defineProps<{
  id: string
  modelValue: string
  /** 撑满父容器高度：给弹窗里「编辑区占掉剩余空间」的排法用，不设时按内容高度自适应 */
  fill?: boolean
}>()
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
  /** 字形本身就说明格式的（B / I / H2）直接用字；其余一律用图标，不混 emoji */
  label?: string
  /** 16px 网格上的线性图标，只给 path 的 d，描边样式统一在 CSS 里定 */
  paths?: string[]
  run: () => void
  active?: () => boolean
  /** 破坏性操作：整篇清空，与格式类按钮区分开 */
  danger?: boolean
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

/** 按职责分组：行内格式 / 段落 / 块级 / 插入 / 历史 / 清空，组间画一条竖线 */
const toolGroups: Tool[][] = [
  [
    { title: '加粗', label: 'B', run: () => editor.value?.chain().focus().toggleBold().run(), active: () => !!editor.value?.isActive('bold') },
    { title: '斜体', label: 'I', run: () => editor.value?.chain().focus().toggleItalic().run(), active: () => !!editor.value?.isActive('italic') },
    { title: '下划线', label: 'U', run: () => editor.value?.chain().focus().toggleUnderline().run(), active: () => !!editor.value?.isActive('underline') },
    { title: '删除线', label: 'S', run: () => editor.value?.chain().focus().toggleStrike().run(), active: () => !!editor.value?.isActive('strike') },
  ],
  [
    { title: '二级标题', label: 'H2', run: () => editor.value?.chain().focus().toggleHeading({ level: 2 }).run(), active: () => !!editor.value?.isActive('heading', { level: 2 }) },
    { title: '三级标题', label: 'H3', run: () => editor.value?.chain().focus().toggleHeading({ level: 3 }).run(), active: () => !!editor.value?.isActive('heading', { level: 3 }) },
  ],
  [
    {
      title: '无序列表',
      paths: ['M8 6h13', 'M8 12h13', 'M8 18h13', 'M3.6 6h.01', 'M3.6 12h.01', 'M3.6 18h.01'],
      run: () => editor.value?.chain().focus().toggleBulletList().run(),
      active: () => !!editor.value?.isActive('bulletList'),
    },
    {
      title: '有序列表',
      paths: ['M10 6h11', 'M10 12h11', 'M10 18h11', 'M3.4 4.8h1.4V9', 'M3.2 9h2.6', 'M3.2 15a1.3 1.3 0 1 1 2.3 1L3.2 19.2h2.6'],
      run: () => editor.value?.chain().focus().toggleOrderedList().run(),
      active: () => !!editor.value?.isActive('orderedList'),
    },
    {
      title: '引用',
      paths: ['M10 7H6a2 2 0 0 0-2 2v2a2 2 0 0 0 2 2h2c0 2-1 3.2-2.8 3.8', 'M20 7h-4a2 2 0 0 0-2 2v2a2 2 0 0 0 2 2h2c0 2-1 3.2-2.8 3.8'],
      run: () => editor.value?.chain().focus().toggleBlockquote().run(),
      active: () => !!editor.value?.isActive('blockquote'),
    },
    { title: '分隔线', paths: ['M4 12h16'], run: () => editor.value?.chain().focus().setHorizontalRule().run() },
  ],
  [
    {
      title: '链接',
      paths: ['M10.5 13.5a4.5 4.5 0 0 0 6.6.4l2.4-2.4a4.5 4.5 0 0 0-6.4-6.4l-1.4 1.4', 'M13.5 10.5a4.5 4.5 0 0 0-6.6-.4l-2.4 2.4a4.5 4.5 0 0 0 6.4 6.4l1.4-1.4'],
      run: promptSetLink,
      active: () => !!editor.value?.isActive('link'),
    },
    {
      title: '图片',
      paths: ['M4 5.5h16v13H4z', 'M4 15l4.5-4.5L14 16', 'M15.5 9h.01'],
      run: promptInsertImage,
    },
  ],
  [
    {
      title: '清除格式',
      paths: ['M4 6h10', 'M9.5 6 7 18', 'M14 12.5l6 6', 'M20 12.5l-6 6'],
      run: () => editor.value?.chain().focus().unsetAllMarks().clearNodes().run(),
    },
    { title: '撤销', paths: ['M4.5 9.5h11a5 5 0 0 1 0 10h-6', 'M4.5 9.5 8.5 5.5', 'M4.5 9.5 8.5 13.5'], run: () => editor.value?.chain().focus().undo().run() },
    { title: '重做', paths: ['M19.5 9.5h-11a5 5 0 0 0 0 10h6', 'M19.5 9.5 15.5 5.5', 'M19.5 9.5 15.5 13.5'], run: () => editor.value?.chain().focus().redo().run() },
  ],
  [
    {
      title: '清空内容',
      paths: ['M4 7h16', 'M9.5 7V4.8h5V7', 'M6 7l1 12.2h10L18 7', 'M10 10.5v6', 'M14 10.5v6'],
      run: () => editor.value?.chain().focus().clearContent(true).run(),
      danger: true,
    },
  ],
]
</script>

<template>
  <div class="rich-text-editor" :class="{ fill }">
    <div class="toolbar" role="toolbar" aria-label="富文本工具栏">
      <template v-for="(group, groupIndex) in toolGroups" :key="groupIndex">
        <span v-if="groupIndex > 0" class="tool-sep" aria-hidden="true"></span>
        <button
          v-for="tool in group"
          :key="tool.title"
          type="button"
          class="tool"
          :class="{ active: tool.active?.(), danger: tool.danger }"
          :title="tool.title"
          :aria-label="tool.title"
          :aria-pressed="tool.active ? tool.active() : undefined"
          @click="tool.run()"
        >
          <svg v-if="tool.paths" class="tool-icon" viewBox="0 0 24 24" aria-hidden="true">
            <path v-for="(d, i) in tool.paths" :key="i" :d="d" />
          </svg>
          <span v-else class="tool-label">{{ tool.label }}</span>
        </button>
      </template>
    </div>
    <div class="editor-body">
      <EditorContent class="editor-content" :editor="editor" />
      <!-- 占位提示自己画：Placeholder 扩展在 TipTap 3 里已挪出 StarterKit，
           为一句灰字多装一个包不值当，空文档时叠一层不吃事件的提示即可 -->
      <p v-if="!modelValue" class="editor-empty" aria-hidden="true">
        在这里写商品详情：卖点、材质尺寸、发货说明……
      </p>
    </div>
  </div>
</template>

<style scoped>
/* ⚠️ 这里原先写的是 --admin-line / --admin-bg / --admin-ink，本项目的 token 里
   根本没有这几个名字（见 base.css / layout.css，前缀是 --color-* 与 --counter-*）。
   带未定义变量的声明在计算值阶段直接作废，border-style 退回 none——编辑区因此
   一圈边框都没有，看着像块飘着的白板。一律换成真实 token。 */
.rich-text-editor {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  overflow: hidden;
  background: var(--color-bg);
  transition: border-color 0.15s ease;
}

.rich-text-editor:hover {
  border-color: var(--color-ink-secondary);
}

/* 编辑区内部聚焦时，整个框跟着亮起来——焦点在 contenteditable 上，
   外面这圈边框不亮的话，「我正在这里打字」这件事没有任何可见依据 */
.rich-text-editor:focus-within {
  border-color: var(--counter-focus);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--counter-focus) 22%, transparent);
}

/* 撑满父容器：编辑区吃掉工具栏之外的全部高度，不再是固定 160～320 的一小格 */
.rich-text-editor.fill {
  flex: 1;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 2px;
  padding: 6px 8px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-bg-cloud);
}

/* 组间竖线：按钮分成「行内格式 / 段落 / 块级 / 插入 / 历史 / 清空」六组，
   靠一条 1px 竖线断句，比全靠间距更能让人一眼看出哪些是一类 */
.tool-sep {
  width: 1px;
  height: 16px;
  margin: 0 6px;
  background: var(--color-border);
}

.tool {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 28px;
  height: 28px;
  padding: 0 6px;
  border: 1px solid transparent;
  border-radius: var(--radius-button);
  background: transparent;
  color: var(--color-ink);
  font-family: inherit;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}

.tool:hover {
  background: color-mix(in srgb, var(--color-ink) 8%, transparent);
}

/* 当前格式生效中：深墨底 + 白字，与「鼠标划过」的浅灰拉开档次，
   不靠加粗这种细微差别去区分 */
.tool.active {
  background: var(--counter-deep);
  color: #ffffff;
}

.tool.danger {
  color: var(--counter-danger);
}

.tool.danger:hover {
  background: color-mix(in srgb, var(--counter-danger) 12%, transparent);
}

/* 图标统一 16px 线性描边，与文字型按钮（B / H2）视觉重量对齐；
   之前混在里面的 🔗 🖼 ⌫ 是彩色 emoji，字号、基线、颜色都跟旁边对不上 */
.tool-icon {
  width: 16px;
  height: 16px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.7;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.tool-label {
  font-weight: 600;
  letter-spacing: 0.01em;
}

/* B / I / U / S 用字形本身表达含义，故各自照着自己的样子画 */
.tool[title='加粗'] .tool-label {
  font-weight: 800;
}

.tool[title='斜体'] .tool-label {
  font-style: italic;
}

.tool[title='下划线'] .tool-label {
  text-decoration: underline;
  text-underline-offset: 2px;
}

.tool[title='删除线'] .tool-label {
  text-decoration: line-through;
}

.tool:focus-visible {
  outline: 2px solid var(--counter-focus);
  outline-offset: 1px;
}

.editor-body {
  position: relative;
  flex: 1;
  min-height: 0;
  display: flex;
}

.editor-content {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.editor-empty {
  position: absolute;
  top: 16px;
  left: 18px;
  right: 18px;
  font-size: 14px;
  line-height: 1.75;
  color: var(--color-ink-secondary);
  pointer-events: none;
}

/* 不 fill 时保留一个上限，免得长详情把普通表单撑到没边 */
.rich-text-editor:not(.fill) .editor-body {
  max-height: 320px;
}

/* 编辑区在 TipTap 内部渲染，作用域样式够不到，用 :deep 穿进去 */
.editor-content :deep(.editor-surface) {
  min-height: 160px;
  /* 正文限宽到约 70 字符：详情是要给顾客读的段落，铺满 600px 一行读起来很累 */
  max-width: 68ch;
  padding: 16px 18px 24px;
  outline: none;
  font-size: 14px;
  line-height: 1.75;
}

.rich-text-editor.fill .editor-content :deep(.editor-surface) {
  min-height: 100%;
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
  border-left: 3px solid var(--color-border);
  color: var(--color-ink-secondary);
}

.editor-content :deep(hr) {
  margin: 16px 0;
  border: none;
  border-top: 1px solid var(--color-border);
}

.editor-content :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: var(--radius-card);
}

.editor-content :deep(a) {
  color: var(--color-brand-deep);
  text-decoration: underline;
}
</style>
