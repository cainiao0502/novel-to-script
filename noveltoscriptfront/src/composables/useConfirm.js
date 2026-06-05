import { reactive } from 'vue'

const state = reactive({
  open: false,
  title: '',
  message: '',
  confirmText: '确定',
  cancelText: '取消',
  variant: 'default',
  resolver: null
})

export function useConfirm() {
  function confirm(options = {}) {
    return new Promise((resolve) => {
      if (state.resolver) state.resolver(false)
      state.title = options.title || '请确认操作'
      state.message = options.message || ''
      state.confirmText = options.confirmText || '确定'
      state.cancelText = options.cancelText || '取消'
      state.variant = options.variant || 'default'
      state.resolver = resolve
      state.open = true
    })
  }

  function close(result) {
    if (state.resolver) {
      state.resolver(result)
      state.resolver = null
    }
    state.open = false
  }

  return { state, confirm, close }
}
