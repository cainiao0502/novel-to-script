<script setup>
import { onMounted, onBeforeUnmount, ref, computed, watch, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import gsap from 'gsap'
import { parse as parseYaml, stringify as stringifyYaml } from 'yaml'
import { api } from '@/api'
import { useConfirm } from '@/composables/useConfirm'
import StatusBadge from '@/components/StatusBadge.vue'
import ScriptEditor from '@/components/ScriptEditor.vue'
import ScriptRender from '@/components/ScriptRender.vue'
import ScreenplayPreview from '@/components/ScreenplayPreview.vue'
import EmotionCurve from '@/components/EmotionCurve.vue'
import ProgressBar from '@/components/ProgressBar.vue'
import ScriptStats from '@/components/ScriptStats.vue'

const route = useRoute()
const projectId = Number(route.params.id)

const project = ref(null)
const chapters = ref([])
const loading = ref(true)
const error = ref('')
let errorTimer = null
function showError(msg) {
  error.value = msg
  clearTimeout(errorTimer)
  errorTimer = setTimeout(() => { error.value = '' }, 5000)
}
const editorRef = ref(null)
const scriptRenderRef = ref(null)
const selectedChapter = ref(null)
const viewMode = ref('script')
const isFullscreen = ref(false)

// ── Emotion curve ──
const emotionData = ref(null) // { arcs: [...], source: 'cache'|'fresh' }
const emotionLoading = ref(false)
const chapterEmotionCache = ref({}) // { [chapterId]: data }
const chapterEmotionLoading = ref(false)
const { confirm: showConfirm } = useConfirm()

function toggleFullscreen() {
  isFullscreen.value = !isFullscreen.value
}

function switchView(mode) {
  if (mode === viewMode.value) return
  viewMode.value = mode
  nextTick(() => {
    if (mode === 'script') scriptRenderRef.value?.animateIn()
  })
  // 切换视图时清除情感数据缓存
  if (mode !== 'emotion') {
    chapterEmotionCache.value = {}
  }
}

async function analyzeEmotions() {
  if (emotionLoading.value) return
  emotionLoading.value = true
  emotionData.value = null
  try {
    const result = await api.analyzeEmotions(projectId)
    emotionData.value = result
  } catch (e) {
    showError('情感分析失败：' + e.message)
  } finally {
    emotionLoading.value = false
  }
}

async function analyzeChapterEmotions(chapterId) {
  // 已有缓存直接跳过
  if (chapterEmotionCache.value[chapterId]) return
  if (chapterEmotionLoading.value) return
  chapterEmotionLoading.value = true
  try {
    const result = await api.analyzeChapterEmotions(projectId, chapterId)
    chapterEmotionCache.value = { ...chapterEmotionCache.value, [chapterId]: result }
  } catch (e) {
    showError('章节情感分析失败：' + e.message)
  } finally {
    chapterEmotionLoading.value = false
  }
}

function openEmotionAnalysis() {
  if (viewMode.value !== 'emotion') return
  if (selectedChapter.value) {
    const cid = selectedChapter.value.id
    if (!chapterEmotionCache.value[cid] && !chapterEmotionLoading.value) {
      analyzeChapterEmotions(cid)
    }
  } else {
    if (!emotionData.value && !emotionLoading.value) {
      analyzeEmotions()
    }
  }
}

let pollHandle = null

const statusTone = (s) => ({
  DRAFT: '', PENDING: 'running', GENERATING: 'running',
  PARTIAL_SUCCESS: 'warning', COMPLETED: 'success',
  FAILED: 'error', DONE: 'success',
  QUEUED: 'queued'
})[s] || ''

const statusLabel = (s) => ({
  DRAFT: '草稿', PENDING: '待处理', GENERATING: '生成中',
  PARTIAL_SUCCESS: '部分成功', COMPLETED: '已完成',
  FAILED: '失败', DONE: '已生成',
  QUEUED: '排队中'
})[s] || s

const liveProgress = ref(0)
const liveCurrent = ref(0)
const liveTotal = ref(0)
const regeneratingIds = ref(new Set())
const compareState = ref(null)
// { originalYaml, newYaml, chapterId, chapterLabel, isFull }

// Chapters with ordered display: DONE chapters that have earlier in-progress siblings show as QUEUED
const displayChapters = computed(() => {
  const sorted = [...chapters.value].sort((a, b) => a.idx - b.idx)
  return sorted.map((ch, i) => {
    let ds = ch.status
    if (ch.status === 'DONE') {
      for (let j = 0; j < i; j++) {
        const prev = sorted[j]
        if (['GENERATING', 'PENDING'].includes(prev.status) && !regeneratingIds.value.has(prev.id)) {
          ds = 'QUEUED'
          break
        }
      }
    }
    return { ...ch, displayStatus: ds }
  })
})

const displayChaptersForBar = computed(() =>
  displayChapters.value.map(ch => ({ idx: ch.idx, status: ch.displayStatus }))
)

const prevChapterStatuses = {}
const chapterToasts = ref([])
const chapterRefs = {}
function setChapterRef(el, id) { if (el) chapterRefs[id] = el; else delete chapterRefs[id] }

async function load() {
  try {
    const p = await api.getProject(projectId)
    project.value = p
    chapters.value = p.chapters || []
    if (p.liveProgress) {
      liveProgress.value = p.liveProgress.progress || 0
      liveCurrent.value = p.liveProgress.currentChapter || 0
      liveTotal.value = p.liveProgress.totalChapters || p.totalChapters || 0
    } else {
      liveProgress.value = p.progress || 0
      liveCurrent.value = p.currentChapter || 0
      liveTotal.value = p.totalChapters || 0
    }
    loading.value = false
  } catch (e) {
    showError(e.message)
    loading.value = false
  }
}

function addChapterToast(msg) {
  const id = Date.now() + Math.random()
  chapterToasts.value.push({ id, msg })
  setTimeout(() => {
    const idx = chapterToasts.value.findIndex(t => t.id === id)
    if (idx >= 0) chapterToasts.value.splice(idx, 1)
  }, 3500)
}

function onChaptersChanged(newChapters) {
  let shouldAutoSelect = false
  newChapters.forEach(ch => {
    const prev = prevChapterStatuses[ch.id]
    if (prev && prev !== ch.status) {
      shouldAutoSelect = true
      nextTick(() => {
        const el = chapterRefs[ch.id]
        if (ch.status === 'DONE') {
          // Populate comparison newYaml when regenerated chapter completes
          if (compareState.value?.chapterId === ch.id && ch.generatedYaml) {
            compareState.value = { ...compareState.value, newYaml: ch.generatedYaml }
          }
          addChapterToast(`第 ${ch.idx} 章生成完成 ✓`)
          if (el) {
            gsap.from(el, { scale: 0.96, opacity: 0.6, duration: 0.45, ease: 'power2.out' })
            el.classList.add('just-done')
            setTimeout(() => el.classList.remove('just-done'), 2000)
          }
        } else if (ch.status === 'FAILED') {
          addChapterToast(`第 ${ch.idx} 章生成失败 ✗`)
        } else if (ch.status === 'GENERATING' && el) {
          el.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
        }
        // Clean up regenerating flag when chapter finishes
        if ((ch.status === 'DONE' || ch.status === 'FAILED') && regeneratingIds.value.has(ch.id)) {
          regeneratingIds.value.delete(ch.id)
        }
      })
    }
    prevChapterStatuses[ch.id] = ch.status
  })
  // 自动选中章节
  if (shouldAutoSelect) {
    nextTick(() => autoSelectChapter(newChapters))
  }
}

const chaptersInitialized = ref(false)
watch(() => chapters.value, (chs) => {
  if (!chs) return
  if (chaptersInitialized.value) {
    onChaptersChanged(chs)
  } else {
    chs.forEach(ch => { prevChapterStatuses[ch.id] = ch.status })
    chaptersInitialized.value = true
  }
}, { deep: true })

function startPolling() {
  stopPolling()
  pollHandle = setInterval(load, 2500)
}
function stopPolling() {
  if (pollHandle) { clearInterval(pollHandle); pollHandle = null }
}

onMounted(async () => {
  await load()
  await nextTick()
  animateIn()
  // 自动选中章节
  if (chapters.value.length) {
    autoSelectChapter(chapters.value)
  }
  if (project.value && ['GENERATING', 'PENDING'].includes(project.value.status)) {
    startPolling()
  }
  document.addEventListener('keydown', onKeyDown)
})

onBeforeUnmount(() => {
  stopPolling()
  document.removeEventListener('keydown', onKeyDown)
})

watch(() => project.value?.status, (s, prev) => {
  if (s && ['GENERATING', 'PENDING'].includes(s)) startPolling()
  else stopPolling()
  // Populate comparison newYaml when full regeneration completes
  if (compareState.value?.isFull && s === 'COMPLETED' && project.value?.scriptYaml) {
    compareState.value = { ...compareState.value, newYaml: project.value.scriptYaml }
  }
  // Completion celebration
  if (s && ['COMPLETED', 'PARTIAL_SUCCESS'].includes(s) && prev && ['GENERATING', 'PENDING'].includes(prev)) {
    nextTick(() => {
      const bar = document.querySelector('.progress-block')
      const bench = document.querySelector('.workbench')
      if (bar) gsap.from(bar, { scale: 1.02, duration: 0.5, ease: 'power2.out' })
      if (bench) gsap.from(bench, { scale: 0.99, opacity: 0.9, duration: 0.6, ease: 'power2.out', delay: 0.1 })
    })
  }
})

// 切换选中章节时自动加载章节情感曲线（有缓存则不重复请求）
watch(selectedChapter, (ch) => {
  if (viewMode.value === 'emotion' && ch) {
    const cid = ch.id
    if (!chapterEmotionCache.value[cid] && !chapterEmotionLoading.value) {
      analyzeChapterEmotions(cid)
    }
  }
})

function animateIn() {
  gsap.from('.header-row', { y: -8, opacity: 0, duration: 0.5, ease: 'power2.out' })
  gsap.from('.workbench', { y: 16, opacity: 0, duration: 0.6, ease: 'power2.out', delay: 0.1 })
}

async function regenerateChapter(chapterId) {
  regeneratingIds.value.add(chapterId)
  const ch = chapters.value.find(c => c.id === chapterId)
  compareState.value = {
    originalYaml: ch?.generatedYaml || '',
    chapterId,
    chapterLabel: ch ? `第 ${ch.idx} 章` : '',
    isFull: false,
    newYaml: ''
  }
  try {
    await api.regenerateChapter(projectId, chapterId)
    startPolling()
  } catch (e) {
    regeneratingIds.value.delete(chapterId)
    compareState.value = null
    showError(e.message)
  }
}

async function regenerateAll() {
  const ok = await showConfirm({
    title: '重新生成全部章节？',
    message: '将丢弃当前所有章节的生成结果，并按顺序重新生成。这会消耗额外的 AI 配额。',
    confirmText: '开始重新生成',
    cancelText: '再想想'
  })
  if (!ok) return
  compareState.value = {
    originalYaml: project.value?.scriptYaml || '',
    chapterId: null,
    chapterLabel: '完整剧本',
    isFull: true,
    newYaml: ''
  }
  try {
    await api.generate(projectId)
    startPolling()
  } catch (e) {
    compareState.value = null
    showError(e.message)
  }
}

function selectChapter(ch) {
  if (compareState.value) compareState.value = null
  selectedChapter.value = selectedChapter.value?.id === ch.id ? null : ch
}

function viewFullScript() {
  selectedChapter.value = null
}

function onKeyDown(e) {
  if (e.key === 'Escape' && isFullscreen.value) {
    isFullscreen.value = false
  }
}

function onCompareAcceptNew() {
  compareState.value = null
}

async function onCompareAcceptOriginal() {
  const st = compareState.value
  if (!st) return
  try {
    if (st.isFull) {
      await api.restoreProjectYaml(projectId, st.originalYaml)
    } else {
      await api.restoreChapterYaml(projectId, st.chapterId, st.originalYaml)
    }
    compareState.value = null
    await load()
  } catch (e) {
    showError(e.message)
  }
}

function onCompareCancel() {
  compareState.value = null
}

// ── AI 改稿 ──

async function onRewriteDialogue({ sceneId, dialogueIndex, line, character, style, context }) {
  try {
    const result = await api.rewriteDialogue(projectId, {
      style,
      currentLine: line,
      context
    })
    scriptRenderRef.value?.showRewriteResult(
      sceneId, dialogueIndex, line, result.rewrittenLine
    )
  } catch (e) {
    scriptRenderRef.value?.onRewriteDone()
    showError('AI 改稿失败：' + e.message)
  }
}

async function onAcceptRewrite({ sceneId, dialogueIndex, rewrittenLine }) {
  // Determine which YAML to modify: chapter-level or project-level
  const yamlSource = selectedChapter.value?.generatedYaml || project.value?.scriptYaml
  if (!yamlSource) return
  try {
    const doc = parseYaml(yamlSource)
    const scene = doc?.scenes?.find(s => (s.scene_id || '') === sceneId)
    if (!scene?.dialogues?.[dialogueIndex]) return
    scene.dialogues[dialogueIndex].line = rewrittenLine
    // Serialize back to YAML
    const newYaml = stringifyYaml(doc)
    if (selectedChapter.value) {
      await api.restoreChapterYaml(projectId, selectedChapter.value.id, newYaml)
    } else {
      await api.restoreProjectYaml(projectId, newYaml)
    }
    await load()
  } catch (e) {
    showError('保存改稿失败：' + e.message)
  }
}

async function copyYaml() {
  if (!project.value?.scriptYaml) return
  try {
    await navigator.clipboard.writeText(project.value.scriptYaml)
    flashToast('已复制到剪贴板')
  } catch {
    flashToast('复制失败')
  }
}

function downloadYaml() {
  window.open(api.scriptYamlUrl(projectId), '_blank')
}

const toast = ref('')
let toastHandle = null
function flashToast(msg) {
  toast.value = msg
  clearTimeout(toastHandle)
  toastHandle = setTimeout(() => (toast.value = ''), 1800)
}

function roleLabel(r) {
  return ({ protagonist: '主角', antagonist: '反派', supporting: '配角', npc: 'NPC' })[r] || r
}

const CHAR_COLORS = [
  '#B8A9E8', '#F0C8A0', '#A8D8A8', '#A0C4E8',
  '#F0A8A8', '#C8B0E0', '#A0D8D0', '#E8D0A0',
]
function getCharColor(id) {
  let h = 0
  for (const ch of id) h = (h * 31 + ch.charCodeAt(0)) & 0xffff
  return CHAR_COLORS[Math.abs(h) % CHAR_COLORS.length]
}

// 从章节 YAML 中提取出现的人物 ID
function extractCharacterIds(yamlStr) {
  if (!yamlStr) return []
  try {
    const doc = parseYaml(yamlStr)
    if (!doc?.scenes) return []
    const ids = new Set()
    for (const scene of doc.scenes) {
      if (Array.isArray(scene.characters)) {
        scene.characters.forEach(id => ids.add(id))
      }
    }
    return [...ids]
  } catch { return [] }
}

// 右侧人物面板：选章时从该章 YAML 直接解析，否则用全局人物表
const displayCharacters = computed(() => {
  if (!selectedChapter.value) {
    return project.value?.characters || []
  }
  // 优先从章节 YAML 的 characters 段直接提取
  if (selectedChapter.value.generatedYaml) {
    try {
      const parsed = parseYaml(selectedChapter.value.generatedYaml)
      const chars = parsed?.characters
      if (chars?.length) {
        return chars.map(c => ({
          charId: c.id,
          name: c.name,
          role: c.role || 'npc'
        }))
      }
    } catch { /* fall through */ }
  }
  // 兜底：用全局人物表按场景角色 ID 过滤
  const all = project.value?.characters || []
  const charIds = extractCharacterIds(selectedChapter.value.generatedYaml)
  if (charIds.length) return all.filter(c => charIds.includes(c.charId))
  return []
})

// 自动选中章节：生成中 → 完成后自动跳到下一个生成中的
function autoSelectChapter(chs) {
  const sorted = [...chs].sort((a, b) => a.idx - b.idx)
  // 当前选中章节仍在生成 → 保持不动
  if (selectedChapter.value) {
    const cur = sorted.find(c => c.id === selectedChapter.value.id)
    if (cur && cur.status === 'GENERATING') return
    // 当前章节已完成 → 跳到下一个生成中的
    if (cur && (cur.status === 'DONE' || cur.status === 'FAILED')) {
      const next = sorted.find(c => c.status === 'GENERATING')
      if (next) { selectedChapter.value = next; return }
      selectedChapter.value = null
      return
    }
  }
  // 未选中 → 选第一个生成中的
  if (!selectedChapter.value) {
    const gen = sorted.find(c => c.status === 'GENERATING')
    if (gen) selectedChapter.value = gen
  }
}
</script>

<template>
  <section class="project">
    <div class="container">
      <div v-if="loading" class="loading">
        <span class="spinner-lg" />
        <span>加载中…</span>
      </div>

      <template v-else-if="project">
        <!-- Unified project toolbar -->
        <div class="project-toolbar">
          <div class="pt-left">
            <RouterLink to="/history" class="pt-back" title="返回列表">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <line x1="19" y1="12" x2="5" y2="12"/><polyline points="12 19 5 12 12 5"/>
              </svg>
            </RouterLink>
            <div class="pt-info">
              <h1 class="pt-title">{{ project.title }}</h1>
              <p class="pt-meta">
                {{ project.sourceNovel || '未填写原著' }} ·
                {{ project.genre || '未填写题材' }} ·
                共 {{ project.totalChapters }} 章
              </p>
            </div>
          </div>
          <div class="pt-right">
            <StatusBadge :status="project.status" />
            <div class="pt-actions">
              <button class="pt-btn" @click="copyYaml" :disabled="!project.scriptYaml" title="复制 YAML">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
                </svg>
              </button>
              <button class="pt-btn pt-btn-primary" @click="downloadYaml" :disabled="!project.scriptYaml" title="下载 YAML">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/>
                </svg>
              </button>
            </div>
          </div>
        </div>

        <ProgressBar
          v-if="liveTotal"
          :progress="liveProgress"
          :current="liveCurrent"
          :total="liveTotal"
          :status="project.status"
          :chapters="displayChaptersForBar"
        />

        <div v-if="error" class="error-bar">{{ error }}</div>

        <ScriptStats
          :yaml="selectedChapter?.generatedYaml || project.scriptYaml"
        />

        <!-- Three column workbench -->
        <div class="workbench" :class="{ 'is-comparing': compareState }">
          <!-- Chapter list -->
          <aside v-if="!isFullscreen" class="panel panel-left">
            <header class="panel-head">
              <div class="ph-left">
                <span class="ph-label">章节</span>
                <span class="ph-count">{{ chapters.length }}</span>
              </div>
              <button
                class="ph-action"
                @click="regenerateAll"
                :disabled="project.status === 'GENERATING'"
                title="全部重新生成"
              >
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                  <polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
                </svg>
              </button>
            </header>
            <ol class="chapters">
              <li
                v-for="ch in displayChapters"
                :key="ch.id"
                :ref="(el) => setChapterRef(el, ch.id)"
                class="chapter"
                :class="[`is-${ch.displayStatus.toLowerCase()}`, { 'is-selected': selectedChapter?.id === ch.id }]"
                @click="selectChapter(ch)"
              >
                <div class="chapter-line">
                  <span class="ch-num">{{ String(ch.idx).padStart(2, '0') }}</span>
                  <span class="ch-title">{{ ch.title }}</span>
                  <button
                    class="btn btn-tertiary btn-mini btn-regen"
                    @click.stop="regenerateChapter(ch.id)"
                    :disabled="['GENERATING','PENDING'].includes(ch.status)"
                    title="重新生成"
                  >
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                      <polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
                    </svg>
                  </button>
                </div>
                <div class="chapter-sub">
                  <span class="badge" :class="statusTone(ch.displayStatus)">
                    {{ statusLabel(ch.displayStatus) }}
                  </span>
                  <span v-if="ch.sceneCount" class="caption">{{ ch.sceneCount }} 场</span>
                </div>
                <div v-if="ch.errorMessage" class="ch-error caption">{{ ch.errorMessage }}</div>
              </li>
            </ol>
          </aside>

          <!-- Editor -->
          <main class="panel panel-main" :class="{ 'is-fullscreen': isFullscreen }">
            <!-- Split comparison mode -->
            <template v-if="compareState">
              <div class="compare-split">
                <div class="compare-col">
                  <div class="compare-col-head">
                    <span class="compare-badge">{{ compareState.chapterLabel }} · 当前版本</span>
                  </div>
                  <div class="compare-col-body">
                    <ScriptRender :yaml="compareState.originalYaml" :is-generating="false" />
                  </div>
                  <div class="compare-col-foot" v-if="compareState.newYaml">
                    <button class="keep-btn keep-ghost" @click="onCompareAcceptOriginal">保留此版本</button>
                  </div>
                </div>
                <div class="compare-divider" />
                <div class="compare-col is-new">
                  <div class="compare-col-head">
                    <span class="compare-badge new">{{ compareState.chapterLabel }} · 新版本</span>
                    <span v-if="!compareState.newYaml" class="writing-badge"><span class="writing-dot" />生成中</span>
                  <button v-else class="btn btn-tertiary btn-mini" @click="onCompareCancel">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                      <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
                    </svg>
                    取消
                  </button>
                  </div>
                  <div class="compare-col-body">
                    <ScriptRender :yaml="compareState.newYaml" :is-generating="!compareState.newYaml" />
                  </div>
                  <div class="compare-col-foot" v-if="compareState.newYaml">
                    <button class="keep-btn keep-primary" @click="onCompareAcceptNew">保留此版本</button>
                  </div>
                </div>
              </div>
            </template>

            <!-- Normal single-panel view -->
            <template v-else>
              <header class="panel-head editor-head">
                <div class="ph-left">
                  <div class="editor-tabs">
                    <button
                      class="etab"
                      :class="{ active: viewMode === 'script' }"
                      @click="switchView('script')"
                    >剧本</button>
                    <button
                      class="etab"
                      :class="{ active: viewMode === 'yaml' }"
                      @click="switchView('yaml')"
                    >YAML</button>
                    <button
                      class="etab"
                      :class="{ active: viewMode === 'emotion' }"
                      @click="switchView('emotion'); openEmotionAnalysis()"
                    >情感曲线</button>
                    <button
                      class="etab"
                      :class="{ active: viewMode === 'preview' }"
                      @click="switchView('preview')"
                    >导出预览</button>
                  </div>
                  <span class="ph-context" v-if="selectedChapter">第 {{ selectedChapter.idx }} 章</span>
                  <span v-if="selectedChapter?.status === 'GENERATING'" class="ph-gen">
                    <span class="ph-dot" />AI 生成中
                  </span>
                </div>
                <div class="ph-right">
                  <button
                    v-if="selectedChapter"
                    class="ph-action"
                    @click="viewFullScript"
                    title="查看完整剧本"
                  >
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/>
                    </svg>
                  </button>
                  <button
                    class="ph-action"
                    @click="toggleFullscreen"
                    :title="isFullscreen ? '退出全屏 (Esc)' : '全屏阅读'"
                  >
                    <svg v-if="isFullscreen" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <polyline points="4 14 10 14 10 20"/><polyline points="20 10 14 10 14 4"/>
                      <line x1="14" y1="10" x2="21" y2="3"/><line x1="3" y1="21" x2="10" y2="14"/>
                    </svg>
                    <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <polyline points="15 3 21 3 21 9"/><polyline points="9 21 3 21 3 15"/>
                      <line x1="21" y1="3" x2="14" y2="10"/><line x1="3" y1="21" x2="10" y2="14"/>
                    </svg>
                  </button>
                </div>
              </header>
              <ScriptRender
                v-if="viewMode === 'script'"
                ref="scriptRenderRef"
                :yaml="selectedChapter?.generatedYaml || project.scriptYaml"
                :is-generating="!project.scriptYaml && ['GENERATING','PENDING'].includes(project.status)"
                @rewrite-dialogue="onRewriteDialogue"
                @accept-rewrite="onAcceptRewrite"
              />
              <ScriptEditor
                v-else-if="viewMode === 'yaml'"
                ref="editorRef"
                :yaml="selectedChapter?.generatedYaml || project.scriptYaml"
                :read-only="true"
              />
              <EmotionCurve
                v-else-if="viewMode === 'emotion'"
                :arcs="selectedChapter ? (chapterEmotionCache[selectedChapter.id]?.arcs || []) : (emotionData?.arcs || [])"
                :loading="selectedChapter ? (chapterEmotionLoading && !chapterEmotionCache[selectedChapter.id]) : emotionLoading"
                :context="selectedChapter ? `第 ${selectedChapter.idx} 章` : '全剧'"
              />
              <ScreenplayPreview
                v-else-if="viewMode === 'preview'"
                :yaml="selectedChapter?.generatedYaml || project.scriptYaml"
              />
            </template>
          </main>

          <!-- Right panel: characters + scenes summary -->
          <aside v-if="!isFullscreen && !compareState" class="panel panel-right">
            <header class="panel-head">
              <div class="ph-left">
                <span class="ph-label">人物</span>
                <span class="ph-count">{{ displayCharacters.length }}</span>
              </div>
            </header>
            <ul class="char-list">
              <li v-for="c in displayCharacters" :key="c.charId" class="char-item">
                <span class="char-avatar" :class="c.role === 'protagonist' ? 'protagonist' : (c.role === 'antagonist' ? 'antagonist' : '')">{{ (c.name || '?').slice(0,1) }}</span>
                <div class="char-info">
                  <span class="char-name">{{ c.name }}</span>
                  <span class="char-id mono">{{ c.charId }}</span>
                </div>
                <span class="badge" :class="c.role === 'protagonist' ? 'success' : (c.role === 'antagonist' ? 'error' : '')">
                  {{ roleLabel(c.role) }}
                </span>
              </li>
              <li v-if="!displayCharacters.length" class="char-empty">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" class="char-empty-icon">
                  <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>
                </svg>
                <span class="body-sm subtle">{{ selectedChapter ? '该章节暂无人物' : '剧本生成后自动列出人物' }}</span>
              </li>
            </ul>

            <div v-if="project.errorMessage" class="project-error">
              <p class="eyebrow error-text">生成异常</p>
              <p class="body-sm">{{ project.errorMessage }}</p>
            </div>
          </aside>
        </div>
      </template>

      <div v-else class="error-bar">未找到项目 #{{ projectId }}</div>
    </div>

    <transition name="toast">
      <div v-if="toast" class="toast">{{ toast }}</div>
    </transition>
    <!-- Chapter completion notifications -->
    <div class="chapter-toast-stack" v-if="chapterToasts.length">
      <transition-group name="chapter-toast">
        <div
          v-for="t in chapterToasts"
          :key="t.id"
          class="chapter-toast-item"
        >{{ t.msg }}</div>
      </transition-group>
    </div>
  </section>
</template>

<style scoped>
.project { padding: var(--space-lg) 0 var(--space-section); min-height: calc(100vh - 56px); }

.loading {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: var(--space-md); padding: var(--space-xxl);
  color: var(--color-ink-subtle);
}
.spinner-lg {
  width: 24px; height: 24px;
  border: 2.5px solid var(--color-hairline-strong);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* Project toolbar */
.project-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  margin-bottom: var(--space-lg);
  padding: 0 0 var(--space-md) 0;
  border-bottom: 1px solid var(--color-hairline);
}
.pt-left {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  min-width: 0;
}
.pt-back {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px; height: 30px;
  border-radius: var(--radius-sm);
  color: var(--color-ink-subtle);
  flex-shrink: 0;
  transition: all var(--duration-fast) var(--ease-out-quad);
}
.pt-back:hover {
  background: var(--color-surface-1);
  color: var(--color-ink);
}
.pt-info {
  min-width: 0;
}
.pt-title {
  font-family: var(--font-display);
  font-size: var(--text-body);
  font-weight: 600;
  color: var(--color-ink);
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pt-meta {
  font-size: var(--text-caption);
  color: var(--color-ink-subtle);
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pt-right {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-shrink: 0;
}
.pt-actions {
  display: flex;
  gap: 2px;
}
.pt-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px; height: 32px;
  border-radius: var(--radius-sm);
  color: var(--color-ink-muted);
  transition: all var(--duration-fast) var(--ease-out-quad);
}
.pt-btn:hover { background: var(--color-surface-1); color: var(--color-ink); }
.pt-btn:disabled { opacity: 0.3; cursor: not-allowed; }
.pt-btn:disabled:hover { background: transparent; color: var(--color-ink-muted); }
.pt-btn-primary {
  color: var(--color-primary);
}
.pt-btn-primary:hover { background: var(--color-primary-soft); color: var(--color-primary-hover); }

.error-bar {
  background: rgba(196, 122, 106,0.06);
  border: 1px solid rgba(196, 122, 106,0.2);
  color: var(--color-semantic-error);
  padding: 10px 14px;
  border-radius: var(--radius-md);
  font-size: var(--text-body-sm);
  margin-bottom: var(--space-md);
}

.workbench {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr) 320px;
  gap: var(--space-md);
  align-items: start;
}
.workbench.is-comparing {
  grid-template-columns: 280px 1fr;
}
.panel {
  position: relative;
  background: var(--color-surface-1);
  border: 1px solid var(--color-hairline);
  border-radius: var(--radius-lg);
  overflow: hidden;
  min-height: 600px;
}
.panel::before {
  content: '';
  position: absolute; top: 0; left: 0; right: 0; height: 1px;
  background: linear-gradient(90deg, transparent 0%, rgba(255,255,255,0.06) 50%, transparent 100%);
  pointer-events: none;
}
.panel-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px var(--space-sm);
  border-bottom: 1px solid var(--color-hairline);
  min-height: 40px;
}
.ph-left {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  min-width: 0;
}
.ph-right {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}
.ph-label {
  font-size: var(--text-caption);
  font-weight: 600;
  color: var(--color-ink-muted);
  letter-spacing: 0.05em;
  text-transform: uppercase;
}
.ph-count {
  font-family: var(--font-mono);
  font-size: var(--text-caption);
  color: var(--color-ink-tertiary);
}
.ph-action {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px; height: 28px;
  border-radius: var(--radius-xs);
  color: var(--color-ink-tertiary);
  transition: all var(--duration-fast) var(--ease-out-quad);
  flex-shrink: 0;
}
.ph-action:hover {
  background: var(--color-surface-2);
  color: var(--color-ink-muted);
}
.ph-action:disabled {
  opacity: 0.25;
  cursor: not-allowed;
}
.ph-action:disabled:hover {
  background: transparent;
  color: var(--color-ink-tertiary);
}
.ph-context {
  font-size: var(--text-body-sm);
  color: var(--color-ink-muted);
  white-space: nowrap;
}
.ph-gen {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: var(--text-caption);
  color: var(--color-primary);
}
.ph-dot {
  width: 5px; height: 5px;
  border-radius: 50%;
  background: var(--color-primary);
  animation: ph-pulse 1s ease-in-out infinite;
}
@keyframes ph-pulse {
  0%, 100% { opacity: 0.3; transform: scale(0.8); }
  50%      { opacity: 1;   transform: scale(1.3); }
}

