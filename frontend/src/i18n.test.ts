import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * i18n.ts 在模块加载时就解析并固化了本次会话语言，
 * 所以每个用例都要先摆好 localStorage / navigator.language，再重置模块图重新 import。
 */
async function loadI18n(env: {
  saved?: string | null
  browserLang?: string
}): Promise<typeof import('./i18n')> {
  localStorage.clear()
  if (env.saved) {
    localStorage.setItem('locale', env.saved)
  }
  vi.spyOn(navigator, 'language', 'get').mockReturnValue(env.browserLang ?? 'en-US')
  vi.resetModules()
  return import('./i18n')
}

describe('会话语言解析', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  afterEach(() => {
    vi.restoreAllMocks()
    localStorage.clear()
    vi.resetModules()
  })

  it('localStorage 里的合法偏好优先于浏览器语言', async () => {
    const { locale } = await loadI18n({ saved: 'zh-CN', browserLang: 'en-US' })
    expect(locale).toBe('zh-CN')
  })

  it('localStorage 里存的是英文偏好时也照用，不被浏览器中文覆盖', async () => {
    const { locale } = await loadI18n({ saved: 'en-US', browserLang: 'zh-CN' })
    expect(locale).toBe('en-US')
  })

  it('localStorage 存了不支持的语言时视为无偏好，回落到浏览器语言', async () => {
    const { locale } = await loadI18n({ saved: 'ja-JP', browserLang: 'en-GB' })
    expect(locale).toBe('en-US')
  })

  it('无偏好且浏览器是任意 en 变体时判为英文', async () => {
    const { locale } = await loadI18n({ browserLang: 'EN-AU' })
    expect(locale).toBe('en-US')
  })

  it('无偏好且浏览器非英文时回退中文', async () => {
    const { locale } = await loadI18n({ browserLang: 'fr-FR' })
    expect(locale).toBe('zh-CN')
  })

  it('把解析结果写到 <html lang>，供屏幕阅读器与浏览器判断页面语言', async () => {
    await loadI18n({ saved: 'zh-CN' })
    expect(document.documentElement.lang).toBe('zh-CN')
  })
})

describe('语言切换', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    localStorage.clear()
    vi.resetModules()
  })

  it('storeLocale 只写偏好，不刷新页面', async () => {
    const { storeLocale } = await loadI18n({ saved: 'zh-CN' })
    const reload = vi.spyOn(location, 'reload').mockImplementation(() => undefined)

    storeLocale('en-US')

    expect(localStorage.getItem('locale')).toBe('en-US')
    expect(reload).not.toHaveBeenCalled()
  })

  it('setLocale 切到新语言时写偏好并整页刷新', async () => {
    const { setLocale } = await loadI18n({ saved: 'zh-CN' })
    const reload = vi.spyOn(location, 'reload').mockImplementation(() => undefined)

    setLocale('en-US')

    expect(localStorage.getItem('locale')).toBe('en-US')
    expect(reload).toHaveBeenCalledOnce()
  })

  it('setLocale 传入当前语言时直接返回，不做无谓刷新', async () => {
    const { setLocale } = await loadI18n({ saved: 'zh-CN' })
    const reload = vi.spyOn(location, 'reload').mockImplementation(() => undefined)

    setLocale('zh-CN')

    expect(reload).not.toHaveBeenCalled()
  })
})

describe('文案表', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    localStorage.clear()
    vi.resetModules()
  })

  it('组件外取文案的 t 按会话语言返回对应语言的文案', async () => {
    const { t } = await loadI18n({ saved: 'zh-CN' })
    expect(t('api.network')).toBe('网络异常，请稍后重试')
  })

  it('中英文案表的键完全对齐，避免某一侧缺键时静默回退', async () => {
    const zh = (await import('./locales/zh-CN.json')).default
    const en = (await import('./locales/en-US.json')).default

    const flatten = (obj: Record<string, unknown>, prefix = ''): string[] =>
      Object.entries(obj).flatMap(([key, value]) =>
        typeof value === 'object' && value !== null
          ? flatten(value as Record<string, unknown>, `${prefix}${key}.`)
          : [`${prefix}${key}`],
      )

    expect(flatten(en).sort()).toEqual(flatten(zh).sort())
  })
})
