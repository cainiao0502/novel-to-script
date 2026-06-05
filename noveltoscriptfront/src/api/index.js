import axios from 'axios'

const http = axios.create({
  baseURL: '/api',
  timeout: 180000
})

http.interceptors.response.use(
  (r) => r,
  (err) => {
    const data = err.response?.data
    const message = data?.message || err.message
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
  }
}
