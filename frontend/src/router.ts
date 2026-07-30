import { createRouter, createWebHistory } from 'vue-router'
import ShopView from './views/ShopView.vue'
import OrdersView from './views/OrdersView.vue'
import PayView from './views/PayView.vue'
import PaymentResultView from './views/PaymentResultView.vue'
import { currentUser, gotoLogin } from './auth'
import { t } from './i18n'
import { showToast } from './toast'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: ShopView },
    { path: '/orders', component: OrdersView },
    { path: '/pay/:orderNo', component: PayView },
    { path: '/payment/result', component: PaymentResultView },
    {
      // 管理端：懒加载切块，不进普通用户首屏包
      path: '/admin',
      component: () => import('./views/admin/AdminLayout.vue'),
      children: [
        { path: '', component: () => import('./views/admin/AdminDashboardView.vue') },
        { path: 'products', component: () => import('./views/admin/AdminProductsView.vue') },
        { path: 'groups', component: () => import('./views/admin/AdminGroupsView.vue') },
        { path: 'orders', component: () => import('./views/admin/AdminOrdersView.vue') },
        { path: 'users', component: () => import('./views/admin/AdminUsersView.vue') },
      ],
    },
  ],
})

// 管理端守卫（仅 UX 引导；安全边界在后端 /api/admin/** 拦截器）：
// 挂载前已完成 loadCurrentUser，这里可同步判定
router.beforeEach((to) => {
  if (!to.path.startsWith('/admin')) {
    return true
  }
  if (!currentUser.value) {
    gotoLogin()
    return false
  }
  if (!currentUser.value.admin) {
    showToast('error', t('admin.forbidden'))
    return '/'
  }
  return true
})
