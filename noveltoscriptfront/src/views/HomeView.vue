<script setup>
import { ref, reactive, onMounted, computed, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import gsap from 'gsap'
import { api } from '@/api'
import StatusBadge from '@/components/StatusBadge.vue'

const router = useRouter()

const heroRef = ref(null)
const eyebrowRef = ref(null)
const headlineRef = ref(null)
const subheadRef = ref(null)
const formRef = ref(null)
const previewRef = ref(null)

const mode = ref('paste') // 'paste' | 'upload'
const submitting = ref(false)
const error = ref('')

const form = reactive({
  title: '',
  sourceNovel: '',
  genre: '短剧/古装',
  text: ''
})
const fileInput = ref(null)
const fileName = ref('')
const fileBlob = ref(null)

const project = ref(null)
const chapters = ref([])

const charCount = computed(() => form.text.length)
const canSubmit = computed(() => {
  if (mode.value === 'paste') return form.title.trim() && form.text.trim().length > 0
  return form.title.trim() && fileBlob.value
})

onMounted(async () => {
  await nextTick()
  // Hero entry timeline
  const tl = gsap.timeline({ defaults: { ease: 'expo.out' } })
  tl.from(eyebrowRef.value, { y: 12, opacity: 0, duration: 0.5 })
    .from(headlineRef.value, { y: 24, opacity: 0, duration: 0.8 }, '-=0.25')
    .from(subheadRef.value,   { y: 16, opacity: 0, duration: 0.6 }, '-=0.45')
    .from(formRef.value,      { y: 20, opacity: 0, duration: 0.7 }, '-=0.4')
})

function onFilePick(e) {
  const f = e.target.files?.[0]
  if (!f) return
  fileBlob.value = f
  fileName.value = f.name
  e.target.value = ''
}

function switchMode(m) {
  mode.value = m
}

async function submit() {
  if (!canSubmit.value || submitting.value) return
  error.value = ''
  submitting.value = true
  try {
    let result
    if (mode.value === 'paste') {
      result = await api.createProject({
        title: form.title.trim(),
        sourceNovel: form.sourceNovel.trim() || null,
        genre: form.genre.trim() || null,
        text: form.text
      })
    } else {
      const fd = new FormData()
      fd.append('title', form.title.trim())
      if (form.sourceNovel.trim()) fd.append('sourceNovel', form.sourceNovel.trim())
      if (form.genre.trim()) fd.append('genre', form.genre.trim())
      fd.append('file', fileBlob.value)
      result = await api.uploadProject(fd)
    }
    project.value = result
    chapters.value = result.chapters || []
    await nextTick()
    animatePreview()
  } catch (e) {
    error.value = e.message
  } finally {
    submitting.value = false
  }
}

function animatePreview() {
  if (!previewRef.value) return
  gsap.from(previewRef.value, { y: 16, opacity: 0, duration: 0.5, ease: 'expo.out' })
  const items = previewRef.value.querySelectorAll('[data-chapter-row]')
  gsap.from(items, {
    x: -8,
    opacity: 0,
    duration: 0.35,
    stagger: 0.04,
    ease: 'power2.out',
    delay: 0.15
  })
}

async function startGeneration() {
  if (!project.value) return
  try {
    // Animate preview card out before navigating
    if (previewRef.value) {
      await gsap.to(previewRef.value, {
        y: -12, opacity: 0, scale: 0.97,
        duration: 0.35, ease: 'power2.in'
      }).then()
    }
    await api.generate(project.value.id, `${project.value.id}-${Date.now()}`)
    router.push({ name: 'project', params: { id: project.value.id } })
  } catch (e) {
    error.value = e.message
  }
}

function reset() {
  project.value = null
  chapters.value = []
  form.text = ''
  fileBlob.value = null
  fileName.value = ''
}

function badgeClass(s) {
  if (s === 'DONE') return 'success'
  if (s === 'FAILED') return 'error'
  if (s === 'GENERATING' || s === 'PENDING') return 'running'
  return ''
}
function statusLabel(s) {
  return ({ DONE: '已生成', FAILED: '失败', GENERATING: '生成中', PENDING: '待处理' })[s] || s
}
</script>

<template>
  <section class="home">
    <div class="container">
      <!-- HERO -->
      <div class="hero" ref="heroRef">
        <div class="hero-glow" />
        <p class="eyebrow" ref="eyebrowRef">AI-Powered Scriptwriting · v1.0</p>
        <h1 class="display-lg" ref="headlineRef">
          <span class="hl">长篇小说</span>，<br />
          一键<span class="hl">剧本化</span>
        </h1>
        <p class="subhead muted" ref="subheadRef">
          AI 深度解析小说文本，智能拆章、逐章生成结构化剧本——
          人物谱系、场景调度、对白旁白、镜头语言，精准提炼，一键即达。
        </p>
      </div>

      <!-- FORM CARD -->
      <div class="form-card" ref="formRef">
        <div class="form-card-border" />
        <div class="form-card-inner">
          <div class="form-card-head">
            <div class="tabs">
              <button
                class="tab"
                :class="{ active: mode === 'paste' }"
                @click="switchMode('paste')"
              >
                <span class="tab-icon">📝</span>
                粘贴文本
              </button>
              <button
                class="tab"
                :class="{ active: mode === 'upload' }"
                @click="switchMode('upload')"
              >
                <span class="tab-icon">📁</span>
                上传文件
              </button>
              <span class="tab-indicator" :class="{ right: mode === 'upload' }" />
            </div>
          </div>

          <div class="form-grid">
            <label class="field">
              <span class="label">剧名 <span class="req">*</span></span>
              <input
                v-model="form.title"
                class="input"
                placeholder="例：庆余年·京都风云"
                maxlength="128"
              />
            </label>

            <label class="field">
              <span class="label">原著出处</span>
              <input
                v-model="form.sourceNovel"
                class="input"
                placeholder="例：猫腻《庆余年》"
                maxlength="256"
              />
            </label>

            <label class="field">
              <span class="label">题材</span>
              <input
                v-model="form.genre"
                class="input"
                placeholder="短剧/古装/权谋"
                maxlength="64"
              />
            </label>
          </div>

          <div v-if="mode === 'paste'" class="text-pane">
            <div class="text-pane-head">
              <span class="label">小说正文 <span class="req">*</span></span>
              <span class="counter subtle">{{ charCount.toLocaleString() }} 字</span>
            </div>
            <textarea
              v-model="form.text"
              class="textarea"
              placeholder="粘贴你的小说正文。系统会自动识别「第N章 / Chapter N」等章标，至少 3 章才能生成剧本。"
              rows="12"
            />
          </div>

          <div v-else class="upload-pane">
            <label class="dropzone">
              <input
                ref="fileInput"
                type="file"
                accept=".txt,.docx,.pdf"
                @change="onFilePick"
                class="file-hidden"
              />
              <span v-if="!fileName" class="empty">
                <span class="dropzone-icon">📄</span>
                <strong>点击选择文件</strong>
                <span class="subtle">支持 .txt / .docx / .pdf，最大 200MB</span>
              </span>
              <span v-else class="filled">
                <span class="dropzone-icon">✅</span>
                <span class="filename">{{ fileName }}</span>
                <span class="subtle">已选择，点击重新选择</span>
              </span>
            </label>
          </div>

          <div v-if="error" class="error-bar">
            <span>⚠ {{ error }}</span>
          </div>

          <div class="actions">
            <span class="hint subtle">提交后会预览识别到的章节；至少 3 章才能继续。</span>
            <button
              class="btn btn-primary"
              :disabled="!canSubmit || submitting"
              @click="submit"
            >
              <span v-if="submitting" class="spinner" />
              <span>{{ submitting ? '解析中…' : '解析章节' }}</span>
            </button>
          </div>
        </div>
      </div>

      <!-- PREVIEW -->
      <div v-if="project" class="preview" ref="previewRef">
        <div class="preview-border" />
        <div class="preview-inner">
          <header class="preview-head">
            <div>
              <p class="eyebrow">章节预览</p>
              <h2 class="card-title">{{ project.title }}</h2>
              <p class="body-sm subtle">
                识别到 <strong>{{ project.totalChapters }}</strong> 个章节 ·
                {{ project.sourceNovel || '未填写原著' }} · {{ project.genre || '未填写题材' }}
              </p>
            </div>
            <StatusBadge :status="project.status" />
          </header>

          <ol class="chapter-list">
            <li v-for="ch in chapters" :key="ch.id" data-chapter-row>
              <span class="ch-index">{{ String(ch.idx).padStart(2, '0') }}</span>
              <span class="ch-title">{{ ch.title }}</span>
              <span class="ch-status badge" :class="badgeClass(ch.status)">
                {{ statusLabel(ch.status) }}
              </span>
            </li>
          </ol>

          <div class="preview-actions">
            <button class="btn btn-tertiary" @click="reset">重新选择</button>
            <button class="btn btn-primary" @click="startGeneration">
              <span>开始生成剧本</span>
              <span class="arrow">→</span>
            </button>
          </div>
        </div>
      </div>

      <!-- FEATURES STRIP -->
      <div v-if="!project" class="features">
        <div class="feature feature-1">
          <span class="feature-num">01</span>
          <h3 class="card-title">自动切章</h3>
          <p class="body-sm subtle">正则识别「第N章 / Chapter N」等章标，无需手动分段。</p>
        </div>
        <div class="feature feature-2">
          <span class="feature-num">02</span>
          <h3 class="card-title">逐章生成</h3>
          <p class="body-sm subtle">每章独立调用 LLM，限流并发 3，自动校验 YAML 合法性。</p>
        </div>
        <div class="feature feature-3">
          <span class="feature-num">03</span>
          <h3 class="card-title">结构化产出</h3>
          <p class="body-sm subtle">场、对白、旁白、镜头提示分门别类；可直接在 CodeMirror 中打磨。</p>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
  min-height: calc(100vh - 56px);
  position: relative;
}
.home .container {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: var(--space-xl) 0 var(--space-md);
}