.panel-left { padding-bottom: var(--space-md); }
.chapters { list-style: none; padding: 0; margin: 0; max-height: 70vh; overflow: auto; }
.chapter {
  display: grid;
  grid-template-columns: 1fr auto;
  grid-template-rows: auto auto;
  column-gap: var(--space-sm);
  row-gap: 4px;
  padding: 12px var(--space-md);
  border-bottom: 1px solid var(--color-hairline);
  transition: background var(--duration-fast) var(--ease-out-quad);
}
.chapter:hover { background: var(--color-surface-2); }
.chapter:last-child { border-bottom: 0; }
.chapter.is-selected {
  background: var(--color-primary-soft);
  border-left: 3px solid var(--color-primary);
}
.chapter-line { display: flex; align-items: center; gap: 8px; min-width: 0; }
.ch-num {
  font-family: var(--font-mono);
  color: var(--color-primary);
  font-size: var(--text-caption);
  font-weight: 500;
}
.ch-title {
  font-size: var(--text-body-sm);
  color: var(--color-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.chapter-sub { display: flex; align-items: center; gap: 8px; }
.chapter-sub .badge { font-size: 10px; height: 18px; padding: 0 6px; }
.chapter-sub .badge::before { display: none; }

.btn-mini { font-size: var(--text-caption); height: 24px; padding: 0 8px; }
.btn-regen {
  font-size: 14px;
  width: 24px; height: 24px;
  padding: 0;
  display: inline-flex; align-items: center; justify-content: center;
  opacity: 0;
  transition: opacity var(--duration-fast) var(--ease-out-quad);
  flex-shrink: 0;
  cursor: pointer;
}
.btn-regen:disabled {
  opacity: 0;
  cursor: not-allowed;
}
.chapter:hover .btn-regen { opacity: 1; }
.chapter:hover .btn-regen:disabled { opacity: 0.35; }
.ch-error { grid-column: 1 / -1; color: var(--color-semantic-error); font-size: var(--text-caption); }

.panel-main { display: flex; flex-direction: column; }
.panel-main .panel-head { flex: 0 0 auto; }
.panel-main :deep(.cm-editor) { flex: 1; }

/* Fullscreen mode */
.panel-main.is-fullscreen {
  position: fixed; inset: 0; z-index: 500;
  border-radius: 0; border: 0;
  min-height: 100vh;
  background: var(--color-canvas);
}
.panel-main.is-fullscreen .script-render {
  max-height: calc(100vh - 56px);
}
.btn-fs {
  font-size: 18px; width: 28px; height: 28px; padding: 0;
  display: inline-flex; align-items: center; justify-content: center;
  opacity: 0.5; flex-shrink: 0;
}
.btn-fs:hover { opacity: 1; }

/* ── Split comparison layout ── */
.compare-split {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  flex: 1; min-height: 0;
}
.compare-divider {
  width: 1px;
  background: var(--color-hairline);
}
.compare-col {
  display: flex; flex-direction: column;
  min-height: 0;
  background: var(--color-surface-1);
}
.compare-col.is-new {
  background: var(--color-surface-2);
}
.compare-col-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px var(--space-md);
  border-bottom: 1px solid var(--color-hairline);
  flex-shrink: 0;
}
.compare-badge {
  font-size: var(--text-body-sm);
  font-weight: 500;
  color: var(--color-ink-muted);
}
.compare-badge.new {
  color: var(--color-primary);
  font-weight: 600;
}
.compare-col-body {
  flex: 1; min-height: 0;
  overflow-y: auto;
}
.compare-col-body :deep(.script-render) {
  max-height: none;
  padding: var(--space-md);
}
.compare-col-foot {
  display: flex; justify-content: center;
  padding: var(--space-sm) var(--space-md);
  border-top: 1px solid var(--color-hairline);
  flex-shrink: 0;
}

/* Keep buttons */
.keep-btn {
  height: 32px; padding: 0 18px;
  border-radius: var(--radius-pill);
  font-size: var(--text-body-sm);
  font-weight: 500;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out-quad);
}
.keep-primary {
  background: var(--color-primary);
  color: var(--color-on-primary);
  border: 0;
}
.keep-primary:hover { background: var(--color-primary-hover); }
.keep-ghost {
  background: transparent;
  color: var(--color-ink-muted);
  border: 1px solid var(--color-hairline-strong);
}
.keep-ghost:hover { background: var(--color-surface-2); color: var(--color-ink); }

