import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import OrderDetailView from './OrderDetailView.vue'
import { fetchOrderDetail, UnauthorizedError, type OrderDetail } from '../api'
import { gotoLogin } from '../auth'
import { formatDateTime } from '../datetime'
import { i18n, t } from '../i18n'

vi.mock('../api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api')>()
  return { ...actual, fetchOrderDetail: vi.fn() }
})

vi.mock('../auth', () => ({ gotoLogin: vi.fn() }))

const fetchOrderDetailMock = vi.mocked(fetchOrderDetail)
const gotoLoginMock = vi.mocked(gotoLogin)

const blank = { template: '<div />' }
let router: Router
let wrapper: VueWrapper | null = null

function detail(overrides: Partial<OrderDetail> = {}): OrderDetail {
  return {
    orderNo: 'MP20260801001',
    productName: 'Claude Pro 会员',
    quantity: 2,
    amountCents: 3998,
    status: 'PAID',
    statusLabel: '已支付',
    createdAt: '2026-08-01T00:00:00Z',
    paidAt: '2026-08-01T00:05:00Z',
    latestShipment: null,
    ...overrides,
  }
}

async function mountDetail(orderNo = 'MP20260801001') {
  await router.push(`/orders/${orderNo}`)
  await router.isReady()
  wrapper = mount(OrderDetailView, { global: { plugins: [i18n, router] } })
  await flushPromises()
  return wrapper
}

/** 按 <dt> 文案取对应 <dd> 内容 */
function fact(w: VueWrapper, label: string): string | undefined {
  return w
    .findAll('.fact-row')
    .find((row) => row.find('dt').text() === label)
    ?.find('dd')
    .text()
}

beforeEach(() => {
  router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/orders', component: blank },
      { path: '/orders/:orderNo', component: blank },
    ],
  })
})

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  vi.restoreAllMocks()
})

describe('订单详情', () => {
  it('按路由里的订单号拉详情，并渲染商品、状态、数量、金额与时间', async () => {
    fetchOrderDetailMock.mockResolvedValue(detail())
    const w = await mountDetail('MP20260801001')

    expect(fetchOrderDetailMock).toHaveBeenCalledWith('MP20260801001')
    expect(w.find('.product').text()).toBe('Claude Pro 会员')
    expect(w.find('.status-tag').text()).toBe('已支付')
    expect(w.find('.status-tag').classes()).toContain('status-tag--PAID')
    expect(fact(w, t('orders.orderNoLabel'))).toBe('MP20260801001')
    expect(fact(w, t('orders.product'))).toBe('Claude Pro 会员 × 2')
    expect(fact(w, t('orders.amount'))).toBe('$39.98')
    expect(fact(w, t('orders.createdAt'))).toBe(formatDateTime('2026-08-01T00:00:00Z'))
    expect(fact(w, t('orders.paidAt'))).toBe(formatDateTime('2026-08-01T00:05:00Z'))
  })

  it('未支付订单不显示支付时间行', async () => {
    fetchOrderDetailMock.mockResolvedValue(detail({ status: 'PENDING', paidAt: null }))
    const w = await mountDetail()
    expect(fact(w, t('orders.paidAt'))).toBeUndefined()
  })

  it('已发货：原样展示发货内容（保留换行）与发货时间', async () => {
    fetchOrderDetailMock.mockResolvedValue(
      detail({
        latestShipment: { content: '账号：a@b.c\n密码：secret', shippedAt: '2026-08-02T00:00:00Z' },
      }),
    )
    const w = await mountDetail()
    expect(w.find('.shipment-content').text()).toBe('账号：a@b.c\n密码：secret')
    expect(w.find('.shipped-at').text()).toBe(
      t('orders.shippedAtLine', { time: formatDateTime('2026-08-02T00:00:00Z') }),
    )
  })

  it('未发货：显示待发货提示，不渲染发货内容区', async () => {
    fetchOrderDetailMock.mockResolvedValue(detail({ latestShipment: null }))
    const w = await mountDetail()
    expect(w.find('.shipment-content').exists()).toBe(false)
    expect(w.text()).toContain(t('orders.notShipped'))
  })

  it('返回链接指向订单列表', async () => {
    fetchOrderDetailMock.mockResolvedValue(detail())
    const w = await mountDetail()
    expect(w.find('.back').attributes('href')).toBe('/orders')
  })

  it('401 直接跳登录，不显示错误', async () => {
    fetchOrderDetailMock.mockRejectedValue(new UnauthorizedError())
    const w = await mountDetail()
    expect(gotoLoginMock).toHaveBeenCalledOnce()
    expect(w.find('.hint.error').exists()).toBe(false)
  })

  it('其它失败显示后端文案；非 Error 异常回退通用文案', async () => {
    fetchOrderDetailMock.mockRejectedValue(new Error('订单不存在'))
    let w = await mountDetail()
    expect(w.find('.hint.error').text()).toBe('订单不存在')
    w.unmount()

    fetchOrderDetailMock.mockRejectedValue('boom')
    w = await mountDetail()
    expect(w.find('.hint.error').text()).toBe(t('common.loadFailed'))
    expect(gotoLoginMock).not.toHaveBeenCalled()
  })
})
