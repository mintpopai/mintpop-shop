import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ProductCard from './ProductCard.vue'
import type { Product } from '../api'
import { i18n, t } from '../i18n'

function product(overrides: Partial<Product> = {}): Product {
  return {
    id: 1,
    name: 'Claude Pro 会员',
    description: '官方正规渠道',
    priceCents: 1999,
    imageUrl: null,
    badge: null,
    accent: 'MINT',
    ...overrides,
  }
}

function render(overrides: Partial<Product> = {}, buying = false) {
  return mount(ProductCard, {
    props: { product: product(overrides), buying },
    global: { plugins: [i18n] },
  })
}

describe('ProductCard 内容渲染', () => {
  it('展示商品名与描述', () => {
    const wrapper = render()
    expect(wrapper.find('.name').text()).toBe('Claude Pro 会员')
    expect(wrapper.find('.desc').text()).toBe('官方正规渠道')
  })

  it('描述为空时不渲染 null 字样', () => {
    const wrapper = render({ description: null })
    expect(wrapper.find('.desc').text()).toBe('')
  })

  it('价格按美元两位小数展示，而不是直接显示美分', () => {
    expect(render({ priceCents: 1999 }).find('.price').text()).toBe('$19.99')
    expect(render({ priceCents: 0 }).find('.price').text()).toBe('$0.00')
  })

  it('有角标文案时渲染角标', () => {
    expect(render({ badge: '热销' }).find('.badge').text()).toBe('热销')
  })

  it('角标为空时不渲染角标节点', () => {
    expect(render({ badge: null }).find('.badge').exists()).toBe(false)
  })
})

describe('ProductCard 图片与占位', () => {
  it('有商品图时渲染图片，并用商品名作为替代文本', () => {
    const wrapper = render({ imageUrl: 'https://cdn.example.com/a.png' })
    const img = wrapper.find('img.photo')

    expect(img.exists()).toBe(true)
    expect(img.attributes('src')).toBe('https://cdn.example.com/a.png')
    expect(img.attributes('alt')).toBe('Claude Pro 会员')
    expect(wrapper.find('.placeholder').exists()).toBe(false)
  })

  it('无商品图时用商品名首字占位，并对屏幕阅读器隐藏', () => {
    const wrapper = render({ imageUrl: null })
    const placeholder = wrapper.find('.placeholder')

    expect(placeholder.text()).toBe('C')
    expect(placeholder.attributes('aria-hidden')).toBe('true')
    expect(wrapper.find('img.photo').exists()).toBe(false)
  })
})

describe('ProductCard 主题色', () => {
  it('按 accent 取对应的渐变色', () => {
    const style = render({ accent: 'ROSE' }).find('.thumb').attributes('style')
    expect(style).toContain('#fae3e7')
    expect(style).toContain('#f6ccd4')
  })

  it('后端给了未知 accent 时回退到 MINT，不至于渲染成透明卡片', () => {
    const style = render({ accent: 'NEON_GREEN' }).find('.thumb').attributes('style')
    expect(style).toContain('#d9f7ec')
  })
})

describe('ProductCard 购买按钮', () => {
  it('点击时把整个商品对象抛给父组件', async () => {
    const wrapper = render({ id: 42 })

    await wrapper.find('.buy-btn').trigger('click')

    expect(wrapper.emitted('buy')).toHaveLength(1)
    expect(wrapper.emitted('buy')?.[0][0]).toMatchObject({ id: 42 })
  })

  it('下单中时按钮禁用并切换为「下单中」文案，防止重复下单', () => {
    const wrapper = render({}, true)
    const button = wrapper.find('.buy-btn')

    expect(button.attributes('disabled')).toBeDefined()
    expect(button.text()).toContain(t('product.buying'))
  })

  it('空闲时按钮可点，显示「购买」文案', () => {
    const button = render({}, false).find('.buy-btn')

    expect(button.attributes('disabled')).toBeUndefined()
    expect(button.text()).toContain(t('product.buy'))
  })

  it('禁用状态下点击不再抛出购买事件', async () => {
    const wrapper = render({}, true)

    await wrapper.find('.buy-btn').trigger('click')

    expect(wrapper.emitted('buy')).toBeUndefined()
  })
})