/* ===== HERO ===== */
.hero {
  position: relative;
  max-width: 800px;
  margin: 0 auto var(--space-xl);
  text-align: center;
}
.hero-glow {
  position: absolute;
  top: -160px; left: 50%;
  width: 700px; height: 500px;
  transform: translateX(-50%);
  background: radial-gradient(ellipse, rgba(108,123,240,0.1) 0%, rgba(168,85,247,0.05) 40%, transparent 70%);
  pointer-events: none;
  z-index: 0;
}
.hero > *:not(.hero-glow) { position: relative; z-index: 1; }
.hero .eyebrow { margin-bottom: var(--space-lg); }
.hero h1 {
  margin: 0 0 var(--space-lg);
  color: var(--color-ink);
  letter-spacing: -0.03em;
  line-height: 1.15;
}
.hero .hl {
  background: linear-gradient(135deg, #6c7bf0 0%, #a78bfa 50%, #818cf8 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  padding: 0 4px;
}
.hero .subhead { max-width: 560px; margin: 0 auto; line-height: 1.75; }

/* ===== FORM CARD ===== */
.form-card {
  position: relative;
  max-width: 800px;
  margin: 0 auto;
  border-radius: var(--radius-xl);
}
.form-card-border {
  position: absolute; inset: 0;
  border-radius: var(--radius-xl);
  padding: 1px;
  background: var(--gradient-card-border);
  -webkit-mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0);
  mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0);
  -webkit-mask-composite: xor;
  mask-composite: exclude;
  pointer-events: none;
}
.form-card-inner {
  position: relative;
  background: var(--color-surface-1);
  border-radius: var(--radius-xl);
  padding: var(--space-xl);
  box-shadow: var(--shadow-card);
}
.form-card-head { margin-bottom: var(--space-lg); }
.tabs {
  position: relative;
  display: inline-flex;
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: var(--radius-pill);
  padding: 3px;
  gap: 0;
}
.tab {
  position: relative; z-index: 2;
  display: inline-flex; align-items: center; gap: 6px;
  padding: 8px 18px;
  border-radius: var(--radius-pill);
  color: var(--color-ink-subtle);
  font-size: var(--text-button);
  font-weight: 500;
  transition: color var(--duration-fast) var(--ease-out-quad);
}
.tab.active { color: var(--color-ink); }
.tab-icon { font-size: 14px; }
.tab-indicator {
  position: absolute; z-index: 1; top: 3px; left: 3px;
  width: calc(50% - 3px); height: calc(100% - 6px);
  background: var(--color-surface-2);
  border-radius: var(--radius-pill);
  transition: transform var(--duration-base) var(--ease-out-cubic);
}
.tab-indicator.right { transform: translateX(100%); }

