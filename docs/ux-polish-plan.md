# UX Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add 6 small UX polish features to the novel-to-script tool: colored dialogue, script stats card, copy-to-clipboard, scene navigation, colored character avatars, and export animation.

**Architecture:** All changes are client-side only, no new dependencies. GSAP and CodeMirror are already in the project. Each feature is independent and can be implemented in any order.

**Tech Stack:** Vue 3 (Composition API), GSAP 3.15, CodeMirror 6, YAML

---

### Task 1: Character Dialogue Coloring

**Files:**
- Modify: `noveltoscriptfront/src/views/ProjectView.vue:1198-1206` — extract color palette as shared constant
- Modify: `noveltoscriptfront/src/components/ScreenplayPreview.vue` — apply colors to character names and dialogue

**Approach:** Define a shared array of 8 soft colors. Use a simple hash of the character ID to assign a deterministic color. Apply via inline `style` binding.

- [ ] **Step 1: Add shared color palette and hash function to ProjectView.vue**

In ProjectView.vue, add after `function roleLabel` (line 434):

```js
const CHAR_COLORS = [
  '#B8A9E8', '#F0C8A0', '#A8D8A8', '#A0C4E8',
  '#F0A8A8', '#C8B0E0', '#A0D8D0', '#E8D0A0',
]
function getCharColor(id) {
  let h = 0
  for (const ch of id) h = (h * 31 + ch.charCodeAt(0)) & 0xffff
  return CHAR_COLORS[Math.abs(h) % CHAR_COLORS.length]
}
```

Later, this will be reused by ScreenplayPreview. For now, just add it and export it.

- [ ] **Step 2: Apply colors to character avatars in ProjectView.vue**

In the template, find `.char-avatar` (line 744). Change:

```html
<span class="char-avatar" ...>{{ (c.name || '?').slice(0,1) }}</span>
```

To:

```html
<span class="char-avatar" :style="{ background: getCharColor(c.charId || c.id) + '30', borderColor: getCharColor(c.charId || c.id) + '60' }">{{ (c.name || '?').slice(0,1) }}</span>
```

Also remove the role-based class logic — we now use per-character colors instead.

- [ ] **Step 3: Add coloring to ScreenplayPreview.vue**

In ScreenplayPreview.vue, add after `charName` computed (line 34):

```js
const CHAR_COLORS = [
  '#B8A9E8', '#F0C8A0', '#A8D8A8', '#A0C4E8',
  '#F0A8A8', '#C8B0E0', '#A0D8D0', '#E8D0A0',
]
function getCharColor(id) {
  let h = 0
  for (const ch of id) h = (h * 31 + ch.charCodeAt(0)) & 0xffff
  return CHAR_COLORS[Math.abs(h) % CHAR_COLORS.length]
}
```

In the template, add `:style` to character name and dialogue elements.

Find line 100:
```html
<div class="sp-character">{{ charName(d.character).toUpperCase() }}</div>
<div v-if="d.parenthetical" class="sp-parenthetical">（{{ cleanParen(d.parenthetical) }}）</div>
<div class="sp-dialogue">{{ d.line }}</div>
```

Change to:
```html
<div class="sp-character" :style="{ color: getCharColor(d.character) }">{{ charName(d.character).toUpperCase() }}</div>
<div v-if="d.parenthetical" class="sp-parenthetical">（{{ cleanParen(d.parenthetical) }}）</div>
<div class="sp-dialogue" :style="{ color: getCharColor(d.character) }">{{ d.line }}</div>
```

Same for voiceover section (line 106-108):
```html
<div class="sp-character" :style="{ color: getCharColor(v.character) }">{{ charName(v.character).toUpperCase() }} (V.O.)</div>
<div class="sp-dialogue" :style="{ color: getCharColor(v.character) }">{{ v.line }}</div>
```

- [ ] **Step 4: Build to verify**

Run: `cd "D:/期末作业/qiniuyun/noveltoscriptfront" && npx vite build 2>&1 | tail -10`
Expected: `✓ built in Xms`

- [ ] **Step 5: Commit**

