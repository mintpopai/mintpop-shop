import { describe, expect, it } from 'vitest'
import { router } from './router'
import ShopView from './views/ShopView.vue'
import ProductDetailView from './views/ProductDetailView.vue'
import OrdersView from './views/OrdersView.vue'
import OrderDetailView from './views/OrderDetailView.vue'
import PayView from './views/PayView.vue'
import PaymentResultView from './views/PaymentResultView.vue'
import SettingsView from './views/SettingsView.vue'

/** 取路径匹配到的页面组件（只看最终命中的那一条路由） */
function componentOf(path: string) {
  const matched = router.resolve(path).matched
  return matched[matched.length - 1]?.components?.default
}

describe('路由表', () => {
  it.each([
    ['/', ShopView],
    ['/products/42', ProductDetailView],
    ['/orders', OrdersView],
    ['/orders/MP20260801001', OrderDetailView],
    ['/pay/MP20260801001', PayView],
    ['/payment/result', PaymentResultView],
    ['/settings', SettingsView],
  ])('%s 渲染对应页面', (path, view) => {
    expect(componentOf(path)).toBe(view)
  })

  it('动态段按名字解析成 params，视图据此取 id / orderNo', () => {
    expect(router.resolve('/products/42').params).toEqual({ id: '42' })
    expect(router.resolve('/orders/MP1').params).toEqual({ orderNo: 'MP1' })
    expect(router.resolve('/pay/MP2').params).toEqual({ orderNo: 'MP2' })
  })

  it('支付回跳的 query 原样保留，结果页据 order_no 轮询', () => {
    const resolved = router.resolve('/payment/result?order_no=MP1&redirect_status=succeeded')
    expect(resolved.query).toEqual({ order_no: 'MP1', redirect_status: 'succeeded' })
  })

  it('未知路径不命中任何页面', () => {
    expect(router.resolve('/nowhere').matched).toHaveLength(0)
  })
})
