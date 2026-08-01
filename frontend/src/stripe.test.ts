import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Stripe, StripeElements } from '@stripe/stripe-js'
import {
  confirmCardPayment,
  createCardElements,
  getStripe,
  startAlipay,
  startWechatPay,
} from './stripe'

const loadStripeMock = vi.fn()
vi.mock('@stripe/stripe-js', () => ({ loadStripe: (...args: unknown[]) => loadStripeMock(...args) }))

/** 只实现被测路径用到的方法，其余按 Stripe 类型断言过去 */
function fakeStripe(overrides: Partial<Stripe>): Stripe {
  return overrides as Stripe
}

const RETURN_URL = 'http://localhost:5173/payment/result?order_no=MP001'

afterEach(() => {
  vi.restoreAllMocks()
})

describe('getStripe', () => {
  it('缓存实例：多次调用只加载一次 SDK', async () => {
    const instance = fakeStripe({})
    loadStripeMock.mockResolvedValue(instance)

    const first = await getStripe('pk_test_1')
    const second = await getStripe('pk_test_2')

    expect(first).toBe(instance)
    expect(second).toBe(instance)
    expect(loadStripeMock).toHaveBeenCalledOnce()
    expect(loadStripeMock).toHaveBeenCalledWith('pk_test_1')
  })
})

describe('startWechatPay', () => {
  it('以 handleActions=false 确认，拿回二维码内容由页面本地渲染', async () => {
    const confirm = vi.fn().mockResolvedValue({
      paymentIntent: { next_action: { wechat_pay_display_qr_code: { data: 'weixin://wxpay/xxx' } } },
    })
    const stripe = fakeStripe({ confirmWechatPayPayment: confirm })

    await expect(startWechatPay(stripe, 'cs_1')).resolves.toBe('weixin://wxpay/xxx')
    expect(confirm).toHaveBeenCalledWith(
      'cs_1',
      { payment_method_options: { wechat_pay: { client: 'web' } } },
      { handleActions: false },
    )
  })

  it('Stripe 返回错误时抛出其提示信息', async () => {
    const stripe = fakeStripe({
      confirmWechatPayPayment: vi.fn().mockResolvedValue({ error: { message: '支付被拒绝' } }),
    })

    await expect(startWechatPay(stripe, 'cs_1')).rejects.toThrow('支付被拒绝')
  })

  it('响应里没有二维码字段时报错，而不是渲染空二维码', async () => {
    const stripe = fakeStripe({
      confirmWechatPayPayment: vi.fn().mockResolvedValue({ paymentIntent: { next_action: null } }),
    })

    await expect(startWechatPay(stripe, 'cs_1')).rejects.toThrow('missing wechat qr code')
  })
})

describe('startAlipay', () => {
  /** 覆写 userAgent，用于区分桌面端与移动端两条分支 */
  function stubUserAgent(ua: string) {
    vi.spyOn(navigator, 'userAgent', 'get').mockReturnValue(ua)
  }

  const DESKTOP_UA = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Chrome/140.0 Safari/537.36'
  const MOBILE_UA = 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) Mobile/15E148'

  it('桌面端以 handleActions=false 取托管页 URL，本地生成二维码', async () => {
    stubUserAgent(DESKTOP_UA)
    const confirm = vi.fn().mockResolvedValue({
      paymentIntent: { next_action: { alipay_handle_redirect: { url: 'https://alipay/xxx' } } },
    })
    const stripe = fakeStripe({ confirmAlipayPayment: confirm })

    await expect(startAlipay(stripe, 'cs_1', RETURN_URL)).resolves.toEqual({
      qrUrl: 'https://alipay/xxx',
    })
    expect(confirm).toHaveBeenCalledWith(
      'cs_1',
      { return_url: RETURN_URL },
      { handleActions: false },
    )
  })

  it('移动端交给 Stripe 整页跳转，不取二维码 URL', async () => {
    stubUserAgent(MOBILE_UA)
    const confirm = vi.fn().mockResolvedValue({})
    const stripe = fakeStripe({ confirmAlipayPayment: confirm })

    await expect(startAlipay(stripe, 'cs_1', RETURN_URL)).resolves.toEqual({})
    // 移动端只传两个参数，让 Stripe 自行处理跳转
    expect(confirm).toHaveBeenCalledWith('cs_1', { return_url: RETURN_URL })
  })

  it('移动端确认出错时同样抛出提示信息', async () => {
    stubUserAgent(MOBILE_UA)
    const stripe = fakeStripe({
      confirmAlipayPayment: vi.fn().mockResolvedValue({ error: { message: '支付宝异常' } }),
    })

    await expect(startAlipay(stripe, 'cs_1', RETURN_URL)).rejects.toThrow('支付宝异常')
  })

  it('桌面端缺少跳转 URL 时报错', async () => {
    stubUserAgent(DESKTOP_UA)
    const stripe = fakeStripe({
      confirmAlipayPayment: vi.fn().mockResolvedValue({ paymentIntent: { next_action: {} } }),
    })

    await expect(startAlipay(stripe, 'cs_1', RETURN_URL)).rejects.toThrow(
      'missing alipay redirect url',
    )
  })
})