.editor-title {
  display: flex; align-items: center; gap: var(--space-sm);
}
.editor-title h3 { margin: 0; display: flex; align-items: center; gap: var(--space-sm); }

/* Writing indicator */
.writing-badge {
  display: inline-flex; align-items: center; gap: 5px;
  font-size: var(--text-caption);
  font-weight: 400;
  color: var(--color-primary);
  background: var(--color-primary-soft);
  padding: 2px 10px;
  border-radius: var(--radius-pill);
  border: 1px solid rgba(125,154,110,0.2);
}
.writing-dot {
  width: 6px; height: 6px;
  border-radius: 50%;
  background: var(--color-primary);
  animation: writing-pulse 1s ease-in-out infinite;
}
@keyframes writing-pulse {
  0%, 100% { opacity: 0.3; transform: scale(0.8); }
  50%      { opacity: 1;   transform: scale(1.4); }
}

/* Editor tabs */
.editor-head {
  gap: var(--space-sm);
}
.editor-tabs {
  display: flex;
  gap: 0;
}
.etab {
  padding: 3px 10px;
  font-size: var(--text-caption);
  font-weight: 500;
  color: var(--color-ink-subtle);
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  transition: all var(--duration-fast) var(--ease-out-quad);
}
.etab.active {
  color: var(--color-ink);
  border-bottom-color: var(--color-primary);
}
.etab:hover:not(.active) { color: var(--color-ink-muted); }

