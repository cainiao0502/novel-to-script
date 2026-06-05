package com.nailinai.noveltoscriptbackend.api.dto;

import com.nailinai.noveltoscriptbackend.domain.entity.ProjectEntity;

import java.time.Instant;

public class ProjectSummaryResponse {
    public Long id;
    public String title;
    public String sourceNovel;
    public String genre;
    public String status;
    public Integer progress;
    public Integer totalChapters;
    public Instant createdAt;
    public Instant updatedAt;

    public static ProjectSummaryResponse from(ProjectEntity p) {
        ProjectSummaryResponse r = new ProjectSummaryResponse();
        r.id = p.getId();
        r.title = p.getTitle();
        r.sourceNovel = p.getSourceNovel();
        r.genre = p.getGenre();
        r.status = p.getStatus();
        r.progress = p.getProgress();
        r.totalChapters = p.getTotalChapters();
        r.createdAt = p.getCreatedAt();
        r.updatedAt = p.getUpdatedAt();
        return r;
    }
}
