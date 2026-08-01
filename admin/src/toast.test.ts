import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { showToast, toast } from './toast'

beforeEach(() => {
  vi.useFakeTimers()
  toast.value = null
})

afterEach(() => {
  vi.useRealTimers()
})

describe('showToast', () => {
  it('立即写入提示内容', () => {
    showToast('success', '已保存')
    expect(toast.value).toEqual({ type: 'success', text: '已保存' })
  })

  it('3 秒后自动消失', () => {
    showToast('error', '请求失败')

    vi.advanceTimersByTime(2999)
    expect(toast.value).not.toBeNull()

    vi.advanceTimersByTime(1)
    expect(toast.value).toBeNull()
  })

  it('连续弹出时后一条覆盖前一条', () => {
    showToast('success', '第一条')
    showToast('error', '第二条')

    expect(toast.value).toEqual({ type: 'error', text: '第二条' })
  })

  it('后一条会重置倒计时，不被前一条的定时器提前清掉', () => {
    showToast('success', '第一条')
    vi.advanceTimersByTime(2500)
    showToast('error', '第二条')

    vi.advanceTimersByTime(500)
    expect(toast.value).toEqual({ type: 'error', text: '第二条' })

    vi.advanceTimersByTime(2500)
    expect(toast.value).toBeNull()
  })
})