.panel-right { padding-bottom: var(--space-md); }
.char-list { list-style: none; padding: 0 var(--space-md); margin: 0; }
.char-item {
  display: grid;
  grid-template-columns: 36px 1fr auto;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid var(--color-hairline);
}
.char-item:last-child { border-bottom: 0; }
.char-avatar {
  width: 36px; height: 36px; border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  background: var(--color-surface-3);
  color: var(--color-ink);
  font-weight: 600; font-size: var(--text-body-sm);
  border: 1px solid var(--color-hairline-strong);
  transition: all var(--duration-fast) var(--ease-out-quad);
}
.char-avatar.protagonist {
  background: rgba(107, 160, 123,0.1);
  border-color: rgba(107, 160, 123,0.3);
  color: var(--color-semantic-success);
}
.char-avatar.antagonist {
  background: rgba(196, 122, 106,0.1);
  border-color: rgba(196, 122, 106,0.3);
  color: var(--color-semantic-error);
}
.char-info { display: flex; flex-direction: column; min-width: 0; }
.char-name { font-size: var(--text-body-sm); color: var(--color-ink); font-weight: 500; }
.char-id { font-size: 10px; color: var(--color-ink-subtle); }
.char-item .badge { font-size: 10px; height: 18px; padding: 0 6px; }
.char-item .badge::before { display: none; }

