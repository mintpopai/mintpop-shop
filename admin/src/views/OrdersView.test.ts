import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import OrdersView from './OrdersView.vue'
import { fetchAdminOrders, type AdminOrderItem, type PageResult } from '../api-admin'

vi.mock('../api-admin', () => ({
  fetchAdminOrders: vi.fn(),
  fetchAdminShipments: vi.fn().mockResolvedValue([]),
  shipAdminOrder: vi.fn(),
}))

const fetchOrdersMock = vi.mocked(fetchAdminOrders)

let wrapper: VueWrapper | null = null

function order(overrides: Partial<AdminOrderItem> = {}): AdminOrderItem {
  return {
    orderNo: 'MP20260801001',
    productName: 'Claude Pro 会员',
    buyerEmail: 'buyer@example.com',
    quantity: 1,
    amountCents: 1999,
    status: 'PAID',
    statusLabel: '已支付',
    paymentProvider: 'stripe',
    createdAt: '2026-08-01T13:45:00Z',
    paidAt: '2026-08-01T13:46:00Z',
    ...overrides,
  }
}

function page(records: AdminOrderItem[], total = records.length): PageResult<AdminOrderItem> {
  return { records, total, page: 1, size: 20 }
}

async function render(result: PageResult<AdminOrderItem> = page([order()])) {
  fetchOrdersMock.mockResolvedValue(result)
  wrapper = mount(OrdersView, { attachTo: document.body })
  await flushPromises()
  return wrapper
}

/** 取最近一次列表请求的参数 */
function lastQuery() {
  return fetchOrdersMock.mock.calls[fetchOrdersMock.mock.calls.length - 1][0]
}

/** 状态筛选条：第 0 个是「全部」，其余按 STATUS_FILTERS 顺序 */
function chips() {
  return wrapper!.findAll('.admin-chip')
}

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  document.body.innerHTML = ''
})

describe('列表加载', () => {
  it('首屏按第一页、每页 20 条、无筛选拉取', async () => {
    await render()

    expect(lastQuery()).toEqual({ page: 1, size: 20, status: undefined, keyword: undefined })
  })

  it('渲染订单的关键字段', async () => {
    const w = await render(page([order()]))
    const row = w.find('tbody tr').text()

    expect(row).toContain('MP20260801001')
    expect(row).toContain('Claude Pro 会员')
    expect(row).toContain('buyer@example.com')
    expect(row).toContain('$19.99')
    expect(row).toContain('已支付')
    expect(row).toContain('2026/08/01 13:45')
  })

  it('游客下单没有买家邮箱时显示「游客」', async () => {
    const w = await render(page([order({ buyerEmail: null })]))

    expect(w.find('tbody tr').text()).toContain('游客')
  })

  it('未支付订单的支付方式与支付时间显示占位符，不渲染 null', async () => {
    const w = await render(page([order({ status: 'PENDING', paymentProvider: null, paidAt: null })]))

    expect(w.find('tbody tr').text()).toContain('—')
  })

  it('加载失败时显示错误信息，不渲染表格', async () => {
    fetchOrdersMock.mockRejectedValue(new Error('查询订单失败'))
    wrapper = mount(OrdersView)
    await flushPromises()

    expect(wrapper.find('.admin-hint.error').text()).toBe('查询订单失败')
    expect(wrapper.find('.admin-table').exists()).toBe(false)
  })

  it('无筛选且结果为空时提示「还没有订单」', async () => {
    const w = await render(page([]))

    expect(w.find('.admin-hint').text()).toContain('还没有订单')
  })

  it('有筛选但结果为空时提示换个条件试试', async () => {
    const w = await render(page([]))

    await chips()[1].trigger('click')
    await flushPromises()

    expect(w.find('.admin-hint').text()).toContain('换个状态或搜索词试试')
  })
})

describe('状态筛选', () => {
  it('默认选中「全部」', async () => {
    const w = await render()

    expect(chips()[0].classes()).toContain('active')
    expect(w.find('.page-facts').text()).toContain('全部')
  })

  it('点某个状态后按该状态服务端筛选', async () => {
    await render()

    await chips()[1].trigger('click')
    await flushPromises()

    expect(lastQuery().status).toBe('PENDING')
  })

  it('切回「全部」时不再传状态参数', async () => {
    await render()
    await chips()[1].trigger('click')
    await flushPromises()

    await chips()[0].trigger('click')
    await flushPromises()

    expect(lastQuery().status).toBeUndefined()
  })

  it('页头的事实行跟着筛选口径变', async () => {
    const w = await render(page([order()], 7))

    await chips()[2].trigger('click')
    await flushPromises()

    expect(w.find('.page-facts').text()).toContain('已支付共')
  })
})

