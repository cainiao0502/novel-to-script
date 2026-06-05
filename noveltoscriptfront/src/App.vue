<script setup>
import { onMounted, ref } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import gsap from 'gsap'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const route = useRoute()
const navEl = ref(null)

onMounted(() => {
  gsap.from(navEl.value, {
    y: -10,
    opacity: 0,
    duration: 0.6,
    ease: 'expo.out'
  })
})
</script>

<template>
  <div class="app-shell">
    <header class="topnav" ref="navEl">
      <RouterLink to="/" class="brand">
        <span class="brand-mark">墨</span>
        <span class="brand-text">剧本工坊</span>
      </RouterLink>
      <nav class="nav-links">
        <RouterLink to="/" :class="{ active: route.name === 'home' }">新建</RouterLink>
        <RouterLink to="/history" :class="{ active: route.name === 'history' }">历史记录</RouterLink>
      </nav>
      <div class="nav-spacer" />
      <a
        class="btn btn-secondary"
        href="https://github.com/"
        target="_blank"
        rel="noopener"
      >文档</a>
    </header>

    <main>
      <RouterView v-slot="{ Component }">
        <transition name="page" mode="out-in">
          <component :is="Component" :key="route.fullPath" />
        </transition>
      </RouterView>
    </main>

    <ConfirmDialog />
  </div>
</template>

<style scoped>
.page-enter-active, .page-leave-active {
  transition: opacity 240ms cubic-bezier(0.25, 0.46, 0.45, 0.94),
              transform 240ms cubic-bezier(0.25, 0.46, 0.45, 0.94);
}
.page-enter-from { opacity: 0; transform: translateY(8px); }
.page-leave-to   { opacity: 0; transform: translateY(-4px); }

.brand-text {
  letter-spacing: -0.01em;
}
</style>