.char-empty {
  display: flex; flex-direction: column; align-items: center; gap: 8px;
  padding: var(--space-lg) 0;
}
.char-empty-icon { width: 28px; height: 28px; opacity: 0.3; }

.project-error {
  margin: var(--space-md);
  padding: var(--space-md);
  background: rgba(201, 160, 67,0.06);
  border: 1px solid rgba(201, 160, 67,0.2);
  border-radius: var(--radius-md);
}
.error-text { color: var(--color-semantic-warning); margin: 0 0 4px; }

/* Chapter completion glow */
.chapter.just-done {
  animation: done-flash 1.8s var(--ease-out-cubic) forwards;
}
@keyframes done-flash {
  0%   { box-shadow: inset 0 0 0 0 rgba(107, 160, 123,0.3); background: rgba(107, 160, 123,0.05); }
  25%  { box-shadow: inset 0 0 24px 6px rgba(107, 160, 123,0.12); background: rgba(107, 160, 123,0.1); }
  100% { box-shadow: inset 0 0 0 0 rgba(107, 160, 123,0); background: transparent; }
}

/* Generating chapter left-edge pulse */
.chapter.is-generating {
  animation: gen-pulse 2s ease-in-out infinite;
}
@keyframes gen-pulse {
  0%, 100% { box-shadow: inset 3px 0 0 0 rgba(125,154,110,0.25); }
  50%      { box-shadow: inset 3px 0 0 0 rgba(125,154,110,0.55); }
}

