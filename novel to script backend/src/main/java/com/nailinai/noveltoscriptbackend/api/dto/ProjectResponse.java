package com.nailinai.noveltoscriptbackend.api.dto;

import com.nailinai.noveltoscriptbackend.domain.entity.ChapterEntity;
import com.nailinai.noveltoscriptbackend.domain.entity.ProjectEntity;
import com.nailinai.noveltoscriptbackend.domain.entity.ProjectCharacterEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class ProjectResponse {
    public Long id;
    public String title;
    public String sourceNovel;
    public String genre;
    public String status;
    public Integer progress;
    public Integer currentChapter;
    public Integer totalChapters;
    public String errorMessage;
    public String scriptYaml;
    public List<CharacterDto> characters;
    public List<ChapterDto> chapters;
    public Map<String, Object> liveProgress;
    public Instant createdAt;
    public Instant updatedAt;

    public record CharacterDto(
            String id, String name, String role, String fullDataJson
    ) {
        public static CharacterDto from(ProjectCharacterEntity e) {
            return new CharacterDto(e.getCharId(), e.getName(), e.getRole(), e.getFullDataJson());
        }
    }

    public record ChapterDto(
            Long id, Integer idx, String title, Integer sceneCount,
            String status, String errorMessage, String generatedYaml
    ) {
        public static ChapterDto from(ChapterEntity e) {
            return new ChapterDto(e.getId(), e.getIdx(), e.getTitle(),
                    e.getSceneCount(), e.getStatus(), e.getErrorMessage(), e.getGeneratedYaml());
        }
    }
}
