<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import gsap from 'gsap'
import { api } from '@/api'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

// ── Refs ──
const frameRef = ref(null)
const brandSideRef = ref(null)
const brandIconRef = ref(null)
const brandTitleRef = ref(null)
const brandDescRef = ref(null)
const brandListRef = ref(null)
const authSideRef = ref(null)
const authCardRef = ref(null)
const tabsRef = ref(null)
const formWrapRef = ref(null)
const submitRef = ref(null)
const footRef = ref(null)
const captchaModalRef = ref(null)
let gsapCtx = null

// ── Auth state ──
const authMode = ref('login')
const loginType = ref(1)

const form = reactive({
  mobile: '',
  password: '',
  confirmPassword: '',
  verifyCode: '',
  codeType: 1
})

const submitting = ref(false)
const sendingCode = ref(false)
const codeCountdown = ref(0)
let countdownTimer = null
const errorMsg = ref('')

// ── Captcha ──
const captchaVisible = ref(false)
const captchaData = ref(null)
const sliderPosition = ref(0)
const sliderDragging = ref(false)
const sliderTrackRef = ref(null)
const sliderBtnRef = ref(null)
const sliderVerified = ref(false)
const sliderVerification = ref('')
const captchaLoading = ref(false)
const captchaError = ref('')
const sliderSuccess = ref(false)
const trackWidth = ref(0)
const btnRadius = 18

// ── Computed ──
const canSubmit = computed(() => {
  if (!form.mobile || !/^1[3-9]\d{9}$/.test(form.mobile)) return false
  if (authMode.value === 'login') {
    if (loginType.value === 1) return !!form.password
    return !!form.verifyCode
  }
  if (!form.password || form.password.length < 6) return false
  if (form.password !== form.confirmPassword) return false
  return !!form.verifyCode
})

const redirectPath = computed(() => route.query?.redirect || '/')

// ── Methods ──
function switchMode(mode) {
  authMode.value = mode
  errorMsg.value = ''
  form.password = ''
  form.confirmPassword = ''
  form.verifyCode = ''
  loginType.value = 1
}

function switchLoginType(type) {
  loginType.value = type
  form.password = ''
  form.verifyCode = ''
  errorMsg.value = ''
}

function startCountdown() {
  codeCountdown.value = 60
  countdownTimer = setInterval(() => {
    codeCountdown.value--
    if (codeCountdown.value <= 0) {
      clearInterval(countdownTimer)
      countdownTimer = null
    }
  }, 1000)
}

async function openCaptcha() {
  captchaLoading.value = true
  captchaVisible.value = true
  captchaError.value = ''
  sliderVerified.value = false
  sliderPosition.value = 0
  sliderSuccess.value = false
  try {
    const data = await api.getCaptcha()
    captchaData.value = data
    await nextTick()
    if (sliderTrackRef.value) {
      trackWidth.value = sliderTrackRef.value.getBoundingClientRect().width
    }
    if (captchaModalRef.value) {
      gsap.from(captchaModalRef.value, {
        scale: 0.95, opacity: 0, duration: 0.25, ease: 'power2.out'
      })
    }
  } catch (e) {
    captchaError.value = e.message || '获取验证码失败'
  } finally {
    captchaLoading.value = false
  }
}

function closeCaptcha() {
  captchaVisible.value = false
  captchaData.value = null
  sliderPosition.value = 0
  sliderDragging.value = false
  document.removeEventListener('mousemove', onSliderMove)
  document.removeEventListener('mouseup', onSliderEnd)
  document.removeEventListener('touchmove', onSliderMove)
  document.removeEventListener('touchend', onSliderEnd)
}

const pieceWidthPct = computed(() => {
  if (!captchaData.value?.backgroundImageWidth) return 0
  return (captchaData.value.templateImageWidth / captchaData.value.backgroundImageWidth) * 100
})
const pieceWidthPx = computed(() => {
  if (!trackWidth.value) return 0
  return (pieceWidthPct.value / 100) * trackWidth.value
})
const sliderPosPct = computed(() => {
  if (!trackWidth.value) return 0
  return (sliderPosition.value / trackWidth.value) * 100
})

