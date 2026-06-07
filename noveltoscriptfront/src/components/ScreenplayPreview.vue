<script setup>
import { computed, nextTick, ref } from 'vue'
import { parse as parseYaml } from 'yaml'
import { yamlToFountain } from '@/utils/fountainGenerator'
import gsap from 'gsap'

const props = defineProps({
  yaml: { type: String, default: '' }
})

const timeLabel = (t) => ({
  DAWN: '黎明', MORNING: '上午', NOON: '正午',
  AFTERNOON: '下午', DUSK: '黄昏', EVENING: '傍晚',
  NIGHT: '夜', MIDNIGHT: '午夜'
})[t] || t

const intExtLabel = (v) => v === 'INT' ? '内景' : v === 'EXT' ? '外景' : v || ''

const parsed = computed(() => {
  if (!props.yaml) return null
  try {
    return parseYaml(props.yaml)
  } catch { return null }
})

const charNames = computed(() => {
  const m = {}
  if (!parsed.value?.characters) return m
  for (const c of parsed.value.characters) {
    m[c.id] = c.name || c.id
  }
  return m
})

const charName = (id) => charNames.value[id] || id

const CHAR_COLORS = [
  '#B8A9E8', '#F0C8A0', '#A8D8A8', '#A0C4E8',
  '#F0A8A8', '#C8B0E0', '#A0D8D0', '#E8D0A0',
]
function getCharColor(id) {
  let h = 0
  for (const ch of id) h = (h * 31 + ch.charCodeAt(0)) & 0xffff
  return CHAR_COLORS[Math.abs(h) % CHAR_COLORS.length]
}

// Strip existing parentheses from parenthetical value (YAML data sometimes includes them)
function cleanParen(text) {
  return text ? text.replace(/^[（(]\s*|\s*[）)]$/g, '') : ''
}

const fountainText = computed(() => yamlToFountain(props.yaml))

const title = computed(() => parsed.value?.meta?.title || '剧本')

