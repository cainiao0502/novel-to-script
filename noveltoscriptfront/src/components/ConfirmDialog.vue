<script setup>
import { ref, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { useConfirm } from '@/composables/useConfirm'

const { state, close } = useConfirm()
const cardRef = ref(null)
const confirmBtnRef = ref(null)
let lastFocused = null

function onKeydown(e) {
  if (!state.open) return
  if (e.key === 'Escape') {
    e.preventDefault()
    close(false)
  } else if (e.key === 'Enter') {
    const tag = document.activeElement?.tagName
    if (tag !== 'BUTTON') {
      e.preventDefault()
      close(true)
    }
  }
}

watch(() => state.open, async (open) => {
  if (open) {
    lastFocused = document.activeElement
    document.body.style.overflow = 'hidden'
    await nextTick()
    confirmBtnRef.value?.focus()
  } else {
    document.body.style.overflow = ''
    if (lastFocused && typeof lastFocused.focus === 'function') {
      lastFocused.focus()
      lastFocused = null
    }
  }
})

onMounted(() => window.addEventListener('keydown', onKeydown))
onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = ''
})
</script>

<template>
  <Teleport to="body">
    <Transition name="cd-fade">
      <div
        v-if="state.open"
        class="cd-overlay"
        @click.self="close(false)"
      >
        <Transition name="cd-pop" appear>
          <div
            v-if="state.open"
            ref="cardRef"
            class="cd-card"
            :class="`cd-${state.variant}`"
            role="alertdialog"
            aria-modal="true"
            :aria-labelledby="'cd-title'"
          >
            <div class="cd-inner">
              <div class="cd-icon">
                <svg v-if="state.variant === 'danger'" viewBox="0 0 24 24" width="24" height="24" fill="none" aria-hidden="true">
                  <path d="M12 3.2 21.4 20H2.6L12 3.2Z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round" />
                  <path d="M12 10v4.5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
                  <circle cx="12" cy="17.4" r="1.05" fill="currentColor" />
                </svg>
                <svg v-else viewBox="0 0 24 24" width="24" height="24" fill="none" aria-hidden="true">
                  <circle cx="12" cy="12" r="9.2" stroke="currentColor" stroke-width="1.8" />
                  <path d="M12 10.5v5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
                  <circle cx="12" cy="8.2" r="1.1" fill="currentColor" />
                </svg>
              </div>

              <h2 id="cd-title" class="cd-title">{{ state.title }}</h2>
              <p v-if="state.message" class="cd-message">{{ state.message }}</p>

              <div class="cd-actions">
                <button
                  type="button"
                  class="btn btn-tertiary"
                  @click="close(false)"
                >{{ state.cancelText }}</button>
                <button
                  ref="confirmBtnRef"
                  type="button"
                  class="btn"
                  :class="state.variant === 'danger' ? 'btn-danger-solid' : 'btn-primary'"
                  @click="close(true)"
                >{{ state.confirmText }}</button>
              </div>
            </div>
          </div>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.cd-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-lg);
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(8px) saturate(140%);
  -webkit-backdrop-filter: blur(8px) saturate(140%);
}

.cd-card {
  position: relative;
  width: 100%;
  max-width: 400px;
  border-radius: var(--radius-lg);
  transform-origin: center;
  border: 1px solid var(--color-hairline);
  background: var(--color-surface-1);
}

.cd-inner {
  padding: var(--space-xl) var(--space-xl) var(--space-lg);
  text-align: center;
}

.cd-icon {
  width: 48px;
  height: 48px;
  margin: 0 auto var(--space-md);
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--color-primary-soft);
  color: var(--color-primary);
}
.cd-danger .cd-icon {
  background: rgba(196, 122, 106, 0.12);
  color: var(--color-semantic-error);
}

.cd-title {
  font-family: var(--font-display);
  font-size: var(--text-headline);
  font-weight: 600;
  color: var(--color-ink);
  margin: 0 0 var(--space-xs);
  letter-spacing: -0.01em;
  line-height: 1.3;
}

.cd-message {
  font-size: var(--text-body);
  color: var(--color-ink-muted);
  line-height: 1.6;
  margin: 0 auto var(--space-lg);
  max-width: 340px;
}

.cd-actions {
  display: flex;
  gap: var(--space-sm);
  justify-content: center;
}
.cd-actions .btn {
  min-width: 90px;
}

.cd-fade-enter-active,
.cd-fade-leave-active {
  transition: opacity var(--duration-base) var(--ease-out-quad);
}
.cd-fade-enter-from,
.cd-fade-leave-to {
  opacity: 0;
}

.cd-pop-enter-active {
  transition: opacity var(--duration-base) var(--ease-out-expo),
              transform var(--duration-base) var(--ease-out-expo);
}
.cd-pop-leave-active {
  transition: opacity 140ms var(--ease-out-quad),
              transform 140ms var(--ease-out-quad);
}
.cd-pop-enter-from {
  opacity: 0;
  transform: scale(0.96) translateY(6px);
}
.cd-pop-leave-to {
  opacity: 0;
  transform: scale(0.98) translateY(2px);
}

@media (prefers-reduced-motion: reduce) {
  .cd-fade-enter-active,
  .cd-fade-leave-active,
  .cd-pop-enter-active,
  .cd-pop-leave-active {
    transition: opacity 100ms linear;
  }
  .cd-pop-enter-from,
  .cd-pop-leave-to {
    transform: none;
  }
}
</style>