function onSliderStart(e) {
  if (sliderVerified.value) return
  if (sliderTrackRef.value) {
    trackWidth.value = sliderTrackRef.value.getBoundingClientRect().width
  }
  sliderDragging.value = true
  sliderSuccess.value = false
  e.preventDefault()
  document.addEventListener('mousemove', onSliderMove)
  document.addEventListener('mouseup', onSliderEnd)
  document.addEventListener('touchmove', onSliderMove, { passive: false })
  document.addEventListener('touchend', onSliderEnd)
}

function onSliderMove(e) {
  if (!sliderDragging.value || !sliderTrackRef.value || !captchaData.value) return
  const rect = sliderTrackRef.value.getBoundingClientRect()
  const clientX = e.touches ? e.touches[0].clientX : e.clientX
  const trackPx = rect.width
  const piecePx = pieceWidthPx.value
  const maxPx = Math.max(0, trackPx - piecePx)
  let x = clientX - rect.left - btnRadius
  x = Math.max(0, Math.min(x, maxPx))
  sliderPosition.value = x
  trackWidth.value = trackPx
}

function onSliderEnd() {
  if (!sliderDragging.value) return
  sliderDragging.value = false
  document.removeEventListener('mousemove', onSliderMove)
  document.removeEventListener('mouseup', onSliderEnd)
  document.removeEventListener('touchmove', onSliderMove)
  document.removeEventListener('touchend', onSliderEnd)
  verifySlider()
}

async function verifySlider() {
  if (!captchaData.value) return
  const trackPx = trackWidth.value || (sliderTrackRef.value?.getBoundingClientRect().width ?? 0)
  const xFrac = trackPx > 0 ? (sliderPosition.value / trackPx) : 0
  try {
    const result = await api.checkCaptcha({
      id: captchaData.value.id, x: xFrac
    })
    const captchaVerification = result?.captchaVerification || result?.id || ''
    sliderVerified.value = true
    sliderVerification.value = captchaVerification
    sliderSuccess.value = true
    setTimeout(() => { closeCaptcha(); sendCode() }, 400)
  } catch (e) {
    gsap.to('.captcha-slider-btn', {
      x: 0, duration: 0.3, ease: 'power2.out',
      onComplete: () => { sliderPosition.value = 0 }
    })
    sliderPosition.value = 0
    captchaError.value = e.message || '验证失败，请重试'
  }
}

async function sendCode() {
  if (sendingCode.value || codeCountdown.value > 0) return
  if (!/^1[3-9]\d{9}$/.test(form.mobile)) {
    errorMsg.value = '请输入正确的手机号'
    return
  }
  if (!sliderVerification.value) { openCaptcha(); return }
  sendingCode.value = true
  errorMsg.value = ''
  try {
    const codeType = authMode.value === 'register' ? 1 : 2
    await api.sendCode({
      mobile: form.mobile, type: codeType,
      captchaVerification: sliderVerification.value
    })
    startCountdown()
  } catch (e) {
    errorMsg.value = e.message || '发送验证码失败'
    if (e.message?.includes('滑块')) sliderVerification.value = ''
  } finally {
    sendingCode.value = false
  }
}

async function handleSubmit() {
  if (!canSubmit.value || submitting.value) return
  errorMsg.value = ''
  submitting.value = true
  try {
    if (authMode.value === 'login') {
      await auth.login({
        mobile: form.mobile, type: loginType.value,
        password: loginType.value === 1 ? form.password : undefined,
        verifyCode: loginType.value === 2 ? form.verifyCode : undefined
      })
    } else {
      await auth.register({
        mobile: form.mobile, password: form.password,
        verifyCode: form.verifyCode
      })
    }
    router.push(redirectPath.value)
  } catch (e) {
    errorMsg.value = e.message || `${authMode.value === 'login' ? '登录' : '注册'}失败`
  } finally {
    submitting.value = false
  }
}

