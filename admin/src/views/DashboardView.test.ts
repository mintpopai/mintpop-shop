import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import DashboardView from './DashboardView.vue'
import { fetchAdminDashboard, type AdminDashboard, type AdminOrderItem } from '../api-admin'

vi.mock('../api-admin', () => ({ fetchAdminDashboard: vi.fn() }))

const fetchDashboardMock = vi.mocked(fetchAdminDashboard)

/** 打开页面的固定时刻（UTC 12:00，正好是纸带正中） */
const NOW = new Date('2026-08-01T12:00:00Z')

let wrapper: VueWrapper | null = null

function order(overrides: Partial<AdminOrderItem> = {}): AdminOrderItem {
  return {
    orderNo: 'MP001',
    productName: 'Claude Pro 会员',
    buyerEmail: 'buyer@example.com',
    quantity: 1,
    amountCents: 1000,
    status: 'PAID',
    statusLabel: '已支付',
    paymentProvider: 'stripe',
    createdAt: '2026-08-01T06:00:00Z',
    paidAt: '2026-08-01T06:01:00Z',
    ...overrides,
  }
}

function dashboard(overrides: Partial<AdminDashboard> = {}): AdminDashboard {
  return {
    totalRevenueCents: 123456,
    totalOrderCount: 42,
    todayOrderCount: 1,
    todayRevenueCents: 1000,
    userCount: 7,
    onSaleProductCount: 3,
    recentOrders: [order()],
    ...overrides,
  }
}

async function render(data: AdminDashboard = dashboard()) {
  fetchDashboardMock.mockResolvedValue(data)
  wrapper = mount(DashboardView)
  await flushPromises()
  return wrapper
}

/** 一根柱子 = 一笔今日订单；读它的内联定位与高度 */
function bars() {
  return wrapper!.findAll('.tape-bar').map((bar) => {
    const style = bar.attributes('style') ?? ''
    return {
      left: Number(/left:\s*([\d.]+)%/.exec(style)?.[1]),
      height: Number(/height:\s*([\d.]+)%/.exec(style)?.[1]),
      orderNo: bar.text(),
    }
  })
}

beforeEach(() => {
  vi.useFakeTimers()
  vi.setSystemTime(NOW)
})

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  vi.useRealTimers()
})

describe('加载', () => {
  it('加载失败时显示错误信息，不渲染纸带', async () => {
    fetchDashboardMock.mockRejectedValue(new Error('查询概览失败'))
    wrapper = mount(DashboardView)
    await flushPromises()

    expect(wrapper.find('.admin-hint.error').text()).toBe('查询概览失败')
    expect(wrapper.find('.tape-card').exists()).toBe(false)
  })

  it('页头写明今天是哪天（UTC 口径）', async () => {
    const w = await render()

    expect(w.find('.page-facts').text()).toContain('2026-08-01')
  })

  it('累计数据整行展示', async () => {
    const w = await render()
    const totals = w.find('.totals').text()

    expect(totals).toContain('$1234.56')
    expect(totals).toContain('42')
    expect(totals).toContain('7')
    expect(totals).toContain('3')
  })
})

