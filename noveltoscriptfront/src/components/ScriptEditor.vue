<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { EditorView, basicSetup } from 'codemirror'
import { EditorState } from '@codemirror/state'
import { yaml } from '@codemirror/lang-yaml'
import { oneDark } from '@codemirror/theme-one-dark'

const props = defineProps({
  yaml: { type: String, default: '' },
  readOnly: { type: Boolean, default: false }
})

const emit = defineEmits(['change'])

const host = ref(null)
let view = null

function build(content) {
  if (!host.value) return
  if (view) { view.destroy(); view = null }
  const state = EditorState.create({
    doc: content || '',
    extensions: [
      basicSetup,
      yaml(),
      oneDark,
      EditorView.editable.of(!props.readOnly),
      EditorState.readOnly.of(props.readOnly),
      EditorView.theme({
        '&': { height: '100%', fontSize: '13px' },
        '.cm-scroller': { fontFamily: 'var(--font-mono)' }
      }),
      EditorView.updateListener.of((u) => {
        if (u.docChanged) emit('change', u.state.doc.toString())
      }),
    ]
  })
  view = new EditorView({ state, parent: host.value })
}

onMounted(() => build(props.yaml))
onBeforeUnmount(() => { if (view) view.destroy() })

watch(() => props.yaml, (v) => {
  if (!view) return
  const current = view.state.doc.toString()
  if (current !== (v || '')) {
    view.dispatch({
      changes: { from: 0, to: current.length, insert: v || '' }
    })
  }
})
</script>

<template>
  <div ref="host" class="editor-host" />
</template>

<style scoped>
.editor-host {
  flex: 1;
  min-height: 500px;
  height: 100%;
  overflow: auto;
  background: var(--color-canvas);
}
.editor-host :deep(.cm-editor) {
  height: 100%;
  background: var(--color-canvas);
}
.editor-host :deep(.cm-editor.cm-focused) {
  outline: none;
}
.editor-host :deep(.cm-gutters) {
  background: var(--color-canvas);
  border-right: 1px solid var(--color-hairline);
  color: var(--color-ink-subtle);
}
.editor-host :deep(.cm-content) {
  padding: 12px 0;
  caret-color: var(--color-primary);
}
.editor-host :deep(.cm-cursor) {
  border-left-color: var(--color-primary);
}
.editor-host :deep(.cm-selectionBackground) {
  background: rgba(94, 106, 210, 0.25) !important;
}
</style>
