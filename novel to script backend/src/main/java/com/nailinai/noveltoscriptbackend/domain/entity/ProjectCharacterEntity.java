package com.nailinai.noveltoscriptbackend.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

@Data
@TableName("characters")
public class ProjectCharacterEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("project_id")
    private Long projectId;

    @TableField("char_id")
    private String charId;

    private String name;

    /** 角色定位：protagonist / antagonist / supporting / npc */
    private String role;

    /** 完整人物对象 JSON。 */
    @TableField("full_data_json")
    private String fullDataJson;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;
}
