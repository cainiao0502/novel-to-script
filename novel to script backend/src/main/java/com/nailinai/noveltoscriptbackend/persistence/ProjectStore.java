package com.nailinai.noveltoscriptbackend.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nailinai.noveltoscriptbackend.domain.entity.ChapterEntity;
import com.nailinai.noveltoscriptbackend.domain.entity.ChapterStatus;
import com.nailinai.noveltoscriptbackend.domain.entity.ProjectCharacterEntity;
import com.nailinai.noveltoscriptbackend.domain.entity.ProjectEntity;
import com.nailinai.noveltoscriptbackend.domain.entity.ProjectStatus;
import com.nailinai.noveltoscriptbackend.persistence.mapper.ChapterMapper;
import com.nailinai.noveltoscriptbackend.persistence.mapper.ProjectCharacterMapper;
import com.nailinai.noveltoscriptbackend.persistence.mapper.ProjectMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 数据访问门面：MyBatis-Plus 落盘 + Redis 进度缓存。
 */
@Component
public class ProjectStore {

    private static final Duration PROGRESS_TTL = Duration.ofHours(24);
    private static final Duration RESULT_TTL = Duration.ofHours(1);

    private final ProjectMapper projectMapper;
    private final ChapterMapper chapterMapper;
    private final ProjectCharacterMapper characterMapper;
    private final RedisTemplate<String, Object> redis;
    private final ObjectMapper json;

    public ProjectStore(ProjectMapper projectMapper,
                        ChapterMapper chapterMapper,
                        ProjectCharacterMapper characterMapper,
                        RedisTemplate<String, Object> redis,
                        ObjectMapper json) {
        this.projectMapper = projectMapper;
        this.chapterMapper = chapterMapper;
        this.characterMapper = characterMapper;
        this.redis = redis;
        this.json = json;
    }

    // ============ Project ============

