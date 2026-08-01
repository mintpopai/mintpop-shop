import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { currentUser, gotoLogin, gotoLogout, loadCurrentUser } from './auth'
import { fetchMe, UnauthorizedError, type Me } from './api'

vi.mock('./api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api')>()
  return { ...actual, fetchMe: vi.fn() }
})

const fetchMeMock = vi.mocked(fetchMe)

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

/** 用可观测的假 location 替换全局 location，避免用例真的触发页面跳转 */
function stubLocation() {
  const fake = { href: 'http://localhost:5174/' }
  vi.stubGlobal('location', fake)
  return fake
}

beforeEach(() => {
  currentUser.value = null
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('loadCurrentUser', () => {
  it('拉取成功时写入当前用户', async () => {
    const user = me()
    fetchMeMock.mockResolvedValue(user)

    await loadCurrentUser()

    expect(currentUser.value).toEqual(user)
  })

  it('非管理员也照常写入，由 App.vue 决定渲染无权限页', async () => {
    fetchMeMock.mockResolvedValue(me({ admin: false }))

    await loadCurrentUser()

    expect(currentUser.value?.admin).toBe(false)
  })

  it('401 按未登录处理，不抛异常阻塞页面挂载', async () => {
    fetchMeMock.mockRejectedValue(new UnauthorizedError())

    await expect(loadCurrentUser()).resolves.toBeUndefined()

    expect(currentUser.value).toBeNull()
  })

  it('网络失败同样按未登录处理', async () => {
    fetchMeMock.mockRejectedValue(new Error('网络异常'))

    await loadCurrentUser()

    expect(currentUser.value).toBeNull()
  })

  it('重新拉取失败时清掉上一次的用户，不残留过期登录态', async () => {
    fetchMeMock.mockResolvedValue(me())
    await loadCurrentUser()

    fetchMeMock.mockRejectedValue(new UnauthorizedError())
    await loadCurrentUser()

    expect(currentUser.value).toBeNull()
  })
})

describe('登录登出跳转', () => {
  it('gotoLogin 整页跳到后端登录入口（管理端单一入口，不带回跳路径）', () => {
    const fake = stubLocation()

    gotoLogin()

    expect(fake.href).toBe('/auth/login')
  })

  it('gotoLogout 整页跳到后端登出入口（清会话 + 单点登出）', () => {
    const fake = stubLocation()

    gotoLogout()

    expect(fake.href).toBe('/auth/logout')
  })
})
