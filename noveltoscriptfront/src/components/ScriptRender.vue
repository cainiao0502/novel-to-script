<script setup>
import { computed, onMounted, onBeforeUnmount, ref, watch, nextTick } from 'vue'
import { parse as parseYaml } from 'yaml'
import gsap from 'gsap'
import DialogueToolbar from '@/components/DialogueToolbar.vue'

const props = defineProps({
  yaml: { type: String, default: '' },
  isGenerating: { type: Boolean, default: false }
})

const emit = defineEmits(['rewrite-dialogue'])

const renderRef = ref(null)
const scrollRef = ref(null)
const parseError = ref('')
const highlightedChar = ref(null)

// ── Dialogue selection + rewrite toolbar ──
const selectedDialogue = ref(null) // { sceneId, dialogueIndex, line, character }
const rewriteLoading = ref(false)

// ── Inline manual editing ──
const editingDialogue = ref(null) // { sceneId, dialogueIndex }
const editText = ref('')
const editTextareaRef = ref(null)

// ── Scene navigation ──
const activeScene = ref(0)
let sceneObserver = null

const navScenes = computed(() => {
  if (!script.value?.scenes) return []
  return script.value.scenes.map((s, i) => ({
    index: i,
    label: `第${i + 1}场`,
    location: s.location || '未标注地点'
  }))
})

