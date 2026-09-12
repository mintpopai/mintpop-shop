import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import GateShell from './GateShell.vue'

describe('GateShell', () => {
  it('左侧品牌面板固定文案，右侧渲染调用方通过 slot 放进来的内容', () => {
    const w = mount(GateShell, {
      slots: { default: '<h1 class="gate-title">用管理员账号登录</h1>' },
    })

    expect(w.find('.gate-panel .gate-kicker').text()).toBe('管理后台')
    expect(w.find('.gate-panel .gate-foot a').attributes('href')).toBe('https://mintpop.ai')
    expect(w.find('.gate-box .gate-title').text()).toBe('用管理员账号登录')
  })

  it('品牌行用 shop 产品图标 + wordmark 图片，装饰图不带 alt 文案', () => {
    const w = mount(GateShell)

    expect(w.find('.gate-icon').attributes('src')).toContain('products/shop/shop-app-cloud.png')
    expect(w.find('.gate-icon').attributes('alt')).toBe('')
    expect(w.find('.gate-wordmark').attributes('src')).toContain('mintpop-wordmark-dark.png')
    expect(w.find('.gate-wordmark').attributes('alt')).toBe('MintPop')
  })
})
