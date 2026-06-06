import { createRouter, createWebHistory } from 'vue-router'

const HomeView = () => import('@/views/HomeView.vue')
const HistoryView = () => import('@/views/HistoryView.vue')
const ProjectView = () => import('@/views/ProjectView.vue')
const LoginView = () => import('@/views/LoginView.vue')

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: HomeView, meta: { title: 'AI 小说转剧本', auth: true } },
    { path: '/history', name: 'history', component: HistoryView, meta: { title: '历史记录', auth: true } },
    { path: '/project/:id', name: 'project', component: ProjectView, props: true, meta: { title: '剧本工作台', auth: true } },
    { path: '/login', name: 'login', component: LoginView, meta: { title: '登录' } }
  ],
  scrollBehavior() { return { top: 0 } }
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('auth_token')
  if (to.meta.auth && !token) {
    next({ name: 'login', query: { redirect: to.fullPath } })
  } else if (to.name === 'login' && token) {
    next({ name: 'home' })
  } else {
    next()
  }
})

router.afterEach((to) => {
  if (to.meta?.title) document.title = `${to.meta.title} · 小说转剧本`
})

export default router
