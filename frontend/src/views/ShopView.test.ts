import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import ShopView from './ShopView.vue'
import {
  createOrder,
  fetchGroups,
  UnauthorizedError,
  type GroupWithProducts,
  type Product,
} from '../api'
import { currentUser, gotoLogin } from '../auth'
import { i18n, t } from '../i18n'
import { toast } from '../toast'

vi.mock('../api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api')>()
  return { ...actual, fetchGroups: vi.fn(), createOrder: vi.fn() }
})

vi.mock('../auth', async () => {
  const { ref } = await import('vue')
  return { currentUser: ref(null), gotoLogin: vi.fn() }
})

const fetchGroupsMock = vi.mocked(fetchGroups)
const createOrderMock = vi.mocked(createOrder)
const gotoLoginMock = vi.mocked(gotoLogin)

const blank = { template: '<div />' }
let router: Router
let wrapper: VueWrapper | null = null

function product(id: number, name: string): Product {
  return {
    id,
    name,
    description: null,
    priceCents: 1999,
    imageUrl: null,
    badge: null,
    accent: 'MINT',
  }
}

function groups(): GroupWithProducts[] {
  return [
    { id: 1, name: 'AI 工具', products: [product(11, 'Claude Pro'), product(12, 'ChatGPT Plus')] },
    { id: 2, name: '会员', products: [product(21, 'Netflix')] },
    { id: 3, name: '预售', products: [] },
  ]
}

async function mountShop(data: GroupWithProducts[] = groups()) {
  fetchGroupsMock.mockResolvedValue(data)
  await router.push('/')
  await router.isReady()
  wrapper = mount(ShopView, { global: { plugins: [i18n, router] } })
  await flushPromises()
  return wrapper
}

function login() {
  currentUser.value = {
    id: 1,
    email: 'mint@mintpop.ai',
    nickname: null,
    avatarUrl: null,
    locale: null,
  }
}

beforeEach(() => {
  toast.value = null
  currentUser.value = null
  router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: blank },
      { path: '/products/:id', component: blank },
      { path: '/pay/:orderNo', component: blank },
    ],
  })
})

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  vi.restoreAllMocks()
})

describe('加载与分组', () => {
  it('加载中先显示占位，拉到数据后默认选中第一个分组并列出其商品', async () => {
    fetchGroupsMock.mockReturnValue(new Promise(() => undefined))
    await router.push('/')
    await router.isReady()
    wrapper = mount(ShopView, { global: { plugins: [i18n, router] } })
    expect(wrapper.find('.hint').text()).toBe(t('common.loading'))
    wrapper.unmount()

    const w = await mountShop()
    const pills = w.findAll('.pill')
    expect(pills.map((p) => p.text())).toEqual(['AI 工具', '会员', '预售'])
    expect(pills[0]!.classes('active')).toBe(true)
    expect(w.find('.group-count').text()).toBe(t('shop.productCount', { n: 2 }))
    expect(w.findAll('.buy-btn')).toHaveLength(2)
  })

  it('切换分组只显示该组商品；空分组显示空提示', async () => {
    const w = await mountShop()

    await w.findAll('.pill')[1]!.trigger('click')
    expect(w.findAll('.buy-btn')).toHaveLength(1)
    expect(w.find('.group-count').text()).toBe(t('shop.productCount', { n: 1 }))

    await w.findAll('.pill')[2]!.trigger('click')
    expect(w.findAll('.buy-btn')).toHaveLength(0)
    expect(w.find('.hint').text()).toBe(t('shop.emptyGroup'))
  })

  it('没有任何分组时不渲染分组栏与商品区，也不报错', async () => {
    const w = await mountShop([])
    expect(w.findAll('.pill')).toHaveLength(0)
    expect(w.find('.group-count').exists()).toBe(false)
    expect(w.find('.grid').exists()).toBe(false)
  })

  it('接口失败显示后端文案；非 Error 的异常回退通用文案', async () => {
    fetchGroupsMock.mockRejectedValue(new Error('服务暂不可用'))
    await router.push('/')
    await router.isReady()
    wrapper = mount(ShopView, { global: { plugins: [i18n, router] } })
    await flushPromises()
    expect(wrapper.find('.hint.error').text()).toBe('服务暂不可用')
    wrapper.unmount()

    fetchGroupsMock.mockRejectedValue('boom')
    wrapper = mount(ShopView, { global: { plugins: [i18n, router] } })
    await flushPromises()
    expect(wrapper.find('.hint.error').text()).toBe(t('common.loadFailed'))
  })
})

describe('购买', () => {
  it('游客点购买：提示先登录并跳登录，不创建订单', async () => {
    const w = await mountShop()
    await w.find('.buy-btn').trigger('click')
    await flushPromises()

    expect(toast.value).toEqual({ type: 'error', text: t('shop.loginRequired') })
    expect(gotoLoginMock).toHaveBeenCalledOnce()
    expect(createOrderMock).not.toHaveBeenCalled()
  })

  it('登录用户点购买：按商品 id 下单，成功后跳收银台', async () => {
    login()
    createOrderMock.mockResolvedValue({ orderNo: 'MP20260801001', amountCents: 1999 })
    const w = await mountShop()

    await w.findAll('.buy-btn')[1]!.trigger('click')
    await flushPromises()

    expect(createOrderMock).toHaveBeenCalledWith(12)
    expect(router.currentRoute.value.path).toBe('/pay/MP20260801001')
  })

  it('下单进行中只禁用被点的那张卡，结束后恢复', async () => {
    login()
    let resolve!: (v: { orderNo: string; amountCents: number }) => void
    createOrderMock.mockReturnValue(new Promise((r) => (resolve = r)))
    const w = await mountShop()

    await w.find('.buy-btn').trigger('click')
    const [first, second] = w.findAll('.buy-btn')
    expect(first!.attributes('disabled')).toBeDefined()
    expect(second!.attributes('disabled')).toBeUndefined()

    resolve({ orderNo: 'MP1', amountCents: 1999 })
    await flushPromises()
    expect(w.find('.buy-btn').attributes('disabled')).toBeUndefined()
  })

  it('会话过期（401）：提示重新登录并跳登录', async () => {
    login()
    createOrderMock.mockRejectedValue(new UnauthorizedError())
    const w = await mountShop()

    await w.find('.buy-btn').trigger('click')
    await flushPromises()

    expect(toast.value).toEqual({ type: 'error', text: t('shop.sessionExpired') })
    expect(gotoLoginMock).toHaveBeenCalledOnce()
    expect(router.currentRoute.value.path).toBe('/')
  })

  it('其它下单失败：提示后端文案，留在当前页', async () => {
    login()
    createOrderMock.mockRejectedValue(new Error('库存不足'))
    const w = await mountShop()

    await w.find('.buy-btn').trigger('click')
    await flushPromises()

    expect(toast.value).toEqual({ type: 'error', text: '库存不足' })
    expect(gotoLoginMock).not.toHaveBeenCalled()
    expect(router.currentRoute.value.path).toBe('/')
  })
})
