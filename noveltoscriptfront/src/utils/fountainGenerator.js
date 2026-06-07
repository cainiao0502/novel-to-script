import { parse as parseYaml } from 'yaml'

const timeLabel = (t) => ({
  DAWN: '黎明', MORNING: '上午', NOON: '正午',
  AFTERNOON: '下午', DUSK: '黄昏', EVENING: '傍晚',
  NIGHT: '夜', MIDNIGHT: '午夜'
})[t] || t

const intExtLabel = (v) => v === 'INT' ? '内景' : v === 'EXT' ? '外景' : v || ''

/**
 * Convert YAML script string to Fountain plain-text format.
 */
export function yamlToFountain(yamlString) {
  if (!yamlString) return ''
  const doc = parseYaml(yamlString)

  // Build character name map
  const charNames = {}
  if (doc.characters) {
    for (const c of doc.characters) {
      charNames[c.id] = c.name || c.id
    }
  }
  const resolve = (id) => charNames[id] || id

  const lines = []

  // ── Fountain header ──
  if (doc.meta?.title) lines.push(`Title: ${doc.meta.title}`)
  if (doc.meta?.source_novel) lines.push(`Source: ${doc.meta.source_novel}`)
  if (lines.length > 0) lines.push('')

  // ── Scene body ──
  for (const scene of doc.scenes || []) {
    // Scene heading
    const heading = [
      intExtLabel(scene.int_ext) + '.',
      scene.location || '未标注地点',
      scene.time_of_day ? `- ${timeLabel(scene.time_of_day)}` : ''
    ].filter(Boolean).join(' ')
    lines.push(`.${heading}`)
    lines.push('')

    // Summary as action
    if (scene.summary) {
      lines.push(scene.summary)
      lines.push('')
    }

    // Actions
    for (const action of scene.actions || []) {
      lines.push(action)
      lines.push('')
    }

    // Dialogues
    for (const d of scene.dialogues || []) {
      lines.push(resolve(d.character).toUpperCase())
      if (d.parenthetical) {
        lines.push(`（${d.parenthetical}）`)
      }
      lines.push(d.line)
      lines.push('')
    }

    // Voiceover
    for (const v of scene.voiceover || []) {
      lines.push(`${resolve(v.character).toUpperCase()} (V.O.)`)
      lines.push(v.line)
      lines.push('')
    }

    // SFX
    if (scene.sfx) {
      lines.push(`> ${scene.sfx}`)
      lines.push('')
    }

    // Camera hint as transition
    if (scene.camera_hint) {
      lines.push(`> ${scene.camera_hint}`)
      lines.push('')
    }
  }

  return lines.join('\n').replace(/\n{3,}/g, '\n\n')
}