describe('createCardElements', () => {
  function capture() {
    const elements = vi.fn().mockReturnValue({} as StripeElements)
    return { stripe: fakeStripe({ elements }), elements }
  }

  it('只渲染银行卡一种方式（拍平在确认层的延续）', () => {
    const { stripe, elements } = capture()

    createCardElements(stripe, { amount: 1999, currency: 'USD', locale: 'en-US' })

    expect(elements).toHaveBeenCalledWith(
      expect.objectContaining({ mode: 'payment', amount: 1999, paymentMethodTypes: ['card'] }),
    )
  })

  it('币种转小写传给 Stripe（后端给的是大写）', () => {
    const { stripe, elements } = capture()

    createCardElements(stripe, { amount: 100, currency: 'USD', locale: 'en-US' })

    expect(elements.mock.calls[0][0]).toMatchObject({ currency: 'usd' })
  })

  it('中文会话映射为 Stripe 的 zh 语言，英文映射为 en', () => {
    const zh = capture()
    createCardElements(zh.stripe, { amount: 100, currency: 'usd', locale: 'zh-CN' })
    expect(zh.elements.mock.calls[0][0]).toMatchObject({ locale: 'zh' })

    const en = capture()
    createCardElements(en.stripe, { amount: 100, currency: 'usd', locale: 'en-US' })
    expect(en.elements.mock.calls[0][0]).toMatchObject({ locale: 'en' })
  })
})

describe('confirmCardPayment', () => {
  let elements: StripeElements
  let submit: ReturnType<typeof vi.fn>

  beforeEach(() => {
    submit = vi.fn().mockResolvedValue({})
    elements = { submit } as unknown as StripeElements
  })

  it('先提交表单再确认支付，成功时返回支付意图状态', async () => {
    const confirmPayment = vi.fn().mockResolvedValue({ paymentIntent: { status: 'succeeded' } })
    const stripe = fakeStripe({ confirmPayment })

    await expect(confirmCardPayment(stripe, elements, 'cs_1', RETURN_URL)).resolves.toBe(
      'succeeded',
    )
    expect(submit).toHaveBeenCalledOnce()
    expect(confirmPayment).toHaveBeenCalledWith(
      expect.objectContaining({
        clientSecret: 'cs_1',
        confirmParams: { return_url: RETURN_URL },
        redirect: 'if_required',
      }),
    )
  })

  it('表单校验不过时直接抛错，不发起确认', async () => {
    submit.mockResolvedValue({ error: { message: '卡号无效' } })
    const confirmPayment = vi.fn()
    const stripe = fakeStripe({ confirmPayment })

    await expect(confirmCardPayment(stripe, elements, 'cs_1', RETURN_URL)).rejects.toThrow(
      '卡号无效',
    )
    expect(confirmPayment).not.toHaveBeenCalled()
  })

  it('确认失败时抛出 Stripe 的提示信息', async () => {
    const stripe = fakeStripe({
      confirmPayment: vi.fn().mockResolvedValue({ error: { message: '余额不足' } }),
    })

    await expect(confirmCardPayment(stripe, elements, 'cs_1', RETURN_URL)).rejects.toThrow(
      '余额不足',
    )
  })

  it('没有返回支付意图时按处理中兜底，交给轮询继续确认', async () => {
    const stripe = fakeStripe({ confirmPayment: vi.fn().mockResolvedValue({}) })

    await expect(confirmCardPayment(stripe, elements, 'cs_1', RETURN_URL)).resolves.toBe(
      'processing',
    )
  })
})
