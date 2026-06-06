-- ============================================================
-- AI 小说转剧本工具 · 数据库 Schema (MySQL 8.0+)
-- 数据库: novel_to_script
-- 字符集: utf8mb4 / utf8mb4_unicode_ci
-- 引擎:   InnoDB（事务 + 行锁 + FK）
-- ============================================================

CREATE DATABASE IF NOT EXISTS `novel_to_script`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE `novel_to_script`;

-- ------------------------------------------------------------
-- 0. users · 用户表
--    描述: 存储注册用户信息；手机号作为登录账号，密码 BCrypt 加密。
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT          COMMENT '主键',
    `mobile`      VARCHAR(20)   NOT NULL                        COMMENT '手机号（登录账号）',
    `password`    VARCHAR(255)  NOT NULL                        COMMENT '密码（BCrypt 加密）',
    `nickname`    VARCHAR(50)   DEFAULT NULL                    COMMENT '昵称',
    `avatar`      VARCHAR(500)  DEFAULT NULL                    COMMENT '头像 URL',
    `status`      TINYINT       DEFAULT 1                       COMMENT '状态：1-正常，0-禁用',
    `create_time` DATETIME      DEFAULT CURRENT_TIMESTAMP       COMMENT '创建时间',
    `update_time` DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_mobile` (`mobile`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '用户表：手机号注册，BCrypt 密码';


-- ------------------------------------------------------------
-- 1. projects · 项目主表
--    描述: 用户每次"上传/粘贴小说"创建一个项目；存储剧名、状态、
--          进度、最终合并的剧本 YAML 和人物表 JSON。
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `projects`;
CREATE TABLE `projects` (
    `id`                BIGINT          NOT NULL AUTO_INCREMENT          COMMENT '主键',
    `user_id`           BIGINT          DEFAULT NULL                    COMMENT '所属用户（逻辑外键，关联 users.id）',
    `title`             VARCHAR(128)    NOT NULL                        COMMENT '剧名',
    `source_novel`      VARCHAR(256)    DEFAULT NULL                    COMMENT '原著出处（书名/作者）',
    `genre`             VARCHAR(64)     DEFAULT NULL                    COMMENT '题材标签，如"短剧/古装"',
    `status`            VARCHAR(32)     NOT NULL DEFAULT 'DRAFT'        COMMENT 'DRAFT/GENERATING/PARTIAL_SUCCESS/COMPLETED/FAILED',
    `progress`          INT             NOT NULL DEFAULT 0              COMMENT '生成进度 0-100',
    `current_chapter`   INT             DEFAULT NULL                    COMMENT '正在生成的章节号（用于 UI 显示）',
    `total_chapters`    INT             NOT NULL DEFAULT 0              COMMENT '总章节数（切章后写定）',
    `script_yaml`       LONGTEXT        DEFAULT NULL                    COMMENT '合并后的最终剧本 YAML（生成完成回填）',
    `characters_yaml`   LONGTEXT        DEFAULT NULL                    COMMENT '人物表 JSON（冗余字段，UI 快速列表用）',
    `error_message`     VARCHAR(1024)   DEFAULT NULL                    COMMENT '生成失败/部分失败的错误摘要',
    `created_at`        TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（毫秒精度）',
    `updated_at`        TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_projects_status_created` (`status`, `created_at`),
    KEY `idx_projects_updated`        (`updated_at`),
    KEY `idx_projects_user_id`        (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '项目主表：每个"上传/粘贴小说"动作对应一行';


-- ------------------------------------------------------------
-- 2. chapters · 章节表
--    描述: 切章后落盘；status 跟踪单章生成状态，generated_yaml
--          缓存该章 LLM 原始返回（即使合并后也能定位单章问题）。
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `chapters`;
CREATE TABLE `chapters` (
    `id`              BIGINT          NOT NULL AUTO_INCREMENT          COMMENT '主键',
    `project_id`      BIGINT          NOT NULL                        COMMENT '所属项目（逻辑外键，关联 projects.id）',
    `idx`             INT             NOT NULL                        COMMENT '章节序号（从 1 起，章内递增）',
    `title`           VARCHAR(255)    DEFAULT NULL                    COMMENT '章标原文（"第N章 标题"）',
    `content`         LONGTEXT        NOT NULL                        COMMENT '该章正文（切章时切出的）',
    `status`          VARCHAR(32)     NOT NULL DEFAULT 'PENDING'      COMMENT 'PENDING/GENERATING/DONE/FAILED',
    `scene_count`     INT             DEFAULT NULL                    COMMENT '本章拆出的场景数（生成完成回填）',
    `generated_yaml`  LONGTEXT        DEFAULT NULL                    COMMENT '该章 LLM 原始 YAML 输出（合并前可单独查看）',
    `error_message`   VARCHAR(1024)   DEFAULT NULL                    COMMENT '该章生成失败的错误信息',
    `created_at`      TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`      TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_chapters_project_idx` (`project_id`, `idx`),
    KEY `idx_chapters_project_status`    (`project_id`, `status`),
    KEY `idx_chapters_updated`           (`updated_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '章节表：每章对应一行，存原文 + 生成结果';


-- ------------------------------------------------------------
-- 3. characters · 项目人物表（反范式）
--    描述: 跨章合并后的人物清单；full_data_json 存人物完整
--          数据（appearance/voice/aliases/gender/age_range），
--          便于 UI 直接渲染而无需解析 script_yaml。
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `characters`;
CREATE TABLE `characters` (
    `id`               BIGINT          NOT NULL AUTO_INCREMENT          COMMENT '主键',
    `project_id`       BIGINT          NOT NULL                        COMMENT '所属项目',
    `char_id`          VARCHAR(64)     NOT NULL                        COMMENT '稳定 ID（YAML 中的 c_xxx，跨章引用）',
    `name`             VARCHAR(128)    DEFAULT NULL                    COMMENT '主名',
    `role`             VARCHAR(32)     DEFAULT NULL                    COMMENT 'protagonist/antagonist/supporting/npc',
    `full_data_json`   LONGTEXT        DEFAULT NULL                    COMMENT '完整人物对象 JSON（appearance/voice/aliases/gender/age_range）',
    `created_at`       TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`       TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_characters_project_charid` (`project_id`, `char_id`),
    KEY `idx_characters_project_role`         (`project_id`, `role`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '项目人物表（反范式视图，便于 UI 快速渲染）';


-- ------------------------------------------------------------
-- 初始化数据：无
-- ------------------------------------------------------------
