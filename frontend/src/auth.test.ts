import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { currentUser, gotoLogin, gotoLogout, loadCurrentUser } from './auth'
import { fetchMe, updateMyLocale, UnauthorizedError, type Me } from './api'
import { locale } from './i18n'

vi.mock('./api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api')>()
  return { ...actual, fetchMe: vi.fn(), updateMyLocale: vi.fn() }
})

const fetchMeMock = vi.mocked(fetchMe)
const updateMyLocaleMock = vi.mocked(updateMyLocale)

/** 当前会话语言的另一种取值，用于构造「服务端偏好与本地不一致」的场景 */
const otherLocale = locale === 'zh-CN' ? 'en-US' : 'zh-CN'

function me(overrides: Partial<Me> = {}): Me {
  return {
    id: 1,
    email: 'a@example.com',
    nickname: '小明',
    avatarUrl: null,
    locale: null,
    ...overrides,
  }
}

/** 用可观测的假 location 替换全局 location，避免用例真的触发页面跳转 */
function stubLocation(init: { pathname?: string; search?: string; hash?: string } = {}) {
  const fake = {
    pathname: init.pathname ?? '/',
    search: init.search ?? '',
    hash: init.hash ?? '',
    origin: 'http://localhost:5173',
    href: 'http://localhost:5173/',
    reload: vi.fn(),
  }
  vi.stubGlobal('location', fake)
  return fake
}

beforeEach(() => {
  currentUser.value = null
  updateMyLocaleMock.mockResolvedValue(null)
})

afterEach(() => {
  vi.unstubAllGlobals()
  localStorage.clear()
})

describe('loadCurrentUser', () => {
  it('拉取成功时写入当前用户', async () => {
    const user = me({ locale })
    fetchMeMock.mockResolvedValue(user)

    await loadCurrentUser()

    expect(currentUser.value).toEqual(user)
  })

  it('401 按游客处理，不抛异常阻塞页面挂载', async () => {
    fetchMeMock.mockRejectedValue(new UnauthorizedError())

    await expect(loadCurrentUser()).resolves.toBeUndefined()

    expect(currentUser.value).toBeNull()
  })

  it('网络失败同样按游客处理，且不去同步语言偏好', async () => {
    fetchMeMock.mockRejectedValue(new Error('boom'))

    await loadCurrentUser()

    expect(currentUser.value).toBeNull()
    expect(updateMyLocaleMock).not.toHaveBeenCalled()
  })
})

describe('登录后的语言偏好对齐', () => {
  it('服务端与本次会话语言一致时什么都不做', async () => {
    const fake = stubLocation()
    fetchMeMock.mockResolvedValue(me({ locale }))

    await loadCurrentUser()

    expect(updateMyLocaleMock).not.toHaveBeenCalled()
    expect(fake.reload).not.toHaveBeenCalled()
  })

  it('服务端存了不同语言时以服务端为准：写回本地并整页刷新', async () => {
    const fake = stubLocation()
    fetchMeMock.mockResolvedValue(me({ locale: otherLocale }))

    await loadCurrentUser()

    expect(localStorage.getItem('locale')).toBe(otherLocale)
    expect(fake.reload).toHaveBeenCalledOnce()
    expect(updateMyLocaleMock).not.toHaveBeenCalled()
  })

  it('服务端没有偏好时把本次会话语言补写上去，且不刷新页面', async () => {
    const fake = stubLocation()
    fetchMeMock.mockResolvedValue(me({ locale: null }))

    await loadCurrentUser()

    expect(updateMyLocaleMock).toHaveBeenCalledWith(locale)
    expect(fake.reload).not.toHaveBeenCalled()
  })

  it('服务端存的是不支持的语言时按「没有偏好」处理，用本地偏好覆盖', async () => {
    fetchMeMock.mockResolvedValue(me({ locale: 'ja-JP' }))

    await loadCurrentUser()

    expect(updateMyLocaleMock).toHaveBeenCalledWith(locale)
  })

  it('补写偏好失败时静默吞掉，不影响页面加载', async () => {
    fetchMeMock.mockResolvedValue(me({ locale: null }))
    updateMyLocaleMock.mockRejectedValue(new Error('网络异常'))

    await expect(loadCurrentUser()).resolves.toBeUndefined()
  })
})

describe('登录登出跳转', () => {
  it('gotoLogin 带上当前路径与查询串，登录后由后端原样回跳', () => {
    const fake = stubLocation({ pathname: '/orders/MP001', search: '?from=email' })

    gotoLogin()

    expect(fake.href).toBe(
      `/auth/login?redirect=${encodeURIComponent('/orders/MP001?from=email')}`,
    )
  })

  it('gotoLogin 不带 hash（后端回跳只认 path + query）', () => {
    const fake = stubLocation({ pathname: '/pay/MP001', hash: '#qr' })

    gotoLogin()

    expect(fake.href).toBe(`/auth/login?redirect=${encodeURIComponent('/pay/MP001')}`)
    expect(fake.href).not.toContain('qr')
  })

  it('gotoLogin 对路径做 URL 编码，防止查询串被截断', () => {
    const fake = stubLocation({ pathname: '/orders', search: '?q=a&b=c' })

    gotoLogin()

    expect(fake.href).toBe('/auth/login?redirect=%2Forders%3Fq%3Da%26b%3Dc')
  })

  it('gotoLogout 整页跳到后端登出入口（清会话 + 单点登出）', () => {
    const fake = stubLocation()

    gotoLogout()

    expect(fake.href).toBe('/auth/logout')
  })
})