.form-grid {
  display: grid;
  grid-template-columns: 2fr 1.4fr 1fr;
  gap: var(--space-md);
  margin-bottom: var(--space-lg);
}
.field { display: flex; flex-direction: column; gap: 8px; }
.label {
  font-size: var(--text-body-sm);
  color: var(--color-ink-muted);
  font-weight: 500;
  letter-spacing: 0.01em;
}
.req { color: var(--color-semantic-error); }

.text-pane { margin-bottom: var(--space-md); }
.text-pane-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.counter { font-size: var(--text-caption); }

.upload-pane { margin-bottom: var(--space-md); }
.dropzone {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 8px; width: 100%;
  min-height: 180px;
  background: var(--color-canvas);
  border: 1.5px dashed var(--color-hairline-strong);
  border-radius: var(--radius-lg);
  color: var(--color-ink-muted);
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-out-cubic);
}
.file-hidden {
  position: absolute;
  width: 0; height: 0;
  opacity: 0;
  pointer-events: none;
}
.dropzone:hover {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
  box-shadow: inset 0 0 0 1px rgba(108,123,240,0.1);
}
.dropzone strong { color: var(--color-ink); font-weight: 600; }
.dropzone .empty, .dropzone .filled { display: flex; flex-direction: column; align-items: center; gap: 6px; }
.dropzone .filename { font-family: var(--font-mono); color: var(--color-ink); }
.dropzone-icon { font-size: 28px; margin-bottom: 4px; }

