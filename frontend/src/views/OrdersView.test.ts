import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import OrdersView from './OrdersView.vue'
import { cancelOrder, fetchMyOrders, type OrderItem } from '../api'
import { i18n, t } from '../i18n'
import { toast } from '../toast'

vi.mock('../api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api')>()
  return { ...actual, fetchMyOrders: vi.fn(), cancelOrder: vi.fn() }
})

vi.mock('../auth', () => ({ gotoLogin: vi.fn() }))

const fetchMyOrdersMock = vi.mocked(fetchMyOrders)
const cancelOrderMock = vi.mocked(cancelOrder)

const blank = { template: '<div />' }
let router: Router
let wrapper: VueWrapper | null = null

function order(overrides: Partial<OrderItem> = {}): OrderItem {
  return {
    orderNo: 'MP20260801001',
    productName: 'Claude Pro 会员',
    quantity: 1,
    amountCents: 1999,
    status: 'PENDING',
    statusLabel: '待支付',
    createdAt: '2026-08-01T00:00:00Z',
    ...overrides,
  }
}

async function mountOrders(orders: OrderItem[] = [order()]) {
  fetchMyOrdersMock.mockResolvedValue(orders)
  await router.push('/orders')
  await router.isReady()
  wrapper = mount(OrdersView, { attachTo: document.body, global: { plugins: [i18n, router] } })
  await flushPromises()
  return wrapper
}

/** 取消确认弹窗 Teleport 到 body，不在 wrapper 根下，直接从 document 取 */
function cancelDialog(): HTMLElement | null {
  return document.querySelector('.dialog')
}

async function clickDialog(selector: string) {
  const button = cancelDialog()?.querySelector<HTMLElement>(selector)
  if (!button) {
    throw new Error(`取消确认弹窗里没有 ${selector}`)
  }
  button.click()
  await flushPromises()
}

beforeEach(() => {
  toast.value = null
  router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: blank },
      { path: '/orders', component: blank },
      { path: '/orders/:orderNo', component: blank },
      { path: '/pay/:orderNo', component: blank },
    ],
  })
})

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  document.body.innerHTML = ''
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('取消订单', () => {
  it('点「取消」先弹自绘确认框（不是 window.confirm），说清是哪一单，且不发请求', async () => {
    const w = await mountOrders()
    const confirmSpy = vi.fn()
    vi.stubGlobal('confirm', confirmSpy)

    await w.find('.cancel-link').trigger('click')
    await flushPromises()

    expect(confirmSpy).not.toHaveBeenCalled()
    expect(cancelDialog()?.getAttribute('aria-label')).toBe(t('payment.cancelConfirmTitle'))
    expect(cancelDialog()?.textContent).toContain('MP20260801001')
    expect(cancelOrderMock).not.toHaveBeenCalled()
  })

  it('选「再想想」关掉弹窗，不发请求', async () => {
    const w = await mountOrders()
    await w.find('.cancel-link').trigger('click')
    await flushPromises()

    await clickDialog('.btn-ghost')

    expect(cancelDialog()).toBeNull()
    expect(cancelOrderMock).not.toHaveBeenCalled()
  })

  it('确认后取消该单、提示成功并重新拉列表', async () => {
    const w = await mountOrders()
    cancelOrderMock.mockResolvedValue(null)
    fetchMyOrdersMock.mockResolvedValue([order({ status: 'CANCELLED', statusLabel: '已取消' })])
    await w.find('.cancel-link').trigger('click')
    await flushPromises()

    await clickDialog('.btn-danger')

    expect(cancelOrderMock).toHaveBeenCalledWith('MP20260801001')
    expect(cancelDialog()).toBeNull()
    expect(toast.value).toEqual({ type: 'success', text: t('payment.cancelled') })
    expect(fetchMyOrdersMock).toHaveBeenCalledTimes(2)
    expect(w.find('.status-tag').text()).toBe('已取消')
  })

  it('取消失败时提示后端给的原因，弹窗留着', async () => {
    const w = await mountOrders()
    cancelOrderMock.mockRejectedValue(new Error('该订单当前不可取消'))
    await w.find('.cancel-link').trigger('click')
    await flushPromises()

    await clickDialog('.btn-danger')

    expect(toast.value).toEqual({ type: 'error', text: '该订单当前不可取消' })
    expect(cancelDialog()).not.toBeNull()
  })
})