// ── GSAP Entrance Animation ──
onMounted(async () => {
  await nextTick()
  gsapCtx = gsap.context(() => {
    const mm = gsap.matchMedia()
    const allRefs = () => [
      frameRef.value,
      brandIconRef.value, brandTitleRef.value,
      brandDescRef.value, brandListRef.value,
      authCardRef.value, tabsRef.value, formWrapRef.value,
      submitRef.value, footRef.value
    ].filter(Boolean)

    mm.add('(prefers-reduced-motion: reduce)', () => {
      gsap.set(allRefs(), { clearProps: 'all', opacity: 1, y: 0 })
      return () => {}
    })
    mm.add('(prefers-reduced-motion: no-preference)', () => {
      const cp = 'opacity,transform,translate,rotate,scale'
      const tl = gsap.timeline({ defaults: { ease: 'power2.out' } })

      tl.from(frameRef.value, { y: 12, opacity: 0, duration: 0.4, clearProps: cp })
        .from(brandIconRef.value, { y: 6, opacity: 0, duration: 0.25, clearProps: cp }, '-=0.18')
        .from(brandTitleRef.value, { y: 5, opacity: 0, duration: 0.25, clearProps: cp }, '-=0.12')
        .from(brandDescRef.value, { y: 5, opacity: 0, duration: 0.2, clearProps: cp }, '-=0.08')
        .from(Array.from(brandListRef.value?.children || []), {
          y: 4, opacity: 0, duration: 0.18, stagger: 0.04, clearProps: cp
        }, '-=0.04')
        .from(authCardRef.value, { y: 8, opacity: 0, duration: 0.25, clearProps: cp }, '-=0.18')
        .from(tabsRef.value, { y: 6, opacity: 0, duration: 0.2, clearProps: cp }, '-=0.12')
        .from(formWrapRef.value, { y: 6, opacity: 0, duration: 0.2, clearProps: cp }, '-=0.08')
        .from(submitRef.value, { y: 5, opacity: 0, duration: 0.18, clearProps: cp }, '-=0.06')
        .from(footRef.value, { y: 4, opacity: 0, duration: 0.15, clearProps: cp }, '-=0.04')

      tl.eventCallback('onComplete', () => {
        gsap.set(allRefs(), { clearProps: cp })
        gsap.set(frameRef.value, { willChange: 'auto' })
      })

      const safetyTimer = setTimeout(() => {
        gsap.set(allRefs(), { clearProps: cp })
      }, 2000)

      return () => { tl.kill(); clearTimeout(safetyTimer) }
    })
  })
})

onBeforeUnmount(() => {
  if (countdownTimer) clearInterval(countdownTimer)
  if (gsapCtx) gsapCtx.revert()
  document.removeEventListener('mousemove', onSliderMove)
  document.removeEventListener('mouseup', onSliderEnd)
  document.removeEventListener('touchmove', onSliderMove)
  document.removeEventListener('touchend', onSliderEnd)
})
</script>

