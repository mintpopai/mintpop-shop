import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ProductsView from './ProductsView.vue'
import {
  createAdminProduct,
  fetchAdminGroups,
  fetchAdminProducts,
  setAdminProductOnSale,
  updateAdminProduct,
  type AdminGroup,
  type AdminProduct,
} from '../api-admin'
import { toast } from '../toast'

vi.mock('../api-admin', () => ({
  fetchAdminGroups: vi.fn(),
  fetchAdminProducts: vi.fn(),
  createAdminProduct: vi.fn(),
  updateAdminProduct: vi.fn(),
  setAdminProductOnSale: vi.fn(),
}))

const fetchGroupsMock = vi.mocked(fetchAdminGroups)
const fetchProductsMock = vi.mocked(fetchAdminProducts)
const createMock = vi.mocked(createAdminProduct)
const updateMock = vi.mocked(updateAdminProduct)
const setOnSaleMock = vi.mocked(setAdminProductOnSale)

let wrapper: VueWrapper | null = null

function product(overrides: Partial<AdminProduct> = {}): AdminProduct {
  return {
    id: 1,
    groupId: 10,
    nameZh: 'Claude Pro 会员',
    nameEn: 'Claude Pro',
    descriptionZh: '官方渠道',
    descriptionEn: null,
    detailZh: null,
    detailEn: null,
    badgeZh: '热销',
    badgeEn: null,
    accent: 'MINT',
    priceCents: 1999,
    imageUrl: null,
    onSale: true,
    ...overrides,
  }
}

const GROUPS: AdminGroup[] = [
  { id: 10, nameZh: '订阅', nameEn: 'Subscription', sortOrder: 10, productCount: 2 },
  { id: 20, nameZh: '卡密', nameEn: 'Codes', sortOrder: 20, productCount: 1 },
]

/** 富文本编辑器有自己的用例，这里只关心「表单怎么用它」，用轻量替身顶掉 ProseMirror */
const RichTextEditorStub = {
  name: 'RichTextEditor',
  props: { id: { type: String, required: true }, modelValue: { type: String, default: '' } },
  emits: ['update:modelValue'],
  template: '<div class="rte-stub" :data-editor-id="id">{{ modelValue }}</div>',
}

async function render(products: AdminProduct[] = [product()], groups = GROUPS) {
  fetchGroupsMock.mockResolvedValue(groups)
  fetchProductsMock.mockResolvedValue(products)
  wrapper = mount(ProductsView, {
    attachTo: document.body,
    global: { stubs: { RichTextEditor: RichTextEditorStub } },
  })
  await flushPromises()
  return wrapper
}

/** 按 id 取某一语言的富文本编辑器 */
function editorOf(w: VueWrapper, id: string) {
  const found = w.findAllComponents(RichTextEditorStub).find((c) => c.props('id') === id)
  if (!found) {
    throw new Error(`没有 id 为 ${id} 的富文本编辑器`)
  }
  return found
}

/** 在某个富文本编辑器里「输入」内容 */
async function typeDetail(w: VueWrapper, id: string, html: string) {
  editorOf(w, id).vm.$emit('update:modelValue', html)
  await flushPromises()
}

/** 弹窗被 Teleport 到 body，统一从 document 取 */
function $(selector: string): HTMLElement | null {
  return document.querySelector(selector)
}

async function type(selector: string, value: string) {
  const input = $(selector) as HTMLInputElement
  input.value = value
  input.dispatchEvent(new Event('input', { bubbles: true }))
  await flushPromises()
}

/** 语言开关按钮在 Teleport 出去的弹窗里，按可见文字点它 */
async function switchLang(label: string) {
  const tab = Array.from(document.querySelectorAll<HTMLElement>('.lang-tab')).find(
    (b) => b.textContent?.trim() === label,
  )
  if (!tab) {
    throw new Error(`没有「${label}」语言页签`)
  }
  tab.click()
  await flushPromises()
}

/** 某个字段所在的语言面板（v-show 把 display 写在面板上，不写在字段上） */
function panelOf(selector: string): HTMLElement {
  const panel = $(selector)?.closest('.lang-panel')
  if (!panel) {
    throw new Error(`${selector} 不在任何语言面板里`)
  }
  return panel as HTMLElement
}

/** 弹窗页脚最后一个按钮是「保存」 */
async function save() {
  const buttons = Array.from(document.querySelectorAll<HTMLElement>('.foot button'))
  buttons[buttons.length - 1].click()
  await flushPromises()
}

