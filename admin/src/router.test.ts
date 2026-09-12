import { describe, expect, it } from 'vitest'
import { router } from './router'
import DashboardView from './views/DashboardView.vue'
import ProductsView from './views/ProductsView.vue'
import GroupsView from './views/GroupsView.vue'
import OrdersView from './views/OrdersView.vue'
import UsersView from './views/UsersView.vue'

/** 懒加载路由的 component 是 () => import()，要真正解析后才能与视图比对 */
async function componentOf(path: string) {
  const matched = router.resolve(path).matched
  const raw = matched[matched.length - 1]?.components?.default
  if (typeof raw === 'function') {
    const mod = (await (raw as () => Promise<{ default: unknown }>)()).default
    return mod
  }
  return raw
}

describe('路由表', () => {
  it('概览页静态引入，落地即渲染', async () => {
    expect(await componentOf('/')).toBe(DashboardView)
  })

  it.each([
    ['/products', ProductsView],
    ['/groups', GroupsView],
    ['/orders', OrdersView],
    ['/users', UsersView],
  ])('%s 懒加载到对应页面', async (path, view) => {
    expect(await componentOf(path)).toBe(view)
  })

  it('未知路径重定向回概览', async () => {
    await router.push('/nowhere/at/all')
    await router.isReady()
    expect(router.currentRoute.value.path).toBe('/')
  })
})