```bash
git add "novel to script backend/src/main/java/com/nailinai/noveltoscriptbackend" noveltoscriptfront/src
git commit -m "feat(ui): add character-based dialogue coloring"
```

---

### Task 2: Script Stats Card

**Files:**
- Create: `noveltoscriptfront/src/components/ScriptStats.vue`
- Modify: `noveltoscriptfront/src/views/ProjectView.vue`

- [ ] **Step 1: Create ScriptStats.vue**

Write to `noveltoscriptfront/src/components/ScriptStats.vue`:

```vue
<script setup>
import { computed } from 'vue'
import { parse as parseYaml } from 'yaml'

const props = defineProps({
  yaml: { type: String, default: '' }
})

const parsed = computed(() => {
  if (!props.yaml) return null
  try { return parseYaml(props.yaml) }
  catch { return null }
})

const stats = computed(() => {
  const scenes = parsed.value?.scenes || []
  const allDialogues = scenes.flatMap(s => s.dialogues || [])
  const allCharacters = new Set(allDialogues.map(d => d.character))
  const locations = new Set(scenes.map(s => s.location).filter(Boolean))
  const totalWords = allDialogues.reduce((sum, d) => sum + (d.line?.length || 0), 0)
  return {
    sceneCount: scenes.length,
    dialogueCount: allDialogues.length,
    characterCount: allCharacters.size,
    locationCount: locations.size,
    totalWords,
  }
})
</script>

<template>
  <div v-if="stats.sceneCount" class="script-stats">
    <div class="stat-item">
      <span class="stat-value">{{ stats.sceneCount }}</span>
      <span class="stat-label">场 戏</span>
    </div>
    <div class="stat-item">
      <span class="stat-value">{{ stats.dialogueCount }}</span>
      <span class="stat-label">段对白</span>
    </div>
    <div class="stat-item">
      <span class="stat-value">{{ stats.characterCount }}</span>
      <span class="stat-label">个角色</span>
    </div>
    <div class="stat-item">
      <span class="stat-value">{{ stats.locationCount }}</span>
      <span class="stat-label">个场景</span>
    </div>
    <div class="stat-item">
      <span class="stat-value">{{ stats.totalWords }}</span>
      <span class="stat-label">对白字数</span>
    </div>
  </div>
</template>

<style scoped>
.script-stats {
  display: flex;
  gap: 1px;
  background: var(--color-hairline);
  border: 1px solid var(--color-hairline);
  border-radius: var(--radius-md);
  overflow: hidden;
  margin-bottom: var(--space-md);
}
.stat-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 10px 8px;
  background: var(--color-surface-1);
}
.stat-value {
  font-family: var(--font-mono);
  font-size: 20px;
  font-weight: 700;
  color: var(--color-primary);
  line-height: 1.2;
}
.stat-label {
  font-size: var(--text-caption);
  color: var(--color-ink-subtle);
  margin-top: 2px;
}
</style>
```

- [ ] **Step 2: Add ScriptStats to ProjectView.vue**

Add import (after line 12):
```js
import ScriptStats from '@/components/ScriptStats.vue'
```

Add the component to the template, right before the three-column workbench (before line 558, the `.workbench` div):

```html
<ScriptStats
  :yaml="selectedChapter?.generatedYaml || project.scriptYaml"
/>
```

- [ ] **Step 3: Build**

Run: `cd "D:/期末作业/qiniuyun/noveltoscriptfront" && npx vite build 2>&1 | tail -10`
Expected: `✓ built in Xms`

- [ ] **Step 4: Commit**

```bash
git add noveltoscriptfront/src/components/ScriptStats.vue noveltoscriptfront/src/views/ProjectView.vue
git commit -m "feat(ui): add script stats card"
```

---

### Task 3: Copy to Clipboard + GSAP Toast

**Files:**
- Modify: `noveltoscriptfront/src/components/ScreenplayPreview.vue`

**Note:** ProjectView.vue already has a working `copyYaml()` + `flashToast()` (lines 412-431). ScreenplayPreview needs a copy button for Fountain text.

- [ ] **Step 1: Add copy function + GSAP toast to ScreenplayPreview.vue**

In the `<script setup>` section, add after `import { yamlToFountain }` (line 3):