async function openCreate() {
  await wrapper!.find('.admin-toolbar .admin-btn').trigger('click')
  await flushPromises()
}

beforeEach(() => {
  toast.value = null
})

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  document.body.innerHTML = ''
})

describe('列表加载', () => {
  it('并行拉取分组与商品，渲染商品行', async () => {
    const w = await render()

    expect(fetchGroupsMock).toHaveBeenCalledOnce()
    expect(fetchProductsMock).toHaveBeenCalledOnce()
    expect(w.find('tbody tr').text()).toContain('Claude Pro 会员')
  })

  it('分组列显示分组中文名而不是 id', async () => {
    const w = await render()

    expect(w.find('tbody tr').text()).toContain('订阅')
  })

  it('商品挂在已被删掉的分组上时退回显示分组 id，不渲染 undefined', async () => {
    const w = await render([product({ groupId: 999 })])

    expect(w.find('tbody tr').text()).toContain('999')
  })

  it('价格按美元展示', async () => {
    const w = await render([product({ priceCents: 12345 })])

    expect(w.find('tbody tr').text()).toContain('$123.45')
  })

  it('没有角标时显示占位符', async () => {
    const w = await render([product({ badgeZh: null })])

    expect(w.find('tbody tr').text()).toContain('—')
  })

  it('页头统计当前范围的上下架构成', async () => {
    const w = await render([product({ id: 1 }), product({ id: 2, onSale: false })])

    const facts = w.find('.page-facts').text()
    expect(facts).toContain('共 2 件')
    expect(facts).toContain('在售 1')
    expect(facts).toContain('已下架 1')
  })

  it('加载失败时显示错误信息', async () => {
    fetchGroupsMock.mockRejectedValue(new Error('查询商品失败'))
    fetchProductsMock.mockResolvedValue([])
    wrapper = mount(ProductsView)
    await flushPromises()

    expect(wrapper.find('.admin-hint.error').text()).toBe('查询商品失败')
  })

  it('一件商品都没有时提示去新增', async () => {
    const w = await render([])

    expect(w.find('.admin-hint').text()).toContain('还没有商品')
  })
})

describe('分组筛选', () => {
  it('默认「全部分组」，展示所有商品', async () => {
    const w = await render([product({ id: 1, groupId: 10 }), product({ id: 2, groupId: 20 })])

    expect(w.findAll('tbody tr')).toHaveLength(2)
  })

  it('选中某个分组后只留该组商品——纯前端过滤，不再请求后端', async () => {
    const w = await render([product({ id: 1, groupId: 10 }), product({ id: 2, groupId: 20 })])
    const before = fetchProductsMock.mock.calls.length

    await w.findComponent({ name: 'Select' }).vm.$emit('update:modelValue', 20)
    await flushPromises()

    expect(w.findAll('tbody tr')).toHaveLength(1)
    expect(w.find('tbody tr').text()).toContain('卡密')
    expect(fetchProductsMock.mock.calls.length).toBe(before)
  })

  it('选中的分组下没有商品时给出针对性的空态文案', async () => {
    const w = await render([product({ groupId: 10 })])

    await w.findComponent({ name: 'Select' }).vm.$emit('update:modelValue', 20)
    await flushPromises()

    expect(w.find('.admin-hint').text()).toBe('这个分组下还没有商品。')
  })
})