<template>
  <div class="login-page">

    <div class="frame" ref="frameRef">
      <div class="split-wrap">

        <!-- ── Brand Panel (Left) ── -->
        <aside class="brand-side" ref="brandSideRef">
          <div class="brand-bg" aria-hidden="true" />
          <div class="brand-body">
            <div class="brand-icon" ref="brandIconRef">
              <svg width="22" height="22" viewBox="0 0 48 48" fill="none" stroke="currentColor"
                stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M12 44h24M24 44V20M6 28l8-18M42 28l-8-18M14 10l10-6 10 6" />
                <path d="M20 20a4 4 0 0 1 8 0v4a4 4 0 0 1-4 4h0a4 4 0 0 1-4-4v-4z" opacity="0.6" />
                <path d="M34 16c-2-3-6-5-10-5s-8 2-10 5" opacity="0.4" />
              </svg>
            </div>
            <h1 class="brand-title" ref="brandTitleRef">剧本工坊</h1>
            <p class="brand-desc" ref="brandDescRef">让每一段故事，找到它的舞台</p>
            <ul class="brand-list" ref="brandListRef">
              <li>
                <svg width="4" height="4" viewBox="0 0 6 6" fill="currentColor" aria-hidden="true">
                  <circle cx="3" cy="3" r="3" />
                </svg>
                智能小说转剧本
              </li>
              <li>
                <svg width="4" height="4" viewBox="0 0 6 6" fill="currentColor" aria-hidden="true">
                  <circle cx="3" cy="3" r="3" />
                </svg>
                AI 辅助对白改写
              </li>
              <li>
                <svg width="4" height="4" viewBox="0 0 6 6" fill="currentColor" aria-hidden="true">
                  <circle cx="3" cy="3" r="3" />
                </svg>
                情感曲线分析
              </li>
            </ul>
          </div>
        </aside>

        <!-- ── Auth Panel (Right) ── -->
        <main class="auth-side" ref="authSideRef">
          <div class="auth-body" ref="authCardRef">

            <!-- Mobile brand -->
            <div class="auth-mobile-brand">
              <span class="auth-mobile-mark">
                <svg width="12" height="12" viewBox="0 0 48 48" fill="none" stroke="currentColor"
                  stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M12 44h24M24 44V20M6 28l8-18M42 28l-8-18M14 10l10-6 10 6" />
                  <path d="M20 20a4 4 0 0 1 8 0v4a4 4 0 0 1-4 4h0a4 4 0 0 1-4-4v-4z" opacity="0.6" />
                </svg>
              </span>
              <span class="auth-mobile-text">剧本工坊</span>
            </div>

            <!-- Tabs -->
            <div class="auth-tabs" ref="tabsRef">
              <button
                class="tab-btn"
                :class="{ active: authMode === 'login' }"
                @click="switchMode('login')"
              >登录</button>
              <button
                class="tab-btn"
                :class="{ active: authMode === 'register' }"
                @click="switchMode('register')"
              >注册</button>
            </div>

            <!-- Form -->
            <div class="form-wrap" ref="formWrapRef">
              <form @submit.prevent="handleSubmit" novalidate>
                <!-- Mobile -->
                <div class="field">
                  <label class="field-label">手机号</label>
                  <div class="input-box">
                    <span class="input-prefix">+86</span>
                    <input
                      v-model="form.mobile"
                      type="tel"
                      class="input-field"
                      placeholder="请输入手机号"
                      maxlength="11"
                      autocomplete="tel"
                      @input="errorMsg = ''"
                    />
                  </div>
                </div>

                <!-- Login: password mode -->
                <template v-if="authMode === 'login' && loginType === 1">
                  <div class="field">
                    <label class="field-label">密码</label>
                    <div class="input-box">
                      <input
                        v-model="form.password"
                        type="password"
                        class="input-field"
                        placeholder="请输入密码"
                        maxlength="20"
                        autocomplete="current-password"
                        @input="errorMsg = ''"
                      />
                    </div>
                  </div>
                  <div class="field-row">
                    <button type="button" class="toggle-link" @click="switchLoginType(2)">
                      使用验证码登录
                    </button>
                  </div>
                </template>

                <!-- Login: code mode -->
                <template v-if="authMode === 'login' && loginType === 2">
                  <div class="field">
                    <label class="field-label">验证码</label>
                    <div class="code-row">
                      <div class="input-box code-input">
                        <input
                          v-model="form.verifyCode"
                          type="text"
                          class="input-field"
                          placeholder="输入验证码"
                          maxlength="6"
                          autocomplete="one-time-code"
                          @input="errorMsg = ''"
                        />
                      </div>
                      <button
                        type="button"
                        class="btn-code"
                        :disabled="sendingCode || codeCountdown > 0"
                        @click="sendCode"
                      >
                        <template v-if="codeCountdown > 0">{{ codeCountdown }}s</template>
                        <template v-else-if="sendingCode">发送中</template>
                        <template v-else>获取验证码</template>
                      </button>
                    </div>
                  </div>
                  <div class="field-row">
                    <button type="button" class="toggle-link" @click="switchLoginType(1)">
                      使用密码登录
                    </button>
                  </div>
                </template>

                <!-- Register -->
                <template v-if="authMode === 'register'">
                  <div class="field">
                    <label class="field-label">设置密码</label>
                    <div class="input-box">
                      <input
                        v-model="form.password"
                        type="password"
                        class="input-field"
                        placeholder="6-20 位密码"
                        maxlength="20"
                        autocomplete="new-password"
                        @input="errorMsg = ''"
                      />
                    </div>
                  </div>
                  <div class="field">
                    <label class="field-label">确认密码</label>
                    <div class="input-box">
                      <input
                        v-model="form.confirmPassword"
                        type="password"
                        class="input-field"
                        placeholder="再次输入密码"
                        maxlength="20"
                        autocomplete="new-password"
                        @input="errorMsg = ''"
                      />
                    </div>
                  </div>
                  <div class="field">
                    <label class="field-label">验证码</label>
                    <div class="code-row">
                      <div class="input-box code-input">
                        <input
                          v-model="form.verifyCode"
                          type="text"
                          class="input-field"
                          placeholder="输入验证码"
                          maxlength="6"
                          autocomplete="one-time-code"
                          @input="errorMsg = ''"
                        />
                      </div>
                      <button
                        type="button"
                        class="btn-code"
                        :disabled="sendingCode || codeCountdown > 0"
                        @click="sendCode"
                      >
                        <template v-if="codeCountdown > 0">{{ codeCountdown }}s</template>
                        <template v-else-if="sendingCode">发送中</template>
                        <template v-else>获取验证码</template>
                      </button>
                    </div>
                  </div>
                </template>

                <!-- Error -->
                <div v-if="errorMsg" class="form-error">{{ errorMsg }}</div>

                <!-- Submit -->
                <button
                  type="submit"
                  class="btn-submit"
                  :disabled="!canSubmit || submitting"
                  ref="submitRef"
                >
                  <template v-if="submitting">
                    <span class="spinner" />
                    <span>处理中</span>
                  </template>
                  <template v-else>
                    <span>{{ authMode === 'login' ? '登录' : '注册' }}</span>
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                      stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                      <line x1="5" y1="12" x2="19" y2="12"/><polyline points="12 5 19 12 12 19"/>
                    </svg>
                  </template>
                </button>
              </form>
            </div>

            <!-- Foot -->
            <div class="auth-foot" ref="footRef">
              <p v-if="authMode === 'login'">
                还没有账号？
                <button class="foot-link" @click="switchMode('register')">注册</button>
              </p>
              <p v-else>
                已有账号？
                <button class="foot-link" @click="switchMode('login')">登录</button>
              </p>
            </div>
          </div>
        </main>

      </div>
    </div>

    <!-- ── Captcha Modal ── -->
    <Teleport to="body">
      <transition name="modal-fade">
        <div v-if="captchaVisible" class="modal-overlay" @click.self="closeCaptcha">
          <div class="modal-card" ref="captchaModalRef">
            <div class="modal-header">
              <span>安全验证</span>
              <button class="modal-close" type="button" @click="closeCaptcha">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none"
                  stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
                </svg>
              </button>
            </div>
            <div class="modal-body">
              <div v-if="captchaLoading" class="captcha-loading">
                <span class="spinner" /><span>加载中</span>
              </div>
              <div v-else-if="captchaError && !captchaData" class="captcha-error-state">
                <p>{{ captchaError }}</p>
                <button class="btn-retry" type="button" @click="openCaptcha">重试</button>
              </div>
              <div v-else-if="captchaData" class="captcha-puzzle">
                <div class="captcha-img-wrap"
                  :style="{ aspectRatio: `${captchaData.backgroundImageWidth} / ${captchaData.backgroundImageHeight}` }">
                  <div class="captcha-bg"
                    :style="{ backgroundImage: `url(${captchaData.backgroundImage})` }">
                    <div class="captcha-piece" :style="{
                      width: pieceWidthPct + '%',
                      backgroundImage: `url(${captchaData.sliderImage})`,
                      backgroundSize: '100% 100%',
                      left: sliderPosPct + '%'
                    }" />
                    <div v-if="sliderSuccess" class="captcha-done">
                      <svg width="32" height="32" viewBox="0 0 24 24" fill="none"
                        stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                        <polyline points="20 6 9 17 4 12"/>
                      </svg>
                    </div>
                  </div>
                </div>
                <div class="captcha-track" ref="sliderTrackRef"
                  @mousedown="onSliderStart" @touchstart.prevent="onSliderStart">
                  <div class="captcha-track-bg">
                    <div class="captcha-track-fill"
                      :style="{ width: (sliderPosPct + pieceWidthPct) + '%' }" />
                  </div>
                  <div ref="sliderBtnRef" class="captcha-slider-btn"
                    :class="{ dragging: sliderDragging, success: sliderSuccess }"
                    :style="{ left: (sliderPosPct + pieceWidthPct / 2) + '%' }">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none"
                      stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                      <polyline points="15 18 9 12 15 6"/>
                    </svg>
                  </div>
                  <span class="captcha-track-label"
                    :class="{ hide: sliderDragging || sliderPosition > 10 }">
                    拖动滑块完成拼图
                  </span>
                </div>
                <p v-if="captchaError && captchaData" class="captcha-err">{{ captchaError }}</p>
              </div>
            </div>
          </div>
        </div>
      </transition>
    </Teleport>
  </div>
