import { createRouter, createWebHistory } from 'vue-router'

const HomeView = () => import('@/views/HomeView.vue')
const HistoryView = () => import('@/views/HistoryView.vue')
const ProjectView = () => import('@/views/ProjectView.vue')

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: HomeView, meta: { title: 'AI 小说转剧本' } },
    { path: '/history', name: 'history', component: HistoryView, meta: { title: '历史记录' } },
    { path: '/project/:id', name: 'project', component: ProjectView, props: true, meta: { title: '剧本工作台' } }
  ],
  scrollBehavior() { return { top: 0 } }
})

router.afterEach((to) => {
  if (to.meta?.title) document.title = `${to.meta.title} · 小说转剧本`
})

export default router
