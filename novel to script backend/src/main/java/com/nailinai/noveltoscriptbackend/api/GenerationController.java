package com.nailinai.noveltoscriptbackend.api;

import cn.dev33.satoken.stp.StpUtil;
import com.nailinai.noveltoscriptbackend.domain.entity.ChapterEntity;
import com.nailinai.noveltoscriptbackend.domain.entity.ChapterStatus;
import com.nailinai.noveltoscriptbackend.domain.entity.ProjectEntity;
import com.nailinai.noveltoscriptbackend.persistence.ProjectStore;
import com.nailinai.noveltoscriptbackend.script.DialogueRewriteService;
import com.nailinai.noveltoscriptbackend.script.ScriptGenerationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/projects/{id}")
public class GenerationController {

    private final ScriptGenerationService service;
    private final ProjectStore store;
    private final DialogueRewriteService dialogueRewriteService;

    public GenerationController(ScriptGenerationService service,
                                ProjectStore store,
                                DialogueRewriteService dialogueRewriteService) {
        this.service = service;
        this.store = store;
        this.dialogueRewriteService = dialogueRewriteService;
    }

    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generate(
            @PathVariable long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        // 先修正可能卡住的状态
        store.fixStuckProjectStatus(id);

        ProjectEntity p = store.findProject(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
        checkOwnership(p);

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
        // 先修正可能卡住的状态
        store.fixStuckProjectStatus(id);

        ProjectEntity p = store.findProject(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
        checkOwnership(p);
        if ("GENERATING".equals(p.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("status", "ALREADY_RUNNING", "message", "generation in progress"));
        }

        ChapterEntity ch = store.findChapter(chapterId)
                .orElseThrow(() -> new ResourceNotFoundException("Chapter not found: " + chapterId));
        if (!ch.getProjectId().equals(id)) {
            throw new IllegalArgumentException("Chapter does not belong to project " + id);
        }
        // 仅重新生成指定章节
        service.regenerateSingleChapterAsync(id, chapterId);
        return ResponseEntity.accepted().body(Map.of(
                "status", "ACCEPTED",
                "projectId", id,
                "chapterId", chapterId,
                "message", "single chapter regeneration started"
        ));
    }

    /**
     * AI 局部改写对白。接收场景上下文 + 目标对白 + 风格指令，返回改写后的文本。
     */
    @PostMapping("/rewrite-dialogue")
    public ResponseEntity<Map<String, Object>> rewriteDialogue(
            @PathVariable long id,
            @RequestBody Map<String, String> body) {

        ProjectEntity p = store.findProject(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
        checkOwnership(p);

        String style = body.get("style");
        String currentLine = body.get("currentLine");
        String context = body.getOrDefault("context", "");

        if (style == null || style.isBlank()) {
            throw new IllegalArgumentException("style is required (dramatic/humorous/concise/colloquial)");
        }
        if (currentLine == null || currentLine.isBlank()) {
            throw new IllegalArgumentException("currentLine is required");
        }

        String rewritten = dialogueRewriteService.rewrite(currentLine, style, context);

        return ResponseEntity.ok(Map.of(
                "originalLine", currentLine,
                "rewrittenLine", rewritten
        ));
    }

    private void checkOwnership(ProjectEntity p) {
        Long userId = StpUtil.getLoginIdAsLong();
        if (p.getUserId() != null && !p.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Project not found: " + p.getId());
        }
    }
}
