<script setup>
import { onMounted, ref, computed } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import gsap from 'gsap'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const navEl = ref(null)
const userMenuOpen = ref(false)

const showNav = computed(() => route.name !== 'login')

onMounted(() => {
  if (navEl.value) {
    gsap.from(navEl.value, {
      y: -6,
      opacity: 0,
      duration: 0.4,
      ease: 'power2.out'
    })
  }
})

async function handleLogout() {
  userMenuOpen.value = false
  await auth.logout()
  router.push({ name: 'login' })
}

function toggleUserMenu() {
  userMenuOpen.value = !userMenuOpen.value
}

function closeUserMenu() {
  userMenuOpen.value = false
}
</script>

<template>
  <div class="app-shell">
    <header v-if="showNav" class="topnav" ref="navEl">
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

      <div v-if="auth.isLoggedIn" class="user-area" @mouseleave="closeUserMenu">
        <button class="user-trigger" @click="toggleUserMenu">
          <span class="user-avatar">{{ auth.nickname?.charAt(0) || '用' }}</span>
          <span class="user-name">{{ auth.nickname || '用户' }}</span>
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="user-chevron" :class="{ open: userMenuOpen }">
            <polyline points="6 9 12 15 18 9"/>
          </svg>
        </button>
        <transition name="dropdown">
          <div v-if="userMenuOpen" class="user-dropdown">
            <button class="dropdown-item" @click="handleLogout">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/>
              </svg>
              退出登录
            </button>
          </div>
        </transition>
      </div>

      <a
        v-else
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

/* User area */
.user-area {
  position: relative;
}
.user-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 34px;
  padding: 0 10px 0 4px;
  border-radius: var(--radius-pill);
  transition: all var(--duration-fast) var(--ease-out-quad);
  cursor: pointer;
}
.user-trigger:hover {
  background: var(--color-surface-2);
}
.user-avatar {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--color-primary);
  color: var(--color-on-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  font-family: var(--font-display);
}
.user-name {
  font-size: var(--text-body-sm);
  color: var(--color-ink);
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.user-chevron {
  transition: transform var(--duration-fast) var(--ease-out-quad);
  color: var(--color-ink-subtle);
}
.user-chevron.open {
  transform: rotate(180deg);
}

.user-dropdown {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  min-width: 140px;
  background: var(--color-surface-1);
  border: 1px solid var(--color-hairline);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-card-hover);
  padding: 4px;
  z-index: 200;
}
.dropdown-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 12px;
  font-size: var(--text-body-sm);
  color: var(--color-ink-muted);
  border-radius: var(--radius-sm);
  transition: all var(--duration-fast) var(--ease-out-quad);
  cursor: pointer;
}
.dropdown-item:hover {
  background: var(--color-surface-2);
  color: var(--color-ink);
}

.dropdown-enter-active, .dropdown-leave-active {
  transition: opacity 120ms var(--ease-out-quad), transform 120ms var(--ease-out-quad);
}
.dropdown-enter-from, .dropdown-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
