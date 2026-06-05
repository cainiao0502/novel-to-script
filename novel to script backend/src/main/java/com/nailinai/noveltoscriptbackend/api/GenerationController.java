package com.nailinai.noveltoscriptbackend.api;

import com.nailinai.noveltoscriptbackend.domain.entity.ChapterEntity;
import com.nailinai.noveltoscriptbackend.domain.entity.ChapterStatus;
import com.nailinai.noveltoscriptbackend.domain.entity.ProjectEntity;
import com.nailinai.noveltoscriptbackend.persistence.ProjectStore;
import com.nailinai.noveltoscriptbackend.script.ScriptGenerationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/projects/{id}")
public class GenerationController {

    private final ScriptGenerationService service;
    private final ProjectStore store;

    public GenerationController(ScriptGenerationService service, ProjectStore store) {
        this.service = service;
        this.store = store;
    }

    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generate(
            @PathVariable long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        ProjectEntity p = store.findProject(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            if (!store.takeIdempotency(idempotencyKey)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("status", "DUPLICATE", "message", "duplicate request"));
            }
        }

        if ("GENERATING".equals(p.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("status", "ALREADY_RUNNING", "message", "generation in progress"));
        }

        service.generateAsync(id);
        return ResponseEntity.accepted().body(Map.of(
                "status", "ACCEPTED",
                "projectId", id,
                "message", "generation started"
        ));
    }

    @PostMapping("/chapters/{chapterId}/regenerate")
    public ResponseEntity<Map<String, Object>> regenerateChapter(
            @PathVariable long id, @PathVariable long chapterId) {
        ChapterEntity ch = store.findChapter(chapterId)
                .orElseThrow(() -> new ResourceNotFoundException("Chapter not found: " + chapterId));
        if (!ch.getProjectId().equals(id)) {
            throw new IllegalArgumentException("Chapter does not belong to project " + id);
        }
        // 重置状态以便整体重新生成
        store.updateChapterStatus(chapterId, ChapterStatus.PENDING, null, null, null);
        // 简单实现：重跑整本（per-chapter regen 可作为后续优化）
        service.generateAsync(id);
        return ResponseEntity.accepted().body(Map.of(
                "status", "ACCEPTED",
                "projectId", id,
                "chapterId", chapterId,
                "message", "regeneration started"
        ));
    }
}
