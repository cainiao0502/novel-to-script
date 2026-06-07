import { gutter, GutterMarker } from '@codemirror/view'

class SceneDotMarker extends GutterMarker {
  constructor(label) {
    super()
    this.label = label
  }
  toDOM() {
    const el = document.createElement('div')
    el.className = 'cm-scene-dot'
    el.title = this.label
    Object.assign(el.style, {
      width: '8px', height: '8px', borderRadius: '50%',
      background: 'var(--color-primary)', margin: '5px 6px',
      cursor: 'pointer', boxSizing: 'border-box'
    })
    return el
  }
}

/**
 * Find scene heading lines in YAML text.
 * Returns a Set of 0-based line numbers.
 */
function findSceneLines(yamlText) {
  const lines = yamlText.split('\n')
  const sceneLines = new Set()
  for (let i = 0; i < lines.length; i++) {
    if (/^\s*- scene_id:\s/.test(lines[i])) {
      sceneLines.add(i)
    }
  }
  return sceneLines
}

/**
 * Create CodeMirror extensions for scene navigation.
 * Returns an array of extensions to pass to EditorState.create().
 */
export function sceneGutterExtensions(yamlText) {
  if (!yamlText) return []

  const sceneLines = findSceneLines(yamlText)
  if (sceneLines.size === 0) return []

  // Build label map: line number -> scene label
  const lines = yamlText.split('\n')
  const labels = {}
  for (const lineNum of sceneLines) {
    let label = ''
    for (let j = lineNum + 1; j < Math.min(lineNum + 10, lines.length); j++) {
      const m = lines[j].trim().match(/location:\s*(.+)/)
      if (m) { label = m[1]; break }
    }
    const idx = [...sceneLines].indexOf(lineNum) + 1
    labels[lineNum] = label || `场景 ${idx}`
  }

  const sceneGutter = gutter({
    class: 'cm-scene-nav',
    markers: () => ({
      forLine(line) {
        if (sceneLines.has(line)) return new SceneDotMarker(labels[line])
        return null
      }
    })
  })

  return [sceneGutter]
}