function scrollToScene(index) {
  const el = renderRef.value?.querySelector(`[data-scene-idx="${index}"]`)
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function setupSceneObserver() {
  sceneObserver?.disconnect()
  const cards = renderRef.value?.querySelectorAll('[data-scene-idx]')
  if (!cards?.length) return
  sceneObserver = new IntersectionObserver((entries) => {
    let maxRatio = 0
    let maxIdx = activeScene.value
    for (const entry of entries) {
      if (entry.intersectionRatio > maxRatio) {
        maxRatio = entry.intersectionRatio
        maxIdx = Number(entry.target.dataset.sceneIdx)
      }
    }
    if (maxRatio > 0) activeScene.value = maxIdx
  }, { root: scrollRef.value, threshold: [0, 0.25, 0.5, 0.75, 1] })
  cards.forEach(el => sceneObserver.observe(el))
}

function toggleHighlight(charId) {
  highlightedChar.value = highlightedChar.value === charId ? null : charId
}

// ── Dialogue selection ──

/** Build context string for the selected dialogue. */
function buildDialogueContext(scenes, sceneIdx, dialogueIdx) {
  const scene = scenes[sceneIdx]
  if (!scene) return ''
  const parts = []
  parts.push(`地点：${scene.location || '未标注'} · ${scene.time_of_day || ''}`)
  if (scene.summary) parts.push(`场景概要：${scene.summary}`)
  const dials = scene.dialogues || []
  // 前一句
  if (dialogueIdx > 0 && dials[dialogueIdx - 1]) {
    const prev = dials[dialogueIdx - 1]
    parts.push(`前一句（${charName(prev.character)}）：${prev.line}`)
  }
  // 后一句
  if (dialogueIdx < dials.length - 1 && dials[dialogueIdx + 1]) {
    const next = dials[dialogueIdx + 1]
    parts.push(`后一句（${charName(next.character)}）：${next.line}`)
  }
  return parts.join('\n')
}

function selectDialogue(event, sceneIdx, dialogueIdx) {
  const s = script.value?.scenes?.[sceneIdx]
  if (!s?.dialogues?.[dialogueIdx]) return

  // Allow text selection when user holds Shift/Cmd/Alt (e.g. for copying)
  if (event && (event.shiftKey || event.metaKey || event.altKey)) return

  activateDialogue(event, sceneIdx, dialogueIdx)
}

function activateDialogue(event, sceneIdx, dialogueIdx) {
  if (event && typeof event.stopPropagation === 'function') event.stopPropagation()
  const s = script.value?.scenes?.[sceneIdx]
  if (!s?.dialogues?.[dialogueIdx]) return

  const d = s.dialogues[dialogueIdx]
  const sceneId = s.scene_id || `s_${sceneIdx}`

  // Toggle off if same block clicked
  if (selectedDialogue.value
      && selectedDialogue.value.sceneId === sceneId
      && selectedDialogue.value.dialogueIndex === dialogueIdx) {
    closeToolbar()
    return
  }

  selectedDialogue.value = {
    sceneId,
    dialogueIndex: dialogueIdx,
    line: d.line,
    character: d.character
  }

  // Position toolbar near the clicked element (viewport-relative, with flip-if-needed)
  nextTick(() => {
    const el = event?.currentTarget
    if (!el) return
    const rect = el.getBoundingClientRect()
    const tbEl = document.querySelector('.dialogue-toolbar')
    if (!tbEl) return

    const tbRect = tbEl.getBoundingClientRect()
    const tbH = tbRect.height || 240
    const tbW = tbRect.width || 240
    const margin = 8

    // Prefer below; flip above if not enough room
    let top = rect.bottom + 6
    if (top + tbH > window.innerHeight - margin) {
      top = rect.top - tbH - 6
    }
    // Horizontal: center on click x, clamp to viewport
    let left = rect.left + rect.width / 2 - tbW / 2
    left = Math.max(margin, Math.min(left, window.innerWidth - tbW - margin))

    tbEl.style.top = top + 'px'
    tbEl.style.left = left + 'px'

    // GSAP entry: scale + fade from origin
    gsap.killTweensOf(tbEl)
    gsap.fromTo(tbEl,
      { opacity: 0, scale: 0.92, y: -6, transformOrigin: '50% 0%' },
      { opacity: 1, scale: 1, y: 0, duration: 0.28, ease: 'power3.out' }
    )
  })
}

function closeToolbar() {
  if (!selectedDialogue.value) return
  const tbEl = document.querySelector('.dialogue-toolbar')
  if (tbEl) {
    gsap.killTweensOf(tbEl)
    gsap.to(tbEl, {
      opacity: 0, scale: 0.96, y: -4, duration: 0.18, ease: 'power2.in',
      onComplete: () => {
        selectedDialogue.value = null
        rewriteLoading.value = false
      }
    })
  } else {
    selectedDialogue.value = null
    rewriteLoading.value = false
  }
}

function handleRewrite(style) {
  const d = selectedDialogue.value
  if (!d) return
  rewriteLoading.value = true

  // Find the scene index for context building
  const scenes = script.value?.scenes || []
  const sceneIdx = scenes.findIndex(s => (s.scene_id || `s_${scenes.indexOf(s)}`) === d.sceneId)
  const context = buildDialogueContext(scenes, sceneIdx >= 0 ? sceneIdx : 0, d.dialogueIndex)

  emit('rewrite-dialogue', {
    sceneId: d.sceneId,
    dialogueIndex: d.dialogueIndex,
    line: d.line,
    character: d.character,
    style,
    context
  })
}

// ── Inline manual editing ──

function handleManualEdit() {
  const d = selectedDialogue.value
  if (!d) return
  editText.value = d.line
  editingDialogue.value = { sceneId: d.sceneId, dialogueIndex: d.dialogueIndex }
  closeToolbar()
}

function confirmEdit() {
  const e = editingDialogue.value
  if (!e) return
  const newLine = editText.value.trim()
  if (!newLine) return
  emit('accept-rewrite', {
    sceneId: e.sceneId,
    dialogueIndex: e.dialogueIndex,
    rewrittenLine: newLine
  })
  editingDialogue.value = null
  editText.value = ''
}

function cancelEdit() {
  editingDialogue.value = null
  editText.value = ''
}

function isEditing(sceneId, dialogueIdx) {
  return editingDialogue.value
    && editingDialogue.value.sceneId === sceneId
    && editingDialogue.value.dialogueIndex === dialogueIdx
}

// Auto-focus textarea when editing starts
// Note: ref is inside v-for, so Vue 3 wraps it in an array
watch(editingDialogue, (val) => {
  if (val) {
    nextTick(() => {
      const el = Array.isArray(editTextareaRef.value) ? editTextareaRef.value[0] : editTextareaRef.value
      el?.focus()
    })
  }
})

/** Called by parent when rewrite completes (to reset loading / close toolbar). */
function onRewriteDone() {
  rewriteLoading.value = false
  selectedDialogue.value = null
}

// ── Inline rewrite diff ──
const rewriteResult = ref(null) // { sceneId, dialogueIndex, originalLine, rewrittenLine }

function showRewriteResult(sceneId, dialogueIdx, originalLine, rewrittenLine) {
  rewriteLoading.value = false
  selectedDialogue.value = null
  rewriteResult.value = { sceneId, dialogueIdx, originalLine, rewrittenLine }

  // GSAP entry: diff slides in from below + blue border pulse
  nextTick(() => {
    const diffEl = renderRef.value?.querySelector('.rewrite-diff')
    if (!diffEl) return
    gsap.killTweensOf(diffEl)
    gsap.fromTo(diffEl,
      { opacity: 0, y: -10, scale: 0.97 },
      { opacity: 1, y: 0, scale: 1, duration: 0.4, ease: 'power3.out' }
    )
    const arrow = diffEl.querySelector('.diff-arrow')
    if (arrow) {
      gsap.fromTo(arrow,
        { opacity: 0, y: -6 },
        { opacity: 1, y: 0, duration: 0.35, delay: 0.15, ease: 'power2.out' }
      )
    }
    diffEl.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  })
}

function acceptRewrite() {
  const r = rewriteResult.value
  if (!r) return
  emit('accept-rewrite', {
    sceneId: r.sceneId,
    dialogueIndex: r.dialogueIdx,
    rewrittenLine: r.rewrittenLine
  })
  rewriteResult.value = null
}

function rejectRewrite() {
  rewriteResult.value = null
}

defineExpose({ animateIn, onRewriteDone, showRewriteResult })

const script = computed(() => {
  parseError.value = ''
  if (!props.yaml) return null
  try {
    return parseYaml(props.yaml)
  } catch (e) {
    parseError.value = e.message
    return null
  }
})

const charMap = computed(() => {
  const m = {}
  if (!script.value?.characters) return m
  for (const c of script.value.characters) {
    m[c.id] = c
  }
  return m
})

const scriptStats = computed(() => {
  if (!script.value?.scenes) return null
  const scenes = script.value.scenes
  let totalDialogues = 0
  const charLines = {}
  scenes.forEach(s => {
    if (s.dialogues) {
      totalDialogues += s.dialogues.length
      s.dialogues.forEach(d => { if (d.character) charLines[d.character] = (charLines[d.character] || 0) + 1 })
    }
    if (s.voiceover) {
      s.voiceover.forEach(v => { if (v.character) charLines[v.character] = (charLines[v.character] || 0) + 1 })
    }
  })
  let topId = null, topCount = 0
  Object.entries(charLines).forEach(([id, n]) => { if (n > topCount) { topId = id; topCount = n } })
  return {
    totalScenes: scenes.length,
    totalDialogues,
    totalCharacters: (script.value.characters || []).filter(c => c.id !== 'narrator').length,
    topCharName: topId ? charMap.value[topId]?.name || topId : null,
    topCharLines: topCount
  }
})

const timeLabel = (t) => ({
  DAWN: '黎明', MORNING: '上午', NOON: '正午',
  AFTERNOON: '下午', DUSK: '黄昏', EVENING: '傍晚',
  NIGHT: '夜', MIDNIGHT: '午夜'
})[t] || t

const intExtLabel = (v) => v === 'INT' ? '内景' : v === 'EXT' ? '外景' : v

const CHAR_COLORS = [
  '#B8A9E8', '#F0C8A0', '#A8D8A8', '#A0C4E8',
  '#F0A8A8', '#C8B0E0', '#A0D8D0', '#E8D0A0',
]
function getCharColor(id) {
  let h = 0
  for (const ch of id) h = (h * 31 + ch.charCodeAt(0)) & 0xffff
  return CHAR_COLORS[Math.abs(h) % CHAR_COLORS.length]
}

function charName(id) {
  const c = charMap.value[id]
  return c ? c.name : id
}

function yamlValue(val) {
  if (val === null || val === undefined) return ''
  if (typeof val === 'boolean') return val ? '是' : '否'
  return String(val)
}

onMounted(() => {
  nextTick(() => { animateIn(); setupSceneObserver() })
})

onBeforeUnmount(() => {
  sceneObserver?.disconnect()
})

// Re-animate when yaml changes (new chapter completes)
watch(() => props.yaml, (val, old) => {
  if (!old && val) {
    // Content just arrived: reset counter so all cards animate in fresh
    prevSceneCount.value = 0
    nextTick(() => { animateIn(); setupSceneObserver() })
    return
  }
  prevSceneCount.value = 0
  nextTick(() => { animateIn(); setupSceneObserver() })
})

const prevSceneCount = ref(0)
function animateIn() {
  if (!renderRef.value) return
  const cards = renderRef.value.querySelectorAll('.scene-card')
  if (cards.length === 0) return
  const startIdx = prevSceneCount.value
  const fresh = [...cards].slice(startIdx)
  prevSceneCount.value = cards.length
  if (fresh.length === 0) return
  gsap.fromTo(fresh,
    { y: 20, opacity: 0 },
    { y: 0, opacity: 1, duration: 0.5, stagger: 0.08, ease: 'power2.out', clearProps: 'all' }
  )
}

</script>

<template>
  <div class="script-render" ref="renderRef">
    <div class="sr-scroll" ref="scrollRef">
    <!-- Parse error -->
    <div v-if="parseError" class="parse-error">
      <p class="eyebrow error-text">YAML 解析失败</p>
      <p class="body-sm">{{ parseError }}</p>
    </div>

    <!-- Skeleton: generating with no content yet -->
    <div v-else-if="!script && isGenerating" class="script-skeleton">
      <div class="skel-scene" v-for="n in 3" :key="n" :style="{ animationDelay: n * 0.15 + 's' }">
        <div class="skel-header">
          <span class="skel-line w-30" />
          <span class="skel-line w-20" />
        </div>
        <div class="skel-body">
          <span class="skel-line w-60" />
          <span class="skel-line w-80" />
          <span class="skel-line w-40" />
          <span class="skel-line w-50" />
        </div>
      </div>
      <p class="skel-hint">
        <span class="skel-dot" />AI 正在编写剧本…
      </p>
    </div>

    <!-- Empty: no content, not generating -->
    <div v-else-if="!script" class="script-empty">
      <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" class="empty-icon">
        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
        <polyline points="14 2 14 8 20 8"/>
        <line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/>
      </svg>
      <p class="body-sm subtle">剧本生成后将在此展示</p>
    </div>

    <!-- Rendered Script -->
    <template v-else>
      <!-- Meta header -->
      <div class="script-head">
        <h2 class="headline">{{ script.meta?.title || '未命名剧本' }}</h2>
        <p v-if="script.meta?.logline" class="logline body-lg muted">{{ script.meta.logline }}</p>
        <div class="script-meta-row caption">
          <span v-if="script.meta?.source_novel">{{ script.meta.source_novel }}</span>
          <span v-if="script.meta?.genre">{{ script.meta.genre }}</span>
          <span v-if="script.meta?.total_scenes">共 {{ script.meta.total_scenes }} 场</span>
        </div>
      </div>

      <!-- Character list -->
      <div v-if="script.characters?.length" class="char-section">
        <h3 class="card-title">人物表</h3>
        <div class="char-grid">
          <div
            v-for="c in script.characters.filter(x => x.id !== 'narrator')"
            :key="c.id"
            class="char-chip"
            :class="{ 'is-active': highlightedChar === c.id, 'is-dimmed': highlightedChar && highlightedChar !== c.id }"
            @click="toggleHighlight(c.id)"
          >
            <span class="char-chip-avatar">{{ (c.name || '?').slice(0, 1) }}</span>
            <span class="char-chip-name">{{ c.name }}</span>
            <span v-if="c.role" class="char-chip-role">{{ c.role }}</span>
            <span class="char-chip-lines" v-if="scriptStats && scriptStats.totalDialogues">💬</span>
          </div>
        </div>
      </div>

      <!-- Scenes -->
      <div class="scenes">
        <div
          v-for="(scene, si) in script.scenes"
          :key="scene.scene_id || si"
          class="scene-card"
          :data-scene-idx="si"
        >
          <!-- Scene header -->
          <div class="scene-header">
            <span class="scene-num">第{{ si + 1 }}场</span>
            <span class="scene-divider">·</span>
            <span class="scene-loc">{{ scene.location || '未标注地点' }}</span>
            <span class="scene-divider">·</span>
            <span class="scene-meta">{{ intExtLabel(scene.int_ext) }} · {{ timeLabel(scene.time_of_day) }}</span>
          </div>

          <!-- Characters -->
          <div v-if="scene.characters?.length" class="scene-chars caption">
            <span class="scene-chars-label">人物：</span>
            <span v-for="(cid, ci) in scene.characters" :key="cid">
              <span class="scene-char-name">{{ charName(cid) }}</span>
              <span v-if="ci < scene.characters.length - 1">、</span>
            </span>
          </div>

          <!-- Summary -->
          <div v-if="scene.summary" class="scene-summary">
            {{ scene.summary }}
          </div>

          <!-- Actions -->
          <div v-if="scene.actions?.length" class="actions">
            <p v-for="(a, ai) in scene.actions" :key="ai" class="action">[ {{ a }} ]</p>
          </div>

          <!-- Dialogues -->
          <div v-if="scene.dialogues?.length" class="dialogues">
            <div
              v-for="(d, di) in scene.dialogues" :key="di"
              class="dialogue-wrap"
            >
              <!-- Inline manual edit mode -->
              <div
                v-if="isEditing(scene.scene_id || `s_${si}`, di)"
                class="dialogue-edit-area"
              >
                <div class="dialogue-character" :style="{ color: getCharColor(d.character) }">
                  {{ charName(d.character) }}
                  <span v-if="d.parenthetical" class="dialogue-parenthetical">（{{ d.parenthetical }}）</span>
                </div>
                <textarea
                  v-model="editText"
                  class="edit-textarea"
                  rows="3"
                  ref="editTextareaRef"
                  @keydown.escape="cancelEdit"
                  @keydown.enter.meta="confirmEdit"
                  @keydown.enter.ctrl="confirmEdit"
                />
                <div class="edit-actions">
                  <button class="edit-btn edit-save" @click="confirmEdit">保存</button>
                  <button class="edit-btn edit-cancel" @click="cancelEdit">取消</button>
                </div>
              </div>
              <!-- Normal display mode -->
              <template v-else>
                <div
                  class="dialogue-block"
                  :class="{
                    'is-highlighted': highlightedChar === d.character,
                    'is-dimmed': highlightedChar && highlightedChar !== d.character,
                    'is-selected': selectedDialogue && selectedDialogue.sceneId === (scene.scene_id || `s_${si}`) && selectedDialogue.dialogueIndex === di
                  }"
                  @mousedown.prevent
                  @click="selectDialogue($event, si, di)"
                >
                  <div class="dialogue-character" :style="{ color: getCharColor(d.character) }">
                    {{ charName(d.character) }}
                    <span v-if="d.parenthetical" class="dialogue-parenthetical">（{{ d.parenthetical }}）</span>
                    <span v-if="d.emotion" class="dialogue-emotion"> · {{ d.emotion }}</span>
                  </div>
                  <p class="dialogue-line">{{ d.line }}</p>
                </div>
              </template>

              <!-- Inline rewrite diff -->
              <div
                v-if="rewriteResult && rewriteResult.sceneId === (scene.scene_id || `s_${si}`) && rewriteResult.dialogueIdx === di"
                class="rewrite-diff"
              >
                <div class="diff-arrow">↓</div>
                <div class="diff-new-line">
                  <span class="diff-label">AI 改写</span>
                  {{ rewriteResult.rewrittenLine }}
                </div>
                <div class="diff-actions">
                  <button class="diff-btn diff-accept" @click="acceptRewrite">保留</button>
                  <button class="diff-btn diff-reject" @click="rejectRewrite">放弃</button>
                </div>
              </div>
            </div>
          </div>

          <!-- Voiceover -->
          <div v-if="scene.voiceover?.length" class="voiceovers">
            <div
              v-for="(v, vi) in scene.voiceover" :key="vi"
              class="voiceover-block"
              :class="{ 'is-highlighted': highlightedChar === v.character, 'is-dimmed': highlightedChar && highlightedChar !== v.character }"
            >
              <span class="voiceover-label" :style="{ color: getCharColor(v.character) }">【{{ charName(v.character) }}·旁白】</span>
              <p class="voiceover-line">{{ v.line }}</p>
            </div>
          </div>

          <!-- Production notes footer -->
          <div v-if="scene.props || scene.sfx || scene.music_cue || scene.camera_hint" class="scene-footer caption">
            <span v-if="scene.props" class="scene-tag">道具：{{ yamlValue(scene.props) }}</span>
            <span v-if="scene.sfx" class="scene-tag">音效：{{ yamlValue(scene.sfx) }}</span>
            <span v-if="scene.music_cue" class="scene-tag">配乐：{{ yamlValue(scene.music_cue) }}</span>
            <span v-if="scene.camera_hint" class="scene-tag">镜头：{{ yamlValue(scene.camera_hint) }}</span>
          </div>
        </div>
      </div>

      <!-- Stats bar -->
      <div v-if="scriptStats" class="stats-bar">
        <div class="stat-item">
          <span class="stat-num">{{ scriptStats.totalScenes }}</span>
          <span class="stat-label">场次</span>
        </div>
        <div class="stat-item">
          <span class="stat-num">{{ scriptStats.totalDialogues }}</span>
          <span class="stat-label">对白</span>
        </div>
        <div class="stat-item">
          <span class="stat-num">{{ scriptStats.totalCharacters }}</span>
          <span class="stat-label">人物</span>
        </div>
        <div v-if="scriptStats.topCharName" class="stat-item">
          <span class="stat-num">{{ scriptStats.topCharName }}</span>
          <span class="stat-label">{{ scriptStats.topCharLines }} 句台词 · 最活跃</span>
        </div>
      </div>

      <!-- Notes -->
      <div v-if="script.notes?.length" class="script-notes">
        <h3 class="card-title">备注</h3>
        <ul>
          <li v-for="(n, ni) in script.notes" :key="ni" class="body-sm subtle">{{ n }}</li>
        </ul>
      </div>
    </template>

    <!-- AI Rewrite Toolbar -->
    <DialogueToolbar
      :visible="!!selectedDialogue"
      :loading="rewriteLoading"
      @rewrite="handleRewrite"
      @close="closeToolbar"
      @manual-edit="handleManualEdit"
    />

    </div><!-- /sr-scroll -->

    <!-- Scene navigation: thin strip on right, expands on hover -->
    <div v-if="navScenes.length > 3" class="scene-nav">
      <div class="scene-nav-inner">
        <button
          v-for="s in navScenes"
          :key="s.index"
          class="scene-nav-item"
          :class="{ active: activeScene === s.index }"
          @click="scrollToScene(s.index)"
          :title="s.location"
        >
          <span class="scene-nav-dot" :class="{ active: activeScene === s.index }" />
          <span class="scene-nav-text">{{ s.label }}</span>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.script-render {
  position: relative;
  font-family: var(--font-text);
  line-height: 1.8;
  color: var(--color-ink);
  min-height: 300px;
}
.sr-scroll {
  padding: var(--space-lg);
  max-height: calc(100vh - 200px);
  overflow-y: auto;
}

