import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import App from './App.vue'
import { currentUser, gotoLogin, gotoLogout } from './auth'
import { i18n, locale, t } from './i18n'
import { toast } from './toast'

vi.mock('./auth', async () => {
  const { ref } = await import('vue')
  return { currentUser: ref(null), gotoLogin: vi.fn(), gotoLogout: vi.fn() }
})

const gotoLoginMock = vi.mocked(gotoLogin)
const gotoLogoutMock = vi.mocked(gotoLogout)

const blank = { template: '<div />' }
const otherLocale = locale === 'zh-CN' ? 'en-US' : 'zh-CN'
let router: Router
let wrapper: VueWrapper | null = null

async function mountApp(path = '/') {
  await router.push(path)
  await router.isReady()
  wrapper = mount(App, { attachTo: document.body, global: { plugins: [i18n, router] } })
  await flushPromises()
  return wrapper
}

function loginAs(nickname: string | null, avatarUrl: string | null = null) {
  currentUser.value = { id: 1, email: 'mint@mintpop.ai', nickname, avatarUrl, locale }
}

beforeEach(() => {
  toast.value = null
  currentUser.value = null
  localStorage.clear()
  history.replaceState(null, '', '/')
  router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: blank },
      { path: '/products/:id', component: blank },
      { path: '/orders', component: blank },
      { path: '/orders/:orderNo', component: blank },
      { path: '/settings', component: blank },
    ],
  })
})

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  document.body.innerHTML = ''
  vi.restoreAllMocks()
})

describe('游客顶栏', () => {
  it('显示语言切换与登录按钮，不显示用户菜单', async () => {
    const w = await mountApp()
    expect(w.find('.lang-btn').exists()).toBe(true)
    expect(w.find('.login-btn').text()).toBe(t('app.login'))
    expect(w.find('.user-menu').exists()).toBe(false)
  })

  it('点登录走统一登录入口', async () => {
    const w = await mountApp()
    await w.find('.login-btn').trigger('click')
    expect(gotoLoginMock).toHaveBeenCalledOnce()
  })

  it('语言按钮显示「另一种语言」，点击写入偏好并整页刷新', async () => {
    const reload = vi.spyOn(location, 'reload').mockImplementation(() => undefined)
    const w = await mountApp()

    expect(w.find('.lang-btn').text()).toBe(locale === 'zh-CN' ? 'EN' : '中文')
    await w.find('.lang-btn').trigger('click')

    expect(localStorage.getItem('locale')).toBe(otherLocale)
    expect(reload).toHaveBeenCalledOnce()
  })

  it('官网与联系方式外链按当前语言指向对应路径，且新开标签', async () => {
    const w = await mountApp()
    const links = w.findAll('.site-link')
    const prefix = locale === 'zh-CN' ? 'https://mintpop.ai/zh/' : 'https://mintpop.ai/'
    expect(links.map((l) => l.attributes('href'))).toEqual([prefix, `${prefix}contact/`])
    expect(links.every((l) => l.attributes('target') === '_blank')).toBe(true)
    expect(links.every((l) => l.attributes('rel') === 'noopener')).toBe(true)
  })
})

