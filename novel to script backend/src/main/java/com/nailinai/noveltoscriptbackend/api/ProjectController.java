package com.nailinai.noveltoscriptbackend.api;

import cn.dev33.satoken.stp.StpUtil;
import com.nailinai.noveltoscriptbackend.api.dto.CreateProjectRequest;
import com.nailinai.noveltoscriptbackend.api.dto.ProjectResponse;
import com.nailinai.noveltoscriptbackend.api.dto.ProjectSummaryResponse;
import com.nailinai.noveltoscriptbackend.domain.entity.ChapterEntity;
import com.nailinai.noveltoscriptbackend.domain.entity.ProjectEntity;
import com.nailinai.noveltoscriptbackend.novel.NovelIngestService;
import com.nailinai.noveltoscriptbackend.persistence.ProjectStore;
import com.nailinai.noveltoscriptbackend.script.EmotionAnalysisService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/projects")
public class ProjectController {

    private static final Logger log = LoggerFactory.getLogger(ProjectController.class);

    private final NovelIngestService ingest;
    private final ProjectStore store;
    private final EmotionAnalysisService emotionAnalysis;
    private final ObjectMapper json;

    public ProjectController(NovelIngestService ingest,
                             ProjectStore store,
                             EmotionAnalysisService emotionAnalysis,
                             ObjectMapper json) {
        this.ingest = ingest;
        this.store = store;
        this.emotionAnalysis = emotionAnalysis;
        this.json = json;
    }

    /** 列出当前用户的所有项目（轻量级摘要）。 */
    @GetMapping
    public ResponseEntity<List<ProjectSummaryResponse>> listAll() {
        Long userId = StpUtil.getLoginIdAsLong();
        List<ProjectSummaryResponse> list = store.listProjectsByUser(userId).stream()
                .map(ProjectSummaryResponse::from)
                .toList();
        return ResponseEntity.ok(list);
    }

