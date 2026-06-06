-- 情绪分析结果持久化表
CREATE TABLE IF NOT EXISTS `emotion_analysis` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `project_id` BIGINT NOT NULL,
  `chapter_id` BIGINT NULL COMMENT 'NULL 表示全剧分析',
  `result_json` LONGTEXT NOT NULL COMMENT 'EmotionArc 列表 JSON',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_project_chapter` (`project_id`, `chapter_id`),
  INDEX `idx_project_id` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='情绪曲线分析结果';
