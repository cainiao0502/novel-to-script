# 小说转剧本工具

> 期末作业 · AI 辅助剧本创作
> 把 3 章以上的中文小说一键转成结构化 YAML 剧本（影视/短剧/网剧）

## 项目结构

```
qiniuyun/
├── docs/
│   ├── script-yaml-schema.md          # ★ 剧本 YAML Schema 定义 + 设计依据
│   ├── json-schema/script-v1.0.schema.json   # 机器可读校验
│   └── database/
│       ├── schema.sql                 # MySQL DDL（含表/索引/注释）
│       └── design.md                  # 数据库表设计文档（决策详解）
├── novel to script backend/           # Spring Boot 4 / Java 21 / MyBatis-Plus
├── noveltoscriptfront/                # Vue 3 / Vite / CodeMirror 6 / GSAP
├── .env                               # LLM 配置（API Key 等，已 git ignore）
├── .env.example                       # .env 模板
└── README.md
```

## 技术栈

| 层 | 选型 |
|---|---|
| 前端 | Vue 3 + Vite + Vue Router + Pinia + CodeMirror 6 + GSAP |
| 后端 | Spring Boot 3.4 + Java 21 + WebClient + MyBatis-Plus 3.5 + MySQL 8 + Redis |
| LLM | OpenAI 兼容 chat/completions（官方 / 代理 / 中转均可） |
| 校验 | JSON Schema (Draft 2020-12) via networknt/json-schema-validator |
| 文档 | Apache POI（.docx 解析） |
| 动画 | GSAP（hero 进场、章节列表 stagger、进度条 tween） |

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
# 根目录已经有一份 .env 模板，直接编辑它
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

## 使用流程

1. 打开首页 → 粘贴小说正文或上传 `.txt` / `.docx`
2. 填写剧名、原著、题材 → 点击"解析章节"
3. 看到至少 3 章预览后 → 点击"开始生成剧本"
4. 跳转工作台：左侧章节列表、中间 YAML（CodeMirror 高亮）、右侧人物表
5. 复制 / 下载 YAML；单章失败时点击"重新生成"

## API 一览

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/projects` | 文本方式创建项目 |
| POST | `/api/projects/upload` | 文件方式（multipart）创建项目 |
| GET | `/api/projects/{id}` | 查询项目详情（meta/chapters/characters/script） |
| POST | `/api/projects/{id}/generate` | 触发生成（async）；`Idempotency-Key` 可选 |
| POST | `/api/projects/{id}/chapters/{chapterId}/regenerate` | 重生成单章（简化为整本重跑） |
| GET | `/api/projects/{id}/script.yaml` | 下载最终 YAML |

## 核心交付物 · YAML Schema

详见 [`docs/script-yaml-schema.md`](docs/script-yaml-schema.md)。要点：

- **版本**：`version: "1.0"`
- **顶层**：`meta` / `characters` / `scenes` / `notes`
- **稳定 ID**：人物和场景用机器稳定 ID（`c_fan_xian` / `s_001`），跨章引用不依赖姓名
- **场景为最小单位**：一章可拆 2-5 场
- **动作 / 对白 / 旁白分离**：表演语义清晰
- **附加字段严格**：`additionalProperties: false` 拒绝 LLM 幻觉

## 设计亮点

- **每章独立调用 + 自动合并**：避免单次超长上下文，每章限流并发 3，单章失败自动重试一次
- **两阶段校验**：JSON Schema（结构）+ 业务规则（ID 唯一 / 引用完整 / 必含 narrator）
- **进度双写**：MySQL 持久化 + Redis HASH 实时缓存（前端 2.5s 轮询）
- **可插拔 LLM**：`LlmClient` 接口便于本地 Mock / 切换代理
- **Linear 风格 UI**：暗色画布 + 薰衣草蓝点缀 + GSAP 动效

## 已知限制

- 单章 > 12k 字仍可能超 LLM 上下文（建议先用编辑器切章）
- 不支持多人协作 / 项目版本管理
- 不做导出 PDF / Fountain 格式（YAML 即产物）
- 当前 LLM 调用 `gpt-4o-mini` 校准；用大模型质量更佳
