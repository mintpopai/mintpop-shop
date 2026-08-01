import { describe, expect, it } from 'vitest'
import { buildPayOptions, isPaidStatus, STRIPE_PM_TYPE, STRIPE_SUB_METHODS } from './payment'

describe('buildPayOptions', () => {
  it('只有 stripe 通道时拍平出微信/支付宝/银行卡三个并列选项', () => {
    expect(buildPayOptions(['stripe'])).toEqual([
      { key: 'stripe:wxpay', paymentType: 'stripe', subMethod: 'wxpay' },
      { key: 'stripe:alipay', paymentType: 'stripe', subMethod: 'alipay' },
      { key: 'stripe:card', paymentType: 'stripe', subMethod: 'card' },
    ])
  })

  it('展示顺序恒为微信 → 支付宝 → 银行卡，不随后端 methods 顺序变化（INVARIANT）', () => {
    const reversed = buildPayOptions(['stripe', 'alipay', 'wxpay'])
    expect(reversed.map((o) => o.key)).toEqual(['wxpay', 'alipay', 'stripe:card'])
  })

  it('直连渠道优先于 Stripe 补位：wxpay 直连、alipay 由 stripe 补位', () => {
    expect(buildPayOptions(['wxpay', 'stripe'])).toEqual([
      { key: 'wxpay', paymentType: 'wxpay' },
      { key: 'stripe:alipay', paymentType: 'stripe', subMethod: 'alipay' },
      { key: 'stripe:card', paymentType: 'stripe', subMethod: 'card' },
    ])
  })

  it('没有 stripe 时不出银行卡选项，直连渠道照常展示', () => {
    expect(buildPayOptions(['wxpay', 'alipay'])).toEqual([
      { key: 'wxpay', paymentType: 'wxpay' },
      { key: 'alipay', paymentType: 'alipay' },
    ])
  })

  it('后端未开任何渠道时返回空列表（页面据此判定不可支付）', () => {
    expect(buildPayOptions([])).toEqual([])
  })

  it('忽略未知渠道，不误产出选项', () => {
    expect(buildPayOptions(['paypal', 'unionpay'])).toEqual([])
  })
})

describe('isPaidStatus', () => {
  it('PAID 与 COMPLETED 都算已支付（轮询可停）', () => {
    expect(isPaidStatus('PAID')).toBe(true)
    expect(isPaidStatus('COMPLETED')).toBe(true)
  })

  it('其余状态一律不算已支付', () => {
    for (const status of ['PENDING', 'EXPIRED', 'CANCELLED', 'FAILED', '']) {
      expect(isPaidStatus(status)).toBe(false)
    }
  })

  it('大小写敏感：后端状态是 SCREAMING_SNAKE_CASE，小写不应被误判为成功', () => {
    expect(isPaidStatus('paid')).toBe(false)
  })
})

describe('支付方式常量', () => {
  it('每个子方式都有对应的 Stripe payment_method_type 映射', () => {
    for (const sub of STRIPE_SUB_METHODS) {
      expect(STRIPE_PM_TYPE[sub]).toBeTruthy()
    }
  })

  it('映射值与后端映射表逐字一致', () => {
    expect(STRIPE_PM_TYPE).toEqual({
      wxpay: 'wechat_pay',
      alipay: 'alipay',
      card: 'card',
    })
  })
})