    public List<ProjectEntity> listAllProjects() {
        return projectMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ProjectEntity>()
                        .orderByDesc("updated_at"));
    }

    public List<ProjectEntity> listProjectsByUser(Long userId) {
        return projectMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ProjectEntity>()
                        .eq("user_id", userId)
                        .orderByDesc("updated_at"));
    }

    public ProjectEntity createProject(String title, String sourceNovel, String genre, int totalChapters) {
        return createProject(title, sourceNovel, genre, totalChapters, null);
    }

    public ProjectEntity createProject(String title, String sourceNovel, String genre, int totalChapters, Long userId) {
        ProjectEntity p = new ProjectEntity();
        p.setUserId(userId);
        p.setTitle(title);
        p.setSourceNovel(sourceNovel);
        p.setGenre(genre);
        p.setStatus(ProjectStatus.DRAFT.name());
        p.setProgress(0);
        p.setTotalChapters(totalChapters);
        Instant now = Instant.now();
        p.setCreatedAt(now);
        p.setUpdatedAt(now);
        projectMapper.insert(p);
        return p;
    }

    public Optional<ProjectEntity> findProject(long id) {
        return Optional.ofNullable(projectMapper.selectById(id));
    }

    /**
     * 检测并修正卡住的项目状态：如果所有章节都已完成（DONE/FAILED），
     * 但项目状态仍是 GENERATING/PENDING，则自动修正为 COMPLETED 或 PARTIAL_SUCCESS。
     */
    public void fixStuckProjectStatus(long projectId) {
        ProjectEntity p = projectMapper.selectById(projectId);
        if (p == null) return;
        String status = p.getStatus();
        if (!"GENERATING".equals(status) && !"PENDING".equals(status)) return;

        List<ChapterEntity> chapters = listChapters(projectId);
        if (chapters.isEmpty()) return;

        boolean allDone = chapters.stream().allMatch(ch ->
                "DONE".equals(ch.getStatus()) || "FAILED".equals(ch.getStatus()));
        if (!allDone) return;

        long failedCount = chapters.stream()
                .filter(ch -> "FAILED".equals(ch.getStatus())).count();

        if (failedCount == 0) {
            updateProjectStatus(projectId, ProjectStatus.COMPLETED, null);
        } else {
            updateProjectStatus(projectId, ProjectStatus.PARTIAL_SUCCESS,
                    failedCount + " chapter(s) failed; partial result saved");
        }
    }

    public void deleteProject(long id) {
        // 先删除关联的章节和人物
        chapterMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ChapterEntity>()
                        .eq("project_id", id));
        characterMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ProjectCharacterEntity>()
                        .eq("project_id", id));
        // 删除项目本身
        projectMapper.deleteById(id);
        // 清理 Redis 缓存
        redis.delete(progressKey(id));
        redis.delete(resultKey(id));
    }

    public void updateProjectStatus(long id, ProjectStatus status, String errorMessage) {
        ProjectEntity p = new ProjectEntity();
        p.setId(id);
        p.setStatus(status.name());
        p.setErrorMessage(errorMessage);
        p.setUpdatedAt(Instant.now());
        projectMapper.updateById(p);
    }

    public void updateProjectProgress(long id, int progress, Integer currentChapter) {
        ProjectEntity p = new ProjectEntity();
        p.setId(id);
        p.setProgress(progress);
        p.setCurrentChapter(currentChapter);
        p.setUpdatedAt(Instant.now());
        projectMapper.updateById(p);
    }

    public void saveProjectScript(long id, String scriptYaml, String charactersYaml) {
        ProjectEntity p = new ProjectEntity();
        p.setId(id);
        p.setScriptYaml(scriptYaml);
        p.setCharactersYaml(charactersYaml);
        p.setProgress(100);
        p.setStatus(ProjectStatus.COMPLETED.name());
        p.setUpdatedAt(Instant.now());
        projectMapper.updateById(p);
        // 缓存合并后的 YAML
        redis.opsForValue().set(resultKey(id), scriptYaml, RESULT_TTL);
    }

    // ============ Chapter ============

    public ChapterEntity createChapter(long projectId, int idx, String title, String content) {
        ChapterEntity c = new ChapterEntity();
        c.setProjectId(projectId);
        c.setIdx(idx);
        c.setTitle(title);
        c.setContent(content);
        c.setStatus(ChapterStatus.PENDING.name());
        Instant now = Instant.now();
        c.setCreatedAt(now);
        c.setUpdatedAt(now);
        chapterMapper.insert(c);
        return c;
    }

    public List<ChapterEntity> listChapters(long projectId) {
        return chapterMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ChapterEntity>()
                        .eq("project_id", projectId)
                        .orderByAsc("idx"));
    }

    public Optional<ChapterEntity> findChapter(long chapterId) {
        return Optional.ofNullable(chapterMapper.selectById(chapterId));
    }

    public void updateChapterStatus(long chapterId, ChapterStatus status,
                                    String generatedYaml, Integer sceneCount, String errorMessage) {
        ChapterEntity c = new ChapterEntity();
        c.setId(chapterId);
        c.setStatus(status.name());
        c.setGeneratedYaml(generatedYaml);
        c.setSceneCount(sceneCount);
        c.setErrorMessage(errorMessage);
        c.setUpdatedAt(Instant.now());
        chapterMapper.updateById(c);
    }

    public long countChaptersByStatus(long projectId, ChapterStatus status) {
        Long n = chapterMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ChapterEntity>()
                        .eq("project_id", projectId)
                        .eq("status", status.name()));
        return n == null ? 0 : n;
    }

    // ============ Character (denormalized) ============

    public void replaceProjectCharacters(long projectId, List<ProjectCharacterEntity> list) {
        // 简单实现：先删后插（项目级数据量小）
        characterMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ProjectCharacterEntity>()
                        .eq("project_id", projectId));
        for (ProjectCharacterEntity c : list) {
            c.setProjectId(projectId);
            Instant now = Instant.now();
            c.setCreatedAt(now);
            c.setUpdatedAt(now);
            characterMapper.insert(c);
        }
    }

    public List<ProjectCharacterEntity> listProjectCharacters(long projectId) {
        return characterMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ProjectCharacterEntity>()
                        .eq("project_id", projectId)
                        .orderByAsc("char_id"));
    }

    /** 把 character 列表导出成 YAML 字符串（用 ObjectMapper 转 JSON，简化实现）。 */
    public String charactersToJson(List<ProjectCharacterEntity> list) {
        try {
            return json.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    public void updateChapterYaml(long chapterId, String yaml) {
        ChapterEntity c = new ChapterEntity();
        c.setId(chapterId);
        c.setGeneratedYaml(yaml);
        c.setUpdatedAt(Instant.now());
        chapterMapper.updateById(c);
    }

    public void updateProjectScriptYaml(long id, String yaml) {
        ProjectEntity p = new ProjectEntity();
        p.setId(id);
        p.setScriptYaml(yaml);
        p.setUpdatedAt(Instant.now());
        projectMapper.updateById(p);
    }

    // ============ Redis progress ============

    public void cacheProgress(long projectId, int progress, int currentChapter, int totalChapters) {
        Map<String, Object> map = new HashMap<>();
        map.put("progress", progress);
        map.put("currentChapter", currentChapter);
        map.put("totalChapters", totalChapters);
        map.put("updatedAt", Instant.now().toString());
        redis.opsForHash().putAll(progressKey(projectId), map);
        redis.expire(progressKey(projectId), PROGRESS_TTL);
    }

    public Map<Object, Object> getCachedProgress(long projectId) {
        return redis.opsForHash().entries(progressKey(projectId));
    }

    public String getCachedResult(long projectId) {
        Object obj = redis.opsForValue().get(resultKey(projectId));
        return obj == null ? null : obj.toString();
    }

    public void putIdempotency(String token) {
        redis.opsForValue().set("idempotency:gen:" + token, "1", Duration.ofMinutes(5));
    }

    public boolean takeIdempotency(String token) {
        Boolean ok = redis.opsForValue().setIfAbsent("idempotency:gen:" + token, "1", Duration.ofMinutes(5));
        return Boolean.TRUE.equals(ok);
    }

    private String progressKey(long id) { return "project:progress:" + id; }
    private String resultKey(long id)   { return "project:result:" + id; }
}