describe('今日纸带', () => {
  it('只画今天（UTC）的订单，昨天的不进来', async () => {
    await render(
      dashboard({
        todayOrderCount: 1,
        recentOrders: [
          order({ orderNo: 'TODAY', createdAt: '2026-08-01T06:00:00Z' }),
          order({ orderNo: 'YESTERDAY', createdAt: '2026-07-31T23:00:00Z' }),
        ],
      }),
    )

    const drawn = bars()
    expect(drawn).toHaveLength(1)
    expect(drawn[0].orderNo).toContain('Claude Pro 会员')
  })

  it('本地已跨日但 UTC 未跨日的订单仍算今天', async () => {
    await render(
      dashboard({ recentOrders: [order({ createdAt: '2026-08-01T23:30:00Z' })] }),
    )

    expect(bars()).toHaveLength(1)
  })

  it('柱子按下单时间正序排，不受接口返回顺序影响', async () => {
    await render(
      dashboard({
        recentOrders: [
          order({ orderNo: 'LATE', createdAt: '2026-08-01T18:00:00Z' }),
          order({ orderNo: 'EARLY', createdAt: '2026-08-01T03:00:00Z' }),
        ],
      }),
    )

    const [first, second] = bars()
    expect(first.left).toBeLessThan(second.left)
  })

  it('横向位置就是这笔单在一天里的时刻（06:00 → 25%）', async () => {
    await render(dashboard({ recentOrders: [order({ createdAt: '2026-08-01T06:00:00Z' })] }))

    expect(bars()[0].left).toBeCloseTo(25, 5)
  })

  it('高度按当天最大额归一：最大的那笔满高', async () => {
    await render(
      dashboard({
        recentOrders: [
          order({ orderNo: 'BIG', amountCents: 10000, createdAt: '2026-08-01T03:00:00Z' }),
          order({ orderNo: 'SMALL', amountCents: 5000, createdAt: '2026-08-01T09:00:00Z' }),
        ],
      }),
    )

    const [big, small] = bars()
    expect(big.height).toBe(100)
    // 半价的那笔：20 + 0.5 * 80
    expect(small.height).toBe(60)
  })

  it('最小的柱子也留 20% 底高，不至于矮到看不见', async () => {
    await render(
      dashboard({
        recentOrders: [
          order({ orderNo: 'BIG', amountCents: 100000, createdAt: '2026-08-01T03:00:00Z' }),
          order({ orderNo: 'TINY', amountCents: 1, createdAt: '2026-08-01T09:00:00Z' }),
        ],
      }),
    )

    expect(bars()[1].height).toBeGreaterThanOrEqual(20)
  })

  it('今天没有订单时给出空态，不画柱子', async () => {
    const w = await render(
      dashboard({ todayOrderCount: 0, recentOrders: [order({ createdAt: '2026-07-31T10:00:00Z' })] }),
    )

    expect(bars()).toHaveLength(0)
    expect(w.find('.tape-empty').text()).toBe('今天还没有订单')
  })

  it('「此刻」标线落在当前 UTC 时刻上', async () => {
    const w = await render()

    const style = w.find('.tape-now').attributes('style') ?? ''
    expect(Number(/left:\s*([\d.]+)%/.exec(style)?.[1])).toBeCloseTo(50, 5)
    expect(w.find('.tape-now-label').text()).toContain('12:00')
  })

  it('临近一天末尾时标线翻向左侧，避免文字被右边缘裁掉', async () => {
    vi.setSystemTime(new Date('2026-08-01T22:00:00Z'))
    const w = await render()

    expect(w.find('.tape-now').classes()).toContain('flip')
  })

  it('纸带给屏幕阅读器的说明包含今日笔数与营收', async () => {
    const w = await render(dashboard({ todayOrderCount: 3, todayRevenueCents: 5000 }))

    expect(w.find('.tape').attributes('aria-label')).toBe('今日（UTC）3 笔订单，营收 $50.00')
  })
})

describe('画不全时的说明', () => {
  it('后端today笔数多于手上的最近订单时，说清纸带只画了其中几笔', async () => {
    const w = await render(dashboard({ todayOrderCount: 30, recentOrders: [order()] }))

    expect(w.find('.tape-lead-sub').text()).toContain('30')
    expect(w.find('.tape-lead-sub').text()).toContain('纸带画出其中最近')
  })

  it('两者对得上时不画蛇添足地解释', async () => {
    const w = await render(dashboard({ todayOrderCount: 1, recentOrders: [order()] }))

    expect(w.find('.tape-lead-sub').text()).not.toContain('纸带画出其中最近')
  })

  it('最近订单里的今日笔数反多于后端统计时不显示负数', async () => {
    const w = await render(
      dashboard({
        todayOrderCount: 1,
        recentOrders: [
          order({ orderNo: 'A', createdAt: '2026-08-01T03:00:00Z' }),
          order({ orderNo: 'B', createdAt: '2026-08-01T09:00:00Z' }),
        ],
      }),
    )

    expect(w.find('.tape-lead-sub').text()).not.toContain('-')
  })
})

describe('最近订单表', () => {
  it('时间列按 UTC 展示，与纸带同一把尺子', async () => {
    const w = await render(dashboard({ recentOrders: [order({ createdAt: '2026-08-01T06:30:00Z' })] }))

    expect(w.find('tbody tr').text()).toContain('2026-08-01 06:30')
  })

  it('游客下单没有邮箱时显示「游客」', async () => {
    const w = await render(dashboard({ recentOrders: [order({ buyerEmail: null })] }))

    expect(w.find('tbody tr').text()).toContain('游客')
  })

  it('一笔订单都没有时提示等第一单', async () => {
    const w = await render(dashboard({ todayOrderCount: 0, recentOrders: [] }))

    expect(w.find('.admin-card .admin-hint').text()).toContain('商城下出第一单后')
  })
})
