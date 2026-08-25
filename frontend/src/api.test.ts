import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  cancelOrder,
  createOrder,
  createPaymentIntent,
  fetchCheckoutInfo,
  fetchGroups,
  fetchMe,
  fetchMyOrders,
  fetchOrderDetail,
  fetchProduct,
  formatPrice,
  request,
  UnauthorizedError,
  updateMyLocale,
  updateMyProfile,
  verifyOrder,
} from './api'
import { locale, t } from './i18n'

/** 构造一个后端风格的成功响应 */
function ok<T>(data: T): Response {
  return new Response(JSON.stringify({ code: 0, data, msg: null }), { status: 200 })
}

/** 构造一个后端风格的业务失败响应（HTTP 仍是 200） */
function bizError(code: number, msg: string | null): Response {
  return new Response(JSON.stringify({ code, data: null, msg }), { status: 200 })
}

let fetchMock: ReturnType<typeof vi.fn>

beforeEach(() => {
  fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

/** 取本次 fetch 调用的 (path, init) */
function lastCall(): [string, RequestInit] {
  return fetchMock.mock.calls[fetchMock.mock.calls.length - 1] as [string, RequestInit]
}

describe('request 统一封装', () => {
  it('业务码为 0 时取出 data 返回', async () => {
    fetchMock.mockResolvedValue(ok({ hello: 'world' }))
    await expect(request('/api/x')).resolves.toEqual({ hello: 'world' })
  })

  it('HTTP 401 转成 UnauthorizedError，让调用方能区分「未登录」与普通失败', async () => {
    fetchMock.mockResolvedValue(new Response('', { status: 401 }))
    await expect(request('/api/x')).rejects.toBeInstanceOf(UnauthorizedError)
  })

  it('业务码非 0 时抛出后端给的 msg', async () => {
    fetchMock.mockResolvedValue(bizError(210001, '库存不足'))
    await expect(request('/api/x')).rejects.toThrow('库存不足')
  })

  it('业务码非 0 但 msg 为空时用兜底文案，不抛出空信息', async () => {
    fetchMock.mockResolvedValue(bizError(210001, null))
    await expect(request('/api/x')).rejects.toThrow(t('api.requestFailed'))
  })

  it('网络层失败时抛本地化文案，不把原始英文报错透给用户', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'))
    await expect(request('/api/x')).rejects.toThrow(t('api.network'))
  })

  it('响应体不是合法 JSON 时也按网络异常处理', async () => {
    fetchMock.mockResolvedValue(new Response('<html>502</html>', { status: 200 }))
    await expect(request('/api/x')).rejects.toThrow(t('api.network'))
  })

  it('业务成功但 data 为 null 时原样返回 null（如仅表示操作成功的接口）', async () => {
    fetchMock.mockResolvedValue(ok(null))
    await expect(request('/api/x')).resolves.toBeNull()
  })

  it('默认带上 JSON 内容类型与当前会话语言，供后端返回本地化文案', async () => {
    fetchMock.mockResolvedValue(ok(null))
    await request('/api/x')

    const [, init] = lastCall()
    expect(init.headers).toMatchObject({
      'Content-Type': 'application/json',
      'Accept-Language': locale,
    })
  })

  it('调用方传入的 headers 可覆盖默认头', async () => {
    fetchMock.mockResolvedValue(ok(null))
    await request('/api/x', { headers: { 'Accept-Language': 'ja-JP' } })

    const [, init] = lastCall()
    expect(init.headers).toMatchObject({ 'Accept-Language': 'ja-JP' })
  })

  it('透传调用方给的 method 与 body', async () => {
    fetchMock.mockResolvedValue(ok(null))
    await request('/api/x', { method: 'DELETE', body: '{"a":1}' })

    const [, init] = lastCall()
    expect(init.method).toBe('DELETE')
    expect(init.body).toBe('{"a":1}')
  })
})

describe('各接口的请求形状', () => {
  beforeEach(() => {
    fetchMock.mockResolvedValue(ok(null))
  })

  it('fetchGroups 走 GET /api/groups', async () => {
    await fetchGroups()
    expect(lastCall()[0]).toBe('/api/groups')
    expect(lastCall()[1].method).toBeUndefined()
  })

  it('fetchProduct 走 GET /api/products/{id}', async () => {
    await fetchProduct(42)
    expect(lastCall()[0]).toBe('/api/products/42')
    expect(lastCall()[1].method).toBeUndefined()
  })

  it('createOrder 提交固定数量 1（骨架阶段约定）', async () => {
    await createOrder(42)
    const [path, init] = lastCall()
    expect(path).toBe('/api/orders')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual({ productId: 42, quantity: 1 })
  })

  it('fetchMe 走 GET /api/me', async () => {
    await fetchMe()
    expect(lastCall()[0]).toBe('/api/me')
  })

  it('updateMyProfile 把昵称与语言一次性 PUT 上去，避免半截生效', async () => {
    await updateMyProfile('小明', 'en-US')
    const [path, init] = lastCall()
    expect(path).toBe('/api/me')
    expect(init.method).toBe('PUT')
    expect(JSON.parse(init.body as string)).toEqual({ nickname: '小明', locale: 'en-US' })
  })

  it('updateMyLocale 只提交语言，不误带昵称把已有昵称冲掉', async () => {
    await updateMyLocale('zh-CN')
    const [path, init] = lastCall()
    expect(path).toBe('/api/me/locale')
    expect(init.method).toBe('PUT')
    expect(JSON.parse(init.body as string)).toEqual({ locale: 'zh-CN' })
  })

  it('fetchMyOrders 走 GET /api/orders', async () => {
    await fetchMyOrders()
    expect(lastCall()[0]).toBe('/api/orders')
  })

  it('fetchOrderDetail 把订单号拼进路径', async () => {
    await fetchOrderDetail('MP20260801001')
    expect(lastCall()[0]).toBe('/api/orders/MP20260801001')
  })

  it('fetchCheckoutInfo 走 GET /api/payment/checkout-info', async () => {
    await fetchCheckoutInfo()
    expect(lastCall()[0]).toBe('/api/payment/checkout-info')
  })

  it('createPaymentIntent 用 POST（懒创建/复用支付意图，非幂等读）', async () => {
    await createPaymentIntent('MP001')
    const [path, init] = lastCall()
    expect(path).toBe('/api/payment/orders/MP001/intent')
    expect(init.method).toBe('POST')
  })

  it('verifyOrder 把订单号放在请求体里', async () => {
    await verifyOrder('MP001')
    const [path, init] = lastCall()
    expect(path).toBe('/api/payment/orders/verify')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual({ orderNo: 'MP001' })
  })

  it('cancelOrder 把订单号拼进路径并用 POST', async () => {
    await cancelOrder('MP001')
    const [path, init] = lastCall()
    expect(path).toBe('/api/payment/orders/MP001/cancel')
    expect(init.method).toBe('POST')
  })
})

describe('formatPrice', () => {
  it('美分转美元并固定两位小数', () => {
    expect(formatPrice(1999)).toBe('$19.99')
  })

  it('整元金额补齐两位小数', () => {
    expect(formatPrice(100000)).toBe('$1000.00')
  })

  it('零元不显示为空', () => {
    expect(formatPrice(0)).toBe('$0.00')
  })

  it('不足一角的金额前面补零', () => {
    expect(formatPrice(5)).toBe('$0.05')
  })
})
