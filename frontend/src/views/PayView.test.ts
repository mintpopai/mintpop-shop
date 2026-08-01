import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import PayView from './PayView.vue'
import {
  cancelOrder,
  createPaymentIntent,
  fetchCheckoutInfo,
  UnauthorizedError,
  verifyOrder,
  type CheckoutInfo,
  type PaymentIntentInfo,
} from '../api'
import { confirmCardPayment, createCardElements, getStripe, startAlipay, startWechatPay } from '../stripe'
import { gotoLogin } from '../auth'
import { i18n, t } from '../i18n'
import { toast } from '../toast'

vi.mock('../api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api')>()
  return {
    ...actual,
    fetchCheckoutInfo: vi.fn(),
    createPaymentIntent: vi.fn(),
    verifyOrder: vi.fn(),
    cancelOrder: vi.fn(),
  }
})

vi.mock('../stripe', () => ({
  getStripe: vi.fn(),
  startWechatPay: vi.fn(),
  startAlipay: vi.fn(),
  createCardElements: vi.fn(),
  confirmCardPayment: vi.fn(),
}))

vi.mock('../auth', () => ({ gotoLogin: vi.fn() }))

vi.mock('qrcode', () => ({ toCanvas: vi.fn().mockResolvedValue(undefined) }))

const fetchCheckoutInfoMock = vi.mocked(fetchCheckoutInfo)
const createPaymentIntentMock = vi.mocked(createPaymentIntent)
const verifyOrderMock = vi.mocked(verifyOrder)
const cancelOrderMock = vi.mocked(cancelOrder)
const getStripeMock = vi.mocked(getStripe)
const startWechatPayMock = vi.mocked(startWechatPay)
const startAlipayMock = vi.mocked(startAlipay)
const createCardElementsMock = vi.mocked(createCardElements)
const confirmCardPaymentMock = vi.mocked(confirmCardPayment)
const gotoLoginMock = vi.mocked(gotoLogin)

const ORDER_NO = 'MP20260801001'
const blank = { template: '<div />' }

function intent(overrides: Partial<PaymentIntentInfo> = {}): PaymentIntentInfo {
  return {
    orderNo: ORDER_NO,
    clientSecret: 'cs_test_1',
    amountCents: 1999,
    currency: 'USD',
    productName: 'Claude Pro 会员',
    quantity: 2,
    expireRemainingSeconds: 1800,
    ...overrides,
  }
}

let router: Router
let push: ReturnType<typeof vi.spyOn>
let wrapper: VueWrapper | null = null

/** 造一个够用的 Stripe Elements 替身：选中银行卡时组件会 create + getElement().mount() */
function fakeCardElements(paymentElement = { mount: vi.fn() }) {
  return {
    create: vi.fn(),
    getElement: vi.fn().mockReturnValue(paymentElement),
  } as unknown as ReturnType<typeof createCardElements>
}

/** happy-dom 不实现 window.confirm，用可控替身顶上 */
function stubConfirm(answer: boolean) {
  const confirmMock = vi.fn().mockReturnValue(answer)
  vi.stubGlobal('confirm', confirmMock)
  return confirmMock
}