</template>

<style scoped>
/* ═══════════════════════════════════════════
   Login Page — Framed Fixed Layout
   ═══════════════════════════════════════════ */
.login-page {
  min-height: 100vh;
  min-height: 100dvh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-canvas);
  padding: var(--space-lg);
}

/* ── Frame (single clean border) ── */
.frame {
  width: 100%;
  max-width: 640px;
  border-radius: 20px;
  background: var(--color-surface-1);
  border: 1px solid var(--color-hairline-strong);
  box-shadow:
    0 1px 3px rgba(44, 40, 37, 0.04),
    0 6px 18px rgba(44, 40, 37, 0.05);
  overflow: hidden;
}

/* Fixed height — sized for register form, same for login */
.split-wrap {
  display: flex;
  min-height: 460px;
}

/* ── Brand Panel (Left) ── */
.brand-side {
  width: 270px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  padding: var(--space-xl) var(--space-lg);
  background:
    radial-gradient(ellipse 80% 55% at 30% 20%, rgba(125, 154, 110, 0.08) 0%, transparent 60%),
    radial-gradient(ellipse 55% 45% at 70% 80%, rgba(125, 154, 110, 0.04) 0%, transparent 60%),
    var(--color-surface-2);
}

.brand-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}
.brand-bg::before {
  content: '';
  position: absolute;
  top: -10%;
  right: -5%;
  width: 45%;
  height: 60%;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(125, 154, 110, 0.06) 0%, transparent 70%);
}
.brand-bg::after {
  content: '';
  position: absolute;
  bottom: -8%;
  left: -4%;
  width: 35%;
  height: 50%;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(125, 154, 110, 0.04) 0%, transparent 70%);
}

