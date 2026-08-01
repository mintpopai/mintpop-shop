import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ShipmentModal from './ShipmentModal.vue'
import { fetchAdminShipments, shipAdminOrder, type AdminShipmentItem } from '../api-admin'
import { toast } from '../toast'

vi.mock('../api-admin', () => ({
  fetchAdminShipments: vi.fn(),
  shipAdminOrder: vi.fn(),
}))

const fetchShipmentsMock = vi.mocked(fetchAdminShipments)
const shipMock = vi.mocked(shipAdminOrder)

const ORDER_NO = 'MP20260801001'
let wrapper: VueWrapper | null = null

function shipment(overrides: Partial<AdminShipmentItem> = {}): AdminShipmentItem {
  return {
    id: 1,
    content: 'CDKEY-1234',
    reason: null,
    operatorEmail: 'admin@example.com',
    emailTo: 'buyer@example.com',
    emailStatus: 'SENT',
    emailError: null,
    shippedAt: '2026-08-01T13:45:00Z',
    ...overrides,
  }
}

/** 挂载弹窗并等首屏的发货历史结算 */
async function render(history: AdminShipmentItem[] = []) {
  fetchShipmentsMock.mockResolvedValue(history)
  wrapper = mount(ShipmentModal, {
    attachTo: document.body,
    props: { orderNo: ORDER_NO },
  })
  await flushPromises()
  return wrapper
}

/** Modal 把内容 Teleport 到 body，统一从 document 取节点 */
function $(selector: string): HTMLElement | null {
  return document.querySelector(selector)
}

/** 写入受控输入并触发 v-model 更新 */
async function type(element: HTMLElement, value: string) {
  const input = element as HTMLInputElement | HTMLTextAreaElement
  input.value = value
  input.dispatchEvent(new Event('input', { bubbles: true }))
  await flushPromises()
}

async function submit() {
  const buttons = Array.from(document.querySelectorAll<HTMLElement>('.foot button'))
  buttons[buttons.length - 1].click()
  await flushPromises()
}

function formError(): string {
  return $('.form .admin-hint.error')?.textContent?.trim() ?? ''
}

beforeEach(() => {
  toast.value = null
})

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  document.body.innerHTML = ''
})

describe('发货历史', () => {
  it('没有历史时标题是「发货」，并且不要求填原因', async () => {
    await render([])

    expect($('.head-title')?.textContent).toBe('发货')
    expect($('.admin-hint')?.textContent).toContain('还没有发货记录')
    expect(document.querySelector('input.admin-input')).toBeNull()
  })

  it('有历史时判定为重新发货：标题改口并要求填原因', async () => {
    await render([shipment()])

    expect($('.head-title')?.textContent).toBe('重新发货')
    expect(document.querySelector('input.admin-input')).not.toBeNull()
  })

  it('渲染每条历史的时间、操作人、收件人与内容', async () => {
    await render([shipment({ content: 'CDKEY-ABCD' })])

    const item = $('.history-item')?.textContent ?? ''
    expect(item).toContain('2026/08/01 13:45')
    expect(item).toContain('admin@example.com')
    expect(item).toContain('buyer@example.com')
    expect(item).toContain('CDKEY-ABCD')
  })

  it('历史里的邮件失败要标出来，并附上失败原因', async () => {
    await render([shipment({ emailStatus: 'FAILED', emailError: 'SMTP 超时' })])

    expect($('.mail-state')?.textContent?.trim()).toBe('邮件发送失败')
    expect($('.history-error')?.textContent).toContain('SMTP 超时')
  })

  it('操作人为空时显示「未知操作人」，不渲染 null', async () => {
    await render([shipment({ operatorEmail: null })])

    expect($('.history-meta')?.textContent).toContain('未知操作人')
  })

  it('重新发货的原因展示在历史条目里', async () => {
    await render([shipment({ reason: '上次发错卡密' })])

    expect($('.history-meta')?.textContent).toContain('上次发错卡密')
  })

  it('历史加载失败时给出提示，但仍可继续发货', async () => {
    fetchShipmentsMock.mockRejectedValue(new Error('查询发货历史失败'))
    wrapper = mount(ShipmentModal, { attachTo: document.body, props: { orderNo: ORDER_NO } })
    await flushPromises()

    expect($('.history .admin-hint.error')?.textContent).toContain('查询发货历史失败')
    expect(document.querySelector('textarea')).not.toBeNull()
  })
})

