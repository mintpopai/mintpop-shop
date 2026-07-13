import { createRouter, createWebHistory } from 'vue-router'
import ShopView from './views/ShopView.vue'
import OrdersView from './views/OrdersView.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: ShopView },
    { path: '/orders', component: OrdersView },
  ],
})