/* ── Empty / Error ── */
.parse-error {
  padding: var(--space-lg);
  background: rgba(196, 122, 106,0.06);
  border: 1px solid rgba(196, 122, 106,0.2);
  border-radius: var(--radius-md);
}
.error-text { color: var(--color-semantic-error); margin: 0 0 8px; }

.script-empty {
  display: flex; flex-direction: column; align-items: center;
  padding: var(--space-xxl) 0; gap: var(--space-sm);
}
.empty-icon { font-size: 36px; opacity: 0.3; }

/* ── Skeleton loading ── */
.script-skeleton {
  padding: var(--space-md) 0;
}
.skel-scene {
  margin-bottom: var(--space-lg);
  padding: var(--space-md);
  border-radius: var(--radius-md);
  background: var(--color-surface-2);
  border: 1px solid var(--color-hairline);
  animation: skel-enter 0.6s var(--ease-out-cubic) both;
}
.skel-header {
  display: flex; gap: var(--space-sm);
  margin-bottom: var(--space-md);
}
.skel-body {
  display: flex; flex-direction: column; gap: var(--space-sm);
}
.skel-line {
  height: 10px;
  border-radius: var(--radius-pill);
  background: linear-gradient(90deg, var(--color-surface-3) 25%, var(--color-surface-4) 50%, var(--color-surface-3) 75%);
  background-size: 200% 100%;
  animation: skel-shimmer 2s ease-in-out infinite;
}
.skel-line.w-20 { width: 20%; }
.skel-line.w-30 { width: 30%; }
.skel-line.w-40 { width: 40%; }
.skel-line.w-50 { width: 50%; }
.skel-line.w-60 { width: 60%; }
.skel-line.w-80 { width: 80%; }

