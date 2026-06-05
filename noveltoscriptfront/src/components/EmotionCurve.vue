<script setup>
import { computed, ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { Line } from 'vue-chartjs'
import gsap from 'gsap'
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Filler,
  Title,
  Tooltip,
  Legend
} from 'chart.js'

ChartJS.register(
  CategoryScale, LinearScale, PointElement,
  LineElement, Filler, Title, Tooltip, Legend
)

const props = defineProps({
  arcs: {
    type: Array,
    default: () => []
    // Each item: { charId, charName, role, emotions: [{ sceneId, intensity, dominantEmotion, tags }] }
  },
  loading: { type: Boolean, default: false }
})

// ── Character selection ──
const selectedIdx = ref(0)

// Reset selection when arcs change
watch(() => props.arcs, () => {
  selectedIdx.value = 0
}, { deep: true })

const selectedArc = computed(() => props.arcs[selectedIdx.value] || null)
const emotions = computed(() => selectedArc.value?.emotions || [])

// ── Role display ──
const roleLabel = (role) => ({
  protagonist: '主角', antagonist: '反派',
  supporting: '配角', npc: 'NPC'
})[role] || role

// ── Emotion → color mapping ──
const emotionColors = {
  '愤怒': 'rgba(239, 68, 68, 0.9)',
  '紧张': 'rgba(245, 158, 11, 0.9)',
  '悬疑': 'rgba(139, 92, 246, 0.9)',
  '恐惧': 'rgba(185, 28, 28, 0.9)',
  '悲伤': 'rgba(59, 130, 246, 0.9)',
  '压抑': 'rgba(107, 114, 128, 0.9)',
  '激昂': 'rgba(220, 38, 38, 0.9)',
  '温馨': 'rgba(16, 185, 129, 0.9)',
  '幽默': 'rgba(250, 204, 21, 0.9)',
  '期待': 'rgba(99, 102, 241, 0.9)',
  '冷静': 'rgba(71, 85, 105, 0.9)',
  '从容': 'rgba(20, 184, 166, 0.9)',
  '忧虑': 'rgba(161, 98, 7, 0.9)',
  '未出场': 'rgba(148, 163, 184, 0.5)',
  '__default': 'rgba(108, 123, 240, 0.9)'
}

function colorFor(emotion) {
  return emotionColors[emotion] || emotionColors.__default
}

// ── Chart data ──
const chartData = computed(() => {
  const em = emotions.value
  if (!em.length) return { labels: [], datasets: [] }

  const labels = em.map((_, i) => `第${i + 1}场`)
  const intensities = em.map(e => e.intensity)
  const colors = em.map(e => colorFor(e.dominantEmotion))

  return {
    labels,
    datasets: [
      {
        label: selectedArc.value?.charName || '情绪强度',
        data: intensities,
        borderColor: 'rgba(108,123,240,0.9)',
        backgroundColor: 'rgba(108,123,240,0.06)',
        pointBackgroundColor: colors,
        pointBorderColor: colors,
        pointRadius: 5,
        pointHoverRadius: 8,
        pointBorderWidth: 2,
        borderWidth: 2.5,
        fill: true,
        tension: 0.35,
        segment: {
          borderColor: (ctx) => {
            const idx = ctx.p0DataIndex
            return em[idx] ? colorFor(em[idx].dominantEmotion) : colorFor('__default')
          }
        }
      }
    ]
  }
})

const chartOptions = computed(() => {
  const em = emotions.value
  return {
    responsive: true,
    maintainAspectRatio: false,
    animation: { duration: 600, easing: 'easeOutQuart' },
    interaction: {
      intersect: false,
      mode: 'index'
    },
    plugins: {
      legend: { display: false },
      tooltip: {
        backgroundColor: 'rgba(30,30,40,0.92)',
        titleFont: { size: 13, weight: '600' },
        bodyFont: { size: 12 },
        padding: 12,
        cornerRadius: 8,
        callbacks: {
          title: (items) => {
            const idx = items[0]?.dataIndex
            const e = em[idx]
            if (!e) return ''
            return `第${idx + 1}场  ·  ${e.dominantEmotion}  ·  强度 ${e.intensity}/10`
          },
          label: (ctx) => {
            const e = em[ctx.dataIndex]
            if (!e) return ''
            if (e.tags?.length) return `标签：${e.tags.join('、')}`
            return ''
          }
        }
      }
    },
    scales: {
      x: {
        grid: { display: false },
        ticks: { color: 'var(--color-ink-subtle)', font: { size: 11 } }
      },
      y: {
        min: 0,
        max: 10,
        ticks: {
          stepSize: 1,
          color: 'var(--color-ink-subtle)',
          font: { size: 11 },
          callback: (v) => v
        },
        grid: { color: 'rgba(255,255,255,0.04)' }
      }
    }
  }
})

