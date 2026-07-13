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

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    headers: { 'Content-Type': 'application/json' },
    ...init,
  })
  const body = (await res.json()) as ApiResponse<T>
  if (body.code !== 0) {
    throw new Error(body.msg ?? '请求失败，请稍后重试')
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

/** 分转元展示 */
export function formatPrice(cents: number): string {
  return `¥${(cents / 100).toFixed(2)}`
}