describe('登录用户菜单', () => {
  it('显示昵称，无头像时用首字符占位；语言与登录按钮都不再显示', async () => {
    loginAs('薄荷猫')
    const w = await mountApp()
    expect(w.find('.lang-btn').exists()).toBe(false)
    expect(w.find('.login-btn').exists()).toBe(false)
    expect(w.find('.nickname').text()).toBe('薄荷猫')
    expect(w.find('.user-trigger .avatar-fallback').text()).toBe('薄')
  })

  it('没昵称时用邮箱代替，展开后身份区不重复显示邮箱', async () => {
    loginAs(null)
    const w = await mountApp()
    expect(w.find('.nickname').text()).toBe('mint@mintpop.ai')
    await w.find('.user-trigger').trigger('click')
    expect(w.find('.identity-name').text()).toBe('mint@mintpop.ai')
    expect(w.find('.identity-email').exists()).toBe(false)
  })

  it('有头像时渲染 <img>，展开后身份区显示昵称 + 邮箱两行', async () => {
    loginAs('薄荷猫', 'https://cdn.example/a.png')
    const w = await mountApp()
    expect(w.find('.user-trigger img.avatar').attributes('src')).toBe('https://cdn.example/a.png')
    await w.find('.user-trigger').trigger('click')
    expect(w.find('.identity-name').text()).toBe('薄荷猫')
    expect(w.find('.identity-email').text()).toBe('mint@mintpop.ai')
  })

  it('点头像展开菜单（aria-expanded 同步），再点一次收起', async () => {
    loginAs('薄荷猫')
    const w = await mountApp()
    expect(w.find('.menu').exists()).toBe(false)
    expect(w.find('.user-trigger').attributes('aria-expanded')).toBe('false')

    await w.find('.user-trigger').trigger('click')
    expect(w.find('.menu').exists()).toBe(true)
    expect(w.find('.user-trigger').attributes('aria-expanded')).toBe('true')

    await w.find('.user-trigger').trigger('click')
    expect(w.find('.menu').exists()).toBe(false)
  })

  it('点菜单外面收起，点菜单内部不收起', async () => {
    loginAs('薄荷猫')
    const w = await mountApp()
    await w.find('.user-trigger').trigger('click')

    document
      .querySelector('.identity-text')!
      .dispatchEvent(new Event('pointerdown', { bubbles: true }))
    await flushPromises()
    expect(w.find('.menu').exists()).toBe(true)

    document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }))
    await flushPromises()
    expect(w.find('.menu').exists()).toBe(false)
  })

  it('按 Escape 收起菜单', async () => {
    loginAs('薄荷猫')
    const w = await mountApp()
    await w.find('.user-trigger').trigger('click')

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await flushPromises()
    expect(w.find('.menu').exists()).toBe(false)
  })

  it('菜单里有账号设置链接；点退出登录走登出入口', async () => {
    loginAs('薄荷猫')
    const w = await mountApp()
    await w.find('.user-trigger').trigger('click')

    expect(w.find('a.menu-item').attributes('href')).toBe('/settings')
    await w.find('.menu-item--logout').trigger('click')
    expect(gotoLogoutMock).toHaveBeenCalledOnce()
  })

  it('卸载后不再响应 document 事件（监听器已解绑）', async () => {
    loginAs('薄荷猫')
    const removeSpy = vi.spyOn(document, 'removeEventListener')
    const w = await mountApp()
    w.unmount()
    wrapper = null
    const removed = removeSpy.mock.calls.map((c) => c[0])
    expect(removed).toContain('pointerdown')
    expect(removed).toContain('keydown')
  })
})

describe('主导航高亮', () => {
  it.each([
    ['/', true, false],
    ['/products/42', true, false],
    ['/orders', false, true],
    ['/orders/MP1', false, true],
    ['/settings', false, false],
  ])('%s → 商店高亮=%s，我的订单高亮=%s', async (path, shop, orders) => {
    const w = await mountApp(path)
    const [shopItem, ordersItem] = w.findAll('.nav-item')
    expect(shopItem!.classes('active')).toBe(shop)
    expect(ordersItem!.classes('active')).toBe(orders)
  })
})

describe('登录失败回跳与 toast', () => {
  it('URL 带 login_error=1 时提示登录失败，并从地址栏清掉该参数、保留其它参数', async () => {
    history.replaceState(null, '', '/?login_error=1&keep=1')
    await mountApp()

    expect(toast.value).toEqual({ type: 'error', text: t('app.loginFailed') })
    expect(location.pathname + location.search).toBe('/?keep=1')
  })

  it('只有 login_error 一个参数时清成干净路径（不留孤零零的 ?）', async () => {
    history.replaceState(null, '', '/?login_error=1')
    await mountApp()
    expect(location.pathname + location.search).toBe('/')
  })

  it('没有 login_error 时不提示、不改 URL', async () => {
    history.replaceState(null, '', '/?keep=1')
    await mountApp()
    expect(toast.value).toBeNull()
    expect(location.pathname + location.search).toBe('/?keep=1')
  })

  it('全局 toast 按类型渲染，清空后消失', async () => {
    const w = await mountApp()
    toast.value = { type: 'success', text: '已保存' }
    await flushPromises()
    expect(w.find('.toast.success').text()).toBe('已保存')

    toast.value = null
    await flushPromises()
    expect(w.find('.toast').exists()).toBe(false)
  })
})
