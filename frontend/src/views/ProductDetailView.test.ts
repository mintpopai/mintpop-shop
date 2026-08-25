// happy-dom 下 DOMPurify 的净化结果不可信（<p> 被吃掉、<script> 反而留下），
// 净化这条断言必须跑在实现更完整的 jsdom 上，否则测的是环境 bug 而不是我们的代码
// @vitest-environment jsdom
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import ProductDetailView from './ProductDetailView.vue'
import { createOrder, fetchProduct, UnauthorizedError, type ProductDetail } from '../api'
import { currentUser, gotoLogin } from '../auth'
import { i18n, t } from '../i18n'
import { toast } from '../toast'

vi.mock('../api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api')>()
  return { ...actual, fetchProduct: vi.fn(), createOrder: vi.fn() }
})

vi.mock('../auth', async () => {
  const { ref } = await import('vue')
  return { currentUser: ref(null), gotoLogin: vi.fn() }
})

const fetchProductMock = vi.mocked(fetchProduct)
const createOrderMock = vi.mocked(createOrder)
const gotoLoginMock = vi.mocked(gotoLogin)

const blank = { template: '<div />' }
let router: Router
let wrapper: VueWrapper | null = null

function detail(overrides: Partial<ProductDetail> = {}): ProductDetail {
  return {
    id: 42,
    name: '薄荷猫手办',
    description: '官方正规渠道',
    detail: '<h2>产品参数</h2><p>高约 12cm</p>',
    priceCents: 5900,
    imageUrl: null,
    badge: '旗舰',
    accent: 'MINT',
    ...overrides,
  }
}

async function mountView(overrides: Partial<ProductDetail> = {}) {
  fetchProductMock.mockResolvedValue(detail(overrides))
  await router.push('/products/42')
  await router.isReady()
  wrapper = mount(ProductDetailView, { global: { plugins: [i18n, router] } })
  await flushPromises()
  return wrapper
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

describe('商品详情加载', () => {
  it('按路由上的商品 ID 拉取详情', async () => {
    await mountView()

    expect(fetchProductMock).toHaveBeenCalledWith(42)
  })

  it('展示商品名、价格、角标与短描述', async () => {
    const w = await mountView()

    expect(w.find('.name').text()).toBe('薄荷猫手办')
    expect(w.find('.price').text()).toBe('$59.00')
    expect(w.find('.badge').text()).toBe('旗舰')
    expect(w.find('.summary').text()).toBe('官方正规渠道')
  })

  it('加载失败时展示错误文案，不渲染商品区', async () => {
    fetchProductMock.mockRejectedValue(new Error('商品不存在'))
    await router.push('/products/42')
    wrapper = mount(ProductDetailView, { global: { plugins: [i18n, router] } })
    await flushPromises()

    expect(wrapper.find('.hint.error').text()).toBe('商品不存在')
    expect(wrapper.find('.name').exists()).toBe(false)
  })
})

describe('富文本详情渲染', () => {
  it('把后端下发的详情 HTML 渲染成真实结构，而不是转义文本', async () => {
    const w = await mountView()
    const rich = w.find('.rich-content')

    expect(rich.find('h2').text()).toBe('产品参数')
    expect(rich.find('p').text()).toBe('高约 12cm')
  })

  it('详情里的脚本与事件属性被净化掉，脏数据也炸不了页面', async () => {
    const w = await mountView({
      detail: '<p>正文</p><script>window.__pwned = 1</script><img src="x" onerror="window.__pwned = 1">',
    })
    const html = w.find('.rich-content').html()

    expect(html).not.toContain('<script')
    expect(html).not.toContain('onerror')
    expect(html).toContain('正文')
  })

  it('未配置详情时回退展示短描述，不留空白页', async () => {
    const w = await mountView({ detail: null })

    expect(w.find('.rich-content').exists()).toBe(false)
    expect(w.find('.detail-fallback').text()).toBe('官方正规渠道')
  })

  it('详情与短描述都为空时给一句占位文案', async () => {
    const w = await mountView({ detail: null, description: null })

    expect(w.find('.detail-fallback').text()).toBe(t('productDetail.noDetail'))
  })
})

describe('详情页下单', () => {
  it('游客点购买：提示登录并引导去账号中心，不发下单请求', async () => {
    const w = await mountView()

    await w.find('.buy-btn').trigger('click')
    await flushPromises()

    expect(createOrderMock).not.toHaveBeenCalled()
    expect(gotoLoginMock).toHaveBeenCalled()
    expect(toast.value?.text).toBe(t('shop.loginRequired'))
  })

  it('已登录点购买：下单成功后跳收银台', async () => {
    currentUser.value = { id: 1, email: 'a@b.c', nickname: null, avatarUrl: null, locale: null }
    createOrderMock.mockResolvedValue({ orderNo: 'MP001', amountCents: 5900 })
    const w = await mountView()
    const push = vi.spyOn(router, 'push')

    await w.find('.buy-btn').trigger('click')
    await flushPromises()

    expect(createOrderMock).toHaveBeenCalledWith(42)
    expect(push).toHaveBeenCalledWith('/pay/MP001')
  })

  it('会话过期时提示重新登录并引导去账号中心', async () => {
    currentUser.value = { id: 1, email: 'a@b.c', nickname: null, avatarUrl: null, locale: null }
    createOrderMock.mockRejectedValue(new UnauthorizedError())
    const w = await mountView()

    await w.find('.buy-btn').trigger('click')
    await flushPromises()

    expect(toast.value?.text).toBe(t('shop.sessionExpired'))
    expect(gotoLoginMock).toHaveBeenCalled()
  })
})