.error-bar {
  background: rgba(240,104,104,0.06);
  border: 1px solid rgba(240,104,104,0.2);
  color: var(--color-semantic-error);
  padding: 10px 14px;
  border-radius: var(--radius-md);
  font-size: var(--text-body-sm);
  margin-bottom: var(--space-md);
}

.actions {
  display: flex; align-items: center; justify-content: space-between;
  padding-top: var(--space-md);
  border-top: 1px solid var(--color-hairline);
}
.hint { font-size: var(--text-body-sm); }

.spinner {
  width: 14px; height: 14px;
  border: 2px solid currentColor; border-right-color: transparent;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* ===== PREVIEW ===== */
.preview {
  position: relative;
  max-width: 800px;
  margin: var(--space-xl) auto 0;
  border-radius: var(--radius-xl);
}
.preview-border {
  position: absolute; inset: 0;
  border-radius: var(--radius-xl);
  padding: 1px;
  background: linear-gradient(135deg, rgba(61,214,140,0.25) 0%, rgba(108,123,240,0.15) 50%, rgba(168,85,247,0.1) 100%);
  -webkit-mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0);
  mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0);
  -webkit-mask-composite: xor;
  mask-composite: exclude;
  pointer-events: none;
}
.preview-inner {
  position: relative;
  background: var(--color-surface-1);
  border-radius: var(--radius-xl);
  padding: var(--space-xl);
  box-shadow: var(--shadow-card);
}
.preview-head {
  display: flex; align-items: flex-start; justify-content: space-between;
  gap: var(--space-md); margin-bottom: var(--space-lg);
}
.preview-head .eyebrow { margin-bottom: 6px; }
.preview-head .card-title { margin: 0 0 6px; }
.chapter-list { list-style: none; padding: 0; margin: 0 0 var(--space-lg); }
.chapter-list li {
  display: grid;
  grid-template-columns: 48px 1fr auto;
  align-items: center;
  gap: var(--space-md);
  padding: 14px var(--space-sm);
  border-bottom: 1px solid var(--color-hairline);
  border-radius: var(--radius-sm);
  transition: background var(--duration-fast) var(--ease-out-quad);
}
.chapter-list li:last-child { border-bottom: 0; }
.chapter-list li:hover { background: var(--color-surface-2); }
.ch-index {
  font-family: var(--font-mono);
  color: var(--color-primary);
  font-size: var(--text-caption);
  letter-spacing: 0.5px;
  font-weight: 500;
}
.ch-title { font-size: var(--text-body); color: var(--color-ink); }
.ch-status { font-size: var(--text-caption); }
.ch-status::before { display: none; }

.preview-actions { display: flex; gap: var(--space-sm); justify-content: flex-end; }
.preview-actions .arrow {
  transition: transform var(--duration-fast) var(--ease-out-quad);
  display: inline-block;
}
.preview-actions .btn-primary:hover .arrow { transform: translateX(3px); }

/* ===== FEATURES ===== */
.features {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-md);
  max-width: 1000px;
  margin: var(--space-xl) auto var(--space-md);
}
.feature {
  position: relative;
  display: flex; flex-direction: column; gap: 10px;
  background: var(--color-surface-1);
  border: 1px solid var(--color-hairline);
  border-radius: var(--radius-lg);
  padding: var(--space-lg);
  overflow: hidden;
  transition: all var(--duration-base) var(--ease-out-cubic);
}
.feature::before {
  content: '';
  position: absolute; top: 0; left: 0; right: 0;
  height: 2px;
  opacity: 0;
  transition: opacity var(--duration-base) var(--ease-out-cubic);
}
.feature:hover { border-color: var(--color-hairline-strong); transform: translateY(-3px); box-shadow: var(--shadow-card); }
.feature:hover::before { opacity: 1; }
.feature-1::before { background: var(--gradient-feature-1); }
.feature-2::before { background: var(--gradient-feature-2); }
.feature-3::before { background: var(--gradient-feature-3); }
.feature-num {
  font-family: var(--font-mono);
  font-size: var(--text-caption);
  letter-spacing: 1px;
  font-weight: 500;
}
.feature-1 .feature-num { color: var(--color-primary); }
.feature-2 .feature-num { color: #a78bfa; }
.feature-3 .feature-num { color: var(--color-semantic-success); }
.feature h3 { margin: 0; }

@media (max-width: 900px) {
  .form-grid { grid-template-columns: 1fr; }
  .features { grid-template-columns: 1fr; }
}
</style>
