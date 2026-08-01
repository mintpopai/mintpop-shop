import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { formatDateTime } from './datetime'

/**
 * 换时区/换语言重新导入模块：datetime.ts 在模块加载时就固化了 Intl 格式化器，
 * 只有重置模块图并重新 import 才能观察到不同环境下的渲染结果。
 * 用 vi.stubEnv 而非直接写 process.env，免得为测试给项目引入 node 类型依赖。
 */
async function importWith(options: { tz?: string; locale?: string }): Promise<
  typeof import('./datetime')
> {
  if (options.tz) {
    vi.stubEnv('TZ', options.tz)
  }
  if (options.locale) {
    localStorage.setItem('locale', options.locale)
  }
  vi.resetModules()
  return import('./datetime')
}

describe('formatDateTime', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  afterEach(() => {
    vi.unstubAllEnvs()
    localStorage.clear()
    vi.resetModules()
  })

  it('把后端的 ISO-8601 UTC 时刻渲染到分钟，不带秒', () => {
    expect(formatDateTime('2026-08-01T13:45:09Z')).toBe('08/01/2026, 13:45')
  })

  it('用 24 小时制，不出现 AM/PM', () => {
    const evening = formatDateTime('2026-01-05T23:07:00Z')
    expect(evening).toBe('01/05/2026, 23:07')
    expect(evening).not.toMatch(/[AP]M/i)
  })

  it('月/日/时/分统一补零到两位', () => {
    expect(formatDateTime('2026-03-09T04:05:00Z')).toBe('03/09/2026, 04:05')
  })

  it('按浏览器所在时区换算，而不是直接照搬 UTC 字面量', async () => {
    const { formatDateTime: formatInShanghai } = await importWith({ tz: 'Asia/Shanghai' })
    // 13:45Z 在东八区是次日之前的 21:45（+8 小时）
    expect(formatInShanghai('2026-08-01T13:45:09Z')).toBe('08/01/2026, 21:45')
  })

  it('跨日边界按目标时区进位到第二天', async () => {
    const { formatDateTime: formatInShanghai } = await importWith({ tz: 'Asia/Shanghai' })
    // 2026-08-01 20:30Z + 8h = 2026-08-02 04:30
    expect(formatInShanghai('2026-08-01T20:30:00Z')).toBe('08/02/2026, 04:30')
  })

  it('中文语言下使用中文日期书写顺序（年/月/日）', async () => {
    const { formatDateTime: formatZh } = await importWith({ tz: 'UTC', locale: 'zh-CN' })
    expect(formatZh('2026-08-01T13:45:09Z')).toBe('2026/08/01 13:45')
  })
})