.brand-body {
  position: relative;
  z-index: 1;
  text-align: center;
}

.brand-icon {
  width: 42px;
  height: 42px;
  margin: 0 auto var(--space-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-lg);
  background: var(--color-primary-soft);
  color: var(--color-primary);
}

.brand-title {
  font-family: var(--font-display);
  font-size: 24px;
  font-weight: 700;
  color: var(--color-ink);
  letter-spacing: -0.02em;
  line-height: 1.15;
  margin: 0 0 4px;
  text-wrap: balance;
}

.brand-desc {
  font-size: 12px;
  color: var(--color-ink-muted);
  line-height: 1.5;
  margin: 0 0 var(--space-sm);
  text-wrap: balance;
}

.brand-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 5px;
  align-items: center;
}
.brand-list li {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11.5px;
  color: var(--color-ink-subtle);
  line-height: 1.4;
}
.brand-list li svg {
  flex-shrink: 0;
  color: var(--color-primary);
  opacity: 0.5;
}

/* ── Auth Panel (Right) ── */
.auth-side {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-lg);
  background: var(--color-surface-1);
}

.auth-body {
  width: 100%;
  max-width: 300px;
}

/* Mobile brand (hidden on desktop) */
.auth-mobile-brand {
  display: none;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-bottom: var(--space-md);
}
.auth-mobile-mark {
  width: 26px;
  height: 26px;
  border-radius: var(--radius-xs);
  background: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-on-primary);
}
.auth-mobile-text {
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 600;
  color: var(--color-ink);
  letter-spacing: -0.01em;
}

/* ── Tabs ── */
.auth-tabs {
  display: flex;
  gap: 0;
  margin-bottom: var(--space-md);
  border-bottom: 1px solid var(--color-hairline);
}
.tab-btn {
  flex: 1;
  padding: 8px 12px;
  font-size: var(--text-body);
  font-weight: 500;
  color: var(--color-ink-muted);
  text-align: center;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out-quad);
}
.tab-btn.active {
  color: var(--color-ink);
  border-bottom-color: var(--color-primary);
}
.tab-btn:hover:not(.active) {
  color: var(--color-ink);
}

/* ── Fields ── */
.form-wrap { margin-bottom: var(--space-xs); }
.field { margin-bottom: var(--space-sm); }
.field-label {
  display: block;
  font-size: 11.5px;
  font-weight: 500;
  color: var(--color-ink);
  margin-bottom: 4px;
}
.input-box {
  display: flex;
  align-items: center;
  height: 38px;
  background: var(--color-surface-2);
  border: 1px solid var(--color-hairline);
  border-radius: 10px;
  transition: border-color var(--duration-fast) var(--ease-out-quad),
              box-shadow var(--duration-fast) var(--ease-out-quad);
}
.input-box:focus-within {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-primary-soft);
}
.input-prefix {
  padding-left: 12px;
  font-size: 12px;
  color: var(--color-ink-muted);
  font-weight: 500;
  white-space: nowrap;
}
.input-field {
  flex: 1;
  border: 0;
  background: transparent;
  padding: 0 10px;
  height: 100%;
  font-size: var(--text-body);
  color: var(--color-ink);
  outline: none;
}
.input-field::placeholder { color: var(--color-ink-tertiary); }

