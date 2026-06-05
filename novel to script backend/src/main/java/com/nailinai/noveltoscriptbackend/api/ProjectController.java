package com.nailinai.noveltoscriptbackend.api;

import com.nailinai.noveltoscriptbackend.api.dto.CreateProjectRequest;
import com.nailinai.noveltoscriptbackend.api.dto.ProjectResponse;
import com.nailinai.noveltoscriptbackend.api.dto.ProjectSummaryResponse;
import com.nailinai.noveltoscriptbackend.domain.entity.ChapterEntity;
import com.nailinai.noveltoscriptbackend.domain.entity.ProjectEntity;
import com.nailinai.noveltoscriptbackend.novel.NovelIngestService;
import com.nailinai.noveltoscriptbackend.persistence.ProjectStore;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final NovelIngestService ingest;
    private final ProjectStore store;

    public ProjectController(NovelIngestService ingest, ProjectStore store) {
        this.ingest = ingest;
        this.store = store;
    }

    /** 列出所有项目（轻量级摘要）。 */
    @GetMapping
    public ResponseEntity<List<ProjectSummaryResponse>> listAll() {
        List<ProjectSummaryResponse> list = store.listAllProjects().stream()
                .map(ProjectSummaryResponse::from)
                .toList();
        return ResponseEntity.ok(list);
    }

    /** 文本方式创建项目。 */
    @PostMapping
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest req) {
        NovelIngestService.IngestResult r = ingest.ingestText(
                req.getTitle(), req.getSourceNovel(), req.getGenre(), req.getText());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(r.project(), r.chapters()));
    }

    /** 文件方式（.txt / .docx）创建项目。 */
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<ProjectResponse> upload(
            @RequestParam String title,
            @RequestParam(required = false) String sourceNovel,
            @RequestParam(required = false) String genre,
            @RequestParam("file") MultipartFile file) {
        NovelIngestService.IngestResult r = ingest.ingestFile(title, sourceNovel, genre, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(r.project(), r.chapters()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        store.findProject(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
        store.deleteProject(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> get(@PathVariable long id) {
        // 自动修正卡住的状态
        store.fixStuckProjectStatus(id);
        ProjectEntity p = store.findProject(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
        List<ChapterEntity> chapters = store.listChapters(id);
        return ResponseEntity.ok(toResponse(p, chapters));
    }

    @GetMapping("/{id}/script.yaml")
    public ResponseEntity<String> downloadScript(@PathVariable long id) {
        ProjectEntity p = store.findProject(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
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
}
