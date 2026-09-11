import { mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import Modal from './Modal.vue'
import { i18n, t } from '../i18n'

let wrapper: VueWrapper | null = null

/**
 * Modal 用 Teleport 挂到 body，且要真实操作焦点，
 * 所以必须 attachTo 到文档里（游离节点收不到焦点，document.activeElement 也不会变）。
 */
function render(slots: Record<string, string> = {}) {
  wrapper = mount(Modal, {
    attachTo: document.body,
    props: { title: '取消订单' },
    slots: { default: '<input class="a" /><input class="b" />', ...slots },
    global: { plugins: [i18n] },
  })
  return wrapper
}

/** Teleport 到 body 后，节点不在 wrapper 的根下，直接从 document 取 */
function dialog(): HTMLElement {
  return document.querySelector('.dialog') as HTMLElement
}

function press(key: string, options: KeyboardEventInit = {}) {
  dialog().dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, ...options }))
}

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  document.body.innerHTML = ''
})

describe('打开与关闭', () => {
  it('渲染标题与 aria-modal 语义，关闭按钮的无障碍名走 i18n', () => {
    render()

    expect(dialog().getAttribute('role')).toBe('dialog')
    expect(dialog().getAttribute('aria-modal')).toBe('true')
    expect(dialog().getAttribute('aria-label')).toBe('取消订单')
    expect(document.querySelector('.close')?.getAttribute('aria-label')).toBe(t('common.close'))
  })

  it('打开时焦点落在第一个可聚焦元素（关闭按钮），而不是危险操作上', () => {
    render()

    expect(document.activeElement).toBe(document.querySelector('.close'))
  })

  it('Esc 抛出关闭事件', () => {
    const w = render()

    press('Escape')

    expect(w.emitted('close')).toHaveLength(1)
  })

  it('点击遮罩层不关闭，避免误点让人以为操作已完成', () => {
    const w = render()

    ;(document.querySelector('.overlay') as HTMLElement).dispatchEvent(
      new MouseEvent('click', { bubbles: true }),
    )

    expect(w.emitted('close')).toBeUndefined()
  })

  it('点关闭按钮抛出关闭事件', () => {
    const w = render()

    ;(document.querySelector('.close') as HTMLElement).click()

    expect(w.emitted('close')).toHaveLength(1)
  })
})

describe('焦点陷阱', () => {
  /** 弹窗内可聚焦元素：关闭按钮 + 两个 input */
  function items() {
    return Array.from(dialog().querySelectorAll<HTMLElement>('button, input'))
  }

  it('在最后一个元素上按 Tab 回到第一个，不跑到背后的页面去', () => {
    render()
    const all = items()
    all[all.length - 1].focus()

    press('Tab')

    expect(document.activeElement).toBe(all[0])
  })

  it('在第一个元素上按 Shift+Tab 跳到最后一个', () => {
    render()
    const all = items()
    all[0].focus()

    press('Tab', { shiftKey: true })

    expect(document.activeElement).toBe(all[all.length - 1])
  })

  it('非 Tab / Esc 的按键不做任何拦截', () => {
    const w = render()
    const all = items()
    all[0].focus()

    press('a')

    expect(w.emitted('close')).toBeUndefined()
    expect(document.activeElement).toBe(all[0])
  })
})

describe('关闭后的焦点归还', () => {
  it('把焦点还给打开弹窗前的那个元素，不把人丢回页首', () => {
    const opener = document.createElement('button')
    document.body.appendChild(opener)
    opener.focus()

    render()
    expect(document.activeElement).not.toBe(opener)

    wrapper?.unmount()
    wrapper = null

    expect(document.activeElement).toBe(opener)
    opener.remove()
  })
})
