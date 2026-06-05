<script setup>
import { ref, computed, watch, onMounted, nextTick } from 'vue'
import gsap from 'gsap'

const props = defineProps({
  progress: { type: Number, default: 0 },
  current: { type: Number, default: 0 },
  total: { type: Number, default: 0 },
  status: { type: String, default: 'PENDING' },
  chapters: { type: Array, default: () => [] }
})

const barRef = ref(null)
const timelineRef = ref(null)
const displayWidth = ref(0)
let tween = null
const prevStatuses = {}

// ── Computed progress from chapter statuses (not backend) ──
const doneCount = computed(() =>
  props.chapters.filter(ch => ch.status === 'DONE' || ch.status === 'QUEUED').length
)
const generatingIdx = computed(() => {
  const gen = props.chapters.find(ch => ch.status === 'GENERATING')
  return gen ? gen.idx : null
})

const computedProgress = computed(() => {
  if (!props.chapters.length) return props.progress
  let weight = 0
  props.chapters.forEach(ch => {
    if (ch.status === 'DONE' || ch.status === 'QUEUED') weight += 1
    else if (ch.status === 'GENERATING') weight += 0.35
  })
  return Math.max(0, Math.min(100, Math.round((weight / props.chapters.length) * 100)))
})

const isActive = computed(() =>
  ['GENERATING', 'PENDING'].includes(props.status)
)

function applyProgress(p) {
  if (tween) tween.kill()
  tween = gsap.to(displayWidth, {
    value: Math.max(0, Math.min(100, p)),
    duration: 0.8,
    ease: 'expo.out'
  })
}

// ── Timeline helpers ──
function segClass(i) {
  const left = props.chapters[i - 1]
  const right = props.chapters[i]
  if (!left || !right) return 'seg-pending'
  const leftDone = left.status === 'DONE' || left.status === 'QUEUED'
  const rightDone = right.status === 'DONE' || right.status === 'QUEUED'
  if (leftDone && rightDone) return 'seg-done'
  if (leftDone && right.status === 'GENERATING') return 'seg-active'
  if (leftDone) return 'seg-half'
  return 'seg-pending'
}

function dotClass(ch) {
  const s = (ch.status || 'PENDING').toLowerCase()
  return 'dot-' + (s === 'queued' ? 'queued' : s)
}

// ── Lifecycle ──
onMounted(() => {
  applyProgress(computedProgress.value)
  props.chapters.forEach(ch => { prevStatuses[ch.idx] = ch.status })
  nextTick(() => {
    if (!timelineRef.value) return
    const dots = timelineRef.value.querySelectorAll('.ch-dot')
    gsap.from(dots, {
      scale: 0, opacity: 0,
      duration: 0.4, stagger: 0.05,
      ease: 'back.out(2)',
      delay: 0.15
    })
  })
})

watch(computedProgress, applyProgress)

watch(() => props.status, (s, prev) => {
  if (s === 'COMPLETED' && prev && prev !== 'COMPLETED') {
    applyProgress(100)
    nextTick(() => {
      if (barRef.value) {
        gsap.from(barRef.value, { scale: 1.03, duration: 0.5, ease: 'elastic.out(1, 0.4)' })
      }
    })
  }
})

// ── Dot status animation ──
watch(() => props.chapters, (chs) => {
  nextTick(() => {
    if (!timelineRef.value) return
    chs.forEach(ch => {
      const prev = prevStatuses[ch.idx]
      if (prev === ch.status) return
      const dot = timelineRef.value.querySelector(`[data-ch-idx="${ch.idx}"]`)
      if (!dot) return

      if (ch.status === 'DONE') {
        gsap.from(dot, { scale: 0, duration: 0.45, ease: 'back.out(2.5)' })
        const glow = dot.querySelector('.dot-glow')
        if (glow) {
          gsap.fromTo(glow,
            { opacity: 0, scale: 0.3 },
            { opacity: 1, scale: 1.6, duration: 0.5, ease: 'power2.out' }
          )
          gsap.to(glow, { opacity: 0, scale: 1, duration: 0.8, delay: 0.4 })
        }
      } else if (ch.status === 'GENERATING') {
        gsap.from(dot, { scale: 1.4, duration: 0.35, ease: 'power2.out' })
      } else if (ch.status === 'FAILED') {
        gsap.from(dot, { scale: 0, duration: 0.4, ease: 'back.out(2)' })
        gsap.to(dot, { x: -3, duration: 0.06, repeat: 3, yoyo: true })
      }

      prevStatuses[ch.idx] = ch.status
    })
  })
}, { deep: true })

