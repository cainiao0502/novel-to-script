import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { api } from '@/api'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('auth_token') || '')
  const user = ref(JSON.parse(localStorage.getItem('auth_user') || 'null'))

  const isLoggedIn = computed(() => !!token.value)
  const nickname = computed(() => user.value?.nickname || '')

  function persist(t, u) {
    token.value = t
    user.value = u
    localStorage.setItem('auth_token', t)
    localStorage.setItem('auth_user', JSON.stringify(u))
  }

  async function login(body) {
    const res = await api.login(body)
    persist(res.token, res.userInfo)
    return res
  }

  async function register(body) {
    await api.register(body)
    // Auto-login after successful registration
    const res = await api.login({
      mobile: body.mobile,
      password: body.password,
      type: 1
    })
    persist(res.token, res.userInfo)
    return res
  }

  async function logout() {
    try {
      await api.logout()
    } catch {
      // ignore
    }
    token.value = ''
    user.value = null
    localStorage.removeItem('auth_token')
    localStorage.removeItem('auth_user')
  }

  return { token, user, isLoggedIn, nickname, login, register, logout }
})
