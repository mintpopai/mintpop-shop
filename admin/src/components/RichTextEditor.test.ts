// ProseMirror 依赖 Range/Selection 等 DOM 细节，happy-dom 支撑不住，这个组件的用例跑 jsdom
// @vitest-environment jsdom
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import RichTextEditor from './RichTextEditor.vue'

let wrapper: VueWrapper | null = null

/** useEditor 在挂载后才建好实例，等一拍再断言 */
async function render(modelValue = '') {
  wrapper = mount(RichTextEditor, {
    props: { id: 'p-detail-zh', modelValue },
    attachTo: document.body,
  })
  await flushPromises()
  return wrapper
}

/** 按工具栏按钮的可访问名字点它（title 是给鼠标看的，也是这里的定位依据） */
async function clickTool(w: VueWrapper, title: string) {
  const button = w.findAll('.tool').find((b) => b.attributes('title') === title)
  if (!button) {
    throw new Error(`工具栏没有「${title}」按钮`)
  }
  await button.trigger('click')
  await flushPromises()
}

/** 选中全部内容：行内格式类命令需要有选区才会改动文档 */
async function selectAll(w: VueWrapper) {
  ;(w.vm as unknown as { editor: { commands: { selectAll: () => void } } }).editor.commands.selectAll()
  await flushPromises()
}

/** 最近一次 emit 出来的 HTML */
function lastEmitted(w: VueWrapper): string {
  const events = w.emitted('update:modelValue') ?? []
  return events[events.length - 1]?.[0] as string
}

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  document.body.innerHTML = ''
})

describe('初始内容', () => {
  it('把传入的 HTML 渲染成可编辑内容', async () => {
    const w = await render('<h2>标题</h2><p>正文</p>')

    const content = w.find('.editor-content').html()
    expect(content).toContain('<h2>标题</h2>')
    expect(content).toContain('<p>正文</p>')
  })

  it('内容区可编辑，运营点进去就能打字', async () => {
    const w = await render('<p>正文</p>')

    expect(w.find('[contenteditable="true"]').exists()).toBe(true)
  })
})

describe('工具栏命令', () => {
  it('二级标题：把当前段落变成 h2 并抛出新 HTML', async () => {
    const w = await render('<p>标题候选</p>')

    await clickTool(w, '二级标题')

    expect(lastEmitted(w)).toContain('<h2>标题候选</h2>')
  })

  it('无序列表：把当前段落变成列表项', async () => {
    const w = await render('<p>一条</p>')

    await clickTool(w, '无序列表')

    expect(lastEmitted(w)).toContain('<li><p>一条</p></li>')
  })

  it('加粗：给选中的文字套上 strong', async () => {
    const w = await render('<p>要加粗的</p>')

    await selectAll(w)
    await clickTool(w, '加粗')

    expect(lastEmitted(w)).toContain('<strong>要加粗的</strong>')
  })

  it('清除格式：把加粗等行内样式去掉，只留文字', async () => {
    const w = await render('<p><strong>加粗过的</strong></p>')

    await selectAll(w)
    await clickTool(w, '清除格式')

    expect(lastEmitted(w)).not.toContain('<strong>')
    expect(lastEmitted(w)).toContain('加粗过的')
  })
})

describe('与外部 v-model 同步', () => {
  it('外部换了内容（如切到另一个商品）时编辑器跟着换', async () => {
    const w = await render('<p>旧的</p>')

    await w.setProps({ modelValue: '<p>新的</p>' })
    await flushPromises()

    expect(w.find('.editor-content').html()).toContain('<p>新的</p>')
  })

  it('外部传回的正是自己刚抛出的内容时不重建文档，避免光标被打回开头', async () => {
    const w = await render('<p>正文</p>')

    await clickTool(w, '二级标题')
    const emitted = lastEmitted(w)
    await w.setProps({ modelValue: emitted })
    await flushPromises()

    // 回填同样的内容不应再触发一次 update（重建文档才会）
    expect(w.emitted('update:modelValue')).toHaveLength(1)
  })
})

describe('空内容', () => {
  it('内容被清空时抛出空串而不是编辑器的空段落壳', async () => {
    const w = await render('<p>要删掉的</p>')

    await clickTool(w, '清空内容')

    expect(lastEmitted(w)).toBe('')
  })
})