.field-row {
  text-align: right;
  margin-top: -6px;
  margin-bottom: var(--space-sm);
}
.toggle-link {
  font-size: 11.5px;
  color: var(--color-ink-subtle);
  cursor: pointer;
  transition: color var(--duration-fast) var(--ease-out-quad);
}
.toggle-link:hover { color: var(--color-primary); }

.code-row { display: flex; gap: 8px; }
.code-input { flex: 1; min-width: 0; }
.btn-code {
  flex-shrink: 0;
  height: 38px;
  padding: 0 12px;
  font-size: 12px;
  font-weight: 500;
  color: var(--color-primary);
  background: var(--color-primary-soft);
  border: 1px solid rgba(125, 154, 110, 0.2);
  border-radius: 10px;
  white-space: nowrap;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out-quad);
}
.btn-code:hover:not(:disabled) {
  background: rgba(125, 154, 110, 0.18);
}
.btn-code:disabled { opacity: 0.4; cursor: not-allowed; }

.form-error {
  text-align: center;
  font-size: 12px;
  color: var(--color-semantic-error);
  margin-bottom: var(--space-xs);
  line-height: 1.4;
}

/* ── Submit ── */
.btn-submit {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  height: 42px;
  background: var(--color-primary);
  color: var(--color-on-primary);
  border: 0;
  border-radius: 10px;
  font-size: var(--text-body);
  font-weight: 600;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out-quad);
}
.btn-submit:hover:not(:disabled) { background: var(--color-primary-hover); }
.btn-submit:active:not(:disabled) { transform: scale(0.98); }
.btn-submit:disabled {
  background: #d4cec5;
  color: #ffffff;
  cursor: not-allowed;
  opacity: 1;
}

.spinner {
  width: 13px; height: 13px;
  border: 2px solid currentColor; border-right-color: transparent;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* ── Foot ── */
.auth-foot { text-align: center; margin-top: var(--space-sm); }
.auth-foot p {
  font-size: 12px;
  color: var(--color-ink-muted);
  margin: 0;
}
.foot-link {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-primary);
  cursor: pointer;
  transition: color var(--duration-fast) var(--ease-out-quad);
}
.foot-link:hover { color: var(--color-primary-hover); }

/* ── Captcha Modal ── */
.modal-overlay {
  position: fixed; inset: 0; z-index: 1000;
  display: flex; align-items: center; justify-content: center;
  background: rgba(44, 40, 37, 0.35);
  backdrop-filter: blur(4px);
}
.modal-card {
  width: 340px;
  background: var(--color-surface-1);
  border: 1px solid var(--color-hairline);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card-hover);
  overflow: hidden;
}
.modal-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: var(--space-md) var(--space-md) var(--space-sm);
  font-size: var(--text-body); font-weight: 600;
  color: var(--color-ink);
}
.modal-close {
  width: 28px; height: 28px;
  display: flex; align-items: center; justify-content: center;
  border-radius: var(--radius-sm);
  color: var(--color-ink-subtle);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out-quad);
}
.modal-close:hover { background: var(--color-surface-2); color: var(--color-ink); }
.modal-body { padding: 0 var(--space-md) var(--space-md); }

.captcha-loading {
  display: flex; align-items: center; justify-content: center;
  gap: 10px; height: 200px;
  color: var(--color-ink-subtle); font-size: var(--text-body-sm);
}
.captcha-error-state {
  display: flex; flex-direction: column; align-items: center;
  gap: 12px; padding: 40px 0;
  color: var(--color-semantic-error); font-size: var(--text-body-sm);
}
.btn-retry {
  height: 32px; padding: 0 14px;
  background: var(--color-primary); color: var(--color-on-primary);
  border: 0; border-radius: var(--radius-sm);
  font-size: var(--text-button); cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out-quad);
}
.btn-retry:hover { background: var(--color-primary-hover); }

