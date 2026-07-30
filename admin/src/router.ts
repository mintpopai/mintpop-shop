import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from './views/DashboardView.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    // 概览是落地页，直接静态引入；其余四页懒加载
    { path: '/', component: DashboardView },
    { path: '/products', component: () => import('./views/ProductsView.vue') },
    { path: '/groups', component: () => import('./views/GroupsView.vue') },
    { path: '/orders', component: () => import('./views/OrdersView.vue') },
    { path: '/users', component: () => import('./views/UsersView.vue') },
    // 未知路径回概览
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})