```js
import gsap from 'gsap'
import { ref } from 'vue'
```

Add toast state and copy function after `printPdf()` (line 61):

```js
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
  // GSAP animation on next tick
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
```

Add `nextTick` to imports:
```js
import { computed, nextTick } from 'vue'
```

In the template, add a copy button in the toolbar (after the PDF button, around line 74):

```html
<button class="preview-btn" @click="copyFountain" :disabled="!fountainText" title="复制剧本">
  复制
</button>
```

Add toast element at end of template (after the closing `</div>` of `.screenplay-preview`, inside the template root):

```html
<div v-if="toastText" class="sp-toast">{{ toastText }}</div>
```

Add toast CSS at end of `<style scoped>`:

```css
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
```

- [ ] **Step 2: Build**

Run: `cd "D:/期末作业/qiniuyun/noveltoscriptfront" && npx vite build 2>&1 | tail -10`
Expected: `✓ built in Xms`

- [ ] **Step 3: Commit**

```bash
git add noveltoscriptfront/src/components/ScreenplayPreview.vue
git commit -m "feat(ui): add copy to clipboard with GSAP toast"
```

---

### Task 4: Scene Navigation (CodeMirror Gutter)

**Files:**
- Create: `noveltoscriptfront/src/utils/sceneGutter.js`
- Modify: `noveltoscriptfront/src/components/ScriptEditor.vue`

- [ ] **Step 1: Create sceneGutter.js**

Write to `noveltoscriptfront/src/utils/sceneGutter.js`:

```js
import { StateField, StateEffect } from '@codemirror/state'
import { Decoration, gutter, GutterMarker, hoverTooltip } from '@codemirror/view'

// Scene marker: a colored dot in the gutter
class SceneDotMarker extends GutterMarker {
  constructor(index, label) {
    super()
    this.index = index
    this.label = label
  }
  toDOM() {
    const el = document.createElement('div')
    el.className = 'cm-scene-dot'
    el.title = this.label
    el.style.cssText = 'width:8px;height:8px;border-radius:50%;background:var(--color-primary);margin:4px 6px;cursor:pointer;'
    el.dataset.index = this.index
    return el
  }
}

// Decoration to highlight the scene heading line
const sceneLineDeco = Decoration.line({ class: 'cm-scene-line' })

// Scene line numbers extracted from YAML
export function sceneGutterExtensions(yamlText) {
  if (!yamlText) return []

  const scenes = []
  const lines = yamlText.split('\n')
  for (let i = 0; i < lines.length; i++) {
    const trimmed = lines[i].trim()
    // Match scene definition start lines: lines like "  - scene_id: s_001"
    if (trimmed.startsWith('- scene_id:') || trimmed.startsWith('scene_id:')) {
      const match = trimmed.match(/scene_id:\s*(.+)/)
      const id = match ? match[1] : `s_${scenes.length + 1}`
      // Read ahead to find the location
      let location = ''
      for (let j = i + 1; j < Math.min(i + 10, lines.length); j++) {
        const locMatch = lines[j].trim().match(/location:\s*(.+)/)
        if (locMatch) { location = locMatch[1]; break }
      }
      scenes.push({ line: i, id, label: location || id })
    }
  }

  if (scenes.length === 0) return []

  // Create markers for each scene
  const markers = []
  const decorations = []
  for (const s of scenes) {
    markers.push({ line: s.line, marker: new SceneDotMarker(scenes.indexOf(s), s.label) })
    decorations.push(sceneLineDeco.range(s.line))
  }

  const sceneGutter = gutter({
    class: 'cm-scene-nav',
    markers: () => markers,
    initializer: () => markers,
  })

  const sceneDecoField = StateField.define({
    create: () => Decoration.none,
    update: (decos) => decos,
    provide: (field) => EditorView.decorations.from(field, () => Decoration.set(decorations, true))
  })

  return [sceneGutter, sceneDecoField]
}
```

Wait — the approach above using `gutter()` with `markers` isn't quite right for CodeMirror 6. The gutter widget expects a function that takes line number and returns a marker. Let me use a simpler approach: just render scene dot markers directly.

