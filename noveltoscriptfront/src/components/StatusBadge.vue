<script setup>
const props = defineProps({
  status: { type: String, required: true }
})

const LABEL = {
  DRAFT: '草稿',
  PENDING: '待处理',
  GENERATING: '生成中',
  PARTIAL_SUCCESS: '部分成功',
  COMPLETED: '已完成',
  FAILED: '失败',
  DONE: '已生成'
}

const TONE = {
  DRAFT: '',
  PENDING: 'running',
  GENERATING: 'running',
  PARTIAL_SUCCESS: 'warning',
  COMPLETED: 'success',
  FAILED: 'error',
  DONE: 'success'
}
</script>

<template>
  <span class="badge" :class="TONE[status] || ''">{{ LABEL[status] || status }}</span>
</template>
