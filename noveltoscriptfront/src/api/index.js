import axios from 'axios'

const http = axios.create({
  baseURL: '/api',
  timeout: 180000
})

// Request interceptor: attach auth token
http.interceptors.request.use((config) => {
  const token = localStorage.getItem('auth_token')
  if (token) {
    config.headers.Authorization = token
  }
  return config
})

// Response interceptor: unwrap ApiResult, handle errors + 401
http.interceptors.response.use(
  (r) => {
    const body = r.data
    if (body && typeof body === 'object' && 'success' in body) {
      if (!body.success) {
        return Promise.reject(new Error(body.message || '请求失败'))
      }
      return body
    }
    return r
  },
  (err) => {
    if (err.response?.status === 401) {
      localStorage.removeItem('auth_token')
      localStorage.removeItem('auth_user')
      window.location.href = '/login'
      return Promise.reject(new Error('登录已过期，请重新登录'))
    }
    const data = err.response?.data
    const message = data?.message || data?.error || err.message
    return Promise.reject(new Error(message))
  }
)

export const api = {
  createProject(body) {
    return http.post('/projects', body).then((r) => r.data)
  },
  uploadProject(formData) {
    return http.post('/projects/upload', formData).then((r) => r.data)
  },
  listProjects() {
    return http.get('/projects').then((r) => r.data)
  },
  getProject(id) {
    return http.get(`/projects/${id}`).then((r) => r.data)
  },
  deleteProject(id) {
    return http.delete(`/projects/${id}`)
  },
  generate(id, idempotencyKey) {
    return http.post(`/projects/${id}/generate`, null, {
      headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}
    }).then((r) => r.data)
  },
  regenerateChapter(id, chapterId) {
    return http.post(`/projects/${id}/chapters/${chapterId}/regenerate`).then((r) => r.data)
  },
  scriptYamlUrl(id) {
    return `/api/projects/${id}/script.yaml`
  },
  restoreChapterYaml(projectId, chapterId, yaml) {
    return http.put(`/projects/${projectId}/chapters/${chapterId}/script`, { yaml })
  },
  restoreProjectYaml(projectId, yaml) {
    return http.put(`/projects/${projectId}/script`, { yaml })
  },
  rewriteDialogue(projectId, body) {
    return http.post(`/projects/${projectId}/rewrite-dialogue`, body).then((r) => r.data)
  },
  analyzeEmotions(projectId, refresh = false) {
    const params = refresh ? '?refresh=true' : ''
    return http.post(`/projects/${projectId}/analyze-emotions${params}`).then((r) => r.data)
  },
  analyzeChapterEmotions(projectId, chapterId) {
    return http.post(`/projects/${projectId}/chapters/${chapterId}/analyze-emotions`).then((r) => r.data)
  },
  getEmotions(projectId) {
    return http.get(`/projects/${projectId}/emotions`).then((r) => r.data)
  },

  // Auth
  sendCode(body) {
    return http.post('/user/code/send', body).then((r) => r.data)
  },
  register(body) {
    return http.post('/user/register', body).then((r) => r.data)
  },
  login(body) {
    return http.post('/user/login', body).then((r) => r.data)
  },
  logout() {
    return http.post('/user/logout').then((r) => r.data)
  },
  getCaptcha() {
    return http.get('/captcha/get').then((r) => r.data)
  },
  checkCaptcha(body) {
    return http.post('/captcha/check', body).then((r) => r.data)
  }
}