Actually, the simplest approach is:
1. Define a gutter that shows scene dots using the `GutterMarker` class
2. `gutter()` takes a `markers` callback that is called per line — if the line is a scene, return a marker

Let me rewrite:

```js
import { gutter, GutterMarker } from '@codemirror/view'

class SceneDotMarker extends GutterMarker {
  constructor(label) {
    super()
    this.label = label
  }
  toDOM() {
    const el = document.createElement('div')
    el.className = 'cm-scene-dot'
    el.title = this.label
    Object.assign(el.style, {
      width: '8px', height: '8px', borderRadius: '50%',
      background: 'var(--color-primary)', margin: '5px 6px',
      cursor: 'pointer', boxSizing: 'border-box'
    })
    return el
  }
}

/**
 * Find scene heading lines in YAML text.
 * Returns a Set of 0-based line numbers.
 */
function findSceneLines(yamlText) {
  const lines = yamlText.split('\n')
  const sceneLines = new Set()
  for (let i = 0; i < lines.length; i++) {
    if (/^\s*- scene_id:\s/.test(lines[i])) {
      sceneLines.add(i)
    }
  }
  return sceneLines
}

/**
 * Create CodeMirror extensions for scene navigation.
 * Returns an array of extensions to pass to EditorState.create().
 */
export function sceneGutterExtensions(yamlText) {
  const sceneLines = findSceneLines(yamlText)
  if (sceneLines.size === 0) return []

  // Build label map: line number → scene label
  const lines = yamlText.split('\n')
  const labels = {}
  for (const lineNum of sceneLines) {
    let label = ''
    for (let j = lineNum + 1; j < Math.min(lineNum + 10, lines.length); j++) {
      const m = lines[j].trim().match(/location:\s*(.+)/)
      if (m) { label = m[1]; break }
    }
    labels[lineNum] = label || `场景 ${[...sceneLines].indexOf(lineNum) + 1}`
  }

  const sceneGutter = gutter({
    class: 'cm-scene-nav',
    markers: () => (line) => {
      if (sceneLines.has(line)) {
        return new SceneDotMarker(labels[line])
      }
      return null
    },
    initializer: () => (line) => {
      if (sceneLines.has(line)) {
        return new SceneDotMarker(labels[line])
      }
      return null
    },
  })

  return [sceneGutter]
}
```

Hmm, I'm not 100% sure about the CodeMirror 6 gutter API. Let me simplify this. The `gutter()` function from `@codemirror/view` takes:
- `class` — CSS class for the gutter
- `markers` — a function that returns a `GutterMarker` source — this is an object with a `forLine(line: number)` method

Let me look at how the markers API works. Actually, `markers` takes a function that returns an object implementing `forLine(line: number): GutterMarker | null`.

```js
const sceneGutter = gutter({
  class: 'cm-scene-nav',
  markers: () => ({
    forLine(line) {
      if (sceneLines.has(line)) return new SceneDotMarker(labels[line])
      return null
    }
  })
})
```

This should work. Let me finalize the implementation.

- [ ] **Step 2: Integrate into ScriptEditor.vue**

In ScriptEditor.vue, add import:
```js
import { sceneGutterExtensions } from '@/utils/sceneGutter'
```

Modify the `build` function to include scene gutter extensions. Change line 23-36:

```js
function build(content) {
  if (!host.value) return
  if (view) { view.destroy(); view = null }
  const state = EditorState.create({
    doc: content || '',
    extensions: [
      basicSetup,
      yaml(),
      oneDark,
      EditorView.editable.of(!props.readOnly),
      EditorState.readOnly.of(props.readOnly),
      EditorView.theme({
        '&': { height: '100%', fontSize: '13px' },
        '.cm-scroller': { fontFamily: 'var(--font-mono)' }
      }),
      EditorView.updateListener.of((u) => {
        if (u.docChanged) emit('change', u.state.doc.toString())
      }),
      // Scene navigation gutter
      ...sceneGutterExtensions(content || ''),
    ]
  })
  view = new EditorView({ state, parent: host.value })
}
```

- [ ] **Step 3: Build**