// ── Stats for selected character ──
const stats = computed(() => {
  const em = emotions.value
  if (!em.length) return null
  const active = em.filter(e => e.intensity > 0)
  const allSorted = [...em].sort((a, b) => b.intensity - a.intensity)
  return {
    peak: allSorted[0],
    valley: em.find(e => e.intensity === 0) ? { dominantEmotion: '未出场', intensity: 0 } : allSorted[allSorted.length - 1],
    avg: active.length ? (active.reduce((s, e) => s + e.intensity, 0) / active.length).toFixed(1) : '0',
    activeScenes: active.length,
    totalScenes: em.length
  }
})

// ── Hover overlay (GSAP-animated guide line + glow) ──
const lineRef = ref(null)
const chartWrapRef = ref(null)
const guideRef = ref(null)
const glowRef = ref(null)
const dotRef = ref(null)
const overlaySize = ref({ w: 800, h: 340 })

const hover = ref({ active: false, x: 0, y: 0, idx: -1, color: 'transparent' })
let hovering = false
let ro = null

function onChartMove(e) {
  const chart = lineRef.value?.chart
  if (!chart) return
  const els = chart.getElementsAtEventForMode(e, 'index', { intersect: false }, true)
  if (!els.length) { if (hovering) hideOverlay(); return }
  const idx = els[0].index
  const point = chart.getDatasetMeta(0).data[idx]
  if (!point) return
  const color = colorFor(emotions.value[idx]?.dominantEmotion)
  showOverlay(point.x, point.y, idx, color)
}

function showOverlay(x, y, idx, color) {
  hover.value = { active: true, x, y, idx, color }

  if (!hovering) {
    hovering = true
    if (guideRef.value) {
      gsap.killTweensOf(guideRef.value)
      gsap.fromTo(guideRef.value,
        { attr: { x1: x, x2: x }, opacity: 0 },
        { attr: { x1: x, x2: x }, opacity: 1, duration: 0.35, ease: 'power3.out' }
      )
    }
    if (glowRef.value) {
      gsap.killTweensOf(glowRef.value)
      gsap.fromTo(glowRef.value,
        { attr: { cx: x, cy: y, r: 0 }, opacity: 0 },
        { attr: { r: 16, cx: x, cy: y }, opacity: 0.55, duration: 0.55, ease: 'power3.out' }
      )
    }
    if (dotRef.value) {
      gsap.killTweensOf(dotRef.value)
      gsap.fromTo(dotRef.value,
        { attr: { cx: x, cy: y, r: 0 } },
        { attr: { r: 6, cx: x, cy: y }, duration: 0.4, ease: 'back.out(2.4)' }
      )
    }
  } else {
    if (guideRef.value) {
      gsap.killTweensOf(guideRef.value)
      gsap.to(guideRef.value, { attr: { x1: x, x2: x }, duration: 0.22, ease: 'power2.out' })
    }
    if (glowRef.value) {
      gsap.killTweensOf(glowRef.value)
      gsap.to(glowRef.value, { attr: { cx: x, cy: y }, duration: 0.3, ease: 'power2.out' })
    }
    if (dotRef.value) {
      gsap.killTweensOf(dotRef.value)
      gsap.to(dotRef.value, { attr: { cx: x, cy: y }, duration: 0.25, ease: 'back.out(1.4)' })
    }
  }
}

function hideOverlay() {
  if (!hovering) return
  hovering = false
  hover.value.active = false
  const targets = [guideRef.value, glowRef.value, dotRef.value].filter(Boolean)
  targets.forEach(el => gsap.killTweensOf(el))
  gsap.to(targets, {
    opacity: 0, attr: { r: 0 }, duration: 0.22, ease: 'power2.in',
    onComplete: () => {
      if (guideRef.value) gsap.set(guideRef.value, { attr: { x1: 0, x2: 0 } })
      if (glowRef.value) gsap.set(glowRef.value, { attr: { cx: 0, cy: 0 } })
      if (dotRef.value) gsap.set(dotRef.value, { attr: { cx: 0, cy: 0 } })
    }
  })
}

onMounted(() => {
  chartWrapRef.value?.addEventListener('mousemove', onChartMove)
  chartWrapRef.value?.addEventListener('mouseleave', hideOverlay)
  ro = new ResizeObserver(entries => {
    const { width, height } = entries[0].contentRect
    overlaySize.value = { w: Math.round(width), h: Math.round(height) }
  })
  if (chartWrapRef.value) ro.observe(chartWrapRef.value)
})

onBeforeUnmount(() => {
  chartWrapRef.value?.removeEventListener('mousemove', onChartMove)
  chartWrapRef.value?.removeEventListener('mouseleave', hideOverlay)
  ro?.disconnect()
  ro = null
})
</script>

