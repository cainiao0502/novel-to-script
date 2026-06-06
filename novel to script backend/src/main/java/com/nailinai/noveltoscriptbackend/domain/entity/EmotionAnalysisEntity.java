package com.nailinai.noveltoscriptbackend.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

@Data
@TableName("emotion_analysis")
public class EmotionAnalysisEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("project_id")
    private Long projectId;

    /** 章节 ID，null 表示全剧分析 */
    @TableField("chapter_id")
    private Long chapterId;

    /** JSON 结果：EmotionArc 列表 */
    @TableField("result_json")
    private String resultJson;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;
}
