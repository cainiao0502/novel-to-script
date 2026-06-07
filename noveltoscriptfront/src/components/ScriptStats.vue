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