describe('搜索', () => {
  it('输入框里打字不会立刻查，点搜索才生效', async () => {
    const w = await render()
    const before = fetchOrdersMock.mock.calls.length

    await w.find('.search input').setValue('MP2026')

    expect(fetchOrdersMock.mock.calls.length).toBe(before)
  })

  it('提交搜索表单后按关键词查询', async () => {
    const w = await render()

    await w.find('.search input').setValue('MP2026')
    await w.find('.search').trigger('submit')
    await flushPromises()

    expect(lastQuery().keyword).toBe('MP2026')
  })

  it('关键词首尾空白被裁掉', async () => {
    const w = await render()

    await w.find('.search input').setValue('  MP2026  ')
    await w.find('.search').trigger('submit')
    await flushPromises()

    expect(lastQuery().keyword).toBe('MP2026')
  })

  it('清空搜索词后不再传关键词', async () => {
    const w = await render()
    await w.find('.search input').setValue('MP2026')
    await w.find('.search').trigger('submit')
    await flushPromises()

    await w.find('.search input').setValue('')
    await w.find('.search').trigger('submit')
    await flushPromises()

    expect(lastQuery().keyword).toBeUndefined()
  })

  it('搜索命中时页头把搜索词说清楚', async () => {
    const w = await render()

    await w.find('.search input').setValue('MP2026')
    await w.find('.search').trigger('submit')
    await flushPromises()

    expect(w.find('.page-facts').text()).toContain('订单号含「MP2026」')
  })
})

describe('分页', () => {
  /** 51 条数据 → 3 页 */
  async function renderPaged() {
    return render(page([order()], 51))
  }

  function pager() {
    return wrapper!.findAll('.admin-pager button')
  }

  it('按总数与页大小算出总页数', async () => {
    const w = await renderPaged()

    expect(w.find('.admin-pager .info').text()).toContain('/ 3 页')
  })

  it('第一页时「上一页」禁用', async () => {
    await renderPaged()

    expect(pager()[0].attributes('disabled')).toBeDefined()
  })

  it('翻到下一页后重新拉取对应页', async () => {
    await renderPaged()

    await pager()[1].trigger('click')
    await flushPromises()

    expect(lastQuery().page).toBe(2)
  })

  it('翻到最后一页时「下一页」禁用', async () => {
    await renderPaged()

    await pager()[1].trigger('click')
    await flushPromises()
    await pager()[1].trigger('click')
    await flushPromises()

    expect(pager()[1].attributes('disabled')).toBeDefined()
  })

  it('切换筛选后回到第一页，避免停在越界的页码上', async () => {
    await renderPaged()
    await pager()[1].trigger('click')
    await flushPromises()

    await chips()[1].trigger('click')
    await flushPromises()

    expect(lastQuery().page).toBe(1)
  })

  it('结果为 0 条时总页数仍按 1 页算，不显示「/ 0 页」', async () => {
    const w = await render(page([], 0))

    expect(w.find('.admin-pager .info').text()).toContain('/ 1 页')
  })
})

describe('发货入口', () => {
  it('已支付订单可以发货', async () => {
    const w = await render(page([order({ status: 'PAID' })]))

    expect(w.find('tbody tr td:last-child button').text()).toBe('发货')
  })

  it('已完成订单是重新发货', async () => {
    const w = await render(page([order({ status: 'COMPLETED', statusLabel: '已完成' })]))

    expect(w.find('tbody tr td:last-child button').text()).toBe('重新发货')
  })

  it('其余状态没有发货动作', async () => {
    for (const status of ['PENDING', 'FAILED', 'CANCELLED', 'EXPIRED']) {
      const w = await render(page([order({ status })]))
      expect(w.find('tbody tr td:last-child button').exists()).toBe(false)
      w.unmount()
    }
  })

  it('点发货打开发货弹窗', async () => {
    const w = await render(page([order({ status: 'PAID' })]))

    await w.find('tbody tr td:last-child button').trigger('click')
    await flushPromises()

    expect(document.querySelector('.dialog')).not.toBeNull()
  })
})
