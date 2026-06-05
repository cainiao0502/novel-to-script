# 数据库表设计文档

> 版本：v1.0 · 配套 SQL：[`schema.sql`](schema.sql)
> 引擎：MySQL 8.0+ / InnoDB / utf8mb4

## 目录

1. [总览](#1-总览)
2. [表关系图](#2-表关系图)
3. [projects · 项目主表](#3-projects--项目主表)
4. [chapters · 章节表](#4-chapters--章节表)
5. [characters · 人物表（反范式）](#5-characters--人物表反范式)
6. [设计决策详解](#6-设计决策详解)
7. [索引策略](#7-索引策略)
8. [容量与性能预估](#8-容量与性能预估)
9. [迁移与回滚](#9-迁移与回滚)
10. [已知不足与未来扩展](#10-已知不足与未来扩展)

---

## 1. 总览

| 表 | 行数预估 | 主要职责 |
|---|---|---|
| `projects` | 用户数 × 项目数 | 项目元信息 + 状态 + 最终产物 |
| `chapters` | 项目数 × 3~50 | 章节原文 + 单章 LLM 输出缓存 |
| `characters` | 项目数 × 5~30 | 跨章合并后的人物清单（反范式） |

**设计原则：**
- **最小三表**：不引入用户表 / 标签表 / 协作表，期末作业范围控制
- **逻辑外键**：不在 DDL 加 FK 约束，由 `ProjectStore` 在代码层维护一致性（理由见 §6.3）
- **时间戳毫秒精度**：`TIMESTAMP(3)` 支持并发更新排序
- **LONGTEXT 存 YAML/JSON**：避免把 LLM 输出再切到多张表（会破坏 YAML 完整性）
- **反范式 `characters` 表**：UI 列表快查，省一次 YAML 解析

---

## 2. 表关系图

```
┌─────────────────────────┐
│       projects          │
│  (项目主表, 1)          │
│  - id PK                │
│  - title / status       │
│  - script_yaml          │  ← 最终合并的剧本
└──────────┬──────────────┘
           │ 1
           │
           │ N
┌──────────┴──────────────┐         ┌─────────────────────────┐
│       chapters          │         │      characters         │
│  (章节, N)              │         │  (人物反范式, N)        │
│  - id PK                │         │  - id PK                │
│  - project_id FK        │         │  - project_id FK        │
│  - idx / content        │         │  - char_id (稳定 ID)    │
│  - generated_yaml       │         │  - name / role / json   │
└─────────────────────────┘         └─────────────────────────┘
```

`projects` 是聚合根；`chapters` 和 `characters` 围绕它做 1:N 展开。删除 `projects` 时，**应用层**负责级联删除子表（`ProjectStore.replaceProjectCharacters` / 未来加 `cascadeDeleteProject`）。

---

## 3. projects · 项目主表

**职责**：项目的"主心骨"——存放元信息、运行状态、最终合并的 YAML。

| 字段 | 类型 | 说明 / 决策 |
|---|---|---|
| `id` | `BIGINT AUTO_INCREMENT` | 主键，单库自增；项目数 ≪ int 上限 |
| `title` | `VARCHAR(128)` | 剧名，必填；UI 列表与详情页直接显示 |
| `source_novel` | `VARCHAR(256)` | 原著出处，可空；不强约束（短剧可能为原创） |
| `genre` | `VARCHAR(64)` | 题材标签，可空；自由文本不预定义枚举（题材组合太多） |
| `status` | `VARCHAR(32)` | 枚举字符串，**不**用 MySQL ENUM（增删状态无需 DDL） |
| `progress` | `INT` | 0-100 的整数百分比；UI 直接展示 |
| `current_chapter` | `INT NULL` | 当前正在生成的章节号（UI 显示"正在生成第 N 章"） |
| `total_chapters` | `INT` | 切章后写定，UI 算"X / Y 章"的分母 |
| `script_yaml` | `LONGTEXT` | **最终合并的剧本 YAML**；生成成功回填 |
| `characters_yaml` | `LONGTEXT` | 人物表 JSON 冗余字段，**与 `characters` 表内容一致**，仅作缓存/审计 |
| `error_message` | `VARCHAR(1024)` | 失败时的简短错误摘要（详情看 `chapters.error_message`） |
| `created_at` / `updated_at` | `TIMESTAMP(3)` | 毫秒精度；`updated_at` 配 `ON UPDATE` 自动维护 |

**索引**：
- `PRIMARY KEY (id)`
- `KEY idx_projects_status_created (status, created_at)` — 后台"列出生成中的项目"扫表
- `KEY idx_projects_updated (updated_at)` — "最近修改"列表

---

## 4. chapters · 章节表

**职责**：存原文（`content`） + 单章 LLM 输出（`generated_yaml`） + 单章状态。

| 字段 | 类型 | 说明 / 决策 |
|---|---|---|
| `id` | `BIGINT AUTO_INCREMENT` | 主键 |
| `project_id` | `BIGINT` | 逻辑外键 → `projects.id`；**不加**硬 FK（理由见 §6.3） |
| `idx` | `INT` | 章节序号（1 起），与 `project_id` 联合唯一 |
| `title` | `VARCHAR(255)` | 章标原文（"第N章 标题"），可能含中文标点 |
| `content` | `LONGTEXT` | 该章正文，切章时切出 |
| `status` | `VARCHAR(32)` | `PENDING` / `GENERATING` / `DONE` / `FAILED` |
| `scene_count` | `INT NULL` | 该章 LLM 拆出的场景数；生成完回填，UI 显示"X 场" |
| `generated_yaml` | `LONGTEXT` | **该章 LLM 的原始 YAML 输出**；合并前可单独查看/调试 |
| `error_message` | `VARCHAR(1024)` | 该章单次生成失败的错误（区别于 project 级） |
| `created_at` / `updated_at` | `TIMESTAMP(3)` | 同上 |

**索引**：
- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_chapters_project_idx (project_id, idx)` — 防重复切章
- `KEY idx_chapters_project_status (project_id, status)` — "统计某项目已完成的章节数"等聚合查询
- `KEY idx_chapters_updated (updated_at)` — 全局按更新时间排序

**为什么保留 `generated_yaml` 而不是合并后立刻删？**
- 单章生成失败时，UI 可展示原始 LLM 输出供用户排错
- 重新合并脚本（`ScriptMerger`）有现成数据源，不必再调 LLM
- 空间成本可接受：每章 1-5KB LONGTEXT

---

## 5. characters · 人物表（反范式）

**职责**：跨章合并后的人物清单。冗余于 `projects.script_yaml`，但便于 UI 直接读取。

| 字段 | 类型 | 说明 / 决策 |
|---|---|---|
| `id` | `BIGINT AUTO_INCREMENT` | 主键 |
| `project_id` | `BIGINT` | 逻辑外键 → `projects.id` |
| `char_id` | `VARCHAR(64)` | 稳定 ID（`c_fan_xian` / `narrator`），与 `project_id` 联合唯一 |
| `name` | `VARCHAR(128)` | 主名（可能含中英混合） |
| `role` | `VARCHAR(32)` | `protagonist` / `antagonist` / `supporting` / `npc` |
| `full_data_json` | `LONGTEXT` | 完整人物对象 JSON（aliases/gender/age_range/appearance/voice） |
| `created_at` / `updated_at` | `TIMESTAMP(3)` | 同上 |

**索引**：
- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_characters_project_charid (project_id, char_id)` — 防重复人物
- `KEY idx_characters_project_role (project_id, role)` — "列出主角 / 反派"用

**为什么不直接用 `script_yaml` 解析？**
- 解析 LONGTEXT 涉及 YAML 解析 + 遍历，单次 10-50ms
- 项目视图一次要展示所有人物，每次请求都解析代价高
- 写一张反范式表，写入代价（生成完成时一次性 replace）一次性付出

**写入策略**：`ProjectStore.replaceProjectCharacters(projectId, list)` — 先 `DELETE WHERE project_id=?` 再批量 `INSERT`（项目级数据量小，简单可靠）。

---

## 6. 设计决策详解

### 6.1 为什么用 LONGTEXT 存 YAML/JSON 而不是拆到子表

**反对拆分的论证**：
- LLM 输出是**结构化文本**（YAML），拆到子表会破坏语义完整性（如 `actions: [str, str]` 与 `dialogues: [{...}]` 混在一个数组里）
- 反序列化时需要复杂的 join；序列化时要重新拼装
- "剧本本身"作为可下载、可在外部编辑器修改的产物，**保持单文件**形式更友好

**支持 LONGTEXT 的论证**：
- MySQL `LONGTEXT` 最大 4GB，单本剧本 ≪ 1MB
- 一次性读/写避免 N+1 查询
- 备份/导出是单文件操作

**代价**：无法在 DB 层做"YAML 内部字段索引"。→ 缓解：通过 `characters` 反范式表做关键字段快查。

### 6.2 为什么 `status` 用 VARCHAR 不用 ENUM

- MySQL `ENUM` 增删值需要 `ALTER TABLE`（DDL 锁表）
- 业务状态会演进（未来加 `PAUSED` / `CANCELLED`）
- VARCHAR + Java enum 字符串值 已足够；类型安全在应用层保证

### 6.3 为什么用逻辑外键而不是硬 FK 约束

**逻辑外键的取舍**：
| 维度 | 硬 FK | 逻辑 FK |
|---|---|---|
| 数据一致性 | DB 强制 | 应用层保证 |
| 删除/迁移灵活性 | 低（级联策略需 DDL） | 高 |
| 性能开销 | 每次 INSERT/UPDATE 检查 | 无 |
| 多语言/多服务 | 单库 OK；跨服务不可行 | 跨服务也行 |

**本项目选择逻辑 FK 的理由**：
1. 单体应用规模小，应用层 `ProjectStore` 已统一所有写入路径
2. 未来如分库/分表/上 TiDB，硬 FK 是迁移拦路虎
3. `ProjectStore.replaceProjectCharacters` 已示范级联删除模式

**风险与缓解**：
- 应用 bug 可能留下孤儿行 → 加定期清理任务（或在删除 project 的 service 方法里强制先删子表）
- 数据完整性回退 → 在测试里加 FK 等价断言

### 6.4 为什么时间戳用 `TIMESTAMP(3)` 而非 DATETIME

- `TIMESTAMP(3)` 占 4 字节，`DATETIME(3)` 占 8 字节（毫秒精度下 TIMESTAMP 更省）
- `TIMESTAMP` 范围 1970-2038（**对本项目够用**；如要存未来时间需改 DATETIME）
- 配 `ON UPDATE CURRENT_TIMESTAMP(3)` 实现自动更新

### 6.5 为什么用 `LONGTEXT` 而不是 `JSON` 列

MySQL 8 有原生 `JSON` 类型，理论上能校验内部结构。**为什么不用？**：
- 我们的 `script_yaml` 是 YAML 不是 JSON；`JSON` 列会拒
- `full_data_json` 字段本可以用 `JSON` 列；选择 `LONGTEXT` 是为了和 YAML 风格统一
- 真正需要 JSON 内部查询（如 `WHERE full_data_json->>'$.gender' = '男'`）目前没有；将来若需要可 `ALTER TABLE` 转换

### 6.6 字符集为什么用 utf8mb4

- `utf8`（MySQL 别名）实际是 3 字节 utf8mb3，**不能存 emoji**
- 项目可能含 emoji（标题、台词）；utf8mb4 是 4 字节完整 UTF-8
- 校对规则选 `utf8mb4_unicode_ci`（标准 Unicode 排序）而非 `utf8mb4_general_ci`（更简单但不准确）

---

## 7. 索引策略

### 7.1 当前索引

| 表 | 索引 | 用途 |
|---|---|---|
| `projects` | `PRIMARY (id)` | 主键 |
| `projects` | `idx_projects_status_created` | 后台扫"生成中"项目 |
| `projects` | `idx_projects_updated` | "最近修改"列表 |
| `chapters` | `PRIMARY (id)` | 主键 |
| `chapters` | `uk_chapters_project_idx` | 防重复切章 + 章节排序 |
| `chapters` | `idx_chapters_project_status` | 按项目统计完成数 |
| `chapters` | `idx_chapters_updated` | 全局按更新时间 |
| `characters` | `PRIMARY (id)` | 主键 |
| `characters` | `uk_characters_project_charid` | 防重复人物 |
| `characters` | `idx_characters_project_role` | 按角色分类 |

### 7.2 不加索引的字段

- `title` / `source_novel` / `genre` 等元信息：项目数 ≪ 1万，不加，全表扫 OK
- `error_message`：极低频查询（人工排查用）
- `progress`：通常和 `status` 联合过滤，走 `status` 索引即可

### 7.3 未来可能需要的索引

| 场景 | 索引 | 当前未加原因 |
|---|---|---|
| 按剧名搜索 | `KEY idx_projects_title (title)` | 项目数小；UI 暂未提供 |
| 按状态分页 + 排序 | 已通过 `idx_projects_status_created` 覆盖 | — |
| 章节全文搜索 | `FULLTEXT idx_chapters_content` | 当前不做全文搜索；引入 ES/Meilisearch 时再考虑 |

---

## 8. 容量与性能预估

### 8.1 单项目数据量

| 内容 | 平均 | 极大 |
|---|---|---|
| 项目元信息 | 1 KB | 1 KB |
| 章节原文 | 30 KB（10 章 × 3 KB/章） | 500 KB（50 章 × 10 KB/章） |
| 章节 LLM 输出 | 20 KB | 300 KB |
| 合并 YAML | 50 KB | 1 MB |
| 人物（25 个） | 15 KB | 50 KB |
| **合计** | ~120 KB | ~2 MB |

### 8.2 1 万项目规模

| 表 | 行数 | 数据量 |
|---|---|---|
| `projects` | 10,000 | 10 MB |
| `chapters` | 300,000 | 9 GB（含 LONGTEXT） |
| `characters` | 250,000 | 250 MB |
| **合计** | — | **~10 GB** |

→ 单机 MySQL 8 足够支撑。如需横向扩展，按 `project_id` 分表分库。

### 8.3 查询性能预估

| 查询 | 期望延迟 | 备注 |
|---|---|---|
| `SELECT * FROM projects WHERE id = ?` | < 5 ms | 主键 |
| `SELECT * FROM chapters WHERE project_id = ?` | < 20 ms | 唯一索引 |
| `SELECT * FROM characters WHERE project_id = ?` | < 20 ms | 唯一索引 |
| `SELECT COUNT(*) FROM chapters WHERE project_id=? AND status='DONE'` | < 50 ms | 联合索引 |
| 列表页 `SELECT * FROM projects ORDER BY updated_at LIMIT 20` | < 10 ms | updated_at 索引 |

---

## 9. 迁移与回滚

### 9.1 首次部署

```bash
mysql -u root -p < docs/database/schema.sql
# Spring Boot 启动时 MyBatis-Plus 不会再自动建表（与本 DDL 一致即可）
```

> 注：当前 `application.properties` 设了 `mybatis-plus.configuration...`，MyBatis-Plus 不会自动 DDL（因为我们没启用 `ddl-auto`）。DDL 是 schema.sql 一手创建，应用层只做数据写入。

### 9.2 Schema 演进

- 所有变更走新的 SQL 文件 `schema-vX.Y.sql`（追加式）
- 不在 `schema.sql` 上 in-place 改（保留 baseline）
- 配合 `Flyway` 或 `Liquibase` 做版本管理（未来可加）

### 9.3 数据回滚

- 删除项目：应用层 `DELETE projects WHERE id=?` + 显式级联 `DELETE chapters / characters`
- 单章重置：`UPDATE chapters SET status='PENDING' WHERE id=?`

---

## 10. 已知不足与未来扩展

| 不足 | 影响 | 未来方案 |
|---|---|---|
| 没有用户表 | 多用户隔离靠应用层 | 引入 `users` 表 + 软删除 |
| 没有版本/快照表 | 同一项目多次生成只有最新结果 | 加 `project_revisions` 表 |
| 章节 LONGTEXT 全文无法索引 | 无法"在所有项目中搜章节" | 接 Meilisearch / ES |
| 没有协作/评论 | 单人用 OK | 加 `comments` 表 + 字段级锁 |
| 没有剧本版本对比 | 改完不能 diff | 加 `script_diffs` 物化视图 |
| 没有标签/分类 | 项目多了难找 | 加 `tags` 表 + `project_tags` 关联表 |
| 硬 FK 缺失 | 孤儿行风险 | 加 `db_integrity_check` 定时任务 |
