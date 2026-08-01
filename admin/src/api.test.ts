import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchMe, formatPrice, request, UnauthorizedError } from './api'

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

function lastCall(): [string, RequestInit] {
  return fetchMock.mock.calls[fetchMock.mock.calls.length - 1] as [string, RequestInit]
}

describe('request 统一封装', () => {
  it('业务码为 0 时取出 data 返回', async () => {
    fetchMock.mockResolvedValue(ok({ id: 1 }))
    await expect(request('/api/admin/x')).resolves.toEqual({ id: 1 })
  })

  it('HTTP 401 转成 UnauthorizedError，让调用方能区分「未登录」与普通失败', async () => {
    fetchMock.mockResolvedValue(new Response('', { status: 401 }))
    await expect(request('/api/admin/x')).rejects.toBeInstanceOf(UnauthorizedError)
  })

  it('业务码非 0 时抛出后端给的 msg', async () => {
    fetchMock.mockResolvedValue(bizError(310002, '分组下还有商品，不能删除'))
    await expect(request('/api/admin/x')).rejects.toThrow('分组下还有商品，不能删除')
  })

  it('业务码非 0 但 msg 为空时用兜底文案', async () => {
    fetchMock.mockResolvedValue(bizError(310002, null))
    await expect(request('/api/admin/x')).rejects.toThrow('请求失败')
  })

  it('网络层失败时抛中文文案，不把原始英文报错透给用户', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'))
    await expect(request('/api/admin/x')).rejects.toThrow('网络异常，请稍后重试')
  })

  it('响应体不是合法 JSON 时也按网络异常处理', async () => {
    fetchMock.mockResolvedValue(new Response('<html>502 Bad Gateway</html>', { status: 200 }))
    await expect(request('/api/admin/x')).rejects.toThrow('网络异常，请稍后重试')
  })

  it('业务成功但 data 为 null 时原样返回 null（如删除类接口）', async () => {
    fetchMock.mockResolvedValue(ok(null))
    await expect(request('/api/admin/x')).resolves.toBeNull()
  })

  it('语言头恒为 zh-CN：管理端不做双语，后端状态文案随之固定中文', async () => {
    fetchMock.mockResolvedValue(ok(null))
    await request('/api/admin/x')

    expect(lastCall()[1].headers).toMatchObject({
      'Content-Type': 'application/json',
      'Accept-Language': 'zh-CN',
    })
  })

  it('透传调用方给的 method 与 body', async () => {
    fetchMock.mockResolvedValue(ok(null))
    await request('/api/admin/x', { method: 'DELETE', body: '{"a":1}' })

    const [, init] = lastCall()
    expect(init.method).toBe('DELETE')
    expect(init.body).toBe('{"a":1}')
  })
})

describe('fetchMe', () => {
  it('走 GET /api/me', async () => {
    fetchMock.mockResolvedValue(ok({ id: 1, email: 'a@b.c', nickname: null, avatarUrl: null, admin: true }))

    await expect(fetchMe()).resolves.toMatchObject({ admin: true })
    expect(lastCall()[0]).toBe('/api/me')
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
