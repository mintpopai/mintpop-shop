import { locale, t } from './i18n'

/** 后端统一返回结构：code=0 成功，非 0 失败取 msg */
export interface ApiResponse<T> {
  code: number
  data: T | null
  msg: string | null
}

/** 商品（镜像后端 ProductResponse） */
export interface Product {
  id: number
  name: string
  description: string | null
  priceCents: number
  imageUrl: string | null
  /** 角标文案，空=不显示 */
  badge: string | null
  /** 卡片主题色（MINT/VIOLET/SKY/AMBER/ROSE），未知值前端回退 MINT */
  accent: string
}

/** 分组含商品（镜像后端 GroupWithProductsResponse） */
export interface GroupWithProducts {
  id: number
  name: string
  products: Product[]
}

/** 下单结果（镜像后端 CreateOrderResponse） */
export interface CreateOrderResult {
  orderNo: string
  amountCents: number
}

/** 当前用户（镜像后端 MeResponse） */
export interface Me {
  id: number
  email: string
  nickname: string | null
  avatarUrl: string | null
}

/** 我的订单列表项（镜像后端 OrderItemResponse） */
export interface OrderItem {
  orderNo: string
  productName: string
  quantity: number
  amountCents: number
  status: string
  statusLabel: string
  createdAt: string
}

/** 收银台信息（镜像后端 CheckoutInfoResponse） */
export interface CheckoutInfo {
  methods: string[]
  stripePublishableKey: string | null
}

/** 支付意图（镜像后端 PaymentIntentResponse） */
export interface PaymentIntentInfo {
  orderNo: string
  clientSecret: string
  amountCents: number
  currency: string
  productName: string
  quantity: number
  /** 剩余支付秒数（服务端按下单时间与支付时限计算，前端据此倒计时） */
  expireRemainingSeconds: number
}

/** 订单支付核实结果（镜像后端 VerifyOrderResponse） */
export interface VerifyResult {
  orderNo: string
  status: string
}

/** 未登录/会话过期（HTTP 401），调用方据此引导登录 */
export class UnauthorizedError extends Error {
  constructor() {
    super(t('api.unauthorized'))
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let res: Response
  try {
    res = await fetch(path, {
      ...init,
      headers: {
        'Content-Type': 'application/json',
        'Accept-Language': locale,
        ...init?.headers,
      },
    })
  } catch {
    // 网络失败：不把原始英文报错透给用户
    throw new Error(t('api.network'))
  }
  // 鉴权中间件的 401 是唯一非 200 业务入口，转成类型化错误
  if (res.status === 401) {
    throw new UnauthorizedError()
  }
  let body: ApiResponse<T>
  try {
    body = (await res.json()) as ApiResponse<T>
  } catch {
    throw new Error(t('api.network'))
  }
  if (body.code !== 0) {
    throw new Error(body.msg ?? t('api.requestFailed'))
  }
  return body.data as T
}

/** 拉取全部分组及上架商品 */
export function fetchGroups(): Promise<GroupWithProducts[]> {
  return request<GroupWithProducts[]>('/api/groups')
}

/** 创建待支付订单（骨架阶段数量固定 1） */
export function createOrder(productId: number): Promise<CreateOrderResult> {
  return request<CreateOrderResult>('/api/orders', {
    method: 'POST',
    body: JSON.stringify({ productId, quantity: 1 }),
  })
}

/** 当前登录用户（401 抛 UnauthorizedError 表示游客） */
export function fetchMe(): Promise<Me> {
  return request<Me>('/api/me')
}

/** 我的订单列表 */
export function fetchMyOrders(): Promise<OrderItem[]> {
  return request<OrderItem[]>('/api/orders')
}

/** 收银台信息：可用支付方式 + Stripe publishable key */
export function fetchCheckoutInfo(): Promise<CheckoutInfo> {
  return request<CheckoutInfo>('/api/payment/checkout-info')
}

/** 懒创建/复用支付意图（支付页加载时调用） */
export function createPaymentIntent(orderNo: string): Promise<PaymentIntentInfo> {
  return request<PaymentIntentInfo>(`/api/payment/orders/${orderNo}/intent`, { method: 'POST' })
}

/** 主动核实订单支付状态（轮询用） */
export function verifyOrder(orderNo: string): Promise<VerifyResult> {
  return request<VerifyResult>('/api/payment/orders/verify', {
    method: 'POST',
    body: JSON.stringify({ orderNo }),
  })
}

/** 取消订单（仅待支付/支付失败可取消） */
export function cancelOrder(orderNo: string): Promise<null> {
  return request<null>(`/api/payment/orders/${orderNo}/cancel`, { method: 'POST' })
}

/** 美分转美元展示 */
export function formatPrice(cents: number): string {
  return `$${(cents / 100).toFixed(2)}`
}