/* Chapter toast stack */
.chapter-toast-stack {
  position: fixed;
  bottom: 24px;
  right: 24px;
  display: flex;
  flex-direction: column-reverse;
  gap: 8px;
  z-index: 210;
  pointer-events: none;
}
.chapter-toast-item {
  background: var(--color-surface-2);
  border: 1px solid var(--color-hairline-strong);
  color: var(--color-ink);
  padding: 10px 18px;
  border-radius: var(--radius-md);
  font-size: var(--text-body-sm);
  box-shadow: 0 4px 20px rgba(0,0,0,0.4);
  white-space: nowrap;
}
.chapter-toast-enter-active {
  transition: all 0.35s var(--ease-out-cubic);
}
.chapter-toast-leave-active {
  transition: all 0.25s var(--ease-in-out-cubic);
}
.chapter-toast-enter-from {
  opacity: 0;
  transform: translateX(20px) scale(0.95);
}
.chapter-toast-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

.toast {
  position: fixed; bottom: 24px; left: 50%; transform: translateX(-50%);
  background: var(--color-surface-2);
  border: 1px solid var(--color-hairline-strong);
  color: var(--color-ink);
  padding: 10px 18px;
  border-radius: var(--radius-pill);
  font-size: var(--text-body-sm);
  box-shadow: 0 8px 32px rgba(0,0,0,0.5);
  z-index: 200;
}
.toast-enter-active, .toast-leave-active { transition: all 240ms var(--ease-out-cubic); }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translateX(-50%) translateY(8px); }

@media (max-width: 1100px) {
  .workbench { grid-template-columns: 1fr; }
  .panel { min-height: auto; }
  .chapters { max-height: 240px; }
}
</style>
