import { mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import App from './App.vue'
import { currentUser, gotoLogin, gotoLogout } from './auth'
import { showToast, toast } from './toast'
import type { Me } from './api'

vi.mock('./auth', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./auth')>()
  return { ...actual, gotoLogin: vi.fn(), gotoLogout: vi.fn() }
})

const gotoLoginMock = vi.mocked(gotoLogin)
const gotoLogoutMock = vi.mocked(gotoLogout)

const blank = { template: '<div class="stub-page" />' }
let router: Router
let wrapper: VueWrapper | null = null

function me(overrides: Partial<Me> = {}): Me {
  return {
    id: 1,
    email: 'admin@example.com',
    nickname: '店主',
    avatarUrl: null,
    admin: true,
    ...overrides,
  }
}

async function render(user: Me | null) {
  currentUser.value = user
  await router.push('/')
  await router.isReady()
  wrapper = mount(App, { global: { plugins: [router] } })
  return wrapper
}

beforeEach(() => {
  vi.useFakeTimers()
  toast.value = null
  router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', component: blank }],
  })
})

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  currentUser.value = null
  vi.useRealTimers()
})

describe('未登录', () => {
  it('渲染登录引导，不渲染后台', async () => {
    const w = await render(null)

    expect(w.find('.gate-title').text()).toBe('用管理员账号登录')
    expect(w.find('.admin-rail').exists()).toBe(false)
  })

  it('点登录跳后端登录入口', async () => {
    const w = await render(null)

    await w.find('.gate-btn').trigger('click')

    expect(gotoLoginMock).toHaveBeenCalledOnce()
  })
})

describe('已登录但不是管理员', () => {
  it('渲染无权限页，并写明当前登录的是哪个账号', async () => {
    const w = await render(me({ admin: false, email: 'user@example.com' }))

    expect(w.find('.gate-title').text()).toBe('这个账号没有后台权限')
    expect(w.find('.gate-text').text()).toContain('user@example.com')
  })

  it('不渲染任何后台页面——但这只是 UX，安全边界在后端拦截器', async () => {
    const w = await render(me({ admin: false }))

    expect(w.find('.admin-rail').exists()).toBe(false)
    expect(w.find('.stub-page').exists()).toBe(false)
  })

  it('给出退出登录的出口，方便换账号', async () => {
    const w = await render(me({ admin: false }))

    await w.find('.gate-btn').trigger('click')

    expect(gotoLogoutMock).toHaveBeenCalledOnce()
  })
})

describe('管理员', () => {
  it('渲染导航轨与工作区', async () => {
    const w = await render(me())

    expect(w.find('.admin-rail').exists()).toBe(true)
    expect(w.find('.stub-page').exists()).toBe(true)
    expect(w.find('.gate').exists()).toBe(false)
  })

  it('导航列出五个页面', async () => {
    const w = await render(me())

    expect(w.findAll('.rail-link').map((link) => link.text())).toEqual([
      '概览',
      '商品',
      '分组',
      '订单',
      '用户',
    ])
  })

  it('有头像时渲染头像图', async () => {
    const w = await render(me({ avatarUrl: 'https://cdn.example.com/a.png' }))

    expect(w.find('img.rail-avatar').attributes('src')).toBe('https://cdn.example.com/a.png')
  })

  it('没有头像时用昵称首字兜底', async () => {
    const w = await render(me({ avatarUrl: null, nickname: '店主' }))

    expect(w.find('.rail-avatar').text()).toBe('店')
  })

  it('连昵称也没有时退到邮箱首字', async () => {
    const w = await render(me({ avatarUrl: null, nickname: null, email: 'admin@example.com' }))

    expect(w.find('.rail-avatar').text()).toBe('a')
    expect(w.find('.rail-user-name').text()).toBe('admin@example.com')
  })

  it('点退出登录跳后端登出入口', async () => {
    const w = await render(me())

    await w.find('.rail-signout').trigger('click')

    expect(gotoLogoutMock).toHaveBeenCalledOnce()
  })
})

describe('全局 toast', () => {
  it('默认不渲染', async () => {
    const w = await render(me())

    expect(w.find('.toast').exists()).toBe(false)
  })

  it('弹出后带上类型样式与 status 语义，供屏幕阅读器播报', async () => {
    const w = await render(me())

    showToast('error', '请求失败')
    await w.vm.$nextTick()

    expect(w.find('.toast').classes()).toContain('error')
    expect(w.find('.toast').attributes('role')).toBe('status')
    expect(w.find('.toast').text()).toBe('请求失败')
  })

  it('未登录时也照常展示，登录失败之类的提示不会被闸门吞掉', async () => {
    const w = await render(null)

    showToast('success', '已保存')
    await w.vm.$nextTick()

    expect(w.find('.toast').exists()).toBe(true)
  })
})
