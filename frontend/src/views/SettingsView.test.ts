import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import SettingsView from './SettingsView.vue'
import { updateMyProfile, UnauthorizedError, type Me } from '../api'
import { currentUser, gotoLogin } from '../auth'
import { i18n, locale, t, type AppLocale } from '../i18n'
import { toast } from '../toast'

vi.mock('../api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api')>()
  return { ...actual, updateMyProfile: vi.fn() }
})

vi.mock('../auth', async () => {
  const { ref } = await import('vue')
  return { currentUser: ref(null), gotoLogin: vi.fn() }
})

const updateMyProfileMock = vi.mocked(updateMyProfile)
const gotoLoginMock = vi.mocked(gotoLogin)

/** 与本次会话语言相反的那一个，用来构造「改了语言」的场景 */
const otherLocale: AppLocale = locale === 'zh-CN' ? 'en-US' : 'zh-CN'
let wrapper: VueWrapper | null = null

function login(overrides: Partial<Me> = {}) {
  currentUser.value = {
    id: 1,
    email: 'mint@mintpop.ai',
    nickname: '薄荷猫',
    avatarUrl: null,
    locale,
    ...overrides,
  }
}

async function mountSettings() {
  wrapper = mount(SettingsView, { global: { plugins: [i18n] } })
  await flushPromises()
  return wrapper
}

function langButton(w: VueWrapper, target: AppLocale) {
  return w.findAll('.lang-option')[target === 'zh-CN' ? 0 : 1]!
}

async function setNickname(w: VueWrapper, value: string) {
  await w.find('#nickname').setValue(value)
}

beforeEach(() => {
  toast.value = null
  currentUser.value = null
  localStorage.clear()
})

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  vi.restoreAllMocks()
})

describe('进入页面', () => {
  it('游客直接跳登录', async () => {
    await mountSettings()
    expect(gotoLoginMock).toHaveBeenCalledOnce()
  })

  it('回填邮箱（只读）、昵称与服务端语言偏好；未改动时保存按钮禁用', async () => {
    login({ locale: otherLocale })
    const w = await mountSettings()

    expect(w.find('.readonly').text()).toBe('mint@mintpop.ai')
    expect((w.find('#nickname').element as HTMLInputElement).value).toBe('薄荷猫')
    expect(langButton(w, otherLocale).classes('active')).toBe(true)
    expect(langButton(w, locale).classes('active')).toBe(false)
    expect(w.find('.save-btn').attributes('disabled')).toBeDefined()
    expect(gotoLoginMock).not.toHaveBeenCalled()
  })

  it('服务端没存过语言（null / 非法值）时用本次会话语言；昵称为空时输入框为空', async () => {
    login({ nickname: null, locale: 'fr-FR' })
    const w = await mountSettings()
    expect((w.find('#nickname').element as HTMLInputElement).value).toBe('')
    expect(langButton(w, locale).classes('active')).toBe(true)
  })

  it('昵称输入框有 30 字上限', async () => {
    login()
    const w = await mountSettings()
    expect(w.find('#nickname').attributes('maxlength')).toBe('30')
  })
})

describe('改动判定', () => {
  it('改昵称或切语言后保存按钮启用；改回原值又禁用（首尾空格不算改动）', async () => {
    login()
    const w = await mountSettings()

    await setNickname(w, '新名字')
    expect(w.find('.save-btn').attributes('disabled')).toBeUndefined()

    await setNickname(w, '  薄荷猫  ')
    expect(w.find('.save-btn').attributes('disabled')).toBeDefined()

    await langButton(w, otherLocale).trigger('click')
    expect(w.find('.save-btn').attributes('disabled')).toBeUndefined()

    await langButton(w, locale).trigger('click')
    expect(w.find('.save-btn').attributes('disabled')).toBeDefined()
  })
})