/** 按给定的后端响应挂载收银台，返回挂载完成（首屏请求已结算）的 wrapper */
async function mountPayView(
  options: { checkout?: Partial<CheckoutInfo>; intent?: Partial<PaymentIntentInfo> } = {},
) {
  fetchCheckoutInfoMock.mockResolvedValue({
    methods: ['stripe'],
    stripePublishableKey: 'pk_test_1',
    ...options.checkout,
  })
  createPaymentIntentMock.mockResolvedValue(intent(options.intent))
  getStripeMock.mockResolvedValue({} as Awaited<ReturnType<typeof getStripe>>)

  await router.push(`/pay/${ORDER_NO}`)
  await router.isReady()
  push = vi.spyOn(router, 'push')

  wrapper = mount(PayView, {
    attachTo: document.body,
    global: { plugins: [i18n, router] },
  })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.useFakeTimers()
  toast.value = null
  // 兜底：选中银行卡的路径在多数用例里只是顺带触发，不给替身会抛未捕获异常
  createCardElementsMock.mockReturnValue(fakeCardElements())
  router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: blank },
      { path: '/orders', component: blank },
      { path: '/pay/:orderNo', component: blank },
      { path: '/payment/result', component: blank },
    ],
  })
})

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  vi.useRealTimers()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('收银台加载', () => {
  it('加载成功后展示订单摘要与拍平的三个支付方式', async () => {
    const w = await mountPayView()

    expect(w.find('.product').text()).toContain('Claude Pro 会员')
    expect(w.find('.product').text()).toContain('2')
    expect(w.find('.amount').text()).toBe('$19.99')
    expect(w.find('.order-no').text()).toContain(ORDER_NO)
    expect(w.findAll('.method-card')).toHaveLength(3)
  })

  it('默认选中第一个支付方式（微信），无需用户先点一下', async () => {
    const w = await mountPayView()
    const cards = w.findAll('.method-card')

    expect(cards[0].attributes('aria-checked')).toBe('true')
    expect(cards[1].attributes('aria-checked')).toBe('false')
  })

  it('未登录时跳登录，不渲染支付面板', async () => {
    fetchCheckoutInfoMock.mockResolvedValue({ methods: ['stripe'], stripePublishableKey: 'pk' })
    createPaymentIntentMock.mockRejectedValue(new UnauthorizedError())

    await router.push(`/pay/${ORDER_NO}`)
    wrapper = mount(PayView, { global: { plugins: [i18n, router] } })
    await flushPromises()

    expect(gotoLoginMock).toHaveBeenCalledOnce()
    expect(wrapper.find('.pay-panel').exists()).toBe(false)
  })

  it('加载失败时展示后端给的错误信息与「查看我的订单」出口', async () => {
    fetchCheckoutInfoMock.mockResolvedValue({ methods: ['stripe'], stripePublishableKey: 'pk' })
    createPaymentIntentMock.mockRejectedValue(new Error('订单不存在'))

    await router.push(`/pay/${ORDER_NO}`)
    wrapper = mount(PayView, { global: { plugins: [i18n, router] } })
    await flushPromises()

    expect(wrapper.find('.hint.error').text()).toContain('订单不存在')
    expect(wrapper.find('.hint.error').text()).toContain(t('payment.viewOrders'))
  })

  it('后端没下发 Stripe 公钥时判为不可支付', async () => {
    const w = await mountPayView({ checkout: { stripePublishableKey: null } })

    expect(w.find('.hint.error').text()).toContain(t('payment.notPayable'))
    expect(w.find('.pay-panel').exists()).toBe(false)
  })

  it('后端没开任何支付渠道时判为不可支付', async () => {
    const w = await mountPayView({ checkout: { methods: [] } })

    expect(w.find('.hint.error').text()).toContain(t('payment.notPayable'))
  })
})

describe('订单支付时限倒计时', () => {
  it('按后端给的剩余秒数渲染 mm:ss 并逐秒递减', async () => {
    const w = await mountPayView({ intent: { expireRemainingSeconds: 1800 } })
    expect(w.find('.pay-deadline').text()).toContain('30:00')

    await vi.advanceTimersByTimeAsync(1000)
    expect(w.find('.pay-deadline').text()).toContain('29:59')

    await vi.advanceTimersByTimeAsync(59000)
    expect(w.find('.pay-deadline').text()).toContain('29:00')
  })

  it('归零时主动核实，仍未支付则切换到订单过期态并收起支付面板', async () => {
    const w = await mountPayView({ intent: { expireRemainingSeconds: 2 } })
    verifyOrderMock.mockResolvedValue({ orderNo: ORDER_NO, status: 'PENDING' })

    await vi.advanceTimersByTimeAsync(2000)

    expect(verifyOrderMock).toHaveBeenCalledWith(ORDER_NO)
    expect(w.find('.hint.error').text()).toContain(t('payment.orderExpired'))
    expect(w.find('.pay-panel').exists()).toBe(false)
  })

  it('归零瞬间发现其实已支付时去结果页，而不是误判过期（竞态兜底）', async () => {
    const w = await mountPayView({ intent: { expireRemainingSeconds: 1 } })
    verifyOrderMock.mockResolvedValue({ orderNo: ORDER_NO, status: 'PAID' })

    await vi.advanceTimersByTimeAsync(1000)

    expect(push).toHaveBeenCalledWith({
      path: '/payment/result',
      query: { order_no: ORDER_NO },
    })
    expect(w.text()).not.toContain(t('payment.orderExpired'))
  })

  it('核实请求失败也照常切过期态（后端下次读到该单仍会过期它）', async () => {
    const w = await mountPayView({ intent: { expireRemainingSeconds: 1 } })
    verifyOrderMock.mockRejectedValue(new Error('网络异常'))

    await vi.advanceTimersByTimeAsync(1000)

    expect(w.find('.hint.error').text()).toContain(t('payment.orderExpired'))
  })

  it('后端给的剩余秒数已是 0 时直接判定，不再空转一秒', async () => {
    verifyOrderMock.mockResolvedValue({ orderNo: ORDER_NO, status: 'PENDING' })
    const w = await mountPayView({ intent: { expireRemainingSeconds: 0 } })
    await flushPromises()

    expect(verifyOrderMock).toHaveBeenCalledOnce()
    expect(w.find('.hint.error').text()).toContain(t('payment.orderExpired'))
  })
})

