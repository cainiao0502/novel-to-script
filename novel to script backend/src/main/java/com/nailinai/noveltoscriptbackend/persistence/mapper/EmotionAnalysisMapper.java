package com.nailinai.noveltoscriptbackend.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nailinai.noveltoscriptbackend.domain.entity.EmotionAnalysisEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EmotionAnalysisMapper extends BaseMapper<EmotionAnalysisEntity> {

    @Select("SELECT * FROM emotion_analysis WHERE project_id = #{projectId} AND chapter_id IS NULL ORDER BY created_at DESC LIMIT 1")
    EmotionAnalysisEntity findByProjectId(@Param("projectId") long projectId);

    @Select("SELECT * FROM emotion_analysis WHERE project_id = #{projectId} AND chapter_id = #{chapterId} ORDER BY created_at DESC LIMIT 1")
    EmotionAnalysisEntity findByChapterId(@Param("projectId") long projectId, @Param("chapterId") long chapterId);

    @Insert("INSERT INTO emotion_analysis (project_id, chapter_id, result_json, created_at, updated_at) "
            + "VALUES (#{projectId}, #{chapterId}, #{resultJson}, NOW(), NOW()) "
            + "ON DUPLICATE KEY UPDATE result_json = VALUES(result_json), updated_at = NOW()")
    void upsert(@Param("projectId") long projectId, @Param("chapterId") Long chapterId, @Param("resultJson") String resultJson);
}