.skel-hint {
  text-align: center;
  color: var(--color-primary);
  font-size: var(--text-body-sm);
  display: flex; align-items: center; justify-content: center;
  gap: 8px;
  padding: var(--space-md);
}
.skel-dot {
  width: 8px; height: 8px;
  border-radius: 50%;
  background: var(--color-primary);
  animation: skel-pulse 1s ease-in-out infinite;
}

@keyframes skel-enter {
  from { opacity: 0; transform: translateY(12px); }
  to   { opacity: 1; transform: translateY(0); }
}
@keyframes skel-shimmer {
  0%   { background-position: 100% 0; }
  100% { background-position: -100% 0; }
}
@keyframes skel-pulse {
  0%, 100% { opacity: 0.3; transform: scale(1); }
  50%      { opacity: 1;   transform: scale(1.6); }
}

/* ── Meta header ── */
.script-head {
  text-align: center;
  padding-bottom: var(--space-lg);
  margin-bottom: var(--space-lg);
  border-bottom: 1px solid var(--color-hairline);
}
.script-head .headline { margin: 0 0 var(--space-xs); }
.logline { margin: 0 0 var(--space-sm); font-style: italic; }
.script-meta-row { display: flex; justify-content: center; gap: var(--space-md); }

/* ── Character grid ── */
.char-section {
  margin-bottom: var(--space-xl);
  padding-bottom: var(--space-lg);
  border-bottom: 1px solid var(--color-hairline);
}
.char-section h3 { margin: 0 0 var(--space-md); }
.char-grid {
  display: flex; flex-wrap: wrap; gap: var(--space-sm);
}
.char-chip {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 6px 12px;
  background: var(--color-surface-2);
  border: 1px solid var(--color-hairline);
  border-radius: var(--radius-pill);
  font-size: var(--text-body-sm);
}
.char-chip-avatar {
  width: 22px; height: 22px; border-radius: 50%;
  background: var(--color-primary);
  color: var(--color-on-primary);
  display: flex; align-items: center; justify-content: center;
  font-size: 11px; font-weight: 600;
}
.char-chip-name { color: var(--color-ink); font-weight: 500; }
.char-chip-role {
  font-size: var(--text-caption);
  color: var(--color-ink-subtle);
  background: var(--color-surface-3);
  padding: 1px 6px;
  border-radius: var(--radius-pill);
}
.char-chip {
  cursor: pointer;
  user-select: none;
  transition: all 0.25s var(--ease-out-cubic);
}
.char-chip:hover { border-color: var(--color-primary); transform: translateY(-1px); }
.char-chip.is-active {
  background: var(--color-primary-soft);
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px rgba(125,154,110,0.2);
}
.char-chip.is-dimmed { opacity: 0.35; }
.char-chip-lines { font-size: 11px; margin-left: 2px; }

