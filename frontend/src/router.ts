import { createRouter, createWebHistory } from 'vue-router'
import ShopView from './views/ShopView.vue'
import OrdersView from './views/OrdersView.vue'
import OrderDetailView from './views/OrderDetailView.vue'
import PayView from './views/PayView.vue'
import PaymentResultView from './views/PaymentResultView.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: ShopView },
    { path: '/orders', component: OrdersView },
    { path: '/orders/:orderNo', component: OrderDetailView },
    { path: '/pay/:orderNo', component: PayView },
    { path: '/payment/result', component: PaymentResultView },
  ],
})
