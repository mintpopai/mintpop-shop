import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import GroupsView from './GroupsView.vue'
import {
  createAdminGroup,
  deleteAdminGroup,
  fetchAdminGroups,
  updateAdminGroup,
  type AdminGroup,
} from '../api-admin'
import { toast } from '../toast'

vi.mock('../api-admin', () => ({
  fetchAdminGroups: vi.fn(),
  createAdminGroup: vi.fn(),
  updateAdminGroup: vi.fn(),
  deleteAdminGroup: vi.fn(),
}))

const fetchGroupsMock = vi.mocked(fetchAdminGroups)
const createMock = vi.mocked(createAdminGroup)
const updateMock = vi.mocked(updateAdminGroup)
const deleteMock = vi.mocked(deleteAdminGroup)

let wrapper: VueWrapper | null = null

function group(overrides: Partial<AdminGroup> = {}): AdminGroup {
  return { id: 10, nameZh: '订阅', nameEn: 'Subscription', sortOrder: 10, productCount: 0, ...overrides }
}

async function render(groups: AdminGroup[] = [group()]) {
  fetchGroupsMock.mockResolvedValue(groups)
  wrapper = mount(GroupsView, { attachTo: document.body })
  await flushPromises()
  return wrapper
}

function $(selector: string): HTMLElement | null {
  return document.querySelector(selector)
}

async function type(selector: string, value: string) {
  const input = $(selector) as HTMLInputElement
  input.value = value
  input.dispatchEvent(new Event('input', { bubbles: true }))
  await flushPromises()
}

/** 弹窗页脚最后一个按钮是主操作（保存 / 删除分组） */
async function confirmFooter() {
  const buttons = Array.from(document.querySelectorAll<HTMLElement>('.foot button'))
  buttons[buttons.length - 1].click()
  await flushPromises()
}

async function openCreate() {
  await wrapper!.find('.admin-toolbar .admin-btn').trigger('click')
  await flushPromises()
}

/** 行内按钮：0=编辑，1=删除 */
function rowActions(rowIndex = 0) {
  return wrapper!.findAll('tbody tr')[rowIndex].findAll('.admin-link')
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
  it('渲染分组的 id、双语名、排序号与商品数', async () => {
    const w = await render([group({ id: 10, sortOrder: 30, productCount: 5 })])
    const row = w.find('tbody tr').text()

    expect(row).toContain('10')
    expect(row).toContain('订阅')
    expect(row).toContain('Subscription')
    expect(row).toContain('30')
    expect(row).toContain('5')
  })

  it('没有英文名时显示占位符，不渲染 null', async () => {
    const w = await render([group({ nameEn: null })])

    expect(w.find('tbody tr').text()).toContain('—')
  })

  it('页头汇总分组数与所覆盖的商品总数', async () => {
    const w = await render([group({ id: 1, productCount: 2 }), group({ id: 2, productCount: 3 })])

    const facts = w.find('.page-facts').text()
    expect(facts).toContain('共 2 组')
    expect(facts).toContain('装着 5 件商品')
  })

  it('加载失败时显示错误信息', async () => {
    fetchGroupsMock.mockRejectedValue(new Error('查询分组失败'))
    wrapper = mount(GroupsView)
    await flushPromises()

    expect(wrapper.find('.admin-hint.error').text()).toBe('查询分组失败')
  })

  it('一个分组都没有时说清「商品必须归到分组下」', async () => {
    const w = await render([])

    expect(w.find('.admin-hint').text()).toContain('商品必须归到某个分组下')
  })
})