describe('新增商品', () => {
  it('弹窗标题是「新增商品」，分组默认取当前筛选', async () => {
    const w = await render()
    await w.findComponent({ name: 'Select' }).vm.$emit('update:modelValue', 20)
    await flushPromises()

    await openCreate()

    expect($('.head-title')?.textContent).toBe('新增商品')
    await type('#p-name-zh', '新商品')
    await type('#p-price', '9.99')
    createMock.mockResolvedValue(product())
    await save()

    expect(createMock.mock.calls[0][0]).toMatchObject({ groupId: 20 })
  })

  it('没有筛选时分组默认取第一个分组，不留 0 这种无效值', async () => {
    const w = await render()

    await openCreate()
    await type('#p-name-zh', '新商品')
    await type('#p-price', '9.99')
    createMock.mockResolvedValue(product())
    await save()

    expect(createMock.mock.calls[0][0]).toMatchObject({ groupId: 10 })
    expect(w.exists()).toBe(true)
  })

  it('价格以美元填写、按美分提交', async () => {
    await render()
    await openCreate()
    await type('#p-name-zh', '新商品')
    await type('#p-price', '19.99')
    createMock.mockResolvedValue(product())

    await save()

    expect(createMock.mock.calls[0][0]).toMatchObject({ priceCents: 1999 })
  })

  it('美元金额的浮点误差被四舍五入掉，不出现 1998 这种脏数据', async () => {
    await render()
    await openCreate()
    await type('#p-name-zh', '新商品')
    await type('#p-price', '119.99')
    createMock.mockResolvedValue(product())

    await save()

    expect(createMock.mock.calls[0][0]).toMatchObject({ priceCents: 11999 })
  })

  it('中文名为空时拦下', async () => {
    await render()
    await openCreate()
    await type('#p-price', '9.99')

    await save()

    expect(toast.value).toEqual({ type: 'error', text: '请填写商品的中文名称' })
    expect(createMock).not.toHaveBeenCalled()
  })

  it('中文名只填空白同样按空处理', async () => {
    await render()
    await openCreate()
    await type('#p-name-zh', '   ')
    await type('#p-price', '9.99')

    await save()

    expect(createMock).not.toHaveBeenCalled()
  })

  it('价格为空或不足一分时拦下', async () => {
    await render()

    for (const price of ['', '0', '0.004', '-5']) {
      await openCreate()
      await type('#p-name-zh', '新商品')
      await type('#p-price', price)
      await save()
    }

    expect(createMock).not.toHaveBeenCalled()
  })

  it('保存成功后提示、关窗并重新拉取列表', async () => {
    await render()
    const before = fetchProductsMock.mock.calls.length
    await openCreate()
    await type('#p-name-zh', '新商品')
    await type('#p-price', '9.99')
    createMock.mockResolvedValue(product())

    await save()

    expect(toast.value).toEqual({ type: 'success', text: '已保存' })
    expect($('.dialog')).toBeNull()
    expect(fetchProductsMock.mock.calls.length).toBe(before + 1)
  })

  it('保存失败时提示后端原因，弹窗留着不丢已填内容', async () => {
    await render()
    await openCreate()
    await type('#p-name-zh', '新商品')
    await type('#p-price', '9.99')
    createMock.mockRejectedValue(new Error('同名商品已存在'))

    await save()

    expect(toast.value).toEqual({ type: 'error', text: '同名商品已存在' })
    expect($('.dialog')).not.toBeNull()
  })
})

describe('编辑商品', () => {
  async function openEdit() {
    await wrapper!.find('tbody tr .admin-link').trigger('click')
    await flushPromises()
  }

  it('弹窗标题改口，并把美分价格回填成美元', async () => {
    await render([product({ priceCents: 12345 })])

    await openEdit()

    expect($('.head-title')?.textContent).toBe('编辑商品')
    expect(($('#p-price') as HTMLInputElement).value).toBe('123.45')
  })

  it('可空字段回填成空串，不把 null 写进输入框', async () => {
    await render([product({ nameEn: null, descriptionZh: null, badgeZh: null, imageUrl: null })])

    await openEdit()

    expect(($('#p-name-en') as HTMLInputElement).value).toBe('')
    expect(($('#p-badge-zh') as HTMLInputElement).value).toBe('')
  })

  it('保存时走更新接口并带上商品 id', async () => {
    await render([product({ id: 42 })])
    updateMock.mockResolvedValue(product())

    await openEdit()
    await save()

    expect(updateMock.mock.calls[0][0]).toBe(42)
    expect(createMock).not.toHaveBeenCalled()
  })
})

describe('上下架', () => {
  /** 行内第二个链接按钮是上/下架 */
  async function toggle() {
    await wrapper!.findAll('tbody tr .admin-link')[1].trigger('click')
    await flushPromises()
  }

  it('在售商品的动作是「下架」，点了提交 onSale=false', async () => {
    const w = await render([product({ id: 7, onSale: true })])
    setOnSaleMock.mockResolvedValue(product({ id: 7, onSale: false }))

    expect(w.findAll('tbody tr .admin-link')[1].text()).toBe('下架')
    await toggle()

    expect(setOnSaleMock).toHaveBeenCalledWith(7, false)
  })

  it('已下架商品的动作是「上架」', async () => {
    const w = await render([product({ id: 7, onSale: false })])
    setOnSaleMock.mockResolvedValue(product({ id: 7, onSale: true }))

    expect(w.findAll('tbody tr .admin-link')[1].text()).toBe('上架')
    await toggle()

    expect(setOnSaleMock).toHaveBeenCalledWith(7, true)
  })

  it('只把返回的那一行换掉，不整表重拉', async () => {
    const w = await render([product({ id: 7, onSale: true })])
    const before = fetchProductsMock.mock.calls.length
    setOnSaleMock.mockResolvedValue(product({ id: 7, onSale: false }))

    await toggle()

    expect(w.find('tbody tr').text()).toContain('已下架')
    expect(fetchProductsMock.mock.calls.length).toBe(before)
    expect(toast.value).toEqual({ type: 'success', text: '已更新上架状态' })
  })

  it('切换失败时提示原因，行状态不动', async () => {
    const w = await render([product({ id: 7, onSale: true })])
    setOnSaleMock.mockRejectedValue(new Error('商品不存在'))

    await toggle()

    expect(toast.value).toEqual({ type: 'error', text: '商品不存在' })
    expect(w.find('tbody tr').text()).toContain('上架中')
  })
})

