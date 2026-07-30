<script setup lang="ts">
// 管理端布局：全站 Header 之下的左侧竖导航 + 内容区；共用样式随本块懒加载
import '../../styles/admin.css'
</script>

<template>
  <div class="admin-shell">
    <nav class="admin-nav" :aria-label="$t('app.admin')">
      <RouterLink to="/admin" class="admin-nav-item">{{ $t('admin.nav.dashboard') }}</RouterLink>
      <RouterLink to="/admin/products" class="admin-nav-item">{{ $t('admin.nav.products') }}</RouterLink>
      <RouterLink to="/admin/groups" class="admin-nav-item">{{ $t('admin.nav.groups') }}</RouterLink>
      <RouterLink to="/admin/orders" class="admin-nav-item">{{ $t('admin.nav.orders') }}</RouterLink>
      <RouterLink to="/admin/users" class="admin-nav-item">{{ $t('admin.nav.users') }}</RouterLink>
    </nav>
    <main class="admin-main">
      <RouterView />
    </main>
  </div>
</template>