/* ── Scene cards ── */
.scene-card {
  margin-bottom: var(--space-xl);
  padding-bottom: var(--space-lg);
  border-bottom: 1px solid var(--color-hairline);
}
.scene-card:last-child { border-bottom: 0; }

.scene-header {
  display: flex; align-items: baseline; gap: 6px;
  margin-bottom: var(--space-sm);
  padding: var(--space-xs) var(--space-md);
  background: var(--color-surface-2);
  border-radius: var(--radius-sm);
  border-left: 3px solid var(--color-primary);
}
.scene-num {
  font-weight: 600;
  color: var(--color-primary);
  font-size: var(--text-body-sm);
}
.scene-divider { color: var(--color-hairline-strong); }
.scene-loc {
  font-weight: 500;
  color: var(--color-ink);
  font-size: var(--text-body-sm);
}
.scene-meta {
  color: var(--color-ink-subtle);
  font-size: var(--text-caption);
}

.scene-chars {
  padding: 0 var(--space-md);
  margin-bottom: var(--space-sm);
}
.scene-chars-label { color: var(--color-ink-muted); }
.scene-char-name { color: var(--color-ink-subtle); }

.scene-summary {
  color: var(--color-ink-muted);
  font-style: italic;
  font-size: var(--text-body-sm);
  padding: 0 var(--space-md);
  margin-bottom: var(--space-md);
}

