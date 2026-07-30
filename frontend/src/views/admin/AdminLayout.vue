<script setup lang="ts">
// 管理端布局：全站 Header 之下的左侧竖导航 + 内容区；共用样式随本块懒加载。
// 管理端不做双语，文案一律写死中文（api-admin 请求也强制 zh-CN）。
import '../../styles/admin.css'
</script>

<template>
  <div class="admin-shell">
    <nav class="admin-nav" aria-label="管理后台">
      <RouterLink to="/admin" class="admin-nav-item">概览</RouterLink>
      <RouterLink to="/admin/products" class="admin-nav-item">商品</RouterLink>
      <RouterLink to="/admin/groups" class="admin-nav-item">分组</RouterLink>
      <RouterLink to="/admin/orders" class="admin-nav-item">订单</RouterLink>
      <RouterLink to="/admin/users" class="admin-nav-item">用户</RouterLink>
    </nav>
    <main class="admin-main">
      <RouterView />
    </main>
  </div>
</template>
