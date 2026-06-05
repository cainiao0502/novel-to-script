<script setup>
import { computed, onMounted, ref, watch, nextTick } from 'vue'
import { parse as parseYaml } from 'yaml'
import gsap from 'gsap'

const props = defineProps({
  yaml: { type: String, default: '' },
  isGenerating: { type: Boolean, default: false }
})

const renderRef = ref(null)
const parseError = ref('')

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

const timeLabel = (t) => ({
  DAWN: '黎明', MORNING: '上午', NOON: '正午',
  AFTERNOON: '下午', DUSK: '黄昏', EVENING: '傍晚',
  NIGHT: '夜', MIDNIGHT: '午夜'
})[t] || t

const intExtLabel = (v) => v === 'INT' ? '内景' : v === 'EXT' ? '外景' : v

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
  nextTick(() => animateIn())
})

// Re-animate when yaml changes (new chapter completes)
watch(() => props.yaml, () => {
  prevSceneCount.value = 0
  nextTick(() => animateIn())
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
  gsap.from(fresh, {
    y: 20, opacity: 0,
    duration: 0.5, stagger: 0.08,
    ease: 'power2.out'
  })
}

defineExpose({ animateIn })
</script>

<template>
  <div class="script-render" ref="renderRef">
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
      <span class="empty-icon">📜</span>
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
          >
            <span class="char-chip-avatar">{{ (c.name || '?').slice(0, 1) }}</span>
            <span class="char-chip-name">{{ c.name }}</span>
            <span v-if="c.role" class="char-chip-role">{{ c.role }}</span>
          </div>
        </div>
      </div>

      <!-- Scenes -->
      <div class="scenes">
        <div
          v-for="(scene, si) in script.scenes"
          :key="scene.scene_id || si"
          class="scene-card"
        >
          <!-- Scene header -->
          <div class="scene-header">
            <span class="scene-num">第{{ scene.order || si + 1 }}场</span>
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
            <div v-for="(d, di) in scene.dialogues" :key="di" class="dialogue-block">
              <div class="dialogue-character">
                {{ charName(d.character) }}
                <span v-if="d.parenthetical" class="dialogue-parenthetical">（{{ d.parenthetical }}）</span>
                <span v-if="d.emotion" class="dialogue-emotion"> — {{ d.emotion }}</span>
              </div>
              <p class="dialogue-line">{{ d.line }}</p>
            </div>
          </div>

          <!-- Voiceover -->
          <div v-if="scene.voiceover?.length" class="voiceovers">
            <div v-for="(v, vi) in scene.voiceover" :key="vi" class="voiceover-block">
              <span class="voiceover-label">【{{ charName(v.character) }}·旁白】</span>
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

      <!-- Notes -->
      <div v-if="script.notes?.length" class="script-notes">
        <h3 class="card-title">备注</h3>
        <ul>
          <li v-for="(n, ni) in script.notes" :key="ni" class="body-sm subtle">{{ n }}</li>
        </ul>
      </div>
    </template>
  </div>
</template>

<style scoped>
.script-render {
  padding: var(--space-lg);
  max-height: calc(100vh - 200px);
  overflow-y: auto;
  font-family: var(--font-text);
  line-height: 1.8;
  color: var(--color-ink);
}

/* ── Empty / Error ── */
.parse-error {
  padding: var(--space-lg);
  background: rgba(240,104,104,0.06);
  border: 1px solid rgba(240,104,104,0.2);
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
  background: var(--gradient-primary);
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
.dialogue-block {
  margin: var(--space-md) 0;
  padding: var(--space-xs) 0;
}
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
.script-notes {
  margin-top: var(--space-lg);
  padding: var(--space-md);
  background: var(--color-surface-1);
  border: 1px solid var(--color-hairline);
  border-radius: var(--radius-md);
}
.script-notes h3 { margin: 0 0 var(--space-sm); }
.script-notes ul { margin: 0; padding-left: var(--space-lg); }
</style>