/* ── Actions ── */
.actions { margin-bottom: var(--space-md); }
.action {
  color: var(--color-ink-subtle);
  font-style: italic;
  font-size: var(--text-body-sm);
  margin: 2px var(--space-md);
  padding: 2px 0 2px var(--space-md);
  border-left: 2px solid var(--color-hairline);
}

/* ── Dialogues ── */
.dialogues { margin-bottom: var(--space-md); }
.dialogue-wrap { display: block; }
.dialogue-block {
  margin: var(--space-md) 0;
  padding: var(--space-xs) var(--space-sm);
  border-radius: var(--radius-sm);
  transition: all 0.3s var(--ease-out-cubic);
}
.dialogue-block.is-highlighted {
  background: rgba(125,154,110,0.06);
  box-shadow: inset 3px 0 0 0 var(--color-primary);
}
.dialogue-block.is-dimmed { opacity: 0.25; }
.dialogue-block {
  cursor: pointer;
  transition: all 0.2s var(--ease-out-quad);
}
.dialogue-block:hover {
  background: rgba(125,154,110,0.03);
}
.dialogue-block.is-selected {
  background: rgba(125,154,110,0.08);
  box-shadow: inset 3px 0 0 0 var(--color-primary), 0 0 0 1px rgba(125,154,110,0.15);
  border-radius: var(--radius-sm);
}
.voiceover-block {
  transition: all 0.3s var(--ease-out-cubic);
}
.voiceover-block.is-highlighted {
  background: rgba(125,154,110,0.08);
  border-left-color: var(--color-primary);
}
.voiceover-block.is-dimmed { opacity: 0.25; }
.dialogue-character {
  text-align: center;
  font-weight: 600;
  color: var(--color-ink);
  font-size: var(--text-body-sm);
  letter-spacing: 0.03em;
  margin-bottom: 4px;
}
.dialogue-parenthetical {
  font-weight: 400;
  color: var(--color-ink-subtle);
  font-style: italic;
}
.dialogue-emotion {
  font-weight: 400;
  font-style: italic;
  color: var(--color-ink-subtle);
  font-size: var(--text-caption);
}
.dialogue-line {
  text-align: center;
  max-width: 520px;
  margin: 0 auto;
  padding: var(--space-xs) var(--space-md);
  font-size: var(--text-body);
  color: var(--color-ink);
}

