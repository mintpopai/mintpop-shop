import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import PaymentResultView from './PaymentResultView.vue'
import { verifyOrder, UnauthorizedError } from '../api'
import { gotoLogin } from '../auth'
import { i18n, t } from '../i18n'

vi.mock('../api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api')>()
  return { ...actual, verifyOrder: vi.fn() }
})

vi.mock('../auth', () => ({ gotoLogin: vi.fn() }))

const verifyOrderMock = vi.mocked(verifyOrder)
const gotoLoginMock = vi.mocked(gotoLogin)

const blank = { template: '<div />' }
let router: Router
let wrapper: VueWrapper | null = null

/** 模拟 Stripe 回跳：带 order_no 与一堆 Stripe 追加的 query */
async function mountResult(query = 'order_no=MP1&payment_intent=pi_1&redirect_status=succeeded') {
  await router.push(`/payment/result${query ? `?${query}` : ''}`)
  await router.isReady()
  wrapper = mount(PaymentResultView, { global: { plugins: [i18n, router] } })
  await flushPromises()
  return wrapper
}

function status(name: string) {
  return { orderNo: 'MP1', status: name }
}

beforeEach(() => {
  vi.useFakeTimers()
  router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: blank },
      { path: '/orders', component: blank },
      { path: '/payment/result', component: blank },
    ],
  })
})

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  vi.useRealTimers()
  vi.restoreAllMocks()
})

describe('回跳清理与首查', () => {
  it('挂载即清掉 Stripe 追加的 query，只留 order_no，并立刻核实一次', async () => {
    verifyOrderMock.mockResolvedValue(status('PENDING'))
    const w = await mountResult()

    expect(router.currentRoute.value.fullPath).toBe('/payment/result?order_no=MP1')
    expect(verifyOrderMock).toHaveBeenCalledTimes(1)
    expect(verifyOrderMock).toHaveBeenCalledWith('MP1')
    expect(w.find('.status').text()).toBe(t('payment.result.confirming'))
    expect(w.find('.links').exists()).toBe(false)
  })

  it('没有 order_no 直接进「待确认」，不轮询', async () => {
    const w = await mountResult('redirect_status=succeeded')

    expect(router.currentRoute.value.fullPath).toBe('/payment/result')
    expect(w.find('.status').text()).toBe(t('payment.result.pending'))
    await vi.advanceTimersByTimeAsync(10_000)
    expect(verifyOrderMock).not.toHaveBeenCalled()
  })
})

describe('轮询定格', () => {
  it.each(['PAID', 'COMPLETED'])('%s → 支付成功，停止轮询并给出两个去向', async (name) => {
    verifyOrderMock.mockResolvedValue(status(name))
    const w = await mountResult()

    expect(w.find('.status.success').text()).toBe(t('payment.result.success'))
    const links = w.findAll('.links a')
    expect(links.map((l) => l.attributes('href'))).toEqual(['/orders', '/'])

    await vi.advanceTimersByTimeAsync(10_000)
    expect(verifyOrderMock).toHaveBeenCalledTimes(1)
  })

  it.each(['FAILED', 'CANCELLED', 'EXPIRED'])('%s → 支付未完成并停止轮询', async (name) => {
    verifyOrderMock.mockResolvedValue(status(name))
    const w = await mountResult()

    expect(w.find('.status.failed').text()).toBe(t('payment.result.failed'))
    await vi.advanceTimersByTimeAsync(10_000)
    expect(verifyOrderMock).toHaveBeenCalledTimes(1)
  })

  it('一直 PENDING：每 2 秒查一次，第 15 次仍未确认转「待确认」并停止', async () => {
    verifyOrderMock.mockResolvedValue(status('PENDING'))
    const w = await mountResult()

    await vi.advanceTimersByTimeAsync(2000 * 13)
    expect(verifyOrderMock).toHaveBeenCalledTimes(14)
    expect(w.find('.status').text()).toBe(t('payment.result.confirming'))

    await vi.advanceTimersByTimeAsync(2000)
    expect(verifyOrderMock).toHaveBeenCalledTimes(15)
    expect(w.find('.status').text()).toBe(t('payment.result.pending'))

    await vi.advanceTimersByTimeAsync(20_000)
    expect(verifyOrderMock).toHaveBeenCalledTimes(15)
  })

  it('中途从 PENDING 变 PAID 立即定格成功', async () => {
    verifyOrderMock.mockResolvedValueOnce(status('PENDING')).mockResolvedValue(status('PAID'))
    const w = await mountResult()
    expect(w.find('.status').text()).toBe(t('payment.result.confirming'))

    await vi.advanceTimersByTimeAsync(2000)
    expect(w.find('.status.success').exists()).toBe(true)
  })

  it('核实请求报错不算失败：继续轮询，到上限转「待确认」', async () => {
    verifyOrderMock.mockRejectedValue(new Error('网络异常'))
    const w = await mountResult()

    await vi.advanceTimersByTimeAsync(2000 * 13)
    expect(w.find('.status').text()).toBe(t('payment.result.confirming'))
    await vi.advanceTimersByTimeAsync(2000)
    expect(w.find('.status').text()).toBe(t('payment.result.pending'))
    expect(verifyOrderMock).toHaveBeenCalledTimes(15)
  })

  it('401 立即停轮询并跳登录', async () => {
    verifyOrderMock.mockRejectedValue(new UnauthorizedError())
    await mountResult()

    expect(gotoLoginMock).toHaveBeenCalledOnce()
    await vi.advanceTimersByTimeAsync(10_000)
    expect(verifyOrderMock).toHaveBeenCalledTimes(1)
  })

  it('慢响应在定格之后才回来，不翻转已定格的结果', async () => {
    let resolveFirst!: (v: { orderNo: string; status: string }) => void
    verifyOrderMock
      .mockReturnValueOnce(new Promise((r) => (resolveFirst = r)))
      .mockResolvedValue(status('PAID'))
    const w = await mountResult()

    await vi.advanceTimersByTimeAsync(2000)
    expect(w.find('.status.success').exists()).toBe(true)

    resolveFirst(status('FAILED'))
    await flushPromises()
    expect(w.find('.status.success').exists()).toBe(true)
    expect(w.find('.status.failed').exists()).toBe(false)
  })

  it('页面卸载后定时器被清掉，不再发请求', async () => {
    verifyOrderMock.mockResolvedValue(status('PENDING'))
    const w = await mountResult()
    w.unmount()
    wrapper = null

    await vi.advanceTimersByTimeAsync(10_000)
    expect(verifyOrderMock).toHaveBeenCalledTimes(1)
  })
})
