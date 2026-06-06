<script setup>
import { onMounted, ref } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import gsap from 'gsap'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const route = useRoute()
const navEl = ref(null)

onMounted(() => {
  gsap.from(navEl.value, {
    y: -6,
    opacity: 0,
    duration: 0.4,
    ease: 'power2.out'
  })
})
</script>

<template>
  <div class="app-shell">
    <header class="topnav" ref="navEl">
      <RouterLink to="/" class="brand">
        <span class="brand-mark">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/>
          </svg>
        </span>
        <span class="brand-text">剧本工坊</span>
      </RouterLink>
      <nav class="nav-links">
        <RouterLink to="/" :class="{ active: route.name === 'home' }">新建项目</RouterLink>
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
  transition: opacity 180ms cubic-bezier(0.25, 0.46, 0.45, 0.94),
              transform 180ms cubic-bezier(0.25, 0.46, 0.45, 0.94);
}
.page-enter-from { opacity: 0; transform: translateY(6px); }
.page-leave-to   { opacity: 0; transform: translateY(-3px); }

.brand-text {
  letter-spacing: -0.01em;
  font-family: var(--font-display);
}
</style>
