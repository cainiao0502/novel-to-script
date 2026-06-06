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
          <p class="compare-overline">版本对比</p>
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
.compare-overlay {
  position: fixed; inset: 0; z-index: 600;
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
  display: flex; align-items: center; justify-content: center;
  padding: var(--space-lg);
}

.compare-card {
  width: 100%; max-width: 1100px; max-height: 90vh;
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: var(--radius-xl);
  display: flex; flex-direction: column;
  overflow: hidden;
  box-shadow: 0 8px 48px rgba(0,0,0,0.4);
}

.compare-head {
  display: flex; align-items: flex-start; justify-content: space-between;
  padding: var(--space-lg) var(--space-xl);
  border-bottom: 1px solid var(--color-hairline);
}
.compare-overline {
  font-size: var(--text-caption);
  font-weight: 600;
  letter-spacing: 0.1em;
  color: var(--color-primary);
  text-transform: uppercase;
  margin: 0 0 var(--space-xs);
}
.compare-title {
  font-family: var(--font-display);
  font-size: var(--text-headline);
  font-weight: 600;
  color: var(--color-ink);
  margin: 0;
}
.compare-close {
  width: 32px; height: 32px;
  display: flex; align-items: center; justify-content: center;
  border-radius: var(--radius-sm);
  color: var(--color-ink-subtle);
  transition: all var(--duration-fast) var(--ease-out-quad);
  flex-shrink: 0;
}
.compare-close:hover { background: var(--color-surface-2); color: var(--color-ink); }

.compare-panels {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1px;
  background: var(--color-hairline);
  flex: 1; min-height: 0;
}
.compare-panel {
  background: var(--color-surface-1);
  display: flex; flex-direction: column;
  min-height: 0;
}
.compare-panel.is-new { background: var(--color-surface-2); }

.panel-badge {
  font-size: var(--text-body-sm);
  font-weight: 500;
  color: var(--color-ink);
  padding: 10px var(--space-md);
  border-bottom: 1px solid var(--color-hairline);
  letter-spacing: -0.01em;
}
.panel-badge.muted { color: var(--color-ink-subtle); }

.panel-body {
  flex: 1; min-height: 0;
  overflow-y: auto;
}
.panel-body :deep(.script-render) {
  padding: var(--space-md);
  max-height: none;
  font-size: var(--text-body-sm);
}
.panel-body :deep(.script-head) { padding-bottom: var(--space-md); margin-bottom: var(--space-md); }
.panel-body :deep(.scene-card) { margin-bottom: var(--space-md); padding-bottom: var(--space-md); }
.panel-body :deep(.scene-header) { padding: 5px 10px; font-size: var(--text-body-sm); }
.panel-body :deep(.dialogue-line) { font-size: var(--text-body-sm); max-width: 100%; }
.panel-body :deep(.dialogue-character) { font-size: var(--text-caption); }

.panel-action {
  padding: var(--space-sm) var(--space-md);
  border-top: 1px solid var(--color-hairline);
  display: flex; justify-content: center;
}

.btn-primary {
  display: inline-flex; align-items: center; justify-content: center;
  height: 34px; padding: 0 18px;
  font-size: var(--text-body-sm); font-weight: 500;
  color: var(--color-on-primary);
  background: var(--color-primary);
  border: 0; border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out-quad);
}
.btn-primary:hover { background: var(--color-primary-hover); }
.btn-primary:active { transform: scale(0.98); }
.btn-primary:disabled { opacity: 0.35; cursor: default; transform: none; }

.btn-ghost {
  display: inline-flex; align-items: center; justify-content: center;
  height: 34px; padding: 0 18px;
  font-size: var(--text-body-sm); font-weight: 500;
  color: var(--color-primary);
  background: transparent;
  border: 1px solid var(--color-primary); border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out-quad);
}
.btn-ghost:hover { background: var(--color-primary-soft); }
.btn-ghost:active { transform: scale(0.98); }
.btn-ghost:disabled { opacity: 0.35; cursor: default; transform: none; }

.btn-text {
  font-size: var(--text-body-sm); font-weight: 500;
  color: var(--color-ink-muted);
  background: none; border: 0;
  cursor: pointer;
  padding: 6px 12px;
  border-radius: var(--radius-sm);
  transition: all var(--duration-fast) var(--ease-out-quad);
}
.btn-text:hover { background: var(--color-surface-2); color: var(--color-ink); }

.compare-foot {
  display: flex; justify-content: center;
  padding: var(--space-sm) var(--space-xl) var(--space-md);
  border-top: 1px solid var(--color-hairline);
}

@media (max-width: 768px) {
  .compare-panels { grid-template-columns: 1fr; }
  .compare-card { max-height: 95vh; }
  .compare-head { padding: var(--space-md); }
  .compare-title { font-size: var(--text-headline); }
}
</style>
