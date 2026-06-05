<script setup>
import { ref } from 'vue'
import ScriptRender from '@/components/ScriptRender.vue'

const props = defineProps({
  originalYaml: { type: String, default: '' },
  newYaml: { type: String, default: '' },
  chapterLabel: { type: String, default: '' }
})

const emit = defineEmits(['acceptNew', 'acceptOriginal', 'cancel'])

const choosing = ref(false)

function choose(side) {
  if (choosing.value) return
  choosing.value = true
  emit(side === 'new' ? 'acceptNew' : 'acceptOriginal')
}
</script>

<template>
  <div class="compare-overlay" @click.self="$emit('cancel')">
    <div class="compare-card">
      <!-- Header -->
      <div class="compare-head">
        <div>
          <p class="compare-eyebrow">版本对比</p>
          <h2 class="compare-title">{{ chapterLabel || '剧本' }}</h2>
        </div>
        <button class="compare-close" @click="$emit('cancel')" title="取消对比">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
            <path d="M3 3L13 13M13 3L3 13" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          </svg>
        </button>
      </div>

      <!-- Two panels -->
      <div class="compare-panels">
        <!-- Original -->
        <div class="compare-panel">
          <div class="panel-badge muted">当前版本</div>
          <div class="panel-body">
            <ScriptRender :yaml="originalYaml" :is-generating="false" />
          </div>
          <div class="panel-action">
            <button class="btn-ghost" @click="choose('original')" :disabled="choosing">
              保留此版本
            </button>
          </div>
        </div>

        <!-- New -->
        <div class="compare-panel is-new">
          <div class="panel-badge">新生成版本</div>
          <div class="panel-body">
            <ScriptRender :yaml="newYaml" :is-generating="false" />
          </div>
          <div class="panel-action">
            <button class="btn-primary" @click="choose('new')" :disabled="choosing">
              保留此版本
            </button>
          </div>
        </div>
      </div>

      <!-- Footer -->
      <div class="compare-foot">
        <button class="btn-text" @click="$emit('cancel')">取消对比</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ── Overlay ── */
.compare-overlay {
  position: fixed; inset: 0; z-index: 600;
  background: rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
  display: flex; align-items: center; justify-content: center;
  padding: var(--space-lg);
}

/* ── Card ── */
.compare-card {
  width: 100%; max-width: 1100px; max-height: 90vh;
  background: #ffffff;
  border-radius: 18px;
  display: flex; flex-direction: column;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04), 0 8px 40px rgba(0,0,0,0.12);
}

/* ── Header ── */
.compare-head {
  display: flex; align-items: flex-start; justify-content: space-between;
  padding: 28px 32px 20px;
  border-bottom: 1px solid #f0f0f0;
}
.compare-eyebrow {
  font-size: 12px; font-weight: 600;
  letter-spacing: 0.08em;
  color: #0066cc;
  text-transform: uppercase;
  margin: 0 0 4px;
}
.compare-title {
  font-size: 28px; font-weight: 600;
  line-height: 1.14; letter-spacing: -0.28px;
  color: #1d1d1f;
  margin: 0;
}
.compare-close {
  width: 36px; height: 36px;
  display: flex; align-items: center; justify-content: center;
  border-radius: 50%;
  color: #7a7a7a;
  transition: all 0.15s ease;
}
.compare-close:hover { background: #f5f5f7; color: #1d1d1f; }

/* ── Panels ── */
.compare-panels {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1px;
  background: #e0e0e0;
  flex: 1; min-height: 0;
}
.compare-panel {
  background: #fafafc;
  display: flex; flex-direction: column;
  min-height: 0;
}
.compare-panel.is-new { background: #ffffff; }

.panel-badge {
  font-size: 13px; font-weight: 500;
  color: #1d1d1f;
  padding: 12px 20px;
  border-bottom: 1px solid #f0f0f0;
  letter-spacing: -0.01em;
}
.panel-badge.muted { color: #7a7a7a; }

.panel-body {
  flex: 1; min-height: 0;
  overflow-y: auto;
  padding: 0;
}
/* Compact ScriptRender inside panel */
.panel-body :deep(.script-render) {
  padding: 16px 20px;
  max-height: none;
  font-size: 14px;
}
.panel-body :deep(.script-head) { padding-bottom: 16px; margin-bottom: 16px; }
.panel-body :deep(.scene-card) { margin-bottom: 16px; padding-bottom: 16px; }
.panel-body :deep(.scene-header) { padding: 6px 12px; }
.panel-body :deep(.dialogue-line) { font-size: 14px; max-width: 100%; }
.panel-body :deep(.dialogue-character) { font-size: 13px; }

.panel-action {
  padding: 16px 20px;
  border-top: 1px solid #f0f0f0;
  display: flex; justify-content: center;
}

/* ── Buttons (Apple style) ── */
.btn-primary {
  display: inline-flex; align-items: center; justify-content: center;
  height: 38px; padding: 0 22px;
  font-size: 15px; font-weight: 400;
  line-height: 1; letter-spacing: -0.01em;
  color: #ffffff;
  background: #0066cc;
  border: 0; border-radius: 9999px;
  cursor: pointer;
  transition: all 0.15s ease;
}
.btn-primary:hover { background: #0071e3; }
.btn-primary:active { transform: scale(0.96); }
.btn-primary:disabled { opacity: 0.4; cursor: default; transform: none; }

.btn-ghost {
  display: inline-flex; align-items: center; justify-content: center;
  height: 38px; padding: 0 22px;
  font-size: 15px; font-weight: 400;
  line-height: 1; letter-spacing: -0.01em;
  color: #0066cc;
  background: transparent;
  border: 1px solid #0066cc; border-radius: 9999px;
  cursor: pointer;
  transition: all 0.15s ease;
}
.btn-ghost:hover { background: rgba(0,102,204,0.04); }
.btn-ghost:active { transform: scale(0.96); }
.btn-ghost:disabled { opacity: 0.4; cursor: default; transform: none; }

.btn-text {
  font-size: 15px; font-weight: 400;
  color: #0066cc;
  background: none; border: 0;
  cursor: pointer;
  padding: 8px 16px;
  border-radius: 6px;
  transition: all 0.15s ease;
}
.btn-text:hover { background: rgba(0,102,204,0.06); }

/* ── Footer ── */
.compare-foot {
  display: flex; justify-content: center;
  padding: 16px 32px 20px;
  border-top: 1px solid #f0f0f0;
}

@media (max-width: 768px) {
  .compare-panels { grid-template-columns: 1fr; }
  .compare-card { max-height: 95vh; }
  .compare-head { padding: 20px 20px 16px; }
  .compare-title { font-size: 22px; }
}
</style>