describe('提交校验', () => {
  it('发货内容为空时拦下，不发请求', async () => {
    await render([])

    await submit()

    expect(formError()).toBe('发货内容不能为空')
    expect(shipMock).not.toHaveBeenCalled()
  })

  it('只填空白同样按空处理', async () => {
    await render([])
    await type(document.querySelector('textarea') as HTMLElement, '   \n  ')

    await submit()

    expect(formError()).toBe('发货内容不能为空')
    expect(shipMock).not.toHaveBeenCalled()
  })

  it('发货内容超过 2000 字时拦下', async () => {
    await render([])
    await type(document.querySelector('textarea') as HTMLElement, 'x'.repeat(2001))

    await submit()

    expect(formError()).toBe('发货内容最多 2000 字')
    expect(shipMock).not.toHaveBeenCalled()
  })

  it('刚好 2000 字放行', async () => {
    await render([])
    shipMock.mockResolvedValue({ shippedAt: '2026-08-01T13:45:00Z', emailStatus: 'SENT', emailError: null })
    await type(document.querySelector('textarea') as HTMLElement, 'x'.repeat(2000))

    await submit()

    expect(shipMock).toHaveBeenCalledOnce()
  })

  it('重新发货没填原因时拦下——避免无痕覆盖上一次发货', async () => {
    await render([shipment()])
    await type(document.querySelector('textarea') as HTMLElement, 'CDKEY-5678')

    await submit()

    expect(formError()).toBe('重新发货必须填写原因')
    expect(shipMock).not.toHaveBeenCalled()
  })

  it('重新发货原因只填空白也不算填了', async () => {
    await render([shipment()])
    await type(document.querySelector('textarea') as HTMLElement, 'CDKEY-5678')
    await type(document.querySelector('input.admin-input') as HTMLElement, '   ')

    await submit()

    expect(formError()).toBe('重新发货必须填写原因')
  })
})

describe('提交发货', () => {
  it('首次发货只提交内容，且首尾空白被裁掉', async () => {
    await render([])
    shipMock.mockResolvedValue({ shippedAt: '2026-08-01T13:45:00Z', emailStatus: 'SENT', emailError: null })
    await type(document.querySelector('textarea') as HTMLElement, '  CDKEY-1234  ')

    await submit()

    expect(shipMock).toHaveBeenCalledWith(ORDER_NO, { content: 'CDKEY-1234', reason: undefined })
  })

  it('重新发货把裁剪后的原因一并提交', async () => {
    await render([shipment()])
    shipMock.mockResolvedValue({ shippedAt: '2026-08-01T13:45:00Z', emailStatus: 'SENT', emailError: null })
    await type(document.querySelector('textarea') as HTMLElement, 'CDKEY-5678')
    await type(document.querySelector('input.admin-input') as HTMLElement, '  上次发错卡密  ')

    await submit()

    expect(shipMock).toHaveBeenCalledWith(ORDER_NO, {
      content: 'CDKEY-5678',
      reason: '上次发错卡密',
    })
  })

  it('发货成功且邮件已发出时提示成功，并通知父组件刷新后关闭', async () => {
    const w = await render([])
    shipMock.mockResolvedValue({ shippedAt: '2026-08-01T13:45:00Z', emailStatus: 'SENT', emailError: null })
    await type(document.querySelector('textarea') as HTMLElement, 'CDKEY-1234')

    await submit()

    expect(toast.value).toEqual({ type: 'success', text: '已发货，发货邮件已发送' })
    expect(w.emitted('shipped')).toHaveLength(1)
    expect(w.emitted('close')).toHaveLength(1)
  })

  it('邮件发送失败时说清「货已发出、只是邮件没发成」，不让人以为整件事失败了', async () => {
    const w = await render([])
    shipMock.mockResolvedValue({
      shippedAt: '2026-08-01T13:45:00Z',
      emailStatus: 'FAILED',
      emailError: 'SMTP 超时',
    })
    await type(document.querySelector('textarea') as HTMLElement, 'CDKEY-1234')

    await submit()

    expect(toast.value).toEqual({ type: 'error', text: '已发货，但邮件发送失败：SMTP 超时' })
    // 发货本身已落库，父组件照样要刷新列表
    expect(w.emitted('shipped')).toHaveLength(1)
  })

  it('邮件失败但后端没给原因时兜底文案', async () => {
    await render([])
    shipMock.mockResolvedValue({
      shippedAt: '2026-08-01T13:45:00Z',
      emailStatus: 'FAILED',
      emailError: null,
    })
    await type(document.querySelector('textarea') as HTMLElement, 'CDKEY-1234')

    await submit()

    expect(toast.value?.text).toBe('已发货，但邮件发送失败：未知原因')
  })

  it('发货请求失败时把后端原因留在表单里，弹窗不关闭', async () => {
    const w = await render([])
    shipMock.mockRejectedValue(new Error('该订单状态不可发货'))
    await type(document.querySelector('textarea') as HTMLElement, 'CDKEY-1234')

    await submit()

    expect(formError()).toBe('该订单状态不可发货')
    expect(w.emitted('close')).toBeUndefined()
    expect(w.emitted('shipped')).toBeUndefined()
  })

  it('点取消直接关闭，不发请求', async () => {
    const w = await render([])

    ;(document.querySelector('.foot .admin-btn-ghost') as HTMLElement).click()
    await flushPromises()

    expect(shipMock).not.toHaveBeenCalled()
    expect(w.emitted('close')).toHaveLength(1)
  })
})
