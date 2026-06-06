package com.nailinai.noveltoscriptbackend.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

@Data
@TableName("projects")
public class ProjectEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    private String title;

    @TableField("source_novel")
    private String sourceNovel;

    private String genre;

    /** {@link ProjectStatus} */
    private String status;

    private Integer progress;

    @TableField("current_chapter")
    private Integer currentChapter;

    @TableField("total_chapters")
    private Integer totalChapters;

    @TableField("script_yaml")
    private String scriptYaml;

    @TableField("characters_yaml")
    private String charactersYaml;

    @TableField("error_message")
    private String errorMessage;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;
}
