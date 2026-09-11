<script setup lang="ts">
// 闸门页（登录引导 / 无权限）共用的分屏骨架：左边深墨品牌面板讲「这是什么」，
// 右边白底由调用方放标题、说明与登录动作。与 meetu / mintpop-lane 管理端同一版式，
// 移植时只换左面板的文案与脚注。样式在 styles/layout.css 的 gate 段。
</script>

<template>
  <div class="gate">
    <aside class="gate-panel">
      <p class="gate-kicker">管理后台</p>
      <div class="gate-story">
        <h2 class="gate-tagline">店面在前，<br />账台在后。</h2>
        <span class="gate-stroke" aria-hidden="true"></span>
        <p class="gate-tagline-sub">商城里的每件商品、每一笔订单，都从这里经手。</p>
      </div>
      <p class="gate-foot"><a href="https://mintpop.ai">mintpop.ai</a></p>
    </aside>

    <main class="gate-main">
      <div class="gate-box">
        <p class="gate-brand">
          <img
            class="gate-icon"
            src="https://standards.mintpop.ai/assets/products/shop/shop-app-cloud.png"
            alt=""
          />
          <span class="gate-brand-text">
            <img
              class="gate-wordmark"
              src="https://standards.mintpop.ai/assets/brand/wordmark/mintpop-wordmark-dark.png"
              alt="MintPop"
            />
            管理后台
          </span>
        </p>
        <slot />
      </div>
    </main>
  </div>
</template>
