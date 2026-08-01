import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  formatDateTime,
  formatUtcDateTime,
  formatUtcTime,
  utcDate,
  utcDayProgress,
} from './datetime'

/**
 * 换时区重新导入：datetime.ts 在模块加载时就固化了 Intl 格式化器，
 * 只有重置模块图并重新 import 才能观察到不同时区下的渲染结果。
 * 用 vi.stubEnv 而非直接写 process.env，免得为测试给项目引入 node 类型依赖。
 */
async function importInTimezone(tz: string): Promise<typeof import('./datetime')> {
  vi.stubEnv('TZ', tz)
  vi.resetModules()
  return import('./datetime')
}

afterEach(() => {
  vi.unstubAllEnvs()
  vi.resetModules()
})

describe('formatDateTime（按浏览器时区展示）', () => {
  it('固定中文书写顺序（年/月/日）并渲染到分钟', () => {
    expect(formatDateTime('2026-08-01T13:45:09Z')).toBe('2026/08/01 13:45')
  })

  it('用 24 小时制，不出现上午/下午', () => {
    const evening = formatDateTime('2026-01-05T23:07:00Z')
    expect(evening).toBe('2026/01/05 23:07')
    expect(evening).not.toMatch(/上午|下午/)
  })

  it('月/日/时/分统一补零到两位', () => {
    expect(formatDateTime('2026-03-09T04:05:00Z')).toBe('2026/03/09 04:05')
  })

  it('按浏览器所在时区换算，而不是直接照搬 UTC 字面量', async () => {
    const { formatDateTime: inShanghai } = await importInTimezone('Asia/Shanghai')
    expect(inShanghai('2026-08-01T13:45:09Z')).toBe('2026/08/01 21:45')
  })

  it('跨日边界按目标时区进位到第二天', async () => {
    const { formatDateTime: inShanghai } = await importInTimezone('Asia/Shanghai')
    expect(inShanghai('2026-08-01T20:30:00Z')).toBe('2026/08/02 04:30')
  })
})

describe('UTC 系列（概览页专用，不混本地时区）', () => {
  it('formatUtcTime 取 UTC 的时:分', () => {
    expect(formatUtcTime('2026-08-01T13:45:09Z')).toBe('13:45')
  })

  it('formatUtcTime 补零到两位', () => {
    expect(formatUtcTime('2026-08-01T04:05:00Z')).toBe('04:05')
  })

  it('formatUtcTime 不随浏览器时区变化——概览整页按 UTC 结算', async () => {
    const { formatUtcTime: inShanghai } = await importInTimezone('Asia/Shanghai')
    expect(inShanghai('2026-08-01T13:45:09Z')).toBe('13:45')
  })

  it('formatUtcDateTime 拼出 UTC 的日期与时间', () => {
    expect(formatUtcDateTime('2026-08-01T13:45:09Z')).toBe('2026-08-01 13:45')
  })

  it('utcDate 取 UTC 日期，接受字符串或 Date', () => {
    expect(utcDate('2026-08-01T13:45:09Z')).toBe('2026-08-01')
    expect(utcDate(new Date('2026-08-01T13:45:09Z'))).toBe('2026-08-01')
  })

  it('utcDate 在本地已跨日、UTC 未跨日时仍取 UTC 那天', async () => {
    const { utcDate: inShanghai } = await importInTimezone('Asia/Shanghai')
    // 东八区此刻是 8/2 04:30，但 UTC 还在 8/1
    expect(inShanghai('2026-08-01T20:30:00Z')).toBe('2026-08-01')
  })
})

describe('utcDayProgress（时刻在一天中的位置）', () => {
  it('UTC 零点是 0', () => {
    expect(utcDayProgress('2026-08-01T00:00:00Z')).toBe(0)
  })

  it('UTC 正午是 0.5', () => {
    expect(utcDayProgress('2026-08-01T12:00:00Z')).toBe(0.5)
  })

  it('一天最后一分钟接近 1 但不到 1', () => {
    const progress = utcDayProgress('2026-08-01T23:59:00Z')
    expect(progress).toBeGreaterThan(0.999)
    expect(progress).toBeLessThan(1)
  })

  it('按分钟粒度换算，忽略秒', () => {
    expect(utcDayProgress('2026-08-01T06:00:59Z')).toBe(360 / 1440)
  })

  it('接受 Date 入参，结果与等价字符串一致', () => {
    const iso = '2026-08-01T18:30:00Z'
    expect(utcDayProgress(new Date(iso))).toBe(utcDayProgress(iso))
  })
})
