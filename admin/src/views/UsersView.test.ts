import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import UsersView from './UsersView.vue'
import { fetchAdminUsers, type AdminUser, type PageResult } from '../api-admin'

vi.mock('../api-admin', () => ({ fetchAdminUsers: vi.fn() }))

const fetchUsersMock = vi.mocked(fetchAdminUsers)

let wrapper: VueWrapper | null = null

function user(overrides: Partial<AdminUser> = {}): AdminUser {
  return {
    id: 1,
    email: 'buyer@example.com',
    nickname: '小明',
    avatarUrl: null,
    role: 'USER',
    orderCount: 3,
    createdAt: '2026-08-01T13:45:00Z',
    ...overrides,
  }
}

function page(records: AdminUser[], total = records.length): PageResult<AdminUser> {
  return { records, total, page: 1, size: 20 }
}

async function render(result: PageResult<AdminUser> = page([user()])) {
  fetchUsersMock.mockResolvedValue(result)
  wrapper = mount(UsersView)
  await flushPromises()
  return wrapper
}

function pager() {
  return wrapper!.findAll('.admin-pager button')
}

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
})

describe('列表加载', () => {
  it('首屏按第一页、每页 20 人拉取', async () => {
    await render()

    expect(fetchUsersMock).toHaveBeenCalledWith(1, 20)
  })

  it('渲染用户的关键字段', async () => {
    const w = await render(page([user()]))
    const row = w.find('tbody tr').text()

    expect(row).toContain('buyer@example.com')
    expect(row).toContain('小明')
    expect(row).toContain('3')
    expect(row).toContain('2026/08/01 13:45')
  })

  it('管理员标出角色徽标，普通用户不抢眼', async () => {
    const admin = await render(page([user({ role: 'ADMIN' })]))
    expect(admin.find('.role-badge').text()).toBe('管理员')
    admin.unmount()

    const normal = await render(page([user({ role: 'USER' })]))
    expect(normal.find('.role-badge').exists()).toBe(false)
    expect(normal.find('tbody tr').text()).toContain('普通用户')
  })

  it('有头像时渲染头像图，没有时用昵称首字兜底', async () => {
    const withAvatar = await render(page([user({ avatarUrl: 'https://cdn.example.com/a.png' })]))
    expect(withAvatar.find('img.avatar').attributes('src')).toBe('https://cdn.example.com/a.png')
    withAvatar.unmount()

    const withoutAvatar = await render(page([user({ avatarUrl: null, nickname: '小明' })]))
    expect(withoutAvatar.find('.avatar-fallback').text()).toBe('小')
  })

  it('连昵称也没有时头像退到邮箱首字，昵称列显示占位符', async () => {
    const w = await render(page([user({ avatarUrl: null, nickname: null, email: 'zoe@example.com' })]))

    expect(w.find('.avatar-fallback').text()).toBe('z')
    expect(w.find('.user-cell').text()).toContain('—')
  })

  it('加载失败时显示错误信息，不渲染表格', async () => {
    fetchUsersMock.mockRejectedValue(new Error('查询用户失败'))
    wrapper = mount(UsersView)
    await flushPromises()

    expect(wrapper.find('.admin-hint.error').text()).toBe('查询用户失败')
    expect(wrapper.find('.admin-table').exists()).toBe(false)
  })

  it('一个用户都没有时提示等人来登录', async () => {
    const w = await render(page([]))

    expect(w.find('.admin-hint').text()).toContain('有人在商城登录后会出现在这里')
  })

  it('页头说明角色只读，避免有人在这里找改权限的入口', async () => {
    const w = await render(page([user()], 12))

    expect(w.find('.page-facts').text()).toContain('共 12 人')
    expect(w.find('.page-facts').text()).toContain('角色只读')
  })
})

describe('分页', () => {
  /** 51 人 → 3 页 */
  async function renderPaged() {
    return render(page([user()], 51))
  }

  it('按总数与页大小算出总页数', async () => {
    const w = await renderPaged()

    expect(w.find('.admin-pager .info').text()).toContain('/ 3 页')
  })

  it('第一页时「上一页」禁用', async () => {
    await renderPaged()

    expect(pager()[0].attributes('disabled')).toBeDefined()
  })

  it('翻页后按新页码重新拉取', async () => {
    await renderPaged()

    await pager()[1].trigger('click')
    await flushPromises()

    expect(fetchUsersMock).toHaveBeenLastCalledWith(2, 20)
  })

  it('翻到最后一页时「下一页」禁用，不会翻出界', async () => {
    await renderPaged()

    await pager()[1].trigger('click')
    await flushPromises()
    await pager()[1].trigger('click')
    await flushPromises()

    expect(pager()[1].attributes('disabled')).toBeDefined()
    expect(fetchUsersMock).toHaveBeenLastCalledWith(3, 20)
  })

  it('结果为 0 人时总页数仍按 1 页算', async () => {
    const w = await render(page([], 0))

    expect(w.find('.admin-pager .info').text()).toContain('/ 1 页')
  })
})
