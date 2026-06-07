<script setup>
import { ref, reactive, onMounted, computed, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import gsap from 'gsap'
import { api } from '@/api'
import StatusBadge from '@/components/StatusBadge.vue'

const router = useRouter()

const headlineRef = ref(null)
const asideRef = ref(null)
const formRef = ref(null)
const previewRef = ref(null)

const mode = ref('paste')
const submitting = ref(false)
const error = ref('')

const form = reactive({
  title: '',
  sourceNovel: '',
  genre: '\u77ED\u5267/\u53E4\u88C5',
  text: ''
})
const fileInput = ref(null)
const fileName = ref('')
const fileBlob = ref(null)
const yamlFileInput = ref(null)
const yamlFileName = ref('')
const yamlFileBlob = ref(null)

const project = ref(null)
const chapters = ref([])

const charCount = computed(() => form.text.length)
const canSubmit = computed(() => {
  if (mode.value === 'paste') return form.title.trim() && form.text.trim().length > 0
  if (mode.value === 'yaml') return yamlFileBlob.value
  return form.title.trim() && fileBlob.value
})

onMounted(async () => {
  await nextTick()
  const tl = gsap.timeline({ defaults: { ease: 'power2.out' } })
  tl.from(headlineRef.value, { y: 16, opacity: 0, duration: 0.45 })
    .from(asideRef.value, { y: 20, opacity: 0, duration: 0.4 }, '-=0.25')
    .from(formRef.value, { y: 24, opacity: 0, duration: 0.5 }, '-=0.35')
})

function onFilePick(e) {
  const f = e.target.files?.[0]
  if (!f) return
  fileBlob.value = f
  fileName.value = f.name
  e.target.value = ''
}

function onYamlFilePick(e) {
  const f = e.target.files?.[0]
  if (!f) return
  yamlFileBlob.value = f
  yamlFileName.value = f.name
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
    if (mode.value === 'yaml') {
      const fd = new FormData()
      fd.append('file', yamlFileBlob.value)
      const result = await api.importYaml(fd)
      project.value = result
      chapters.value = result.chapters || []
      await nextTick()
      animatePreview()
      return
    }

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
  gsap.from(previewRef.value, { y: 12, opacity: 0, duration: 0.4, ease: 'power2.out' })
  const items = previewRef.value.querySelectorAll('[data-chapter-row]')
  gsap.from(items, { x: -6, opacity: 0, duration: 0.3, stagger: 0.04, ease: 'power2.out', delay: 0.1 })
}

async function startGeneration() {
  if (!project.value) return
  try {
    if (previewRef.value) {
      await gsap.to(previewRef.value, {
        y: -10, opacity: 0, scale: 0.98, duration: 0.3, ease: 'power2.in'
      }).then()
    }
    await api.generate(project.value.id, `${project.value.id}-${Date.now()}`)
    router.push({ name: 'project', params: { id: project.value.id } })
  } catch (e) {
    error.value = e.message
  }
}

function goToProject() {
  if (!project.value) return
  router.push({ name: 'project', params: { id: project.value.id } })
}

function reset() {
  project.value = null
  chapters.value = []
  form.text = ''
  fileBlob.value = null
  fileName.value = ''
  yamlFileBlob.value = null
  yamlFileName.value = ''
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
    <!-- Asymmetric editorial layout: headline aside + form main -->
    <div class="home-inner">

      <!-- Left: aside with headline -->
      <aside class="home-aside" ref="asideRef">
        <p class="home-label">小说转剧本</p>
        <h1 class="home-title" ref="headlineRef">
          长篇小说<br />
          <span class="hl">一键剧本化</span>
        </h1>
        <div class="home-desc">
          <p>AI 深度解析小说文本，智能拆章、逐章生成结构化剧本。人物谱系、场景调度、对白旁白、镜头语言，精准提炼。</p>
        </div>

        <!-- Feature spec list, not cards -->
        <ul class="home-specs">
          <li>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="20 6 9 17 4 12"/>
            </svg>
            <span>自动识别「第N章」章标，无需手动分段</span>
          </li>
          <li>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="20 6 9 17 4 12"/>
            </svg>
            <span>每章独立调用 LLM，限流并发 3，自动校验 YAML</span>
          </li>
          <li>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="20 6 9 17 4 12"/>
            </svg>
            <span>场、对白、旁白、镜头提示分门别类，可直接打磨</span>
          </li>
        </ul>
      </aside>

      <!-- Right: form -->
      <div class="home-main" ref="formRef">
        <div class="workzone">
          <div class="workzone-tabs">
            <button class="wz-tab" :class="{ active: mode === 'paste' }" @click="switchMode('paste')">
              粘贴文本
            </button>
            <button class="wz-tab" :class="{ active: mode === 'upload' }" @click="switchMode('upload')">
              上传文件
            </button>
            <button class="wz-tab" :class="{ active: mode === 'yaml' }" @click="switchMode('yaml')">
              导入 YAML
            </button>
          </div>

          <div class="wz-fields">
            <div class="wz-row">
              <input v-model="form.title" class="wz-input" placeholder="剧名" maxlength="128" />
              <input v-model="form.sourceNovel" class="wz-input" placeholder="原著出处" maxlength="256" />
              <input v-model="form.genre" class="wz-input" placeholder="题材" maxlength="64" />
            </div>
          </div>

          <div v-if="mode === 'paste'" class="wz-editor">
            <div class="wz-editor-head">
              <span>小说正文</span>
              <span class="wz-counter">{{ charCount.toLocaleString() }} 字</span>
            </div>
            <textarea v-model="form.text" class="wz-textarea" placeholder="粘贴小说正文。提交后自动识别章节并预览。" rows="10" />
          </div>

          <div v-else-if="mode === 'upload'" class="wz-upload">
            <label class="wz-drop">
              <input ref="fileInput" type="file" accept=".txt,.docx,.pdf" @change="onFilePick" class="wz-file-hidden" />
              <template v-if="!fileName">
                <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round" class="wz-drop-icon">
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                  <polyline points="17 8 12 3 7 8"/>
                  <line x1="12" y1="3" x2="12" y2="15"/>
                </svg>
                <span>点击选择文件</span>
                <span class="wz-drop-hint">.txt .docx .pdf，最大 200MB</span>
              </template>
              <template v-else>
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="color: var(--color-semantic-success)">
                  <polyline points="20 6 9 17 4 12"/>
                </svg>
                <span>{{ fileName }}</span>
                <span class="wz-drop-hint">点击重新选择</span>
              </template>
            </label>
          </div>

          <!-- YAML import -->
          <div v-else class="wz-upload">
            <label class="wz-drop">
              <input ref="yamlFileInput" type="file" accept=".yaml,.yml" @change="onYamlFilePick" class="wz-file-hidden" />
              <template v-if="!yamlFileName">
                <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round" class="wz-drop-icon">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                  <polyline points="14 2 14 8 20 8"/>
                </svg>
                <span>选择 YAML 剧本文件</span>
                <span class="wz-drop-hint">.yaml，直接导入已格式化的剧本</span>
              </template>
              <template v-else>
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="color: var(--color-semantic-success)">
                  <polyline points="20 6 9 17 4 12"/>
                </svg>
                <span>{{ yamlFileName }}</span>
                <span class="wz-drop-hint">点击重新选择</span>
              </template>
            </label>
          </div>

          <div v-if="error" class="wz-error">{{ error }}</div>

          <div class="wz-foot">
            <span class="wz-hint">{{ mode === 'yaml' ? '导入后直接进入剧本编辑视图。' : '提交后预览章节，确认无误即可生成剧本。' }}</span>
            <button class="wz-submit" :disabled="!canSubmit || submitting" @click="submit">
              <span v-if="submitting" class="spinner" />
              <span>{{ submitting ? '导入中' : mode === 'yaml' ? '导入剧本' : '解析章节' }}</span>
              <svg v-if="!submitting" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                <line x1="5" y1="12" x2="19" y2="12"/><polyline points="12 5 19 12 12 19"/>
              </svg>
            </button>
          </div>
        </div>

        <!-- Preview inline -->
        <div v-if="project" class="preview-block" ref="previewRef">
          <header class="pv-head">
            <div>
              <p class="pv-label">章节预览</p>
              <h2 class="pv-title">{{ project.title }}</h2>
              <p class="pv-meta">
                {{ project.totalChapters }} 个章节 ·
                {{ project.sourceNovel || '未填写原著' }} ·
                {{ project.genre || '未填写题材' }}
              </p>
            </div>
            <StatusBadge :status="project.status" />
          </header>
          <ol class="pv-list">
            <li v-for="ch in chapters" :key="ch.id" data-chapter-row>
              <span class="pv-idx">{{ String(ch.idx).padStart(2, '0') }}</span>
              <span class="pv-name">{{ ch.title }}</span>
              <span class="badge" :class="badgeClass(ch.status)">{{ statusLabel(ch.status) }}</span>
            </li>
          </ol>
          <div class="pv-actions">
            <button class="pv-btn-ghost" @click="reset">重新选择</button>
            <button class="wz-submit" @click="mode === 'yaml' ? goToProject() : startGeneration()">
              {{ mode === 'yaml' ? '进入剧本' : '开始生成剧本' }}
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                <line x1="5" y1="12" x2="19" y2="12"/><polyline points="12 5 19 12 12 19"/>
              </svg>
            </button>
          </div>
        </div>
      </div>

    </div>
  </section>
</template>

<style scoped>
.home {
  min-height: calc(100vh - 52px);
  display: flex;
  background:
    radial-gradient(ellipse 800px 500px at 20% 10%, rgba(125, 154, 110, 0.04) 0%, transparent 60%),
    radial-gradient(ellipse 600px 400px at 80% 90%, rgba(196, 122, 106, 0.03) 0%, transparent 60%),
    var(--color-canvas);
}
.home-inner {
  display: flex;
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
  padding: var(--space-section) var(--space-lg) var(--space-xl);
  gap: 80px;
}

/* Left aside */
.home-aside {
  flex: 0 0 320px;
  padding-top: var(--space-lg);
}
.home-label {
  font-size: var(--text-caption);
  font-weight: 600;
  letter-spacing: 0.12em;
  color: var(--color-primary);
  text-transform: uppercase;
  margin: 0 0 var(--space-md);
}
.home-title {
  font-family: var(--font-display);
  font-size: 40px;
  font-weight: 700;
  line-height: 1.15;
  color: var(--color-ink);
  margin: 0 0 var(--space-md);
  letter-spacing: -0.02em;
}
.hl { color: var(--color-primary); }
.home-desc {
  font-size: var(--text-body);
  color: var(--color-ink-muted);
  line-height: 1.8;
  margin-bottom: var(--space-lg);
}
.home-desc p { margin: 0; }

/* Spec list */
.home-specs {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}
.home-specs li {
  display: flex;
  align-items: flex-start;
  gap: var(--space-sm);
  font-size: var(--text-body-sm);
  color: var(--color-ink-subtle);
  line-height: 1.6;
}
.home-specs li svg {
  margin-top: 3px;
  flex-shrink: 0;
  color: var(--color-primary);
  opacity: 0.7;
}

/* Right main */
.home-main {
  flex: 1;
  min-width: 0;
}

/* Workzone (form) */
.workzone {
  background: var(--color-surface-1);
  border: 1px solid var(--color-hairline);
  border-radius: var(--radius-lg);
}
.workzone-tabs {
  display: flex;
  border-bottom: 1px solid var(--color-hairline);
}
.wz-tab {
  flex: 1;
  padding: 12px 16px;
  font-size: var(--text-body-sm);
  font-weight: 500;
  color: var(--color-ink-subtle);
  text-align: center;
  transition: all var(--duration-fast) var(--ease-out-quad);
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
}
.wz-tab.active {
  color: var(--color-ink);
  border-bottom-color: var(--color-primary);
}
.wz-tab:hover:not(.active) { color: var(--color-ink-muted); }

.wz-fields {
  padding: var(--space-md);
}
.wz-row {
  display: flex;
  gap: var(--space-sm);
}
.wz-input {
  flex: 1;
  height: 36px;
  padding: 0 10px;
  background: var(--color-surface-2);
  border: 1px solid var(--color-hairline);
  border-radius: var(--radius-sm);
  color: var(--color-ink);
  font-size: var(--text-body-sm);
  outline: none;
  transition: border-color var(--duration-fast) var(--ease-out-quad);
}
.wz-input:focus {
  border-color: var(--color-primary);
}
.wz-input::placeholder { color: var(--color-ink-tertiary); }

/* Editor textarea */
.wz-editor {
  padding: 0 var(--space-md) var(--space-md);
}
.wz-editor-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
  font-size: var(--text-caption);
  color: var(--color-ink-subtle);
}
.wz-counter { font-family: var(--font-mono); }
.wz-textarea {
  width: 100%;
  min-height: 200px;
  padding: 10px;
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: var(--radius-sm);
  color: var(--color-ink);
  font-size: var(--text-body-sm);
  font-family: var(--font-mono);
  line-height: 1.7;
  resize: vertical;
  outline: none;
  transition: border-color var(--duration-fast) var(--ease-out-quad);
}
.wz-textarea:focus {
  border-color: var(--color-primary);
}
.wz-textarea::placeholder { color: var(--color-ink-tertiary); }

/* Upload */
.wz-upload {
  padding: 0 var(--space-md) var(--space-md);
}
.wz-drop {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 140px;
  background: var(--color-canvas);
  border: 1.5px dashed var(--color-hairline-strong);
  border-radius: var(--radius-sm);
  color: var(--color-ink-muted);
  cursor: pointer;
  font-size: var(--text-body-sm);
  transition: all var(--duration-base) var(--ease-out-quad);
}
.wz-drop:hover {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
}
.wz-file-hidden {
  position: absolute; width: 0; height: 0; opacity: 0; pointer-events: none;
}
.wz-drop-icon { opacity: 0.4; }
.wz-drop-hint { font-size: var(--text-caption); color: var(--color-ink-tertiary); }

.wz-error {
  margin: 0 var(--space-md) var(--space-md);
  padding: 8px 10px;
  background: rgba(196, 122, 106, 0.06);
  border: 1px solid rgba(196, 122, 106, 0.2);
  color: var(--color-semantic-error);
  border-radius: var(--radius-sm);
  font-size: var(--text-body-sm);
}

.wz-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-sm) var(--space-md) var(--space-md);
}
.wz-hint { font-size: var(--text-caption); color: var(--color-ink-tertiary); }