describe('支付方式选择', () => {
  it('点击卡片切换选中态', async () => {
    const w = await mountPayView()

    await w.findAll('.method-card')[1].trigger('click')

    expect(w.findAll('.method-card')[1].attributes('aria-checked')).toBe('true')
    expect(w.findAll('.method-card')[0].attributes('aria-checked')).toBe('false')
  })

  it('方向键向后移动选中项', async () => {
    const w = await mountPayView()

    await w.find('.method-grid').trigger('keydown', { key: 'ArrowRight' })

    expect(w.findAll('.method-card')[1].attributes('aria-checked')).toBe('true')
  })

  it('方向键在末尾向后时循环回第一个', async () => {
    const w = await mountPayView()
    const grid = w.find('.method-grid')

    await grid.trigger('keydown', { key: 'ArrowRight' })
    await grid.trigger('keydown', { key: 'ArrowRight' })
    await grid.trigger('keydown', { key: 'ArrowRight' })

    expect(w.findAll('.method-card')[0].attributes('aria-checked')).toBe('true')
  })

  it('方向键在首位向前时循环到最后一个', async () => {
    const w = await mountPayView()

    await w.find('.method-grid').trigger('keydown', { key: 'ArrowLeft' })

    expect(w.findAll('.method-card')[2].attributes('aria-checked')).toBe('true')
  })

  it('无关按键不改变选中项', async () => {
    const w = await mountPayView()

    await w.find('.method-grid').trigger('keydown', { key: 'Tab' })

    expect(w.findAll('.method-card')[0].attributes('aria-checked')).toBe('true')
  })

  it('选中银行卡时创建并挂载 Payment Element', async () => {
    const paymentElement = { mount: vi.fn() }
    createCardElementsMock.mockReturnValue(fakeCardElements(paymentElement))

    const w = await mountPayView()
    await w.findAll('.method-card')[2].trigger('click')
    await flushPromises()

    expect(createCardElementsMock).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({ amount: 1999, currency: 'USD' }),
    )
    expect(paymentElement.mount).toHaveBeenCalledOnce()
  })
})

describe('确认支付前的状态核实', () => {
  it('未支付则照常发起微信支付，并渲染二维码', async () => {
    const w = await mountPayView()
    verifyOrderMock.mockResolvedValue({ orderNo: ORDER_NO, status: 'PENDING' })
    startWechatPayMock.mockResolvedValue('weixin://wxpay/xxx')

    await w.find('.pay-btn').trigger('click')
    await flushPromises()

    expect(startWechatPayMock).toHaveBeenCalledWith(expect.anything(), 'cs_test_1')
    expect(w.find('.qr-box').exists()).toBe(true)
    expect(w.find('.qr-hint').text()).toBe(t('payment.scanWithWechat'))
  })

  it('已支付时直接去结果页，不重复发起支付', async () => {
    const w = await mountPayView()
    verifyOrderMock.mockResolvedValue({ orderNo: ORDER_NO, status: 'PAID' })

    await w.find('.pay-btn').trigger('click')
    await flushPromises()

    expect(startWechatPayMock).not.toHaveBeenCalled()
    expect(push).toHaveBeenCalledWith({
      path: '/payment/result',
      query: { order_no: ORDER_NO },
    })
  })

  it('订单已超时时切过期态，不放行支付', async () => {
    const w = await mountPayView()
    verifyOrderMock.mockResolvedValue({ orderNo: ORDER_NO, status: 'EXPIRED' })

    await w.find('.pay-btn').trigger('click')
    await flushPromises()

    expect(startWechatPayMock).not.toHaveBeenCalled()
    expect(w.find('.hint.error').text()).toContain(t('payment.orderExpired'))
  })

  it('订单已取消时提示不可支付并回订单列表', async () => {
    const w = await mountPayView()
    verifyOrderMock.mockResolvedValue({ orderNo: ORDER_NO, status: 'CANCELLED' })

    await w.find('.pay-btn').trigger('click')
    await flushPromises()

    expect(startWechatPayMock).not.toHaveBeenCalled()
    expect(toast.value).toEqual({ type: 'error', text: t('payment.notPayable') })
    expect(push).toHaveBeenCalledWith('/orders')
  })

  it('核实请求失败时不阻断支付（后端与 Stripe 侧仍各有拦截）', async () => {
    const w = await mountPayView()
    verifyOrderMock.mockRejectedValue(new Error('网络异常'))
    startWechatPayMock.mockResolvedValue('weixin://wxpay/xxx')

    await w.find('.pay-btn').trigger('click')
    await flushPromises()

    expect(startWechatPayMock).toHaveBeenCalledOnce()
  })

  it('发起支付失败时用 toast 提示 Stripe 的错误信息', async () => {
    const w = await mountPayView()
    verifyOrderMock.mockResolvedValue({ orderNo: ORDER_NO, status: 'PENDING' })
    startWechatPayMock.mockRejectedValue(new Error('支付被拒绝'))

    await w.find('.pay-btn').trigger('click')
    await flushPromises()

    expect(toast.value).toEqual({ type: 'error', text: '支付被拒绝' })
    expect(w.find('.qr-box').exists()).toBe(false)
  })
})

