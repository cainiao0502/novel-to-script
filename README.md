# 小说转剧本工具

> 期末作业 · AI 辅助剧本创作
> 把中文小说一键转成结构化 YAML 剧本（影视/短剧/网剧）

📺 **在线演示**：[https://b23.tv/mrVRUuo](https://b23.tv/mrVRUuo) · [http://xhslink.com/o/5W6OXAyw4hD](http://xhslink.com/o/5W6OXAyw4hD)

> 注：演示视频声音没录上，画面操作完整。

基于 LLM 的小说结构化拆解与剧本生成工具。支持粘贴文本或上传文件（.txt/.docx/.pdf），自动识别章节、逐章调用 AI 生成带场景/对白/旁白/镜头语言的标准化剧本，提供在线编辑、情感曲线分析、AI 改稿、多种格式导出等功能。

---

## 目录

- [技术栈](#技术栈)
- [功能总览](#功能总览)
- [快速启动](#快速启动)
- [使用流程](#使用流程)
- [功能详解](#功能详解)
  - [用户认证](#用户认证)
  - [项目创建](#项目创建)
  - [剧本生成](#剧本生成)
  - [工作台](#工作台)
  - [AI 改稿](#ai-改稿)
  - [情感曲线分析](#情感曲线分析)
  - [剧本导出](#剧本导出)
- [限流体系](#限流体系)
- [架构设计](#架构设计)
- [API 一览](#api-一览)
- [项目结构](#项目结构)
- [设计亮点](#设计亮点)
- [已知限制](#已知限制)

---

## 技术栈

| 层 | 选型 |
|---|---|
| 前端框架 | Vue 3 (Composition API) + Vite 8 |
| 路由/状态 | Vue Router 4 + Pinia 3 |
| 编辑器 | CodeMirror 6（YAML 语法高亮 + 场景导航） |
| 图表 | Chart.js + vue-chartjs（情感曲线） |
| 动画 | GSAP 3.15（入场、列表 stagger、进度条、导出反馈） |
| 后端框架 | Spring Boot 3.4 + Java 21 |
| ORM | MyBatis-Plus 3.5 |
| 数据库 | MySQL 8 |
| 缓存/限流 | Redis 7（String / HASH / SortedSet） |
| LLM 客户端 | WebClient（非阻塞 HTTP），OpenAI 兼容 chat/completions |
| 鉴权 | Sa-Token 1.42（Token-based，Redis 持久化会话） |
| 行为验证码 | Tianai Captcha（滑块/点选） |
| 文档导入 | Apache POI（.docx）+ MinerU API（.pdf） |
| YAML 校验 | JSON Schema (Draft 2020-12) via networknt/json-schema-validator |
| PDF 导出 | html2pdf.js（html2canvas + jsPDF） |

---

## 功能总览

```
用户认证 ──→ 登录/注册 ──→ 滑块验证码 ──→ 短信验证码
                    │
项目创建 ──→ 粘贴文本 / 上传文件(.txt/.docx/.pdf) / 导入 YAML
                    │
章节解析 ──→ 自动识别「第N章」标 ──→ 章节预览
                    │
剧本生成 ──→ 逐章调用 LLM ──→ JSON Schema 校验 ──→ 失败自动重试
                    │
   ┌────────────────┼──────────────────┐
   ▼                ▼                  ▼
 剧本视图         YAML 编辑         人物面板
 场景卡片          语法高亮          角色列表
 角色高亮          场景导航          着色头像
 AI 改稿           复制/下载         角色统计
   │                │                  │
   └────────────────┼──────────────────┘
                    ▼
          情感曲线分析 ──→ 全剧/单章情感走向
                    │
              ┌─────┴─────┐
              ▼           ▼
       导出 Fountain   导出 PDF
       (.fountain)     (直接下载)
```

---

## 快速启动

### 0. 准备

- JDK 21
- Node.js ≥ 20
- MySQL 8（默认 `localhost:3306`，库 `novel_to_script` 自动建）
- Redis 7+（默认 `localhost:6379`）
- 一个 OpenAI 兼容的 API Key

### 1. 配置

数据库和 Redis 已写死在 `novel to script backend/src/main/resources/application.properties` 里，默认连 `localhost`，如需改实例直接编辑该文件。

`.env` 只放需要你自己填的 LLM 配置（**`.env` 已在 `.gitignore` 里，不会被提交**）：

```bash
# Windows 推荐用 IntelliJ IDEA 的 EnvFile 插件，或在启动前手动 set：
#   set APP_LLM_API_KEY=sk-xxxxxxxx
#   set APP_LLM_BASE_URL=https://api.openai.com/v1
#   set APP_LLM_MODEL=gpt-4o-mini
notepad .env
```

`.env` 字段说明：

```
APP_LLM_BASE_URL=https://api.openai.com/v1   # 官方/代理/中转 都行
APP_LLM_API_KEY=sk-REPLACE_ME                # ← 必填
APP_LLM_MODEL=gpt-4o-mini                    # 默认模型
```

### 2. 启动后端

```bash
cd "novel to script backend"
mvn spring-boot:run
# 默认监听 http://localhost:8080
```

### 3. 启动前端

```bash
cd noveltoscriptfront
npm install
npm run dev
# 打开 http://localhost:5173
```

> 前端通过 Vite proxy 把 `/api/*` 转发到后端 `http://localhost:8080`，无需额外 CORS 配置。

---

## 使用流程

1. **注册/登录** → 手机号 + 短信验证码，首次注册自动登录
2. **创建项目** → 粘贴小说正文、上传 `.txt` / `.docx` / `.pdf`，或导入已有 YAML
3. **预览章节** → 自动识别章标并拆章，确认章节无误
4. **生成剧本** → 点击"开始生成"，逐章调用 LLM，自动校验 YAML 结构
5. **工作台打磨** → 切换 剧本/YAML/情感曲线/导出预览 四个视图
6. **AI 改稿** → 选中对白，选择风格（精简/扩写/润色/正式/口语），一键改写
7. **导出** → 下载 `.fountain` 标准剧本格式或直接生成 PDF

---

## 功能详解

### 用户认证

- **登录注册一体化**：手机号 + 短信验证码，输入手机号 → 获取验证码 → 登录/自动注册
- **滑块验证码**：登录/注册前需通过 Tianai Captcha 滑块验证，防止机器刷接口
- **Token 鉴权**：基于 Sa-Token，登录后返回 token，前端 localStorage 持久化，Redis 管理会话
- **自动过期**：30 天无活动 token 自动过期；检测到 401 自动跳转登录页
- **限流**：验证码发送 60 秒内不可重复发送；登录接口单 IP 每分钟最多 5 次（见限流体系）

### 项目创建

支持三种创建方式：

| 方式 | 路径 | 说明 |
|---|---|---|
| 粘贴文本 | `POST /api/projects` | 直接粘贴小说正文，提交后服务端拆章 |
| 上传文件 | `POST /api/projects/upload` | .txt / .docx / .pdf，最大 200MB |
| 导入 YAML | `POST /api/projects/import-yaml` | 直接导入已存在的 `.yaml` 剧本文件 |

提交后自动解析章节并返回预览列表，确认后进入生成阶段。

### 剧本生成

- **逐章并发**：每章独立调用 LLM，最多 3 章并发，避免单次超长上下文
- **两阶段校验**：
  1. JSON Schema 校验（Draft 2020-12）：检查 YAML 结构合法性
  2. 业务规则校验：角色 ID 唯一性、场景引用完整性、必含 narrator
- **失败重试**：单章生成失败自动重试一次；最终部分成功时标记 PARTIAL_SUCCESS
- **幂等生成**：支持 `Idempotency-Key` 请求头，防止重复提交
- **进度追踪**：MySQL 持久化 + Redis HASH 实时缓存，前端 2.5s 轮询

### 工作台

**剧本视图**（ScriptRender）：
- 场景卡片布局，每场独立展示
- 角色名着色（柔和色系，角色 ID 哈希分配）
- 点击角色高亮，只看该角色的对白
- 点击对白弹出 AI 改稿工具栏
- 右侧场景导航条（悬浮展开，显示场次列表，点击跳转）
- 底部统计栏：总场次、对白数、角色数、最活跃角色

**YAML 编辑视图**（CodeMirror 6）：
- YAML 语法高亮（`@codemirror/lang-yaml`）
- One Dark 暗色主题
- 场景行左侧彩色圆点标记，快速导航
- 只读模式，可复制到剪贴板

**人物面板**（右侧）：
- 角色头像圈（着色圆形首字头像）
- 角色名 + ID + 角色类型（主角/反派/配角/NPC）
- 选中章节时可查看该章角色

**对比模式**：重新生成章节时自动进入左右对比视图，可分别保留新旧版本。

### AI 改稿

- 在剧本视图点击任意对白，弹出工具栏
- 支持 5 种改写风格：精简、扩写、润色、正式、口语化
- 改写后显示 diff 对比（原文 ↔ 改写），可保留或放弃
- 支持手动编辑（直接修改对白文本）
- 改写结果保存后自动更新 YAML 数据

### 情感曲线分析

- **全剧分析**：对整个剧本进行情感走向分析，生成情感弧线图
- **单章分析**：选中章节后单独分析该章情感
- **缓存策略**：分析结果缓存到 Redis，重复请求不消耗 LLM 配额
- **多维度**：基于每句对白的 `emotion` 字段，按场景聚合
- **图表渲染**：使用 Chart.js 绘制折线图，支持交互悬停

### 剧本导出

| 格式 | 方式 | 说明 |
|---|---|---|
| Fountain | 直接下载 `.fountain` 文件 | 纯文本标准剧本格式，可导入 Final Draft 等专业软件 |
| PDF | html2pdf.js 直接生成 | 不弹打印框，Courier 12pt 专业排版，自动分页，场景着色保留 |

---

## 限流体系

系统采用三层限流策略，覆盖接口层、LLM 调用层和认证层。

### 通用接口限流

基于 Redis SortedSet 滑动窗口算法，全局限流：

```
配置: app.rate-limit.max-requests=10 / window=60s
```

- 每个请求按路径生成 Redis key（如 `ratelimit:POST:/api/projects/{id}/generate`）
- SortedSet 存储时间戳，定期清理窗口外数据
- 超限时返回 `429 Too Many Requests`，响应头携带 `X-Retry-After`（秒）
- 拦截路径：所有 `/api/**` 接口（白名单除外）

### LLM 并发限流

```
配置: app.llm.max-concurrent=3
```

- 基于 Semaphore 控制 LLM 调用并发量
- 超过并发数时请求排队等待
- 覆盖：剧本生成（generate）、单章重生成（regenerate）、AI 改稿（rewrite-dialogue）、情感分析（analyze-emotions）

### 认证限流

- **验证码发送**：同一手机号 60 秒内不可重复发送，Redis key 过期控制
- **登录接口**：配合通用限流，同一 IP 每分钟最多 5 次登录尝试
- **验证码校验**：Tianai Captcha 二次校验，验证码 2 分钟过期

---

## 架构设计

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│   Vue 3 FE   │────▶│  Spring Boot │────▶│    MySQL 8   │
│  (Vite 8)    │◀────│  (Java 21)   │◀────│  (MyBatis+)  │
└──────────────┘     └──────┬───────┘     └──────────────┘
                            │                     ▲
                            ▼                     │
                     ┌──────────────┐     ┌───────┴───────┐
                     │    Redis     │     │  YAML Schema  │
                     │ 缓存/限流/   │     │   校验引擎     │
                     │ 会话管理     │     └───────────────┘
                     └──────┬───────┘
                            │
                            ▼
                     ┌──────────────┐
                     │  LLM API     │
                     │ (OpenAI 兼容)│
                     └──────────────┘
```

### 核心模块

| 模块 | 路径 | 职责 |
|---|---|---|
| Controller | `api/` | REST 接口 + 参数校验 + 限流注解 |
| LLM Client | `llm/` | OpenAI 兼容调用 + 重试 + 并发控制 |
| Script Service | `script/` | YAML 生成/解析/校验 + 情感分析 |
| Novel Ingest | `novel/` | 文件解析 + 章节拆分 |
| Persistence | `persistence/` | MyBatis-Plus DAO + Redis 缓存 |
| Config | `config/` | Sa-Token + 限流 + Jackson + Redis 配置 |

---

## API 一览

### 认证

| 方法 | 路径 | 说明 | 限流 |
|---|---|---|---|
| GET | `/api/captcha/get` | 获取滑块验证码 | — |
| POST | `/api/captcha/check` | 校验滑块验证码 | — |
| POST | `/api/user/code/send` | 发送短信验证码 | 60s/次 |
| POST | `/api/user/register` | 注册 | 5次/分钟 |
| POST | `/api/user/login` | 登录 | 5次/分钟 |
| POST | `/api/user/logout` | 登出 | — |

### 项目

| 方法 | 路径 | 说明 | 限流 |
|---|---|---|---|
| GET | `/api/projects` | 项目列表 | 10次/分钟 |
| POST | `/api/projects` | 文本创建项目 | 10次/分钟 |
| POST | `/api/projects/upload` | 上传文件创建项目 | 10次/分钟 |
| POST | `/api/projects/import-yaml` | 导入 YAML | 10次/分钟 |
| GET | `/api/projects/{id}` | 项目详情 | 10次/分钟 |
| DELETE | `/api/projects/{id}` | 删除项目 | 10次/分钟 |

### 生成

| 方法 | 路径 | 说明 | 限流 |
|---|---|---|---|
| POST | `/api/projects/{id}/generate` | 触发全本生成 | 3 并发 |
| POST | `/api/projects/{id}/chapters/{chapterId}/regenerate` | 重生成单章 | 3 并发 |
| GET | `/api/projects/{id}/script.yaml` | 下载 YAML | — |

### 编辑

| 方法 | 路径 | 说明 |
|---|---|---|
| PUT | `/api/projects/{id}/chapters/{chapterId}/script` | 更新章节 YAML |
| PUT | `/api/projects/{id}/script` | 更新整本 YAML |
| POST | `/api/projects/{id}/rewrite-dialogue` | AI 改稿 |

### 情感分析

| 方法 | 路径 | 说明 | 限流 |
|---|---|---|---|
| POST | `/api/projects/{id}/analyze-emotions` | 全剧情感分析 | 3 并发 |
| POST | `/api/projects/{id}/chapters/{chapterId}/analyze-emotions` | 单章情感分析 | 3 并发 |
| GET | `/api/projects/{id}/emotions` | 查询缓存的情感数据 | — |

---

## 项目结构

```
qiniuyun/
├── docs/
│   ├── script-yaml-schema.md                    # 剧本 YAML Schema 定义
│   ├── json-schema/script-v1.0.schema.json      # 机器可读校验
│   ├── database/
│   │   ├── schema.sql                           # MySQL DDL
│   │   └── design.md                            # 数据库表设计文档
│   ├── ux-polish-design.md                      # UX 优化设计文档
│   └── ux-polish-plan.md                        # UX 优化实现计划
├── novel to script backend/
│   └── src/main/java/com/nailinai/noveltoscriptbackend/
│       ├── api/                                 # Controller + DTO
│       │   ├── AuthController.java              # 登录/注册/验证码
│       │   ├── CaptchaController.java           # 滑块验证码
│       │   ├── ProjectController.java           # 项目 CRUD + 生成
│       │   └── dto/                             # 响应体定义
│       ├── config/                              # 框架配置
│       │   ├── RateLimitFilter.java             # 滑动窗口限流
│       │   ├── RateLimitProperties.java         # 限流配置映射
│       │   ├── RedisSaTokenDao.java             # Redis 会话存储
│       │   └── WebConfig.java                   # Jackson/CORS 等
│       ├── domain/
│       │   ├── entity/                          # MyBatis-Plus 实体
│       │   └── script/                          # YAML 映射模型
│       ├── llm/                                 # LLM 调用
│       │   ├── LlmClient.java                   # 接口抽象
│       │   └── OpenAiCompatibleClient.java      # OpenAI 兼容实现
│       ├── novel/                               # 小说解析
│       │   └── NovelIngestService.java          # 拆章 + 字数统计
│       ├── persistence/                         # 数据持久化
│       │   ├── mapper/                          # MyBatis Mapper
│       │   └── ProjectStore.java                # DAO 封装
│       ├── script/
│       │   ├── EmotionAnalysisService.java      # 情感分析
│       │   ├── ScriptValidator.java             # 两阶段校验
│       │   └── YamlMergeService.java            # 章节 YAML 合并
│       ├── service/
│       │   ├── UserService.java                 # 用户业务
│       │   └── VerifyCodeService.java           # 验证码业务
│       └── utils/                               # 工具类
├── noveltoscriptfront/
│   └── src/
│       ├── api/index.js                         # 全量 API 封装
│       ├── components/
│       │   ├── ScreenplayPreview.vue            # Fountain 预览 + 导出
│       │   ├── ScriptRender.vue                 # 剧本渲染（场景卡片）
│       │   ├── ScriptEditor.vue                 # CodeMirror YAML 编辑器
│       │   ├── ScriptStats.vue                  # 剧本统计卡片
│       │   ├── EmotionCurve.vue                 # 情感曲线图表
│       │   ├── DialogueToolbar.vue              # AI 改稿工具栏
│       │   ├── CompareView.vue                  # 版本对比
│       │   ├── ConfirmDialog.vue                # 确认弹窗
│       │   ├── ProgressBar.vue                  # 生成进度条
│       │   └── StatusBadge.vue                  # 状态徽标
│       ├── views/
│       │   ├── HomeView.vue                     # 首页（创建项目）
│       │   ├── ProjectView.vue                  # 工作台（核心页面）
│       │   ├── HistoryView.vue                  # 历史项目
│       │   └── LoginView.vue                    # 登录/注册
│       └── utils/
│           ├── fountainGenerator.js             # YAML → Fountain 转换
│           └── sceneGutter.js                   # CodeMirror 场景导航
├── .env                                         # LLM 配置（不提交）
├── .env.example                                 # 环境变量模板
├── sample-novel.txt                             # 示例小说
└── sample-script.yaml                           # 示例剧本 YAML
```

---

## 设计亮点

- **每章独立调用 + 自动合并**：避免单次超长上下文，每章限流并发 3，单章失败自动重试一次
- **两阶段校验**：JSON Schema（结构）+ 业务规则（ID 唯一 / 引用完整 / 必含 narrator）
- **进度双写**：MySQL 持久化 + Redis HASH 实时缓存（前端 2.5s 轮询）
- **幂等生成**：Idempotency-Key 机制防止重复提交
- **可插拔 LLM**：`LlmClient` 接口便于本地 Mock / 切换代理
- **滑动窗口限流**：基于 Redis SortedSet，精确到毫秒级
- **角色名着色**：8 色柔和色系，角色 ID 哈希分配，贯穿剧本/预览/人物面板
- **场景导航**：剧本视图右侧悬浮导航条，鼠标悬停展开
- **情感分析缓存**：分析结果缓存到 Redis，重复请求不消耗 LLM
- **版本对比**：重新生成时自动进入左右对比，可分别保留新旧版本
- **AI 改稿 diff**：改写后显示原文对比，支持保留/放弃/手动编辑
- **直接 PDF 导出**：html2pdf.js 引擎，不弹打印框，自动分页

---

## 已知限制

- 单章 > 12k 字仍可能超 LLM 上下文（建议先用编辑器切章）
- 不支持多人协作 / 项目版本管理
- PDF 导出基于 html2canvas，长文本跨页分割可能不完全精确
- 当前 LLM 调用 `gpt-4o-mini` 校准；用更大的模型质量更佳