/* ── Voiceover ── */
.voiceovers { margin-bottom: var(--space-md); }
.voiceover-block {
  margin: var(--space-sm) var(--space-md);
  padding: var(--space-sm) var(--space-md);
  background: var(--color-surface-2);
  border-radius: var(--radius-sm);
  border-left: 2px solid var(--color-semantic-warning);
}
.voiceover-label {
  font-size: var(--text-caption);
  color: var(--color-semantic-warning);
  font-weight: 600;
}
.voiceover-line {
  margin: 4px 0 0;
  font-size: var(--text-body-sm);
  color: var(--color-ink-muted);
  font-style: italic;
}

/* ── Scene footer ── */
.scene-footer {
  display: flex; flex-wrap: wrap; gap: var(--space-sm);
  padding: var(--space-sm) var(--space-md);
  margin-top: var(--space-sm);
  border-top: 1px dashed var(--color-hairline);
}
.scene-tag {
  padding: 2px 8px;
  background: var(--color-surface-3);
  border-radius: var(--radius-sm);
  font-size: var(--text-caption);
  color: var(--color-ink-subtle);
}

/* ── Notes ── */
/* ── Stats bar ── */
.stats-bar {
  display: flex; gap: var(--space-lg);
  padding: var(--space-md) var(--space-lg);
  margin-top: var(--space-lg);
  background: var(--color-surface-2);
  border-radius: var(--radius-md);
  border: 1px solid var(--color-hairline);
  justify-content: center;
}
.stat-item {
  display: flex; flex-direction: column; align-items: center;
  gap: 2px;
}
.stat-num {
  font-family: var(--font-mono);
  font-size: var(--text-card-title);
  font-weight: 600;
  color: var(--color-primary);
}
.stat-label {
  font-size: var(--text-caption);
  color: var(--color-ink-subtle);
}

.script-notes {
  margin-top: var(--space-lg);
  padding: var(--space-md);
  background: var(--color-surface-1);
  border: 1px solid var(--color-hairline);
  border-radius: var(--radius-md);
}
.script-notes h3 { margin: 0 0 var(--space-sm); }
.script-notes ul { margin: 0; padding-left: var(--space-lg); }

