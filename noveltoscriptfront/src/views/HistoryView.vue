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
  animateRows()
})

function animateRows() {
  const rows = document.querySelectorAll('.history-row')
  if (!rows.length) return
  gsap.from(rows, {
    y: 10, opacity: 0, duration: 0.35, stagger: 0.04, ease: 'power2.out'
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
    title: `删除「${title}」？`,
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
    <div class="history-container">
      <header class="history-bar">
        <div>
          <h1 class="history-title">历史记录</h1>
          <p class="history-count" v-if="projects.length">共 {{ projects.length }} 个项目</p>
        </div>
        <RouterLink to="/" class="bar-btn">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
          </svg>
          新建项目
        </RouterLink>
      </header>

      <!-- Loading -->
      <div v-if="loading" class="state-line">
        <div class="spinner" />
        <span>加载中</span>
      </div>

      <!-- Error -->
      <div v-else-if="error" class="error-line">{{ error }}</div>

      <!-- Empty -->
      <div v-else-if="projects.length === 0" class="state-line">
        <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round" class="state-icon">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
          <polyline points="14 2 14 8 20 8"/>
          <line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/>
        </svg>
        <span>暂无项目</span>
        <RouterLink to="/" class="bar-btn">创建第一个项目</RouterLink>
      </div>

      <!-- Table -->
      <div v-else class="history-table">
        <div class="table-head">
          <span class="th-name">项目</span>
          <span class="th-meta">章节</span>
          <span class="th-meta">状态</span>
          <span class="th-date">更新时间</span>
          <span class="th-action"></span>
        </div>
        <div
          v-for="p in projects"
          :key="p.id"
          class="history-row"
          @click="openProject(p.id)"
        >
          <div class="td-name">
            <span class="td-title">{{ p.title }}</span>
            <span class="td-sub" v-if="p.sourceNovel || p.genre">{{ p.sourceNovel || p.genre }}</span>
          </div>
          <div class="td-meta">
            <span class="td-chapter">{{ p.totalChapters || '-' }}</span>
          </div>
          <div class="td-meta">
            <StatusBadge :status="p.status" />
          </div>
          <div class="td-date">{{ formatDate(p.updatedAt) }}</div>
          <div class="td-action" @click.stop>
            <button
              class="td-del"
              @click="deleteProject($event, p.id, p.title)"
              title="删除项目"
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
              </svg>
            </button>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.history {
  min-height: calc(100vh - 52px);
}
.history-container {
  max-width: 960px;
  margin: 0 auto;
  padding: var(--space-xl) var(--space-lg) var(--space-section);
}

/* Header bar */
.history-bar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: var(--space-lg);
}
.history-title {
  font-family: var(--font-display);
  font-size: var(--text-headline);
  font-weight: 600;
  color: var(--color-ink);
  margin: 0 0 2px;
}
.history-count {
  font-size: var(--text-body-sm);
  color: var(--color-ink-subtle);
  margin: 0;
}

.bar-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
  height: 34px;
  padding: 0 14px;
  background: var(--color-primary);
  color: var(--color-on-primary);
  border-radius: var(--radius-sm);
  font-size: var(--text-button);
  font-weight: 500;
  transition: background var(--duration-fast) var(--ease-out-quad);
  text-decoration: none;
}
.bar-btn:hover { background: var(--color-primary-hover); }

/* States */
.state-line {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-xxl) 0;
  color: var(--color-ink-subtle);
  font-size: var(--text-body-sm);
}
.state-icon { opacity: 0.25; }
.spinner {
  width: 18px; height: 18px;
  border: 2px solid var(--color-hairline-strong);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.error-line {
  padding: 9px 12px;
  background: rgba(196, 122, 106, 0.06);
  border: 1px solid rgba(196, 122, 106, 0.2);
  color: var(--color-semantic-error);
  border-radius: var(--radius-sm);
  font-size: var(--text-body-sm);
}

/* Table */
.history-table {
  border: 1px solid var(--color-hairline);
  border-radius: var(--radius-md);
  overflow: hidden;
}
.table-head {
  display: grid;
  grid-template-columns: 1fr 60px 90px 150px 40px;
  gap: var(--space-sm);
  padding: 10px var(--space-md);
  background: var(--color-surface-1);
  border-bottom: 1px solid var(--color-hairline);
  font-size: var(--text-caption);
  font-weight: 500;
  color: var(--color-ink-tertiary);
  letter-spacing: 0.05em;
}
.th-name { text-align: left; }
.th-meta { text-align: center; }
.th-date { text-align: right; }
.th-action { text-align: center; }

.history-row {
  display: grid;
  grid-template-columns: 1fr 60px 90px 150px 40px;
  gap: var(--space-sm);
  padding: 14px var(--space-md);
  background: transparent;
  border-bottom: 1px solid var(--color-hairline);
  cursor: pointer;
  transition: background var(--duration-fast) var(--ease-out-quad);
  align-items: center;
}
.history-row:last-child { border-bottom: 0; }
.history-row:hover { background: var(--color-surface-1); }

.td-name {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.td-title {
  font-size: var(--text-body);
  font-weight: 500;
  color: var(--color-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.td-sub {
  font-size: var(--text-caption);
  color: var(--color-ink-subtle);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.td-meta {
  display: flex;
  align-items: center;
  justify-content: center;
}
.td-chapter {
  font-family: var(--font-mono);
  font-size: var(--text-body-sm);
  color: var(--color-ink-muted);
}
.td-date {
  font-size: var(--text-body-sm);
  color: var(--color-ink-subtle);
  text-align: right;
}
.td-action {
  display: flex;
  justify-content: center;
}
.td-del {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px; height: 28px;
  border-radius: var(--radius-xs);
  color: var(--color-ink-tertiary);
  opacity: 0;
  transition: all var(--duration-fast) var(--ease-out-quad);
}
.history-row:hover .td-del { opacity: 1; }
.td-del:hover {
  color: var(--color-semantic-error);
  background: rgba(196, 122, 106, 0.1);
}

@media (max-width: 700px) {
  .table-head { display: none; }
  .history-row {
    grid-template-columns: 1fr auto;
    gap: var(--space-xs);
    padding: var(--space-md);
  }
  .td-meta { display: none; }
  .td-date { display: none; }
  .td-action { display: flex; align-items: center; }
  .td-del { opacity: 1; }
}
</style>