/* Submit button */
.wz-submit {
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
  height: 34px;
  padding: 0 16px;
  background: var(--color-primary);
  color: var(--color-on-primary);
  border: 0;
  border-radius: var(--radius-sm);
  font-size: var(--text-button);
  font-weight: 500;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out-quad);
}
.wz-submit:hover { background: var(--color-primary-hover); }
.wz-submit:active { transform: scale(0.98); }
.wz-submit:disabled { opacity: 0.35; cursor: not-allowed; transform: none; }

.spinner {
  width: 13px; height: 13px;
  border: 2px solid currentColor; border-right-color: transparent;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* Preview block */
.preview-block {
  margin-top: var(--space-md);
  border: 1px solid var(--color-hairline);
  border-radius: var(--radius-md);
  background: var(--color-surface-1);
}
.pv-head {
  display: flex; align-items: flex-start; justify-content: space-between;
  padding: var(--space-md); gap: var(--space-md);
  border-bottom: 1px solid var(--color-hairline);
}
.pv-label {
  font-size: var(--text-caption); font-weight: 600; letter-spacing: 0.1em;
  color: var(--color-primary); text-transform: uppercase;
  margin: 0 0 var(--space-xs);
}
.pv-title {
  font-family: var(--font-display);
  font-size: var(--text-card-title); font-weight: 600;
  color: var(--color-ink); margin: 0 0 2px;
}
.pv-meta { font-size: var(--text-body-sm); color: var(--color-ink-subtle); margin: 0; }

.pv-list {
  list-style: none; padding: 0; margin: 0;
}
.pv-list li {
  display: grid;
  grid-template-columns: 36px 1fr auto;
  align-items: center;
  gap: var(--space-sm);
  padding: 10px var(--space-md);
  border-bottom: 1px solid var(--color-hairline);
  transition: background var(--duration-fast) var(--ease-out-quad);
}
.pv-list li:last-child { border-bottom: 0; }
.pv-list li:hover { background: var(--color-surface-2); }
.pv-idx { font-family: var(--font-mono); color: var(--color-primary); font-size: var(--text-caption); font-weight: 500; }
.pv-name { font-size: var(--text-body); color: var(--color-ink); }
.pv-list .badge { font-size: 10px; height: 18px; padding: 0 6px; }
.pv-list .badge::before { display: none; }

.pv-actions {
  display: flex; gap: var(--space-sm); justify-content: flex-end;
  padding: var(--space-sm) var(--space-md);
  border-top: 1px solid var(--color-hairline);
}
.pv-btn-ghost {
  height: 34px; padding: 0 14px;
  background: transparent;
  border: 0;
  color: var(--color-ink-muted);
  font-size: var(--text-button);
  cursor: pointer;
  border-radius: var(--radius-sm);
  transition: all var(--duration-fast) var(--ease-out-quad);
}
.pv-btn-ghost:hover { background: var(--color-surface-2); color: var(--color-ink); }

@media (max-width: 860px) {
  .home-inner {
    flex-direction: column;
    padding: var(--space-lg) var(--space-md);
    gap: var(--space-lg);
  }
  .home-aside {
    flex: none;
    padding-top: 0;
  }
  .home-title { font-size: 32px; }
  .wz-row { flex-direction: column; }
}
</style>