describe('商品详情富文本', () => {
  it('列表用一列标出哪些商品还没配详情', async () => {
    const w = await render([
      product({ id: 1, detailZh: '<p>有详情</p>' }),
      product({ id: 2, detailZh: null }),
    ])
    const rows = w.findAll('tbody tr')

    expect(rows[0].find('.col-detail').text()).toBe('✓')
    expect(rows[1].find('.col-detail').text()).toBe('—')
  })

  it('新增：中英详情随表单一起提交', async () => {
    const w = await render()
    await openCreate()
    await type('#p-name-zh', '新商品')
    await type('#p-price', '9.99')
    await typeDetail(w, 'p-detail-zh', '<h2>中文详情</h2>')
    await typeDetail(w, 'p-detail-en', '<h2>English detail</h2>')
    createMock.mockResolvedValue(product())

    await save()

    expect(createMock.mock.calls[0][0]).toMatchObject({
      detailZh: '<h2>中文详情</h2>',
      detailEn: '<h2>English detail</h2>',
    })
  })

  it('编辑：已有详情回填进两个编辑器', async () => {
    const w = await render([product({ detailZh: '<p>中文</p>', detailEn: '<p>EN</p>' })])

    await w.find('tbody tr .admin-link').trigger('click')
    await flushPromises()

    expect(editorOf(w, 'p-detail-zh').props('modelValue')).toBe('<p>中文</p>')
    expect(editorOf(w, 'p-detail-en').props('modelValue')).toBe('<p>EN</p>')
  })

  it('编辑：没配详情的商品回填空串，不把 null 塞进编辑器', async () => {
    const w = await render([product({ detailZh: null, detailEn: null })])

    await w.find('tbody tr .admin-link').trigger('click')
    await flushPromises()

    expect(editorOf(w, 'p-detail-zh').props('modelValue')).toBe('')
  })

  it('中英文案用一个语言开关整体切换，默认停在中文', async () => {
    const w = await render()
    await openCreate()

    expect(editorOf(w, 'p-detail-zh').isVisible()).toBe(true)
    expect(editorOf(w, 'p-detail-en').isVisible()).toBe(false)
  })

  it('切到英文后显示英文编辑器，中文的内容留在原地不丢', async () => {
    const w = await render()
    await openCreate()
    await typeDetail(w, 'p-detail-zh', '<p>中文草稿</p>')

    await switchLang('English')

    expect(editorOf(w, 'p-detail-en').isVisible()).toBe(true)
    expect(editorOf(w, 'p-detail-zh').isVisible()).toBe(false)
    expect(editorOf(w, 'p-detail-zh').props('modelValue')).toBe('<p>中文草稿</p>')
  })
})

describe('语言开关', () => {
  it('名称、描述、角标随语言一起切换，不再左右并排各占半栏', async () => {
    await render()
    await openCreate()

    expect(($('#p-name-zh') as HTMLElement).offsetParent).not.toBeNull()

    await switchLang('English')

    // v-show 切换：中文那套字段仍在 DOM 里（草稿不丢），只是不显示
    expect($('#p-name-zh')).not.toBeNull()
    expect(panelOf('#p-name-zh').style.display).toBe('none')
    expect(panelOf('#p-name-en').style.display).toBe('')
  })

  it('中文名为空时报错并把中文面板翻回来，别让人对着英文面板找那个框', async () => {
    await render()
    await openCreate()
    await type('#p-price', '9.99')
    await switchLang('English')

    await save()

    expect(toast.value).toEqual({ type: 'error', text: '请填写商品的中文名称' })
    expect(panelOf('#p-name-zh').style.display).toBe('')
  })
})
