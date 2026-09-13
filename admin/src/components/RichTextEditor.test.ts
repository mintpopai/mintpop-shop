// ProseMirror 依赖 Range/Selection 等 DOM 细节，happy-dom 支撑不住，这个组件的用例跑 jsdom
// @vitest-environment jsdom
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import RichTextEditor from './RichTextEditor.vue'

// 弹窗里的上传按钮会导入上传 API；这里只关心「上传成功后编辑器怎么用 URL」，把 API 顶掉
vi.mock('../api-admin', () => ({ uploadAdminImage: vi.fn() }))

// jsdom 没有布局引擎，Range 上连 getClientRects / getBoundingClientRect 都没有；而 TipTap 的 focus()
// 会在下一帧（requestAnimationFrame）异步调 scrollIntoView，ProseMirror 据此算选区坐标，撞到 Range 就抛
// 「target.getClientRects is not a function」。它在用例结束后才炸，本机快、编辑器已销毁所以侥幸躲过，
// CI 慢就成了 Vitest 的未处理异常。补两个空实现让这一帧安静过去；坐标本身在测试里没有意义。
if (typeof Range.prototype.getClientRects !== 'function') {
  Range.prototype.getClientRects = () => [] as unknown as DOMRectList
  Range.prototype.getBoundingClientRect = () => new DOMRect(0, 0, 0, 0)
}

/** 等 TipTap focus() 排在下一帧的 scrollIntoView 跑完：把「用例结束后才炸」变成「在用例里就能看见」 */
async function settleFocus() {
  await new Promise((resolve) => requestAnimationFrame(() => resolve(null)))
  await flushPromises()
}

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
  ;(
    w.vm as unknown as { editor: { commands: { selectAll: () => void } } }
  ).editor.commands.selectAll()
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

describe('链接与图片：问地址走自绘弹窗，不用 window.prompt', () => {
  /** 弹窗 Teleport 到 body，不在 wrapper 根下 */
  function dialog(): HTMLElement | null {
    return document.querySelector('.dialog')
  }

  function urlInput(): HTMLInputElement {
    return document.querySelector('#rte-url') as HTMLInputElement
  }

  async function typeUrl(value: string) {
    const input = urlInput()
    input.value = value
    input.dispatchEvent(new Event('input', { bubbles: true }))
    await flushPromises()
  }

  async function clickPrimary() {
    ;(dialog()?.querySelector('.admin-btn') as HTMLElement).click()
    await flushPromises()
  }

  it('点「链接」打开弹窗而不是 window.prompt，焦点直接落在输入框', async () => {
    const w = await render('<p>看这里</p>')
    const promptSpy = vi.fn()
    vi.stubGlobal('prompt', promptSpy)
    await selectAll(w)

    await clickTool(w, '链接')

    expect(promptSpy).not.toHaveBeenCalled()
    expect(dialog()?.getAttribute('aria-label')).toBe('设置链接')
    expect(document.activeElement).toBe(urlInput())
    vi.unstubAllGlobals()
  })

  it('填地址后确认：选中文字套上 <a>，弹窗关闭', async () => {
    const w = await render('<p>看这里</p>')
    await selectAll(w)
    await clickTool(w, '链接')

    await typeUrl(' https://mintpop.ai ')
    await clickPrimary()

    expect(lastEmitted(w)).toContain('<a')
    expect(lastEmitted(w)).toContain('href="https://mintpop.ai"')
    expect(dialog()).toBeNull()
  })

  it('输入框里按回车等同于确认', async () => {
    const w = await render('<p>看这里</p>')
    await selectAll(w)
    await clickTool(w, '链接')

    await typeUrl('https://mintpop.ai')
    urlInput().dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
    await flushPromises()

    expect(lastEmitted(w)).toContain('href="https://mintpop.ai"')
    expect(dialog()).toBeNull()
  })

  it('已有链接时弹窗回填原地址，留空确认即移除链接', async () => {
    const w = await render('<p><a href="https://old.example">看这里</a></p>')
    await selectAll(w)
    await clickTool(w, '链接')

    expect(urlInput().value).toBe('https://old.example')
    expect(dialog()?.querySelector('.admin-btn')?.textContent?.trim()).toBe('设置链接')

    await typeUrl('')
    expect(dialog()?.querySelector('.admin-btn')?.textContent?.trim()).toBe('移除链接')
    await clickPrimary()

    expect(lastEmitted(w)).not.toContain('<a')
    expect(lastEmitted(w)).toContain('看这里')
  })

  it('本来没有链接时留空的主按钮禁用：没东西可移除', async () => {
    const w = await render('<p>看这里</p>')
    await selectAll(w)
    await clickTool(w, '链接')

    expect((dialog()?.querySelector('.admin-btn') as HTMLButtonElement).disabled).toBe(true)
  })

  it('取消关掉弹窗，文档不动', async () => {
    const w = await render('<p>看这里</p>')
    await selectAll(w)
    await clickTool(w, '链接')

    await typeUrl('https://mintpop.ai')
    ;(dialog()?.querySelector('.admin-btn-ghost') as HTMLElement).click()
    await flushPromises()

    expect(dialog()).toBeNull()
    expect(w.emitted('update:modelValue') ?? []).toHaveLength(0)
  })

  it('插入图片：地址为空时主按钮禁用，填了地址确认后文档里出现 <img>', async () => {
    const w = await render('<p>看这里</p>')
    await clickTool(w, '图片')

    expect(dialog()?.getAttribute('aria-label')).toBe('插入图片')
    expect((dialog()?.querySelector('.admin-btn') as HTMLButtonElement).disabled).toBe(true)

    await typeUrl('https://cdn.example/a.png')
    await clickPrimary()
    await settleFocus()

    expect(lastEmitted(w)).toContain('<img')
    expect(lastEmitted(w)).toContain('src="https://cdn.example/a.png"')
    expect(dialog()).toBeNull()
  })

  it('插入图片弹窗里有本地上传；上传成功后直接插入 <img> 并关窗', async () => {
    const w = await render('<p>看这里</p>')
    await clickTool(w, '图片')

    const uploader = w.findComponent({ name: 'ImageUploadButton' })
    expect(uploader.exists()).toBe(true)
    uploader.vm.$emit('uploaded', 'https://shop-assets.mintpop.ai/products/2026/09/a.png')
    await settleFocus()

    expect(lastEmitted(w)).toContain('src="https://shop-assets.mintpop.ai/products/2026/09/a.png"')
    expect(dialog()).toBeNull()
  })

  it('设置链接的弹窗里没有上传按钮', async () => {
    const w = await render('<p>看这里</p>')
    await selectAll(w)
    await clickTool(w, '链接')

    expect(w.findComponent({ name: 'ImageUploadButton' }).exists()).toBe(false)
  })
})
