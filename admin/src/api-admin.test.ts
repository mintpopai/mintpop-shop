import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  createAdminGroup,
  createAdminProduct,
  deleteAdminGroup,
  fetchAdminDashboard,
  fetchAdminGroups,
  fetchAdminOrders,
  fetchAdminProducts,
  fetchAdminShipments,
  fetchAdminUsers,
  setAdminProductOnSale,
  shipAdminOrder,
  updateAdminGroup,
  updateAdminProduct,
  type AdminProductUpsert,
} from './api-admin'
import { request } from './api'

vi.mock('./api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api')>()
  return { ...actual, request: vi.fn() }
})

const requestMock = vi.mocked(request)

beforeEach(() => {
  requestMock.mockResolvedValue(null as never)
})

/** 取本次 request 调用的 (path, init) */
function lastCall(): [string, RequestInit | undefined] {
  return requestMock.mock.calls[requestMock.mock.calls.length - 1] as [string, RequestInit | undefined]
}

/** 取本次调用请求体解析后的对象 */
function lastBody(): unknown {
  return JSON.parse(lastCall()[1]?.body as string)
}

function productUpsert(overrides: Partial<AdminProductUpsert> = {}): AdminProductUpsert {
  return {
    groupId: 1,
    nameZh: '会员月卡',
    nameEn: 'Monthly',
    descriptionZh: '',
    descriptionEn: '',
    badgeZh: '',
    badgeEn: '',
    accent: 'MINT',
    priceCents: 1999,
    imageUrl: '',
    onSale: true,
    ...overrides,
  }
}

describe('概览', () => {
  it('fetchAdminDashboard 走 GET /api/admin/dashboard', async () => {
    await fetchAdminDashboard()

    const [path, init] = lastCall()
    expect(path).toBe('/api/admin/dashboard')
    // 读接口不传 init，走 request 的默认 GET
    expect(init).toBeUndefined()
  })
})

describe('商品接口', () => {
  it('不传分组时拉全部商品，不带查询串', async () => {
    await fetchAdminProducts()
    expect(lastCall()[0]).toBe('/api/admin/products')
  })

  it('传分组时按分组过滤', async () => {
    await fetchAdminProducts(7)
    expect(lastCall()[0]).toBe('/api/admin/products?groupId=7')
  })

  it('分组 id 为 0 时视为「全部」，不拼出 groupId=0 这种无效过滤', async () => {
    await fetchAdminProducts(0)
    expect(lastCall()[0]).toBe('/api/admin/products')
  })

  it('新增商品用 POST，整个表单进请求体', async () => {
    await createAdminProduct(productUpsert({ nameZh: '新商品' }))

    const [path, init] = lastCall()
    expect(path).toBe('/api/admin/products')
    expect(init?.method).toBe('POST')
    expect(lastBody()).toMatchObject({ nameZh: '新商品', priceCents: 1999 })
  })

  it('编辑商品用 PUT 并把 id 拼进路径', async () => {
    await updateAdminProduct(42, productUpsert())

    const [path, init] = lastCall()
    expect(path).toBe('/api/admin/products/42')
    expect(init?.method).toBe('PUT')
  })

  it('上下架是独立的 PUT 子资源，只提交 onSale', async () => {
    await setAdminProductOnSale(42, false)

    const [path, init] = lastCall()
    expect(path).toBe('/api/admin/products/42/on-sale')
    expect(init?.method).toBe('PUT')
    expect(lastBody()).toEqual({ onSale: false })
  })
})

describe('分组接口', () => {
  it('fetchAdminGroups 走 GET /api/admin/groups', async () => {
    await fetchAdminGroups()
    expect(lastCall()[0]).toBe('/api/admin/groups')
  })

  it('新增分组用 POST', async () => {
    await createAdminGroup({ nameZh: '订阅', nameEn: 'Subscription', sortOrder: 10 })

    const [path, init] = lastCall()
    expect(path).toBe('/api/admin/groups')
    expect(init?.method).toBe('POST')
    expect(lastBody()).toEqual({ nameZh: '订阅', nameEn: 'Subscription', sortOrder: 10 })
  })

  it('编辑分组用 PUT 并把 id 拼进路径', async () => {
    await updateAdminGroup(3, { nameZh: '订阅', nameEn: '', sortOrder: 20 })

    const [path, init] = lastCall()
    expect(path).toBe('/api/admin/groups/3')
    expect(init?.method).toBe('PUT')
  })

  it('删除分组用 DELETE', async () => {
    await deleteAdminGroup(3)

    const [path, init] = lastCall()
    expect(path).toBe('/api/admin/groups/3')
    expect(init?.method).toBe('DELETE')
  })
})

describe('订单分页接口', () => {
  it('只有分页参数时不带上筛选条件', async () => {
    await fetchAdminOrders({ page: 1, size: 20 })
    expect(lastCall()[0]).toBe('/api/admin/orders?page=1&size=20')
  })

  it('带状态与关键词时一并拼进查询串', async () => {
    await fetchAdminOrders({ page: 2, size: 20, status: 'PAID', keyword: 'MP2026' })
    expect(lastCall()[0]).toBe('/api/admin/orders?page=2&size=20&status=PAID&keyword=MP2026')
  })

  it('空字符串的筛选条件不进查询串，避免后端按空值过滤', async () => {
    await fetchAdminOrders({ page: 1, size: 20, status: '', keyword: '' })
    expect(lastCall()[0]).toBe('/api/admin/orders?page=1&size=20')
  })

  it('关键词里的特殊字符做 URL 编码', async () => {
    await fetchAdminOrders({ page: 1, size: 20, keyword: 'a&b=c d' })
    expect(lastCall()[0]).toContain('keyword=a%26b%3Dc+d')
  })
})

describe('用户分页接口', () => {
  it('分页参数拼进查询串', async () => {
    await fetchAdminUsers(3, 50)
    expect(lastCall()[0]).toBe('/api/admin/users?page=3&size=50')
  })
})

describe('发货接口', () => {
  it('发货历史按订单号拉取', async () => {
    await fetchAdminShipments('MP20260801001')
    expect(lastCall()[0]).toBe('/api/admin/orders/MP20260801001/shipments')
  })

  it('首次发货只提交内容', async () => {
    await shipAdminOrder('MP001', { content: 'CDKEY-1234' })

    const [path, init] = lastCall()
    expect(path).toBe('/api/admin/orders/MP001/shipments')
    expect(init?.method).toBe('POST')
    expect(lastBody()).toEqual({ content: 'CDKEY-1234' })
  })

  it('重新发货把原因一并提交', async () => {
    await shipAdminOrder('MP001', { content: 'CDKEY-5678', reason: '上次发错卡密' })
    expect(lastBody()).toEqual({ content: 'CDKEY-5678', reason: '上次发错卡密' })
  })
})
