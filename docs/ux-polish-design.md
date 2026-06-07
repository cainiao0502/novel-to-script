# UX Polish: 6 个小创意

> 在现有功能基础上添加低成本、高感知的细节优化，让工具更有温度。

## 总览

6 个独立的小改动，互不依赖，可逐个实现：

| # | 功能 | 涉及文件 | 改动量 | 技术要点 |
|---|---|---|---|---|
| 1 | 角色对白着色 | ScreenplayPreview.vue | 小 | CSS 颜色分配 |
| 2 | 剧本速览卡片 | 新建 + ProjectView.vue | 小 | 计算属性 |
| 3 | 一键复制 + 反馈 | ScriptEditor.vue / ScreenplayPreview.vue | 小 | Clipboard API + GSAP |
| 4 | 场景快速导航 | 新建或 ScriptEditor.vue | 中 | CodeMirror gutter 扩展 |
| 5 | 角色头像圈 | ProjectView.vue（角色列表） | 极小 | CSS 圆形渐变色块 |
| 6 | 导出动画 | ScreenplayPreview.vue | 小 | GSAP tl.fromTo() |

---

### 1. 角色对白自动着色

**目标：** 在 ScreenplayPreview 中每个人物的对白自动分配一个柔和色系，视觉上区分角色。

**实现：**

```js
// 为每个角色分配固定颜色（基于角色 ID 的哈希）
const CHARACTER_COLORS = [
  '#7C5CFC', // 紫色
  '#E8A87C', // 橙色
  '#5CB85C', // 绿色
  '#4A9BD9', // 蓝色
  '#E86A6A', // 红色
  '#9B59B6', // 紫罗兰
  '#1ABC9C', // 蓝绿
  '#F39C12', // 金色
]
function getCharacterColor(characterId) {
  let hash = 0
  for (const ch of characterId) hash = (hash * 31 + ch.charCodeAt(0)) & 0xffff
  return CHARACTER_COLORS[Math.abs(hash) % CHARACTER_COLORS.length]
}
```

角色名 `.sp-character` 和对应对白 `.sp-dialogue` 用 `color: var(--char-color)`。

**文件：** `ScreenplayPreview.vue` — 计算属性生成角色 → 颜色映射，模板绑定 style。

---

### 2. 剧本速览卡片

**目标：** 工作台顶部显示一个极简统计栏：总场次、对白数、角色数、场景数。

**实现：** 新建 `ScriptStats.vue`，接收 YAML prop，计算属性统计：

```js
computed: {
  stats() {
    const scenes = this.parsed?.scenes || []
    return {
      sceneCount: scenes.length,
      dialogueCount: scenes.reduce((s, sc) => s + (sc.dialogues?.length || 0), 0),
      characterCount: new Set(scenes.flatMap(s => (s.dialogues || []).map(d => d.character))).size,
      locationCount: new Set(scenes.map(s => s.location).filter(Boolean)).size,
    }
  }
}
```

4 个卡片横向排列，每个带微图标，颜色柔和。可折叠。

**文件：** 新建 `ScriptStats.vue`，在 `ProjectView.vue` 中条件渲染（viewMode === 'script' 时）。

---

### 3. 一键复制 + GSAP 轻提示

**目标：** YAML 编辑器和剧本预览加"复制到剪贴板"按钮，复制后 GSAP 动画反馈。

**实现：**

```js
import gsap from 'gsap'

function copyToClipboard(text) {
  navigator.clipboard.writeText(text)
  showToast('✓ 已复制到剪贴板')
}

function showToast(msg) {
  // toast 元素淡入 → 停留 1.5s → 淡出
  // GSAP: gsap.fromTo(toastEl, { opacity: 0, y: 10 }, { opacity: 1, y: 0 })
  // 1.5s delay → gsap.to(toastEl, { opacity: 0, y: -10, duration: 0.3 })
}
```

Toast 组件悬浮在工具栏下方，白色背景 + 绿色对勾 + 阴影。

**文件：** `ScreenplayPreview.vue` 已有工具栏，加一个按钮。`ScriptEditor.vue` 同理。

---

### 4. 场景快速导航

**目标：** 在 YAML 编辑区（CodeMirror）侧边添加场景标记，点击跳转。

**实现：**
- CodeMirror gutter 扩展：在 `scenes` 数组对应行渲染彩色圆点
- 鼠标悬停显示场景标题（INT/EXT. 地点）
- 点击跳转到场景起始行

**文件：** 新增 `src/utils/sceneGutter.js`（CodeMirror 6 gutter extension），在 `ScriptEditor.vue` 中引入并配置。

```js
import { gutter, GutterMarker } from '@codemirror/view'
```

---

### 5. 角色头像圈

**目标：** 角色列表每个人物加一个彩色圆形头像，取代纯文字列表。

**实现：** 纯 CSS。取角色名的第一个字，背景用固定色（同对冲着色方案）。

```html
<div class="char-avatar" :style="{ background: getColor(char.id) }">
  {{ char.name?.[0] }}
</div>
```

```css
.char-avatar {
  width: 32px; height: 32px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 600;
  font-size: 14px;
}
```

**文件：** `ProjectView.vue` 中角色列表渲染部分。

---

### 6. 导出完成动画

**目标：** 导出 Fountain/PDF 时，按钮触发一个 GSAP 轻动画。

**实现：** 点击导出按钮时，在按钮位置播放 tl.fromTo 动画：

```js
function animateExport(btnEl) {
  const tl = gsap.timeline()
  tl.fromTo(btnEl, { scale: 1 }, { scale: 1.05, duration: 0.1 })
    .to(btnEl, { scale: 1, duration: 0.2, ease: 'power2.out' })
  // 如果导出成功，再闪一下绿色边框
}
```

**文件：** `ScreenplayPreview.vue` 中的导出按钮点击事件。

---

## 数据流

所有功能都是纯前端改动：

```
YAML (string)
  ├→ ScriptStats.vue          (computed → 4 个数字)
  ├→ ScreenplayPreview.vue    (computed → 着色映射 + 复制 + 导出动画)
  ├→ ScriptEditor.vue         (CodeMirror gutter → 场景导航 + 复制)
  └→ ProjectView.vue          (角色列表 CSS → 头像圈)
```

零后端改动，零新依赖。

## 文件清单

| 操作 | 文件 |
|---|---|
| 新建 | `src/components/ScriptStats.vue` |
| 新建 | `src/utils/sceneGutter.js` |
| 修改 | `src/components/ScreenplayPreview.vue` |
| 修改 | `src/components/ScriptEditor.vue` |
| 修改 | `src/views/ProjectView.vue` |

## 不受影响的部分

- 后端 Java 代码 — 零改动
- 路由、状态管理（Pinia）— 零改动
- 数据库、Redis — 零改动
- API 接口 — 零改动