.captcha-puzzle { display: flex; flex-direction: column; gap: var(--space-sm); }
.captcha-img-wrap {
  position: relative; width: 100%;
  border-radius: var(--radius-sm); overflow: hidden;
  box-shadow: inset 0 0 0 1px rgba(0,0,0,0.04);
}
.captcha-bg {
  position: relative; width: 100%; height: 100%;
  background-size: 100% 100%; background-position: center;
  background-color: #e8eef3;
  border-radius: var(--radius-sm);
}
.captcha-piece {
  position: absolute; top: 0; height: 100%;
  background-repeat: no-repeat; pointer-events: none; z-index: 2;
}
.captcha-done {
  position: absolute; inset: 0;
  display: flex; align-items: center; justify-content: center;
  background: rgba(107, 160, 123, 0.85); color: #fff; z-index: 5;
}
.captcha-track {
  position: relative; height: 40px;
  background: var(--color-surface-3);
  border: 1px solid var(--color-hairline);
  border-radius: var(--radius-pill);
  cursor: pointer; user-select: none; touch-action: none;
}
.captcha-track-bg {
  position: absolute; inset: 3px;
  border-radius: var(--radius-pill); overflow: hidden;
}
.captcha-track-fill {
  height: 100%; background: var(--color-primary-soft);
  border-radius: var(--radius-pill); transition: none;
}
.captcha-slider-btn {
  position: absolute; top: 2px; left: 0;
  width: 36px; height: 36px;
  display: flex; align-items: center; justify-content: center;
  background: var(--color-surface-1);
  border: 1px solid var(--color-hairline-strong);
  border-radius: 50%; cursor: grab; z-index: 3;
  color: var(--color-ink-muted);
  transform: translateX(-50%);
  transition: box-shadow var(--duration-fast) var(--ease-out-quad);
}
.captcha-slider-btn.dragging {
  cursor: grabbing; border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-primary-soft);
  color: var(--color-primary);
}
.captcha-slider-btn.success {
  background: var(--color-semantic-success);
  border-color: var(--color-semantic-success); color: #fff;
}
.captcha-track-label {
  position: absolute; inset: 0;
  display: flex; align-items: center; justify-content: center;
  font-size: var(--text-body-sm); color: var(--color-ink-tertiary);
  pointer-events: none; transition: opacity var(--duration-fast) var(--ease-out-quad);
}
.captcha-track-label.hide { opacity: 0; }
.captcha-err {
  text-align: center; font-size: var(--text-caption);
  color: var(--color-semantic-error); margin: var(--space-xs) 0 0;
}

.modal-fade-enter-active, .modal-fade-leave-active {
  transition: opacity 200ms var(--ease-out-quad);
}
.modal-fade-enter-from, .modal-fade-leave-to { opacity: 0; }

/* ═══════════════════════════════════════════
   Responsive
   ═══════════════════════════════════════════ */
@media (max-width: 700px) {
  .login-page {
    padding: var(--space-sm);
    align-items: flex-start;
    padding-top: 4vh;
  }
  .frame {
    max-width: 100%;
    border-radius: 16px;
    box-shadow: none;
  }
}

@media (max-width: 600px) {
  .split-wrap {
    flex-direction: column;
    min-height: 0;
  }

  .brand-side {
    width: 100%;
    padding: var(--space-md) var(--space-md) var(--space-sm);
    background:
      radial-gradient(ellipse 80% 60% at 50% 20%, rgba(125, 154, 110, 0.08) 0%, transparent 60%),
      var(--color-surface-2);
  }

  .brand-body {
    max-width: 100%;
  }

  .brand-icon {
    width: 36px;
    height: 36px;
    margin-bottom: 6px;
  }
  .brand-icon svg {
    width: 18px;
    height: 18px;
  }

  .brand-title {
    font-size: 20px;
  }

  .brand-desc {
    font-size: 11.5px;
    margin-bottom: var(--space-xs);
  }

  .brand-list {
    flex-direction: row;
    flex-wrap: wrap;
    justify-content: center;
    gap: 3px 10px;
  }
  .brand-list li {
    font-size: 11px;
  }

  .auth-mobile-brand {
    display: flex;
  }

  .auth-side {
    padding: var(--space-sm) var(--space-md) var(--space-md);
    align-items: flex-start;
  }

  .auth-body {
    max-width: 100%;
  }

  .auth-tabs {
    margin-bottom: var(--space-sm);
  }

  .code-row {
    flex-direction: column;
  }

  .btn-code {
    width: 100%;
  }
}

@media (max-width: 380px) {
  .brand-side {
    padding: var(--space-sm) var(--space-xs);
  }
  .auth-side {
    padding: var(--space-xs) var(--space-sm) var(--space-md);
  }
  .brand-list {
    flex-direction: column;
    align-items: center;
  }
}
</style>