/* ── Inline rewrite diff ── */
.rewrite-diff {
  margin: var(--space-sm) var(--space-md);
  padding: var(--space-md);
  background: rgba(125,154,110,0.04);
  border: 1px solid rgba(125,154,110,0.2);
  border-radius: var(--radius-md);
  border-left: 3px solid var(--color-primary);
}
.diff-arrow {
  text-align: center;
  color: var(--color-primary);
  font-size: 16px;
  margin-bottom: 6px;
}
.diff-new-line {
  text-align: center;
  font-size: var(--text-body);
  color: var(--color-ink);
  padding: var(--space-xs) 0;
  line-height: 1.8;
}
.diff-label {
  display: inline-block;
  font-size: var(--text-caption);
  color: var(--color-primary);
  background: var(--color-primary-soft);
  padding: 1px 8px;
  border-radius: var(--radius-pill);
  margin-right: 8px;
  vertical-align: middle;
  font-weight: 500;
}
.diff-actions {
  display: flex;
  justify-content: center;
  gap: var(--space-sm);
  margin-top: var(--space-sm);
}
.diff-btn {
  height: 30px;
  padding: 0 16px;
  border-radius: var(--radius-pill);
  font-size: var(--text-body-sm);
  font-weight: 500;
  cursor: pointer;
  border: 1px solid transparent;
  transition: all 0.15s ease;
}
.diff-accept {
  background: var(--color-primary);
  color: var(--color-on-primary);
  border-color: var(--color-primary);
}
.diff-accept:hover { filter: brightness(1.1); }
.diff-accept:active { transform: scale(0.96); }
.diff-reject {
  background: transparent;
  color: var(--color-ink-subtle);
  border-color: var(--color-hairline-strong);
}
.diff-reject:hover {
  background: var(--color-surface-3);
  color: var(--color-ink);
}

/* ── Inline manual edit mode ── */
.dialogue-edit-area {
  margin: var(--space-md) 0;
  padding: var(--space-md);
  background: rgba(125,154,110,0.04);
  border: 1px solid rgba(125,154,110,0.2);
  border-radius: var(--radius-md);
  border-left: 3px solid var(--color-primary);
}
.edit-textarea {
  display: block;
  width: 100%;
  max-width: 520px;
  margin: var(--space-sm) auto;
  padding: var(--space-sm) var(--space-md);
  font-family: var(--font-text);
  font-size: var(--text-body);
  line-height: 1.8;
  color: var(--color-ink);
  background: var(--color-surface-1);
  border: 1px solid var(--color-hairline-strong);
  border-radius: var(--radius-sm);
  resize: vertical;
  transition: border-color 0.2s ease;
  box-sizing: border-box;
}
.edit-textarea:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px rgba(125,154,110,0.15);
}
.edit-actions {
  display: flex;
  justify-content: center;
  gap: var(--space-sm);
  margin-top: var(--space-sm);
}
.edit-btn {
  height: 30px;
  padding: 0 16px;
  border-radius: var(--radius-pill);
  font-size: var(--text-body-sm);
  font-weight: 500;
  cursor: pointer;
  border: 1px solid transparent;
  transition: all 0.15s ease;
}
.edit-save {
  background: var(--color-primary);
  color: var(--color-on-primary);
  border-color: var(--color-primary);
}
.edit-save:hover { filter: brightness(1.1); }
.edit-save:active { transform: scale(0.96); }
.edit-cancel {
  background: transparent;
  color: var(--color-ink-subtle);
  border-color: var(--color-hairline-strong);
}
.edit-cancel:hover {
  background: var(--color-surface-3);
  color: var(--color-ink);
}

/* ── Scene navigation: thin strip, expands on hover ── */
.scene-nav {
  position: absolute;
  right: 0;
  top: 0;
  bottom: 0;
  width: 20px;
  z-index: 10;
  padding: 40px 0;
  cursor: pointer;
  transition: width 0.2s ease;
}
.scene-nav:hover {
  width: 80px;
}
.scene-nav-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  height: 100%;
  padding: 8px 0;
  overflow: hidden;
  border-left: 1px solid var(--color-hairline);
  background: transparent;
  transition: background 0.2s ease, border-color 0.2s ease;
}
.scene-nav:hover .scene-nav-inner {
  background: var(--color-surface-1);
  border-left-color: var(--color-hairline-strong);
  border-radius: var(--radius-sm) 0 0 var(--radius-sm);
  align-items: flex-start;
  padding-left: 6px;
  overflow-y: auto;
}
.scene-nav-item {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 3px 4px;
  border-radius: var(--radius-xs);
  cursor: pointer;
  font-size: 10px;
  color: var(--color-ink-subtle);
  transition: all 0.15s ease;
  border: 0;
  background: transparent;
  flex-shrink: 0;
  white-space: nowrap;
}
.scene-nav-item:hover { background: var(--color-surface-3); color: var(--color-ink); }
.scene-nav-item.active { color: var(--color-primary); font-weight: 600; }
.scene-nav-dot {
  width: 5px; height: 5px;
  border-radius: 50%;
  background: var(--color-hairline-strong);
  flex-shrink: 0;
  transition: all 0.15s ease;
}
.scene-nav-dot.active { background: var(--color-primary); }
.scene-nav-text {
  white-space: nowrap;
  overflow: hidden;
  max-width: 0;
  opacity: 0;
  transition: max-width 0.15s ease, opacity 0.15s ease;
}
.scene-nav:hover .scene-nav-text {
  max-width: 60px;
  opacity: 1;
}
</style>