describe('新增分组', () => {
  it('新分组的排序号默认排到最后（当前最大值 +10）', async () => {
    await render([group({ id: 1, sortOrder: 10 }), group({ id: 2, sortOrder: 40 })])

    await openCreate()

    expect($('.head-title')?.textContent).toBe('新增分组')
    expect(($('#group-sort') as HTMLInputElement).value).toBe('50')
  })

  it('一个分组都没有时排序号从 10 起', async () => {
    await render([])

    await openCreate()

    expect(($('#group-sort') as HTMLInputElement).value).toBe('10')
  })

  it('中文名为空时拦下，不发请求', async () => {
    await render()

    await openCreate()
    await confirmFooter()

    expect(toast.value).toEqual({ type: 'error', text: '请完整填写必填项' })
    expect(createMock).not.toHaveBeenCalled()
  })

  it('中文名只填空白同样按空处理', async () => {
    await render()

    await openCreate()
    await type('#group-name-zh', '   ')
    await confirmFooter()

    expect(createMock).not.toHaveBeenCalled()
  })

  it('保存成功后提示、关窗并重新拉取列表', async () => {
    await render()
    const before = fetchGroupsMock.mock.calls.length
    createMock.mockResolvedValue(group())

    await openCreate()
    await type('#group-name-zh', '卡密')
    await confirmFooter()

    expect(createMock.mock.calls[0][0]).toMatchObject({ nameZh: '卡密' })
    expect(toast.value).toEqual({ type: 'success', text: '已保存' })
    expect($('.dialog')).toBeNull()
    expect(fetchGroupsMock.mock.calls.length).toBe(before + 1)
  })

  it('保存失败时提示后端原因，弹窗留着', async () => {
    await render()
    createMock.mockRejectedValue(new Error('同名分组已存在'))

    await openCreate()
    await type('#group-name-zh', '订阅')
    await confirmFooter()

    expect(toast.value).toEqual({ type: 'error', text: '同名分组已存在' })
    expect($('.dialog')).not.toBeNull()
  })
})

describe('编辑分组', () => {
  it('弹窗标题改口，并回填现有值', async () => {
    await render([group({ nameZh: '订阅', nameEn: 'Subscription', sortOrder: 30 })])

    await rowActions()[0].trigger('click')
    await flushPromises()

    expect($('.head-title')?.textContent).toBe('编辑分组')
    expect(($('#group-name-zh') as HTMLInputElement).value).toBe('订阅')
    expect(($('#group-sort') as HTMLInputElement).value).toBe('30')
  })

  it('英文名为空时回填成空串，不写进 null', async () => {
    await render([group({ nameEn: null })])

    await rowActions()[0].trigger('click')
    await flushPromises()

    expect(($('#group-name-en') as HTMLInputElement).value).toBe('')
  })

  it('保存时走更新接口并带上分组 id', async () => {
    await render([group({ id: 42 })])
    updateMock.mockResolvedValue(group())

    await rowActions()[0].trigger('click')
    await flushPromises()
    await confirmFooter()

    expect(updateMock.mock.calls[0][0]).toBe(42)
    expect(createMock).not.toHaveBeenCalled()
  })
})

describe('删除分组', () => {
  it('组内还有商品时删除按钮禁用，并说明原因', async () => {
    await render([group({ productCount: 3 })])
    const del = rowActions()[1]

    expect(del.attributes('disabled')).toBeDefined()
    expect(del.attributes('title')).toBe('组内有商品，不可删除')
  })

  it('空组的删除按钮可用', async () => {
    await render([group({ productCount: 0 })])

    expect(rowActions()[1].attributes('disabled')).toBeUndefined()
  })

  it('点删除先开确认弹窗，说清删的是哪一个，而不是直接删', async () => {
    await render([group({ nameZh: '订阅' })])

    await rowActions()[1].trigger('click')
    await flushPromises()

    expect($('.head-title')?.textContent).toBe('删除分组')
    expect($('.confirm-text')?.textContent).toContain('订阅')
    expect(deleteMock).not.toHaveBeenCalled()
  })

  it('在确认弹窗里点取消不删除', async () => {
    await render()

    await rowActions()[1].trigger('click')
    await flushPromises()
    ;($('.foot .admin-btn-ghost') as HTMLElement).click()
    await flushPromises()

    expect(deleteMock).not.toHaveBeenCalled()
    expect($('.dialog')).toBeNull()
  })

  it('确认后删除、提示并重新拉取列表', async () => {
    await render([group({ id: 42 })])
    const before = fetchGroupsMock.mock.calls.length
    deleteMock.mockResolvedValue(null)

    await rowActions()[1].trigger('click')
    await flushPromises()
    await confirmFooter()

    expect(deleteMock).toHaveBeenCalledWith(42)
    expect(toast.value).toEqual({ type: 'success', text: '已删除' })
    expect(fetchGroupsMock.mock.calls.length).toBe(before + 1)
  })

  it('删除失败时提示后端原因，确认弹窗留着', async () => {
    await render()
    deleteMock.mockRejectedValue(new Error('分组下还有商品，不能删除'))

    await rowActions()[1].trigger('click')
    await flushPromises()
    await confirmFooter()

    expect(toast.value).toEqual({ type: 'error', text: '分组下还有商品，不能删除' })
    expect($('.dialog')).not.toBeNull()
  })
})