const statusLabel = (s) => ({
  PENDING: '待开始', GENERATING: '生成中',
  COMPLETED: '已完成', PARTIAL_SUCCESS: '部分完成',
  FAILED: '失败', DRAFT: '草稿', DONE: '已生成'
})[s] || s
</script>

<template>
  <div class="progress-block">
    <div class="progress-meta">
      <span class="eyebrow">生成进度</span>
      <span class="caption">
        <template v-if="isActive && generatingIdx">
          第 {{ generatingIdx }} 章生成中 ·
        </template>
        {{ statusLabel(status) }}
        <template v-if="chapters.length">
          · 已完成 {{ doneCount }}/{{ chapters.length }} 章
        </template>
        · {{ Math.round(displayWidth) }}%
      </span>
    </div>

    <div class="bar" ref="barRef" :class="{ 'is-complete': status === 'COMPLETED' }">
      <div class="fill" :style="{ width: displayWidth + '%' }">
        <!-- Flowing streams inside the fill -->
        <div v-if="isActive" class="stream s1" />
        <div v-if="isActive" class="stream s2" />
        <div v-if="isActive" class="stream s3" />
      </div>
      <!-- Shimmer on unfilled portion -->
      <div class="shimmer" v-if="isActive" />
    </div>

    <!-- Chapter timeline -->
    <div v-if="chapters.length" class="chapter-timeline" ref="timelineRef">
      <div class="timeline-track">
        <template v-for="(ch, i) in chapters" :key="ch.idx">
          <div v-if="i > 0" class="timeline-seg" :class="segClass(i)" />
          <div
            class="ch-dot"
            :class="dotClass(ch)"
            :data-ch-idx="ch.idx"
            :title="`第${ch.idx}章 · ${statusLabel(ch.status)}`"
          >
            <span class="dot-glow" />
            <span class="dot-ring" />
            <span class="dot-inner">
              <span v-if="ch.status === 'DONE' || ch.status === 'QUEUED'" class="dot-check">✓</span>
              <span v-else-if="ch.status === 'FAILED'" class="dot-x">✗</span>
              <span v-else class="dot-num">{{ ch.idx }}</span>
            </span>
            <span class="dot-label">{{ ch.idx }}</span>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.progress-block { margin-bottom: var(--space-lg); }
.progress-meta {
  display: flex; justify-content: space-between; align-items: baseline;
  margin-bottom: 8px;
}

/* ── Percentage bar ── */
.bar {
  position: relative; height: 6px;
  background: var(--color-surface-2);
  border-radius: var(--radius-pill);
  overflow: hidden;
  border: 1px solid var(--color-hairline);
}
.fill {
  position: absolute; top: -1px; left: 0; bottom: -1px;
  background: var(--gradient-primary);
  border-radius: var(--radius-pill);
  overflow: hidden; /* clip streams */
}
.shimmer {
  position: absolute; inset: 0;
  background: linear-gradient(90deg, transparent 0%, rgba(255,255,255,0.08) 50%, transparent 100%);
  background-size: 200% 100%;
  animation: shimmer 2s linear infinite;
  mix-blend-mode: overlay;
}
.bar.is-complete .fill {
  box-shadow: 0 0 14px rgba(61,214,140,0.35);
}

/* ── Flowing stream bands ── */
.stream {
  position: absolute; top: 0; bottom: 0;
  width: 35%;
  background: linear-gradient(90deg,
    transparent 0%,
    rgba(255,255,255,0.06) 25%,
    rgba(255,255,255,0.18) 50%,
    rgba(255,255,255,0.06) 75%,
    transparent 100%
  );
  animation: flow-right 2.4s linear infinite;
}
.s2 {
  animation-duration: 1.7s;
  animation-delay: -0.6s;
  width: 22%;
}
.s3 {
  animation-duration: 3.0s;
  animation-delay: -1.3s;
  width: 28%;
}

@keyframes shimmer {
  0% { background-position: 100% 0; }
  100% { background-position: -100% 0; }
}
@keyframes flow-right {
  0%   { left: -40%; }
  100% { left: 105%; }
}