describe('支付宝与银行卡分支', () => {
  it('支付宝桌面端拿到托管页 URL 后本地渲染二维码', async () => {
    const w = await mountPayView()
    verifyOrderMock.mockResolvedValue({ orderNo: ORDER_NO, status: 'PENDING' })
    startAlipayMock.mockResolvedValue({ qrUrl: 'https://alipay/xxx' })

    await w.findAll('.method-card')[1].trigger('click')
    await w.find('.pay-btn').trigger('click')
    await flushPromises()

    expect(w.find('.qr-hint').text()).toBe(t('payment.scanWithAlipay'))
  })

  it('支付宝移动端整页跳转，不渲染二维码也不启动轮询', async () => {
    const w = await mountPayView()
    verifyOrderMock.mockResolvedValue({ orderNo: ORDER_NO, status: 'PENDING' })
    startAlipayMock.mockResolvedValue({})

    await w.findAll('.method-card')[1].trigger('click')
    await w.find('.pay-btn').trigger('click')
    await flushPromises()

    expect(w.find('.qr-box').exists()).toBe(false)

    verifyOrderMock.mockClear()
    await vi.advanceTimersByTimeAsync(4000)
    expect(verifyOrderMock).not.toHaveBeenCalled()
  })

  it('银行卡确认返回 succeeded 时直接去结果页', async () => {
    const w = await mountPayView()
    verifyOrderMock.mockResolvedValue({ orderNo: ORDER_NO, status: 'PENDING' })
    confirmCardPaymentMock.mockResolvedValue('succeeded')

    await w.findAll('.method-card')[2].trigger('click')
    await flushPromises()
    await w.find('.pay-btn').trigger('click')
    await flushPromises()

    expect(push).toHaveBeenCalledWith({
      path: '/payment/result',
      query: { order_no: ORDER_NO },
    })
  })

  it('银行卡确认返回非终态时转轮询，等待异步结果', async () => {
    const w = await mountPayView()
    verifyOrderMock.mockResolvedValue({ orderNo: ORDER_NO, status: 'PENDING' })
    confirmCardPaymentMock.mockResolvedValue('processing')

    await w.findAll('.method-card')[2].trigger('click')
    await flushPromises()
    await w.find('.pay-btn').trigger('click')
    await flushPromises()

    verifyOrderMock.mockClear()
    await vi.advanceTimersByTimeAsync(2000)
    expect(verifyOrderMock).toHaveBeenCalledOnce()
  })
})

