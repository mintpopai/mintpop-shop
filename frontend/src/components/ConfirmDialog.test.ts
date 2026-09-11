import { mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import ConfirmDialog from './ConfirmDialog.vue'
import { i18n } from '../i18n'

let wrapper: VueWrapper | null = null

function render(props: Partial<InstanceType<typeof ConfirmDialog>['$props']> = {}) {
  wrapper = mount(ConfirmDialog, {
    attachTo: document.body,
    props: {
      title: '取消订单',
      text: '订单 MP1 取消后将无法再支付。',
      confirmLabel: '确认取消',
      cancelLabel: '再想想',
      busyLabel: '取消中…',
      ...props,
    },
    global: { plugins: [i18n] },
  })
  return wrapper
}

function button(selector: string): HTMLButtonElement {
  return document.querySelector(selector) as HTMLButtonElement
}

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  document.body.innerHTML = ''
})

describe('确认弹窗', () => {
  it('渲染标题、后果说明与两个写明动作的按钮', () => {
    render()

    expect(document.querySelector('.dialog')?.getAttribute('aria-label')).toBe('取消订单')
    expect(document.querySelector('.confirm-text')?.textContent).toBe('订单 MP1 取消后将无法再支付。')
    expect(button('.btn-ghost').textContent?.trim()).toBe('再想想')
    expect(button('.btn-danger').textContent?.trim()).toBe('确认取消')
  })

  it('主按钮抛 confirm，次按钮抛 close', () => {
    const w = render()

    button('.btn-danger').click()
    button('.btn-ghost').click()

    expect(w.emitted('confirm')).toHaveLength(1)
    expect(w.emitted('close')).toHaveLength(1)
  })

  it('busy 时主按钮禁用并换成进行中文案，防止连点', () => {
    render({ busy: true })

    expect(button('.btn-danger').disabled).toBe(true)
    expect(button('.btn-danger').textContent?.trim()).toBe('取消中…')
  })
})