    /** 文本方式创建项目。 */
    @PostMapping
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest req) {
        Long userId = StpUtil.getLoginIdAsLong();
        NovelIngestService.IngestResult r = ingest.ingestText(
                req.getTitle(), req.getSourceNovel(), req.getGenre(), req.getText(), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(r.project(), r.chapters()));
    }

    /** 文件方式（.txt / .docx）创建项目。 */
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<ProjectResponse> upload(
            @RequestParam String title,
            @RequestParam(required = false) String sourceNovel,
            @RequestParam(required = false) String genre,
            @RequestParam("file") MultipartFile file) {
        Long userId = StpUtil.getLoginIdAsLong();
        NovelIngestService.IngestResult r = ingest.ingestFile(title, sourceNovel, genre, file, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(r.project(), r.chapters()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        ProjectEntity p = store.findProject(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
        checkOwnership(p, userId);
        store.deleteProject(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> get(@PathVariable long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        // 自动修正卡住的状态
        store.fixStuckProjectStatus(id);
        ProjectEntity p = store.findProject(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
        checkOwnership(p, userId);
        List<ChapterEntity> chapters = store.listChapters(id);
        return ResponseEntity.ok(toResponse(p, chapters));
    }

    /** 恢复单章 YAML（用户拒绝了重生成的新版本）。 */
    @PutMapping("/{id}/chapters/{chapterId}/script")
    public ResponseEntity<Void> restoreChapterYaml(
            @PathVariable long id,
            @PathVariable long chapterId,
            @RequestBody Map<String, String> body) {
        Long userId = StpUtil.getLoginIdAsLong();
        ProjectEntity p = store.findProject(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
        checkOwnership(p, userId);
        ChapterEntity ch = store.findChapter(chapterId)
                .orElseThrow(() -> new ResourceNotFoundException("Chapter not found: " + chapterId));
        if (!ch.getProjectId().equals(id)) {
            throw new IllegalArgumentException("Chapter does not belong to project " + id);
        }
        String yaml = body.get("yaml");
        if (yaml == null || yaml.isBlank()) {
            throw new IllegalArgumentException("yaml is required");
        }
        store.updateChapterYaml(chapterId, yaml);
        return ResponseEntity.noContent().build();
    }

    /** 恢复全量剧本 YAML（用户拒绝了重新生成的全部结果）。 */
    @PutMapping("/{id}/script")
    public ResponseEntity<Void> restoreProjectYaml(
            @PathVariable long id,
            @RequestBody Map<String, String> body) {
        Long userId = StpUtil.getLoginIdAsLong();
        ProjectEntity p = store.findProject(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
        checkOwnership(p, userId);
        String yaml = body.get("yaml");
        if (yaml == null || yaml.isBlank()) {
            throw new IllegalArgumentException("yaml is required");
        }
        store.updateProjectScriptYaml(id, yaml);
        return ResponseEntity.noContent().build();
    }

    /**
     * 全剧情绪曲线分析。
     *
     * 缓存链路：Redis → MySQL → LLM
     * 1. 检查 Redis 缓存
     * 2. Redis 无 → 检查 MySQL
     * 3. MySQL 有 → 写入 Redis → 返回
     * 4. MySQL 无 → 调用 LLM → 写入 Redis + MySQL → 返回
     */
    @PostMapping("/{id}/analyze-emotions")
    public ResponseEntity<?> analyzeEmotions(@PathVariable long id,
                                              @RequestParam(defaultValue = "false") boolean refresh) {
        Long userId = StpUtil.getLoginIdAsLong();
        ProjectEntity p = store.findProject(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
        checkOwnership(p, userId);

        String yaml = p.getScriptYaml();
        if (yaml == null || yaml.isBlank()) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                    .body(Map.of("error", "Script not ready. Generate the script first."));
        }

        // 1. Redis
        if (!refresh) {
            List<EmotionAnalysisService.EmotionArc> cached = emotionAnalysis.getCached(id);
            if (cached != null) {
                return ResponseEntity.ok(Map.of("arcs", cached, "source", "cache"));
            }
        }

        // 2. MySQL（refresh=true 时跳过）
        if (!refresh) {
            String cachedJson = store.getEmotionAnalysisResult(id);
            if (cachedJson != null) {
                try {
                    List<EmotionAnalysisService.EmotionArc> fromDb = json.readValue(cachedJson,
                            new TypeReference<List<EmotionAnalysisService.EmotionArc>>() {});
                    // 3. 回写 Redis
                    emotionAnalysis.cacheResult(id, fromDb);
                    return ResponseEntity.ok(Map.of("arcs", fromDb, "source", "cache"));
                } catch (Exception e) {
                    log.warn("Failed to deserialize DB emotion analysis, re-analyzing: {}", e.getMessage());
                }
            }
        }

        // 4. LLM
        try {
            List<EmotionAnalysisService.EmotionArc> result = emotionAnalysis.analyze(yaml);
            String resultJson = json.writeValueAsString(result);
            store.saveEmotionAnalysisResult(id, null, resultJson);
            emotionAnalysis.cacheResult(id, result);
            return ResponseEntity.ok(Map.of("arcs", result, "source", "fresh"));
        } catch (Exception e) {
            log.error("Emotion analysis failed for project {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Analysis failed: " + e.getMessage()));
        }
    }

    /**
     * 单章情绪曲线分析。
     * 缓存链路：Redis → MySQL → LLM
     */
    @PostMapping("/{id}/chapters/{chapterId}/analyze-emotions")
    public ResponseEntity<?> analyzeChapterEmotions(
            @PathVariable long id,
            @PathVariable long chapterId) {
        Long userId = StpUtil.getLoginIdAsLong();
        ProjectEntity p = store.findProject(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
        checkOwnership(p, userId);

        ChapterEntity ch = store.findChapter(chapterId)
                .orElseThrow(() -> new ResourceNotFoundException("Chapter not found: " + chapterId));
        if (!ch.getProjectId().equals(id)) {
            throw new IllegalArgumentException("Chapter does not belong to project " + id);
        }

        String yaml = ch.getGeneratedYaml();
        if (yaml == null || yaml.isBlank()) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                    .body(Map.of("error", "Chapter script not ready. Generate the script first."));
        }

        // 1. Redis
        List<EmotionAnalysisService.EmotionArc> cached = emotionAnalysis.getCachedChapter(chapterId);
        if (cached != null) {
            return ResponseEntity.ok(Map.of("arcs", cached, "source", "cache"));
        }

        // 2. MySQL
        String cachedJson = store.getChapterEmotionAnalysisResult(id, chapterId);
        if (cachedJson != null) {
            try {
                List<EmotionAnalysisService.EmotionArc> fromDb = json.readValue(cachedJson,
                        new TypeReference<List<EmotionAnalysisService.EmotionArc>>() {});
                // 3. 回写 Redis
                emotionAnalysis.cacheChapterResult(chapterId, fromDb);
                return ResponseEntity.ok(Map.of("arcs", fromDb, "source", "cache"));
            } catch (Exception e) {
                log.warn("Failed to deserialize stored chapter emotion analysis, re-analyzing: {}", e.getMessage());
            }
        }

        // 4. LLM
        try {
            List<EmotionAnalysisService.EmotionArc> result = emotionAnalysis.analyze(yaml);
            String resultJson = json.writeValueAsString(result);
            store.saveEmotionAnalysisResult(id, chapterId, resultJson);
            emotionAnalysis.cacheChapterResult(chapterId, result);
            return ResponseEntity.ok(Map.of("arcs", result, "source", "fresh"));
        } catch (Exception e) {
            log.error("Chapter emotion analysis failed for project {} chapter {}", id, chapterId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Chapter analysis failed: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}/emotions")
    public ResponseEntity<?> getEmotions(@PathVariable long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        ProjectEntity p = store.findProject(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
        checkOwnership(p, userId);

        String resultJson = store.getEmotionAnalysisResult(id);
        if (resultJson == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "No analysis found. POST /analyze-emotions first."));
        }
        try {
            List<EmotionAnalysisService.EmotionArc> arcs = json.readValue(resultJson,
                    new TypeReference<List<EmotionAnalysisService.EmotionArc>>() {});
            return ResponseEntity.ok(Map.of("arcs", arcs));
        } catch (Exception e) {
            log.error("Failed to deserialize emotion analysis for project {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to read stored analysis."));
        }
    }

    @GetMapping("/{id}/script.yaml")
    public ResponseEntity<String> downloadScript(@PathVariable long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        ProjectEntity p = store.findProject(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
        checkOwnership(p, userId);
        String yaml = p.getScriptYaml() != null ? p.getScriptYaml() : store.getCachedResult(id);
        if (yaml == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("script not ready");
        }
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"project-" + id + ".yaml\"")
                .header("Content-Type", "application/x-yaml; charset=utf-8")
                .body(yaml);
    }

    private ProjectResponse toResponse(ProjectEntity p, List<ChapterEntity> chapters) {
        ProjectResponse r = new ProjectResponse();
        r.id = p.getId();
        r.title = p.getTitle();
        r.sourceNovel = p.getSourceNovel();
        r.genre = p.getGenre();
        r.status = p.getStatus();
        r.progress = p.getProgress();
        r.currentChapter = p.getCurrentChapter();
        r.totalChapters = p.getTotalChapters();
        r.errorMessage = p.getErrorMessage();
        r.scriptYaml = p.getScriptYaml();
        r.characters = store.listProjectCharacters(p.getId()).stream()
                .map(ProjectResponse.CharacterDto::from).toList();
        r.chapters = chapters.stream().map(ProjectResponse.ChapterDto::from).toList();
        Map<Object, Object> live = store.getCachedProgress(p.getId());
        if (live != null && !live.isEmpty()) {
            Map<String, Object> converted = new java.util.HashMap<>(live.size());
            for (Map.Entry<Object, Object> e : live.entrySet()) {
                converted.put(String.valueOf(e.getKey()), e.getValue());
            }
            r.liveProgress = converted;
        }
        r.createdAt = p.getCreatedAt();
        r.updatedAt = p.getUpdatedAt();
        return r;
    }

    private void checkOwnership(ProjectEntity p, Long userId) {
        if (p.getUserId() != null && !p.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Project not found: " + p.getId());
        }
    }
}