describe('支付结果轮询', () => {
  /** 进入「已出二维码 + 轮询中」的状态 */
  async function startPolling() {
    const w = await mountPayView()
    verifyOrderMock.mockResolvedValue({ orderNo: ORDER_NO, status: 'PENDING' })
    startWechatPayMock.mockResolvedValue('weixin://wxpay/xxx')
    await w.find('.pay-btn').trigger('click')
    await flushPromises()
    verifyOrderMock.mockClear()
    return w
  }

  it('每 2 秒核实一次订单状态', async () => {
    await startPolling()

    await vi.advanceTimersByTimeAsync(2000)
    expect(verifyOrderMock).toHaveBeenCalledOnce()

    await vi.advanceTimersByTimeAsync(2000)
    expect(verifyOrderMock).toHaveBeenCalledTimes(2)
  })

  it('轮到已支付时跳结果页并停止轮询', async () => {
    await startPolling()
    verifyOrderMock.mockResolvedValue({ orderNo: ORDER_NO, status: 'PAID' })

    await vi.advanceTimersByTimeAsync(2000)
    expect(push).toHaveBeenCalledWith({
      path: '/payment/result',
      query: { order_no: ORDER_NO },
    })

    verifyOrderMock.mockClear()
    await vi.advanceTimersByTimeAsync(4000)
    expect(verifyOrderMock).not.toHaveBeenCalled()
  })

  it('轮到订单超时时切过期态并停止轮询', async () => {
    const w = await startPolling()
    verifyOrderMock.mockResolvedValue({ orderNo: ORDER_NO, status: 'EXPIRED' })

    await vi.advanceTimersByTimeAsync(2000)
    expect(w.find('.hint.error').text()).toContain(t('payment.orderExpired'))

    verifyOrderMock.mockClear()
    await vi.advanceTimersByTimeAsync(4000)
    expect(verifyOrderMock).not.toHaveBeenCalled()
  })

  it('轮到订单被取消时提示并回订单列表', async () => {
    await startPolling()
    verifyOrderMock.mockResolvedValue({ orderNo: ORDER_NO, status: 'CANCELLED' })

    await vi.advanceTimersByTimeAsync(2000)

    expect(toast.value).toEqual({ type: 'error', text: t('payment.notPayable') })
    expect(push).toHaveBeenCalledWith('/orders')
  })

  it('单次核实失败不打断轮询，下一轮继续', async () => {
    await startPolling()
    verifyOrderMock.mockRejectedValueOnce(new Error('网络抖动'))
    verifyOrderMock.mockResolvedValue({ orderNo: ORDER_NO, status: 'PENDING' })

    await vi.advanceTimersByTimeAsync(2000)
    await vi.advanceTimersByTimeAsync(2000)

    expect(verifyOrderMock).toHaveBeenCalledTimes(2)
  })

  it('二维码有效期倒计时逐秒递减，归零后提示重新获取', async () => {
    const w = await startPolling()
    expect(w.find('.qr-expiry').text()).toContain('15:00')

    await vi.advanceTimersByTimeAsync(1000)
    expect(w.find('.qr-expiry').text()).toContain('14:59')

    await vi.advanceTimersByTimeAsync(15 * 60 * 1000)
    expect(w.find('.qr-expiry').text()).toContain(t('payment.qrExpired'))
    expect(w.find('.link-btn').text()).toBe(t('payment.regenerateQr'))
  })

  it('组件卸载后停止所有轮询与倒计时', async () => {
    const w = await startPolling()

    w.unmount()
    wrapper = null

    await vi.advanceTimersByTimeAsync(6000)
    expect(verifyOrderMock).not.toHaveBeenCalled()
  })
})

describe('取消订单', () => {
  it('用户在确认框里点取消时不发请求', async () => {
    const w = await mountPayView()
    stubConfirm(false)

    await w.find('.cancel-btn').trigger('click')
    await flushPromises()

    expect(cancelOrderMock).not.toHaveBeenCalled()
  })

  it('确认后取消订单，提示成功并回订单列表', async () => {
    const w = await mountPayView()
    stubConfirm(true)
    cancelOrderMock.mockResolvedValue(null)

    await w.find('.cancel-btn').trigger('click')
    await flushPromises()

    expect(cancelOrderMock).toHaveBeenCalledWith(ORDER_NO)
    expect(toast.value).toEqual({ type: 'success', text: t('payment.cancelled') })
    expect(push).toHaveBeenCalledWith('/orders')
  })

  it('取消失败时提示后端给的原因，且不跳走', async () => {
    const w = await mountPayView()
    stubConfirm(true)
    cancelOrderMock.mockRejectedValue(new Error('该订单当前不可取消'))

    await w.find('.cancel-btn').trigger('click')
    await flushPromises()

    expect(toast.value).toEqual({ type: 'error', text: '该订单当前不可取消' })
    expect(push).not.toHaveBeenCalled()
  })
})
