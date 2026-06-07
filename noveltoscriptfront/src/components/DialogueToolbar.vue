<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['rewrite', 'close', 'manual-edit'])

const styles = [
  { id: 'dramatic', label: '更戏剧化', desc: '增强冲突与张力' },
  { id: 'humorous', label: '更幽默', desc: '诙谐有趣' },
  { id: 'concise', label: '更简洁', desc: '精炼短促' },
  { id: 'colloquial', label: '更口语化', desc: '自然对话感' }
]

function onRewrite(style) {
  if (props.loading) return
  emit('rewrite', style)
}
</script>

<template>
  <Teleport to="body">
    <div v-show="visible" class="dialogue-toolbar">
      <div class="toolbar-header">
        <span class="toolbar-label">AI 改稿</span>
        <button class="toolbar-close" @click="$emit('close')" :disabled="loading">✕</button>
      </div>
      <div class="toolbar-actions">
        <button
          v-for="s in styles"
          :key="s.id"
          class="toolbar-btn"
          :class="{ 'is-loading': loading }"
          :disabled="loading"
          @click="onRewrite(s.id)"
        >
          <span class="toolbar-btn-label">{{ s.label }}</span>
          <span class="toolbar-btn-desc">{{ s.desc }}</span>
        </button>
      </div>
      <hr class="toolbar-divider">
      <button
        class="toolbar-btn toolbar-edit-btn"
        :disabled="loading"
        @click="$emit('manual-edit')"
      >
        <span class="toolbar-btn-label">✏️ 手动编辑</span>
        <span class="toolbar-btn-desc">自定义台词</span>
      </button>

      <div v-if="loading" class="toolbar-loading">
        <span class="spinner-sm" /> AI 正在改写…
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.dialogue-toolbar {
  position: fixed;
  z-index: 1000;
  background: var(--color-surface-1);
  border: 1px solid var(--color-hairline-strong);
  border-radius: var(--radius-lg);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
  padding: 12px;
  min-width: 220px;
  max-width: 280px;
  opacity: 0;
  will-change: transform, opacity;
}
.toolbar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--color-hairline);
}
.toolbar-label {
  font-size: var(--text-body-sm);
  font-weight: 600;
  color: var(--color-primary);
}
.toolbar-close {
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  font-size: 12px;
  color: var(--color-ink-subtle);
  border: 0;
  background: transparent;
  cursor: pointer;
  transition: all 0.15s ease;
}
.toolbar-close:hover {
  background: var(--color-surface-3);
  color: var(--color-ink);
}
.toolbar-actions {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.toolbar-btn {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border: 1px solid var(--color-hairline);
  border-radius: var(--radius-md);
  background: var(--color-surface-2);
  cursor: pointer;
  transition: all 0.15s ease;
  font-size: var(--text-body-sm);
  color: var(--color-ink);
  text-align: left;
  width: 100%;
}
.toolbar-btn:hover {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
}
.toolbar-btn:active {
  transform: scale(0.98);
}
.toolbar-btn:disabled {
  opacity: 0.5;
  cursor: default;
  transform: none;
}
.toolbar-btn-label {
  font-weight: 500;
}
.toolbar-btn-desc {
  font-size: var(--text-caption);
  color: var(--color-ink-subtle);
}
.toolbar-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 8px 0 0;
  font-size: var(--text-caption);
  color: var(--color-primary);
}
.spinner-sm {
  width: 12px;
  height: 12px;
  border: 2px solid var(--color-hairline-strong);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.toolbar-divider {
  border: 0;
  border-top: 1px solid var(--color-hairline);
  margin: 8px -12px;
}
.toolbar-edit-btn .toolbar-btn-label {
  font-size: var(--text-body-sm);
}
</style>
