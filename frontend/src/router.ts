import { createRouter, createWebHistory } from 'vue-router'
import ShopView from './views/ShopView.vue'
import OrdersView from './views/OrdersView.vue'
import PayView from './views/PayView.vue'
import PaymentResultView from './views/PaymentResultView.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: ShopView },
    { path: '/orders', component: OrdersView },
    { path: '/pay/:orderNo', component: PayView },
    { path: '/payment/result', component: PaymentResultView },
  ],
})