/* ── Chapter timeline ── */
.chapter-timeline {
  margin-top: var(--space-lg);
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}
.timeline-track {
  display: flex;
  align-items: center;
  min-width: max-content;
  padding: 0 2px;
}

.timeline-seg {
  flex: 0 0 auto;
  width: 28px; height: 2px;
  border-radius: 1px;
  background: var(--color-hairline-strong);
  transition: background var(--duration-slow) var(--ease-out-cubic);
  margin: 0 2px;
}
.timeline-seg.seg-done  { background: var(--color-semantic-success); }
.timeline-seg.seg-active,
.timeline-seg.seg-half  { background: linear-gradient(90deg, var(--color-semantic-success) 50%, var(--color-hairline-strong) 50%); }
.timeline-seg.seg-pending { background: var(--color-hairline-strong); }

/* ── Chapter dots ── */
.ch-dot {
  position: relative;
  flex: 0 0 auto;
  display: flex; flex-direction: column; align-items: center; gap: 5px;
  cursor: default;
}
.dot-ring {
  position: absolute; top: 50%; left: 50%;
  width: 28px; height: 28px;
  transform: translate(-50%, -50%);
  border-radius: 50%;
  pointer-events: none;
}
.dot-glow {
  position: absolute; top: 50%; left: 50%;
  width: 36px; height: 36px;
  transform: translate(-50%, -50%);
  border-radius: 50%;
  pointer-events: none;
  opacity: 0;
}
.dot-inner {
  position: relative; z-index: 1;
  width: 24px; height: 24px;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 11px; font-weight: 600;
  transition: background var(--duration-base) var(--ease-out-cubic),
              color var(--duration-base) var(--ease-out-cubic),
              box-shadow var(--duration-base) var(--ease-out-cubic);
}
.dot-label {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--color-ink-subtle);
  line-height: 1;
  transition: color var(--duration-base) var(--ease-out-cubic);
}

.ch-dot.dot-pending .dot-inner {
  background: var(--color-surface-3);
  color: var(--color-ink-subtle);
  border: 1.5px solid var(--color-hairline-strong);
}
.ch-dot.dot-pending .dot-num { font-size: 10px; }

.ch-dot.dot-generating .dot-inner {
  background: var(--color-primary-soft);
  color: var(--color-primary);
  border: 1.5px solid rgba(108,123,240,0.35);
  box-shadow: 0 0 0 4px rgba(108,123,240,0.1);
}
.ch-dot.dot-generating .dot-ring {
  animation: pulse-ring 1.5s ease-out infinite;
  border: 1.5px solid rgba(108,123,240,0.3);
}
.ch-dot.dot-generating .dot-label { color: var(--color-primary); }

.ch-dot.dot-done .dot-inner {
  background: rgba(61,214,140,0.15);
  color: var(--color-semantic-success);
  border: 1.5px solid rgba(61,214,140,0.4);
}
.ch-dot.dot-done .dot-check { font-size: 13px; line-height: 1; }
.ch-dot.dot-done .dot-glow {
  background: radial-gradient(circle, rgba(61,214,140,0.25) 0%, transparent 70%);
}
.ch-dot.dot-done .dot-label { color: var(--color-semantic-success); }

.ch-dot.dot-failed .dot-inner {
  background: rgba(240,104,104,0.12);
  color: var(--color-semantic-error);
  border: 1.5px solid rgba(240,104,104,0.35);
}
.ch-dot.dot-failed .dot-x { font-size: 12px; line-height: 1; font-weight: 700; }
.ch-dot.dot-failed .dot-label { color: var(--color-semantic-error); }

.ch-dot.dot-queued .dot-inner {
  background: rgba(61,214,140,0.06);
  color: rgba(61,214,140,0.5);
  border: 1.5px solid rgba(61,214,140,0.2);
}
.ch-dot.dot-queued .dot-check { font-size: 13px; line-height: 1; opacity: 0.5; }
.ch-dot.dot-queued .dot-label { color: rgba(61,214,140,0.45); }

@keyframes pulse-ring {
  0%   { transform: translate(-50%, -50%) scale(1);   opacity: 1; }
  100% { transform: translate(-50%, -50%) scale(2.2); opacity: 0; }
}

@media (max-width: 640px) {
  .timeline-seg { width: 18px; }
  .dot-inner { width: 20px; height: 20px; font-size: 9px; }
  .dot-label { font-size: 9px; }
}
</style>