Run: `cd "D:/期末作业/qiniuyun/noveltoscriptfront" && npx vite build 2>&1 | tail -10`
Expected: `✓ built in Xms`

- [ ] **Step 4: Commit**

```bash
git add noveltoscriptfront/src/utils/sceneGutter.js noveltoscriptfront/src/components/ScriptEditor.vue
git commit -m "feat(ui): add scene navigation gutter to CodeMirror"
```

---

### Task 5: Character Avatar Colors

**Files:**
- Modify: `noveltoscriptfront/src/views/ProjectView.vue`

**Note:** The `getCharColor` function was already added in Task 1. This task applies it to the character avatars.

- [ ] **Step 1: Apply per-character colors to avatars**

In ProjectView.vue template, find the `.char-avatar` span (line 744):

```html
<span class="char-avatar" :class="c.role === 'protagonist' ? 'protagonist' : (c.role === 'antagonist' ? 'antagonist' : '')">{{ (c.name || '?').slice(0,1) }}</span>
```

Replace with:

```html
<span
  class="char-avatar"
  :style="{
    background: getCharColor(c.charId || c.id) + '25',
    borderColor: getCharColor(c.charId || c.id) + '50',
    color: getCharColor(c.charId || c.id)
  }"
>{{ (c.name || '?').slice(0,1) }}</span>
```

Remove the `.char-avatar.protagonist` and `.char-avatar.antagonist` CSS blocks (lines 1207-1216) since we now assign colors dynamically.

- [ ] **Step 2: Build**

Run: `cd "D:/期末作业/qiniuyun/noveltoscriptfront" && npx vite build 2>&1 | tail -10`
Expected: `✓ built in Xms`

- [ ] **Step 3: Commit**

```bash
git add noveltoscriptfront/src/views/ProjectView.vue
git commit -m "feat(ui): add per-character avatar colors"
```

---

### Task 6: Export Animation (GSAP)

**Files:**
- Modify: `noveltoscriptfront/src/components/ScreenplayPreview.vue`

**Note:** GSAP is already imported in this component (from Task 3).

- [ ] **Step 1: Add GSAP animation to export buttons**

In ScreenplayPreview.vue, add a ref for the toolbar and modify `downloadFountain` and `printPdf`:

Add after `const toastText = ref('')`:
```js
const toolbarRef = ref(null)
```

Add `ref="toolbarRef"` to the `.preview-toolbar` div (line 66):
```html
<div class="preview-toolbar" ref="toolbarRef">
```

Modify `downloadFountain` function:

```js
function downloadFountain() {
  const text = fountainText.value
  if (!text) return
  animateExport()
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
  animateExport()
  window.print()
}

function animateExport() {
  const el = toolbarRef.value
  if (!el) return
  const tl = gsap.timeline()
  tl.to(el, { scale: 1.015, duration: 0.1, ease: 'power1.out' })
    .to(el, { scale: 1, duration: 0.25, ease: 'power2.out' })
}
```

- [ ] **Step 2: Build**

Run: `cd "D:/期末作业/qiniuyun/noveltoscriptfront" && npx vite build 2>&1 | tail -10`
Expected: `✓ built in Xms`

- [ ] **Step 3: Commit**

```bash
git add noveltoscriptfront/src/components/ScreenplayPreview.vue
git commit -m "feat(ui): add GSAP export button animation"
```

---

## Verification

After all tasks are complete, verify the full build:

```bash
cd "D:/期末作业/qiniuyun/noveltoscriptfront" && npx vite build
```

Expected: `✓ built in Xms` with no errors.

Run a quick sanity check on each feature:
1. **Dialogue coloring**: Open project → 导出预览 tab → verify each character's name and dialogue has a different soft color
2. **Stats card**: Open project → verify stats card displays above the workbench with scene/dialogue/character/location/word counts
3. **Copy + toast**: In 导出预览 tab → click 复制 button → verify GSAP toast animation appears
4. **Scene nav**: Open YAML tab → verify scene dots in the gutter
5. **Avatar colors**: Open project → verify character avatars show unique soft colors instead of uniform background
6. **Export animation**: Click 导出 Fountain or 导出 PDF · 打印 → verify toolbar briefly scales up