describe('保存', () => {
  it('昵称为空（或全空格）：提示必填，不发请求', async () => {
    login()
    const w = await mountSettings()
    await setNickname(w, '   ')
    await w.find('.save-btn').trigger('click')
    await flushPromises()

    expect(w.find('.help.error').text()).toBe(t('settings.nicknameRequired'))
    expect(updateMyProfileMock).not.toHaveBeenCalled()
  })

  it('昵称超过 30 字：提示过长，不发请求', async () => {
    login()
    const w = await mountSettings()
    // maxlength 只约束键盘输入，粘贴/程序赋值仍可能超长，脚本侧必须再校验一次
    await setNickname(w, 'a'.repeat(31))
    await w.find('.save-btn').trigger('click')
    await flushPromises()

    expect(w.find('.help.error').text()).toBe(t('settings.nicknameTooLong', { max: 30 }))
    expect(updateMyProfileMock).not.toHaveBeenCalled()
  })

  it('只改昵称：提交去掉首尾空格的昵称与当前语言，成功后同步页头昵称、提示已保存、按钮回到禁用', async () => {
    login()
    updateMyProfileMock.mockResolvedValue(null)
    const w = await mountSettings()
    await setNickname(w, '  新名字  ')
    await w.find('.save-btn').trigger('click')
    await flushPromises()

    expect(updateMyProfileMock).toHaveBeenCalledWith('新名字', locale)
    expect(currentUser.value?.nickname).toBe('新名字')
    expect((w.find('#nickname').element as HTMLInputElement).value).toBe('新名字')
    expect(toast.value).toEqual({ type: 'success', text: t('settings.saved') })
    expect(w.find('.save-btn').attributes('disabled')).toBeDefined()
    expect(w.find('.help.error').exists()).toBe(false)
  })

  it('之前的校验错误在下一次通过校验的保存时清掉', async () => {
    login()
    updateMyProfileMock.mockResolvedValue(null)
    const w = await mountSettings()
    await setNickname(w, '')
    await w.find('.save-btn').trigger('click')
    await flushPromises()
    expect(w.find('.help.error').exists()).toBe(true)

    await setNickname(w, '合法昵称')
    await w.find('.save-btn').trigger('click')
    await flushPromises()
    expect(w.find('.help.error').exists()).toBe(false)
  })

  it('保存进行中按钮显示「保存中」并禁用，防止重复提交', async () => {
    login()
    let resolve!: (v: null) => void
    updateMyProfileMock.mockReturnValue(new Promise((r) => (resolve = r)))
    const w = await mountSettings()
    await setNickname(w, '新名字')
    await w.find('.save-btn').trigger('click')
    await flushPromises()

    expect(w.find('.save-btn').text()).toBe(t('settings.saving'))
    expect(w.find('.save-btn').attributes('disabled')).toBeDefined()

    resolve(null)
    await flushPromises()
    expect(w.find('.save-btn').text()).toBe(t('settings.save'))
  })

  it('改了语言：保存成功后写本地偏好并整页刷新，不弹 toast', async () => {
    login()
    updateMyProfileMock.mockResolvedValue(null)
    const reload = vi.spyOn(location, 'reload').mockImplementation(() => undefined)
    const w = await mountSettings()
    await langButton(w, otherLocale).trigger('click')
    await w.find('.save-btn').trigger('click')
    await flushPromises()

    expect(updateMyProfileMock).toHaveBeenCalledWith('薄荷猫', otherLocale)
    expect(currentUser.value?.locale).toBe(otherLocale)
    expect(localStorage.getItem('locale')).toBe(otherLocale)
    expect(reload).toHaveBeenCalledOnce()
    expect(toast.value).toBeNull()
  })

  it('401：跳登录，不提示、不改本地状态', async () => {
    login()
    updateMyProfileMock.mockRejectedValue(new UnauthorizedError())
    const w = await mountSettings()
    await setNickname(w, '新名字')
    await w.find('.save-btn').trigger('click')
    await flushPromises()

    expect(gotoLoginMock).toHaveBeenCalledOnce()
    expect(toast.value).toBeNull()
    expect(currentUser.value?.nickname).toBe('薄荷猫')
  })

  it('其它失败：toast 提示后端文案，页头昵称不变，按钮仍可再次保存', async () => {
    login()
    updateMyProfileMock.mockRejectedValue(new Error('昵称含敏感词'))
    const w = await mountSettings()
    await setNickname(w, '新名字')
    await w.find('.save-btn').trigger('click')
    await flushPromises()

    expect(toast.value).toEqual({ type: 'error', text: '昵称含敏感词' })
    expect(currentUser.value?.nickname).toBe('薄荷猫')
    expect(w.find('.save-btn').attributes('disabled')).toBeUndefined()
  })
})
