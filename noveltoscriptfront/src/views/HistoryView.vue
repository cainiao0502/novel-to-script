<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import gsap from 'gsap'
import { api } from '@/api'
import { useConfirm } from '@/composables/useConfirm'
import StatusBadge from '@/components/StatusBadge.vue'

const router = useRouter()

const projects = ref([])
const loading = ref(true)
const error = ref('')
const { confirm: showConfirm } = useConfirm()

onMounted(async () => {
  try {
    projects.value = await api.listProjects()
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
  await nextTick()
  animateCards()
})

function animateCards() {
  const cards = document.querySelectorAll('.history-card')
  if (!cards.length) return
  gsap.from(cards, {
    y: 16,
    duration: 0.45,
    stagger: 0.06,
    ease: 'expo.out'
  })
}

function openProject(id) {
  router.push({ name: 'project', params: { id } })
}

function formatDate(iso) {
  if (!iso) return '--'
  return new Date(iso).toLocaleDateString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit'
  })
}

function statusLabel(s) {
  return ({
    DRAFT: '草稿', PENDING: '待处理', GENERATING: '生成中',
    PARTIAL_SUCCESS: '部分成功', COMPLETED: '已完成', FAILED: '失败', DONE: '已生成'
  })[s] || s
}

async function deleteProject(e, id, title) {
  e.stopPropagation()
  const ok = await showConfirm({
    title: `删除项目「${title}」？`,
    message: '项目及其所有章节、生成的剧本将永久消失，无法恢复。',
    confirmText: '永久删除',
    cancelText: '取消',
    variant: 'danger'
  })
  if (!ok) return
  try {
    await api.deleteProject(id)
    projects.value = projects.value.filter(p => p.id !== id)
  } catch (err) {
    error.value = err.message
  }
}
</script>

<template>
  <section class="history">
    <div class="container">
      <header class="history-header">
        <p class="eyebrow">项目历史</p>
        <h1 class="headline">历史记录</h1>
        <p class="body-sm subtle">所有已创建的项目，点击进入工作台</p>
      </header>

      <div v-if="loading" class="loading">
        <div class="spinner-lg" />
        <span class="subtle">加载中…</span>
      </div>

      <div v-else-if="error" class="error-bar">
        <span>{{ error }}</span>
      </div>

      <div v-else-if="projects.length === 0" class="empty-state">
        <span class="empty-icon">📂</span>
        <p class="body-lg">暂无项目</p>
        <p class="body-sm subtle">创建你的第一个项目开始体验</p>
        <RouterLink to="/" class="btn btn-primary" style="margin-top: var(--space-md);">
          创建项目
        </RouterLink>
      </div>

      <div v-else class="history-grid">
        <div
          v-for="p in projects"
          :key="p.id"
          class="history-card"
          @click="openProject(p.id)"
        >
          <div class="card-header">
            <h3 class="card-title">{{ p.title }}</h3>
            <div class="card-actions">
              <StatusBadge :status="p.status" />
              <button
                class="btn-delete"
                @click="deleteProject($event, p.id, p.title)"
                title="删除项目"
              >✕</button>
            </div>
          </div>
          <div class="card-meta">
            <span v-if="p.genre">{{ p.genre }}</span>
            <span v-if="p.totalChapters">共 {{ p.totalChapters }} 章</span>
            <span v-if="p.sourceNovel">{{ p.sourceNovel }}</span>
          </div>
          <div class="card-footer">
            <span class="caption subtle">{{ formatDate(p.updatedAt) }}</span>
            <span class="arrow">→</span>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.history {
  padding: var(--space-xl) 0 var(--space-section);
  min-height: calc(100vh - 56px);
}
.history-header {
  max-width: 800px;
  margin: 0 auto var(--space-xl);
}
.history-header .eyebrow { margin-bottom: 6px; }
.history-header h1 { margin: 0 0 6px; }

/* Loading */
.loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-xxl) 0;
}
.spinner-lg {
  width: 32px; height: 32px;
  border: 3px solid var(--color-hairline-strong);
  border-right-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* Error */
.error-bar {
  background: rgba(240,104,104,0.06);
  border: 1px solid rgba(240,104,104,0.2);
  color: var(--color-semantic-error);
  padding: 10px 14px;
  border-radius: var(--radius-md);
  font-size: var(--text-body-sm);
}

/* Empty state */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: var(--space-xxl) 0;
}
.empty-icon {
  font-size: 48px;
  margin-bottom: var(--space-md);
  opacity: 0.6;
}
.empty-state p { margin: 0 0 4px; }

/* Grid */
.history-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: var(--space-md);
  max-width: 1000px;
  margin: 0 auto;
}

/* Card */
.history-card {
  position: relative;
  background: var(--color-surface-1);
  border: 1px solid var(--color-hairline);
  border-radius: var(--radius-lg);
  padding: var(--space-lg);
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-out-cubic);
}
.history-card:hover {
  border-color: var(--color-hairline-strong);
  transform: translateY(-3px);
  box-shadow: var(--shadow-card-hover);
}
.history-card::before {
  content: '';
  position: absolute;
  left: 0; right: 0; top: 0; height: 2px;
  background: linear-gradient(90deg, var(--color-primary) 0%, #a78bfa 50%, transparent 100%);
  border-radius: var(--radius-lg) var(--radius-lg) 0 0;
  opacity: 0;
  transition: opacity var(--duration-base) var(--ease-out-cubic);
}
.history-card:hover::before { opacity: 1; }

.card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-sm);
  margin-bottom: var(--space-md);
}
.card-header .card-title {
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--text-body-lg);
  color: var(--color-ink);
}
.card-actions {
  display: flex; align-items: center; gap: var(--space-sm); flex-shrink: 0;
}
.btn-delete {
  background: none; border: none; cursor: pointer;
  color: var(--color-ink-subtle);
  font-size: 14px; width: 24px; height: 24px;
  display: flex; align-items: center; justify-content: center;
  border-radius: var(--radius-sm);
  opacity: 0; transition: all var(--duration-fast) var(--ease-out-quad);
}
.btn-delete:hover {
  color: var(--color-semantic-error);
  background: rgba(240,104,104,0.1);
}
.history-card:hover .btn-delete { opacity: 1; }

.card-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
  margin-bottom: var(--space-md);
}
.card-meta span {
  background: var(--color-surface-2);
  padding: 2px 10px;
  border-radius: var(--radius-sm);
  font-size: var(--text-caption);
  color: var(--color-ink-subtle);
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: var(--space-sm);
  border-top: 1px solid var(--color-hairline);
}
.card-footer .arrow {
  color: var(--color-primary);
  font-size: var(--text-body-lg);
  transition: transform var(--duration-fast) var(--ease-out-quad);
}
.history-card:hover .arrow {
  transform: translateX(3px);
}

@media (max-width: 600px) {
  .history-grid {
    grid-template-columns: 1fr;
  }
}
</style>