function downloadFountain() {
  const text = fountainText.value
  if (!text) return
  const blob = new Blob([text], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${title.value}.fountain`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

function printPdf() {
  window.print()
}

const toastText = ref('')
let toastTimer = null

function copyFountain() {
  const text = fountainText.value
  if (!text) return
  navigator.clipboard.writeText(text).then(() => {
    showToast('✓ 已复制')
  }).catch(() => {
    showToast('复制失败')
  })
}

function showToast(msg) {
  toastText.value = msg
  clearTimeout(toastTimer)
  nextTick(() => {
    const el = document.querySelector('.sp-toast')
    if (el) {
      gsap.fromTo(el,
        { opacity: 0, y: 12, scale: 0.95 },
        { opacity: 1, y: 0, scale: 1, duration: 0.25, ease: 'power2.out' }
      )
    }
  })
  toastTimer = setTimeout(() => {
    const el = document.querySelector('.sp-toast')
    if (el) {
      gsap.to(el, { opacity: 0, y: -8, duration: 0.2, ease: 'power2.in', onComplete: () => { toastText.value = '' } })
    } else {
      toastText.value = ''
    }
  }, 1500)
}
</script>

<template>
  <div class="screenplay-preview">
    <div class="preview-toolbar">
      <span class="preview-label">{{ title }}</span>
      <div class="preview-actions">
        <button class="preview-btn" @click="downloadFountain" :disabled="!fountainText">
          导出 Fountain
        </button>
        <button class="preview-btn preview-btn-primary" @click="printPdf" :disabled="!fountainText">
          导出 PDF · 打印
        </button>
        <button class="preview-btn" @click="copyFountain" :disabled="!fountainText" title="复制剧本">
          复制
        </button>
      </div>
    </div>

    <div v-if="toastText" class="sp-toast">{{ toastText }}</div>

    <div class="screenplay-content" v-if="parsed">
      <template v-for="(scene, si) in parsed.scenes || []" :key="scene.scene_id || si">
        <!-- Scene separator -->
        <div v-if="si > 0" class="sp-scene-divider"></div>

        <!-- Scene heading -->
        <div class="sp-scene-heading">
          <span class="sp-scene-num">{{ si + 1 }}</span>
          {{ intExtLabel(scene.int_ext) }}. {{ scene.location || '未标注地点' }}
          <template v-if="scene.time_of_day"> — {{ timeLabel(scene.time_of_day) }}</template>
        </div>

        <!-- Summary -->
        <p v-if="scene.summary" class="sp-action">{{ scene.summary }}</p>

        <!-- Actions (italicized, grouped) -->
        <div v-if="scene.actions?.length" class="sp-action-block">
          <p v-for="(action, ai) in scene.actions" :key="'a'+ai" class="sp-action">{{ action }}</p>
        </div>

        <!-- Dialogues -->
        <div v-for="(d, di) in scene.dialogues" :key="'d'+di" class="sp-dialogue-group">
          <div class="sp-character" :style="{ color: getCharColor(d.character) }">{{ charName(d.character).toUpperCase() }}</div>
          <div v-if="d.parenthetical" class="sp-parenthetical">（{{ cleanParen(d.parenthetical) }}）</div>
          <div class="sp-dialogue" :style="{ color: getCharColor(d.character) }">{{ d.line }}</div>
        </div>

        <!-- Voiceover -->
        <div v-for="(v, vi) in scene.voiceover" :key="'v'+vi" class="sp-dialogue-group">
          <div class="sp-character" :style="{ color: getCharColor(v.character) }">{{ charName(v.character).toUpperCase() }} (V.O.)</div>
          <div class="sp-dialogue" :style="{ color: getCharColor(v.character) }">{{ v.line }}</div>
        </div>
      </template>
    </div>

    <div v-else class="sp-empty">
      <p>没有可预览的剧本内容</p>
    </div>
  </div>
</template>

<style scoped>
.screenplay-preview {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  background: var(--color-surface-1);
  color: var(--color-ink);
}

/* ── Toolbar (hidden on print) ── */
.preview-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  border-bottom: 1px solid var(--color-hairline);
  background: var(--color-surface-2);
  flex-shrink: 0;
}
.preview-label {
  font-family: var(--font-text);
  font-size: var(--text-body-sm);
  font-weight: 600;
  color: var(--color-ink);
}
.preview-actions {
  display: flex;
  gap: 8px;
}
.preview-btn {
  height: 30px;
  padding: 0 14px;
  border-radius: var(--radius-pill);
  font-size: var(--text-body-sm);
  font-weight: 500;
  cursor: pointer;
  border: 1px solid var(--color-hairline-strong);
  background: transparent;
  color: var(--color-ink);
  transition: all 0.15s ease;
}
.preview-btn:hover {
  background: var(--color-surface-3);
}
.preview-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.preview-btn-primary {
  background: var(--color-primary);
  color: var(--color-on-primary);
  border-color: var(--color-primary);
}
.preview-btn-primary:hover {
  filter: brightness(1.1);
}

/* ── Screenplay content ── */
.screenplay-content {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-lg) 32px;
  max-width: 720px;
  margin: 0 auto;
  width: 100%;
  box-sizing: border-box;
}

/* Professional screenplay typography */
.sp-scene-heading {
  font-family: 'Courier New', Courier, monospace;
  font-size: 15px;
  line-height: 1.5;
  color: var(--color-ink);
  text-transform: uppercase;
  margin: 1.2em 0 0.5em 0;
  font-weight: 600;
  letter-spacing: 0.01em;
}
.sp-scene-num {
  display: inline-block;
  width: 2.5em;
  color: var(--color-primary);
  font-weight: 700;
}

.sp-scene-divider {
  height: 1px;
  background: var(--color-hairline);
  margin: 1.2em 0;
}

.sp-action-block {
  margin-bottom: 0.6em;
}

.sp-action {
  font-family: 'Courier New', Courier, monospace;
  font-size: 14px;
  line-height: 1.6;
  color: var(--color-ink-muted);
  margin: 0 0 0.4em 0;
  white-space: pre-wrap;
}

.sp-dialogue-group {
  margin: 0.4em 0 0.7em 0;
  padding-left: 8%;
}

.sp-character {
  font-family: 'Courier New', Courier, monospace;
  font-size: 14px;
  line-height: 1.5;
  color: var(--color-ink);
  margin-left: 24%;
  text-transform: uppercase;
  text-align: left;
  margin-bottom: 0;
  font-weight: 600;
  letter-spacing: 0.03em;
}

.sp-parenthetical {
  font-family: 'Courier New', Courier, monospace;
  font-size: 13px;
  line-height: 1.5;
  color: var(--color-ink-muted);
  margin-left: 16%;
  font-style: italic;
  margin-bottom: 0;
}

.sp-dialogue {
  font-family: 'Courier New', Courier, monospace;
  font-size: 14px;
  line-height: 1.65;
  color: var(--color-ink);
  margin-left: 6%;
  margin-right: 6%;
  margin-bottom: 0.5em;
  white-space: pre-wrap;
}

.sp-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  font-family: 'Courier New', Courier, monospace;
  font-size: 14px;
  color: var(--color-ink-muted);
}

/* ── Print styles ── */
@media print {
  .preview-toolbar { display: none; }
  .screenplay-content {
    padding: 1in 1in 1in 1.5in;
    max-width: 100%;
    overflow: visible;
  }
  .screenplay-content .sp-scene-heading {
    margin-top: 2em;
    margin-bottom: 1em;
  }
  .screenplay-content .sp-dialogue-group {
    padding-left: 0;
  }
  .screenplay-content .sp-character {
    margin-left: 2.2in;
  }
  .screenplay-content .sp-parenthetical {
    margin-left: 1.6in;
  }
  .screenplay-content .sp-dialogue {
    margin-left: 1.0in;
    margin-right: 1.0in;
  }
  .screenplay-content .sp-action {
    max-width: 6in;
  }
  .screenplay-content .sp-scene-divider {
    background: #ccc;
  }
  @page {
    margin: 0.5in 0.5in 0.5in 1in;
    size: letter;
  }
}

.sp-toast {
  position: fixed;
  bottom: 24px;
  left: 50%;
  transform: translateX(-50%);
  background: var(--color-surface-2);
  border: 1px solid var(--color-hairline-strong);
  color: var(--color-ink);
  padding: 10px 18px;
  border-radius: var(--radius-pill);
  font-size: var(--text-body-sm);
  box-shadow: 0 8px 32px rgba(0,0,0,0.5);
  z-index: 999;
  pointer-events: none;
}
</style>
