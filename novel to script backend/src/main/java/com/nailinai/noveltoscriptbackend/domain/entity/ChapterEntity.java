package com.nailinai.noveltoscriptbackend.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

@Data
@TableName("chapters")
public class ChapterEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("project_id")
    private Long projectId;

    /** 章节序号，从 1 起。 */
    private Integer idx;

    private String title;

    private String content;

    /** {@link ChapterStatus} */
    private String status;

    @TableField("scene_count")
    private Integer sceneCount;

    @TableField("generated_yaml")
    private String generatedYaml;

    @TableField("error_message")
    private String errorMessage;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;
}
