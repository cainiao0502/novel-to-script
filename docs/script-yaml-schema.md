# 剧本 YAML Schema 定义与设计依据

> 版本：**1.0.0** · 适用剧本类型：**影视 / 短剧 / 网剧**
> 适用项目：`AI 小说转剧本工具`（Novel-to-Script）
> 文档目的：定义 AI 生成剧本的结构化格式，并说明每项设计的取舍，供开发者与算法同学参考。

---

## 目录

1. [概述](#1-概述)
2. [设计原则](#2-设计原则)
3. [完整 YAML 示例](#3-完整-yaml-示例)
4. [字段字典](#4-字段字典)
5. [JSON Schema（机读校验）](#5-json-schema机读校验)
6. [设计决策详解](#6-设计决策详解)
7. [校验规则与约束](#7-校验规则与约束)
8. [扩展示例：多场景单章](#8-扩展示例多场景单章)
9. [版本与兼容性策略](#9-版本与兼容性策略)
10. [给 LLM 的输出契约模板](#10-给-llm-的输出契约模板)

---

## 1. 概述

本 Schema 用于描述由"小说文本"经 AI 转换而来的**结构化剧本**。一个完整的剧本 YAML 文件描述：

- **元信息**（剧名、集号、类型等）
- **人物表**（登场角色、关系标签、外貌/声音）
- **场景序列**（按章节组织，每场含地点/时间/动作/对白/旁白等）
- **备注**（AI 自留的改编说明、疑点、建议）

Schema **不**包含：
- 镜头切换的精确时长（留给真人导演/剪辑）
- 演员表、班表、预算、场务安排（属于制片系统）
- 已拍成片的元数据（片号、设备、签收单）

> **设计取舍**：我们生成"可读、可二次打磨"的剧本初稿，而不是"可直送机房"的分镜表。AI 给结构与文本，人类定执行。

---

## 2. 设计原则

| 原则 | 含义 |
|---|---|
| **可读性优先** | 选 YAML 而非 JSON；中文字段值不必引号包裹；全角标点；作者可裸眼读改 |
| **稳定标识符** | 人物、场景都使用**稳定 ID**（与具体姓名解耦），跨章引用不因别名错位 |
| **场景为最小单位** | 一章可含多场景（多地点/多时间），剧本行当以"场"为单位 |
| **动作 / 对白 / 旁白分离** | 排版、表演、剪辑语义不同，强合并会丢失信息 |
| **可选项留白** | `camera_hint`、`music_cue` 等导演向字段留空允许，不强约束 |
| **可机读校验** | 配套 JSON Schema 强制 `additionalProperties: false`，拒绝 LLM 幻觉字段 |
| **可流式构造** | LLM 逐章生成片段即可，片段是合法 Schema 文档 |

---

## 3. 完整 YAML 示例

```yaml
version: "1.0"

meta:
  title: "庆余年·京都风云"
  source_novel: "猫腻《庆余年》"
  episode: 1
  genre: "短剧/古装/权谋"
  logline: "一个身世成谜的少年，在京都的朝堂与江湖中揭开自己与天下的秘密。"
  total_scenes: 12
  generated_at: "2026-06-05T10:00:00Z"

characters:
  - id: "c_fan_xian"
    name: "范闲"
    aliases: ["范公子", "闲哥"]
    gender: "男"
    age_range: "20-25"
    role: "protagonist"
    appearance: "身形修长，眉目清朗，常着素色长衫。"
    voice: "低沉有力，语调从容"

  - id: "c_lin_wanrong"
    name: "林婉蓉"
    aliases: ["婉蓉妹妹"]
    gender: "女"
    age_range: "18-22"
    role: "supporting"
    appearance: "鹅黄襦裙，长发如瀑。"
    voice: "清亮柔婉"

  - id: "c_yan_xiaoyi"
    name: "严晓一"
    aliases: ["严公子"]
    gender: "男"
    age_range: "30-35"
    role: "antagonist"
    appearance: "官服严整，笑容不达眼底。"
    voice: "阴柔克制"

  - id: "narrator"
    name: "旁白"
    role: "npc"
    appearance: ""
    voice: "沉厚客观"

scenes:
  - scene_id: "s_001"
    chapter: 1
    order: 1
    int_ext: "INT"
    location: "范府·书房"
    time_of_day: "DAY"
    characters: ["c_fan_xian", "c_lin_wanrong"]
    summary: "范闲与林婉蓉书房议事，初露政见不合"
    actions:
      - "暖黄色的光从雕花窗棂斜入，洒在书案上的宣纸。"
      - "范闲推门而入，步履带风，目光扫过室内。"
      - "林婉蓉起身行礼，眉间微蹙。"
    dialogues:
      - character: "c_fan_xian"
        parenthetical: "（坐下，斟茶）"
        line: "今夜的月亮，倒比范府后院那口井的月亮圆些。"
        emotion: "从容"
      - character: "c_lin_wanrong"
        parenthetical: "（低声）"
        line: "公子慎言。昨夜的事，已传到陈萍萍耳中。"
        emotion: "忧虑"
    voiceover:
      - character: "narrator"
        line: "京都的暗流，从这一盏茶开始涌动。"
    props: ["茶盏", "宣纸", "墨锭"]
    sfx: ["推门声", "茶汤注入声", "远处更鼓"]
    music_cue: "古琴·淡入"
    camera_hint: "中景 → 特写茶杯"

  - scene_id: "s_002"
    chapter: 1
    order: 2
    int_ext: "EXT"
    location: "京都·朱雀大街"
    time_of_day: "DUSK"
    characters: ["c_fan_xian", "c_yan_xiaoyi"]
    summary: "范闲与严晓一街头偶遇，言语交锋"
    actions:
      - "夕阳将朱雀大街的琉璃瓦染成橙红。"
      - "两人驻足于一棵老槐树下，街市喧嚣渐远。"
    dialogues:
      - character: "c_yan_xiaoyi"
        parenthetical: "（抱拳，笑容不达眼底）"
        line: "范公子别来无恙。"
        emotion: "客套"
      - character: "c_fan_xian"
        line: "严公子今日却是有恙——眼底青黑，夜里少睡。"
        emotion: "戏谑"
    voiceover: []
    props: ["折扇"]
    sfx: ["市井喧闹", "马蹄声"]
    music_cue: ""
    camera_hint: "双人同框，景深变化"

notes:
  - "本章为开篇定调场景，建议保留两场对白节奏差。"
  - "严晓一首次出场，台词量克制，避免抢戏。"
```

---

## 4. 字段字典

### 4.1 顶层字段

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `version` | string (semver) | 是 | Schema 版本，目前固定 `"1.0"` |
| `meta` | object | 是 | 剧本元信息 |
| `characters` | array\<Character\> | 是 | 人物表，**至少 1 项**（包含 `narrator`） |
| `scenes` | array\<Scene\> | 是 | 场景序列，**至少 1 项** |
| `notes` | array\<string\> | 否 | AI 自留的改编说明、疑点、建议 |

### 4.2 `meta`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `title` | string | 是 | 剧名 |
| `source_novel` | string | 否 | 原著出处（书名/作者） |
| `episode` | integer ≥ 0 | 否 | 集号；短剧单集可省略 |
| `genre` | string | 否 | 题材标签，自由文本，例：`"短剧/古装/权谋"` |
| `logline` | string | 否 | 一句话故事梗概，便于审稿 |
| `total_scenes` | integer ≥ 0 | 否 | AI 自报的总场景数（用于前端显示） |
| `generated_at` | string (ISO 8601) | 否 | 生成时间戳，例：`"2026-06-05T10:00:00Z"` |

### 4.3 `Character`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | string (^[a-z][a-z0-9_]{1,63}$) | 是 | 稳定 ID，跨章引用都用这个；推荐 `c_<拼音/英文>` |
| `name` | string (1-32 字) | 是 | 角色主名 |
| `aliases` | array\<string\> | 否 | 别名/别称；原作同人多名时使用 |
| `gender` | string (`男/女/其他`) | 否 | 性别 |
| `age_range` | string | 否 | 年龄段自由描述，例：`"20-25"`、`"少年"` |
| `role` | enum | 是 | `protagonist` / `antagonist` / `supporting` / `npc` |
| `appearance` | string | 否 | 外貌描写（1-200 字） |
| `voice` | string | 否 | 声音特点（1-100 字） |

### 4.4 `Scene`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `scene_id` | string (^[a-z][a-z0-9_]{1,63}$) | 是 | 场景稳定 ID，**全剧唯一**，推荐 `s_001`、`s_002`... |
| `chapter` | integer ≥ 1 | 是 | 来自原小说第几章 |
| `order` | integer ≥ 1 | 是 | 本章内的场景顺序（1 起递增） |
| `int_ext` | enum | 是 | `INT`（内景）/ `EXT`（外景）/ `INT-EXT`（内外景切换） |
| `location` | string (1-64 字) | 是 | 场景地点，例：`"范府·书房"` |
| `time_of_day` | enum | 是 | `DAY` / `NIGHT` / `DAWN` / `DUSK` / `CONTINUOUS`（连续） |
| `characters` | array\<string\> | 是 | 出场人物 ID 列表；**每项必须出现在 `characters` 表中** |
| `summary` | string (1-200 字) | 是 | 一句话场景概述（用于场景卡片） |
| `actions` | array\<string\> | 是 | 动作/环境描写；**可空数组**；每条 1-200 字 |
| `dialogues` | array\<Dialogue\> | 是 | 对白；**可空数组**（纯动作戏可无对白） |
| `voiceover` | array\<Voiceover\> | 否 | 画外音/旁白；可空数组 |
| `props` | array\<string\> | 否 | 道具清单 |
| `sfx` | array\<string\> | 否 | 音效提示 |
| `music_cue` | string | 否 | 配乐提示，例：`"古琴·淡入"` |
| `camera_hint` | string | 否 | 镜头提示，例：`"中景 → 特写茶杯"` |

### 4.5 `Dialogue`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `character` | string | 是 | 说话人 ID，必须等于某 `Character.id` |
| `parenthetical` | string | 否 | 括号提示（动作/语调），**使用全角括号**：`（……）` |
| `line` | string (1-300 字) | 是 | 台词 |
| `emotion` | string | 否 | 情绪标签，例：`"愤怒"`,`"从容"`,`"压抑"` |

### 4.6 `Voiceover`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `character` | string | 是 | 说话人 ID；通常为 `narrator`，亦可为角色内心独白 |
| `line` | string (1-500 字) | 是 | 旁白/画外音内容 |

---

## 5. JSON Schema（机读校验）

完整 JSON Schema 存于 [`docs/json-schema/script-v1.0.schema.json`](json-schema/script-v1.0.schema.json)。核心特征：

- 根 `$schema: "https://json-schema.org/draft/2020-12/schema"`
- **每个对象都设置 `additionalProperties: false`**，拒绝 LLM 幻觉字段
- 枚举字段使用 `enum` 限定取值范围
- 字符串字段使用 `pattern` 约束格式（ID、括号、时间戳）
- 跨字段引用（`scene.characters` 引用 `character.id`）使用 `if/then` 强制
- 后端使用 `com.networknt:json-schema-validator` 加载并校验

> **设计原因**：LLM 倾向于"自由发挥"，多加字段是常态。`additionalProperties: false` 是最便宜也最有效的护栏。

---

## 6. 设计决策详解

### 6.1 为什么选 YAML 而非 JSON

- **作者可读**：YAML 支持注释、宽松引号、长字符串不转义；中文作者能直接用编辑器打开修改
- **可写性**：作者补一个对白不需要引号包裹整行
- **可机读**：社区库成熟（Jackson YAML、PyYAML），可与 JSON 互转
- **可校验**：JSON Schema 不直接支持 YAML，但先 `yaml.load()` → 内存对象，再走 JSON Schema 校验即可

**代价**：解析比 JSON 慢一个数量级；引号/缩进对小白不友好。 → **缓解**：在前端 CodeMirror 6 上做语法高亮与格式校验。

### 6.2 为什么用稳定 ID 引用人物

问题：原作里同一人有"范闲 / 小范大人 / 闲哥"三种称呼；LLM 还可能写错字（"范弦"）。若以 `name` 为引用键，整本剧本的引用关系会断。

**做法**：每个角色分配一个**机器稳定**的 ID（`c_fan_xian`），人名/别名都进 `aliases`。所有跨字段引用（`scene.characters`、`dialogue.character`、`voiceover.character`）都用 ID。

**好处**：
- 改名不改引用
- 错字也可在 `characters` 表里订正
- 检索"某角色所有场次"变成 `O(1)` 哈希查找
- 后续可派生人物关系图、共事网络

**代价**：作者新增人物时得想个 ID。 → **缓解**：前端表单自动建议 ID（`c_` + 拼音首字母）。

### 6.3 为什么场景是最小单位，而非章节

小说一章常包含：
- 多个地点（范府 → 街市 → 宫中）
- 多次时间跳转（白天 → 夜）
- 多个视角人物

**影视剧本以"场"为单位**（一场戏 = 一组不切镜的连续表演）。把场景作为最小单位才匹配实际拍摄节奏。

**好处**：
- 一章可能拆出 3-5 场剧本，更细粒度
- 单场失败可独立重生成
- 前端可按"场"导航、按"场"统计台词量

**代价**：单章生成的 scene 数量需要 LLM 自决。 → **缓解**：在 prompt 中给出"1 章拆 2-5 场"的明确建议，并在 `meta.total_scenes` 自报。

### 6.4 为什么动作 / 对白 / 旁白分离

- **动作（actions）**：环境、人物行为、镜头可读的描述。**第三人称客观**。
- **对白（dialogues）**：被听见/看见的台词，含说话人、括号提示、情绪。
- **旁白（voiceover）**：不被角色表演、观众听到的画外音/内心独白。

三者的**表演语义**不同：
- 演员拿到的剧本只关心 dialogue
- 导演关心 action 与 camera_hint
- 剪辑师关心 voiceover 与 music_cue

合并到一个数组里会丢失这一层结构。

**旁白为什么独立成数组而非塞进 dialogues**：旁白通常不参与演员走位，且常常是后期录制或预录。独立成字段便于后期处理与字幕时间轴对齐。

### 6.5 为什么 parenthetical 用全角括号

中文剧本排版传统使用全角 `（……）`，与正文 `，。、；` 自然衔接。半角 `(...)` 在中文语境下会显得突兀，且与"西式剧本范本"混淆。

**约定**：所有 parenthetical 字面量必须用全角括号；后端在 `ScriptValidator` 里用正则 `^[（][^）]{0,30}[）]$` 校验。

### 6.6 为什么 camera_hint / music_cue 是 hint 不是 spec

AI 对"什么时候用 30 度仰拍、配什么 BGM"几乎没有判断力，强行输出反而会显得武断、误导新人作者。

**做法**：留作 hint 字段，**允许为空**。AI 只在"该场明显需要"的少数情况下给出提示。

**好处**：
- 不污染剧本的"事实"层（动作/对白）
- 给真人导演留足空间
- 缺失不报错，便于快速出稿

### 6.7 为什么增加 `notes` 数组

AI 在改编过程中会产生"上下文有用但不属于剧本本身"的信息：
- "本章疑似双视角，建议改单视角"
- "此段对白与第三章重复，建议删"
- "范闲 OOC 嫌疑，请原作者确认"

把这些放 `notes`，作者可在审稿时一眼看到，**但不污染剧本正文**。前端用侧栏展示，不直接渲染到分镜卡片。

### 6.8 字段命名：snake_case vs camelCase

**采用 `snake_case`**。原因：
- YAML 字段名常出现在中文/英文混合的对话中，作者口头"scene id"更自然
- 与数据库列命名一致（MySQL 习惯）
- 跨语言无歧义（JS、Java、Python 都能直接当 key 用）

**权衡**：与 Java 驼峰式 POJO 字段不一致。 → **缓解**：后端 POJO 用 Lombok `@JsonProperty` / Jackson `@JsonNaming` 双向映射。

### 6.9 校验为什么不嵌进 YAML（用 YAML 1.2 标签）

理论上 YAML 1.2 允许用 `!!str` `!!int` `!!enum[protagonist,antagonist]` 之类标签做类型标注，但：

- 实际 LLM 输出里几乎不会带标签
- 解析器对中文场景下的标签支持参差
- 校验逻辑仍要写一套，重复

**结论**：用 JSON Schema 校验更稳、更通用。

### 6.10 为什么 totals_scenes、generated_at 由 AI 自报

- `total_scenes`：LLM 比硬编码更清楚自己拆了多少场
- `generated_at`：作为审计字段，便于多版本对比

**注意**：这两个字段都标记为可选，LLM 漏写不阻断流程（前端可自行计算回填）。

---

## 7. 校验规则与约束

### 7.1 引用完整性

| 引用 | 必须存在于 |
|---|---|
| `scene.characters[*]` | `characters[*].id` |
| `dialogue.character` | `characters[*].id` |
| `voiceover.character` | `characters[*].id` |

后端校验时：先用 JSON Schema 校验**类型/枚举/格式**；再跑一轮**业务校验**（跨字段引用）。两轮都过才视为合法。

### 7.2 ID 唯一性

- `characters[*].id` 整个剧本内**唯一**
- `scenes[*].scene_id` 整个剧本内**唯一**
- 不强制 `chapter.order` 严格递增（LLM 可能漏号），但建议严格递增

### 7.3 文本长度上限（建议）

| 字段 | 软上限 | 触发动作 |
|---|---|---|
| `scene.summary` | 200 字 | 超长告警但不阻断 |
| `dialogue.line` | 300 字 | 单条对白过长，告警并提示切分 |
| `voiceover.line` | 500 字 | 告警 |
| `actions[*]` | 200 字 | 告警 |
| `Character.appearance` | 200 字 | 告警 |

### 7.4 必含 narrator

人物表必须包含 `id: "narrator"` 的旁白角色。若 LLM 漏写，后端自动补一个：

```yaml
- id: "narrator"
  name: "旁白"
  role: "npc"
```

---

## 8. 扩展示例：多场景单章

下面是一章内含 3 场剧本的最小合法例子：

```yaml
version: "1.0"
meta:
  title: "示例"
  total_scenes: 3
characters:
  - id: "c_a"
    name: "甲"
    role: "protagonist"
  - id: "c_b"
    name: "乙"
    role: "antagonist"
  - id: "narrator"
    name: "旁白"
    role: "npc"
scenes:
  - scene_id: "s_001"
    chapter: 1
    order: 1
    int_ext: "INT"
    location: "茶楼"
    time_of_day: "DAY"
    characters: ["c_a"]
    summary: "甲独坐"
    actions: ["甲端茶不语"]
    dialogues: []
  - scene_id: "s_002"
    chapter: 1
    order: 2
    int_ext: "INT"
    location: "茶楼"
    time_of_day: "DAY"
    characters: ["c_a", "c_b"]
    summary: "乙至，二人对话"
    actions: ["乙推门而入"]
    dialogues:
      - character: "c_b"
        line: "你来了。"
      - character: "c_a"
        parenthetical: "（抬眼）"
        line: "我等你很久。"
        emotion: "克制"
  - scene_id: "s_003"
    chapter: 1
    order: 3
    int_ext: "EXT"
    location: "茶楼外·街市"
    time_of_day: "DUSK"
    characters: ["c_a", "c_b"]
    summary: "二人离去"
    actions: ["夕阳将影子拉得很长"]
    dialogues: []
    voiceover:
      - character: "narrator"
        line: "故事，才刚开始。"
```

---

## 9. 版本与兼容性策略

| 版本 | 兼容性 | 校验器处理 |
|---|---|---|
| `1.0.x` | 向后兼容 | 1.0.0 生成的剧本，1.0.5 解析器必须能读 |
| `1.1.0` | 增加**可选**字段 | 旧剧本可读；新字段允许缺失 |
| `2.0.0` | 重大重构 | 旧剧本需要 migration 工具升级 |

**约定**：
- `version` 字段是必填的，缺则视为非法
- 解析器只解析**自己声明支持的 major 版本**
- 不向前兼容 major 不一致的输入（直接拒绝并提示用户用对应版本工具重新生成）

---

## 10. 给 LLM 的输出契约模板

后端 `PromptBuilder` 会把以下契约嵌入 system prompt，确保 LLM 输出合法 YAML：

```text
你是一名资深影视剧本改编。请阅读下方【小说章节正文】，
按【剧本 YAML Schema v1.0】输出**该章节的剧本片段**。

严格约束：
1. 只输出合法 YAML，不要包裹 ```yaml 等代码块标记
2. 顶层字段：version / meta / characters / scenes / notes
3. characters 数组内必须包含 id="narrator" 的人物
4. scene.characters 数组内每一项必须是已声明的 Character.id
5. 不要自创未在 Schema 定义的字段
6. actions / dialogues 数组可为空 []，但**必须存在**（不能省略）
7. parenthetical 必须用全角括号（……）
8. time_of_day 只能取 DAY/NIGHT/DAWN/DUSK/CONTINUOUS
9. int_ext 只能取 INT/EXT/INT-EXT
10. role 只能取 protagonist/antagonist/supporting/npc
11. 字段名严格使用 snake_case，不要用 camelCase 或中文
12. 输出末尾不要再追加说明文字

# 小说章节正文
{chapter_text}

# 当前已知人物（来自前文，供引用）
{known_characters_yaml}
```

后端拿到 LLM 输出后，会用 `snakeyaml` 解析 → `ScriptValidator` 校验 → 不通过则一次性自动重试（带上错误明细），仍失败则标记章节为 FAILED 并保留原文供前端展示。

---

## 附录 A：JSON Schema 文件索引

- [`script-v1.0.schema.json`](json-schema/script-v1.0.schema.json) — 完整机器可读校验规则
- `snakeyaml-engine` 解析后产出 Java 对象 / Python dict / JS object
- 推荐校验库：Java `com.networknt:json-schema-validator` / Python `jsonschema` / Node `ajv`

## 附录 B：参考的行业惯例

- **Fountain**（影视剧本纯文本格式）的 dialogue 三件套（character / parenthetical / line）
- **Final Draft FDX**（XML 格式）的 scene-heading（INT/EXT + location + time of day）
- **好莱坞剧本节拍**（Save the Cat 节拍）—— Schema 不强制节拍，AI 在 `notes` 中自由标注
