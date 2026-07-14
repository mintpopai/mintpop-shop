/**
 * 支付方式拍平层（品牌 INVARIANT）：后端只有单一 stripe 通道，
 * 前端在纯展示层把它摊开成「微信支付 / 支付宝 / 银行卡」三个并列选项。
 */

/** 展示顺序即数组顺序：微信 → 支付宝 → 银行卡（INVARIANT） */
export const STRIPE_SUB_METHODS = ['wxpay', 'alipay', 'card'] as const

export type StripeSubMethod = (typeof STRIPE_SUB_METHODS)[number]

/** 子方式 → Stripe payment_method_types（与后端映射表逐字一致） */
export const STRIPE_PM_TYPE: Record<StripeSubMethod, string> = {
  wxpay: 'wechat_pay',
  alipay: 'alipay',
  card: 'card',
}

/** 拍平后的单个支付选项：subMethod 只决定前端渲染哪种确认 UI，不进下单参数 */
export interface PayOption {
  key: string
  paymentType: string
  subMethod?: StripeSubMethod
}

/** 从后端 methods 构建拍平列表：直连微信/支付宝优先，Stripe 补位；银行卡只来自 Stripe */
export function buildPayOptions(methods: string[]): PayOption[] {
  const options: PayOption[] = []
  const hasStripe = methods.includes('stripe')
  for (const m of ['wxpay', 'alipay'] as const) {
    if (methods.includes(m)) {
      options.push({ key: m, paymentType: m })
    } else if (hasStripe) {
      options.push({ key: `stripe:${m}`, paymentType: 'stripe', subMethod: m })
    }
  }
  if (hasStripe) {
    options.push({ key: 'stripe:card', paymentType: 'stripe', subMethod: 'card' })
  }
  return options
}

/** 已支付/轮询可停（成功）口径：与后端状态机逐字对应 */
export function isPaidStatus(status: string): boolean {
  return status === 'PAID' || status === 'COMPLETED'
}