<template>
  <div class="emotion-curve">
    <!-- Loading -->
    <div v-if="loading" class="ec-loading">
      <span class="spinner-lg" />
      <p class="body-sm subtle">AI 正在分析全剧情绪曲线…</p>
    </div>

    <!-- Empty -->
    <div v-else-if="!arcs.length" class="ec-empty">
      <span class="empty-icon">📈</span>
      <p class="body-sm subtle">尚未分析情绪曲线</p>
    </div>

    <!-- Content -->
    <template v-else>
      <!-- Character selector -->
      <div class="ec-char-select">
        <label class="ec-char-select-label">角色</label>
        <select v-model="selectedIdx" class="ec-char-select-dd">
          <option v-for="(arc, i) in arcs" :key="arc.charId" :value="i">
            {{ arc.charName || '?' }} · {{ roleLabel(arc.role) }}
          </option>
        </select>
      </div>

      <!-- Stats row -->
      <div v-if="stats" class="ec-stats">
        <div class="ec-stat">
          <span class="ec-stat-num">{{ stats.peak?.intensity || '-' }}</span>
          <span class="ec-stat-label">峰值</span>
          <span class="ec-stat-extra">{{ stats.peak?.dominantEmotion }}</span>
        </div>
        <div class="ec-stat">
          <span class="ec-stat-num">{{ stats.avg }}</span>
          <span class="ec-stat-label">平均强度</span>
        </div>
        <div class="ec-stat">
          <span class="ec-stat-num">{{ stats.activeScenes }}/{{ stats.totalScenes }}</span>
          <span class="ec-stat-label">出场场次</span>
        </div>
      </div>

      <div class="ec-chart-container" ref="chartWrapRef">
        <Line ref="lineRef" :data="chartData" :options="chartOptions" :key="selectedIdx" />
        <svg
          class="ec-hover-svg"
          :viewBox="`0 0 ${overlaySize.w} ${overlaySize.h}`"
          preserveAspectRatio="none"
          aria-hidden="true"
        >
          <line
            ref="guideRef"
            class="ec-guide"
            x1="0" x2="0"
            y1="0" :y2="overlaySize.h"
          />
          <circle
            ref="glowRef"
            class="ec-glow"
            cx="0" cy="0" r="0"
            :style="{ color: hover.color }"
          />
          <circle
            ref="dotRef"
            class="ec-dot"
            cx="0" cy="0" r="0"
            :style="{ color: hover.color }"
          />
        </svg>
      </div>
    </template>
  </div>
</template>

<style scoped>
.emotion-curve {
  padding: var(--space-lg);
  font-family: var(--font-text);
}

/* ── States ── */
.ec-loading, .ec-empty {
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  padding: var(--space-xxl) 0; gap: var(--space-md);
  color: var(--color-ink-subtle);
}
.empty-icon { font-size: 36px; opacity: 0.3; }
.spinner-lg {
  width: 24px; height: 24px;
  border: 2.5px solid var(--color-hairline-strong);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* ── Character selector (compact dropdown) ── */
.ec-char-select {
  display: flex; align-items: center; gap: 8px;
  margin-bottom: var(--space-lg);
}
.ec-char-select-label {
  font-size: var(--text-body-sm);
  font-weight: 500;
  color: var(--color-ink-subtle);
  white-space: nowrap;
}
.ec-char-select-dd {
  flex: 1;
  height: 32px;
  padding: 0 10px;
  border: 1px solid var(--color-hairline-strong);
  border-radius: var(--radius-md);
  background: var(--color-surface-2);
  color: var(--color-ink);
  font-size: var(--text-body-sm);
  font-family: var(--font-text);
  cursor: pointer;
  outline: none;
  transition: border-color 0.15s ease;
}
.ec-char-select-dd:focus {
  border-color: var(--color-primary);
}

/* ── Stats ── */
.ec-stats {
  display: flex; gap: var(--space-lg);
  justify-content: center;
  margin-bottom: var(--space-lg);
  flex-wrap: wrap;
}
.ec-stat {
  display: flex; flex-direction: column; align-items: center;
  gap: 2px;
  padding: var(--space-sm) var(--space-md);
  background: var(--color-surface-2);
  border-radius: var(--radius-md);
  border: 1px solid var(--color-hairline);
  min-width: 90px;
}
.ec-stat-num {
  font-family: var(--font-mono);
  font-size: var(--text-card-title);
  font-weight: 600;
  color: var(--color-primary);
}
.ec-stat-label {
  font-size: var(--text-caption);
  color: var(--color-ink-subtle);
}
.ec-stat-extra {
  font-size: 10px;
  color: var(--color-ink-muted);
  text-align: center;
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ── Chart ── */
.ec-chart-container {
  position: relative;
  height: 340px;
}
.ec-hover-svg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  overflow: visible;
  z-index: 2;
}
.ec-guide {
  stroke: currentColor;
  stroke-width: 1;
  stroke-dasharray: 3 4;
  opacity: 0;
  color: var(--color-ink-subtle);
  filter: drop-shadow(0 0 4px rgba(108, 123, 240, 0.4));
}
.ec-glow {
  fill: currentColor;
  opacity: 0;
  filter: blur(5px);
}
.ec-dot {
  fill: currentColor;
  stroke: #ffffff;
  stroke-width: 1.5;
  opacity: 0;
  filter: drop-shadow(0 0 6px currentColor);
}
.ec-chart-container :deep(canvas) {
  transition: filter 240ms cubic-bezier(0.215, 0.61, 0.355, 1);
}
.ec-chart-container:has(.ec-glow[opacity="0.5"]):deep(canvas) {
  filter: brightness(1.05);
}
</style>
