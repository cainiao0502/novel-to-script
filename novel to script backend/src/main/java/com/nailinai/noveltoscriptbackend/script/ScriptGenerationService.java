package com.nailinai.noveltoscriptbackend.script;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nailinai.noveltoscriptbackend.config.LlmProperties;
import com.nailinai.noveltoscriptbackend.domain.entity.ChapterEntity;
import com.nailinai.noveltoscriptbackend.domain.entity.ChapterStatus;
import com.nailinai.noveltoscriptbackend.domain.entity.ProjectCharacterEntity;
import com.nailinai.noveltoscriptbackend.domain.entity.ProjectEntity;
import com.nailinai.noveltoscriptbackend.domain.entity.ProjectStatus;
import com.nailinai.noveltoscriptbackend.domain.script.Character;
import com.nailinai.noveltoscriptbackend.domain.script.Script;
import com.nailinai.noveltoscriptbackend.llm.LlmClient;
import com.nailinai.noveltoscriptbackend.llm.LlmException;
import com.nailinai.noveltoscriptbackend.llm.PromptBuilder;
import com.nailinai.noveltoscriptbackend.persistence.ProjectStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

/**
 * 剧本生成编排：
 *   1. 逐章并发调用 LLM（信号量限流）
 *   2. 每章输出经 ScriptValidator 校验；失败一次自动重试
 *   3. 全部完成后由 ScriptMerger 合并 → 写入 Project
 *   4. 进度：每完成一章 → 更新 Project.progress 与 Redis 缓存
 */
@Service
public class ScriptGenerationService {

    private static final Logger log = LoggerFactory.getLogger(ScriptGenerationService.class);

    private final LlmClient llmClient;
    private final PromptBuilder promptBuilder;
    private final ScriptYamlMapper yamlMapper;
    private final ScriptValidator validator;
    private final ScriptMerger merger;
    private final ProjectStore store;
    private final LlmProperties props;
    private final ObjectMapper json;

    public ScriptGenerationService(LlmClient llmClient,
                                   PromptBuilder promptBuilder,
                                   ScriptYamlMapper yamlMapper,
                                   ScriptValidator validator,
                                   ScriptMerger merger,
                                   ProjectStore store,
                                   LlmProperties props,
                                   ObjectMapper json) {
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.yamlMapper = yamlMapper;
        this.validator = validator;
        this.merger = merger;
        this.store = store;
        this.props = props;
        this.json = json;
    }

    @Async("llmExecutor")
    public void generateAsync(long projectId) {
        try {
            generate(projectId);
        } catch (Exception e) {
            log.error("Generation crashed for project {}", projectId, e);
            store.updateProjectStatus(projectId, ProjectStatus.FAILED, e.getMessage());
            store.cacheProgress(projectId, store.findProject(projectId)
                    .map(p -> p.getProgress() == null ? 0 : p.getProgress()).orElse(0),
                    0,
                    store.findProject(projectId).map(ProjectEntity::getTotalChapters).orElse(0));
        }
    }

    @Async("llmExecutor")
    public void regenerateSingleChapterAsync(long projectId, long chapterId) {
        try {
            regenerateSingleChapter(projectId, chapterId);
        } catch (Exception e) {
            log.error("Single chapter regeneration crashed for project {} chapter {}", projectId, chapterId, e);
            store.updateChapterStatus(chapterId, ChapterStatus.FAILED, null, null, e.getMessage());
            store.updateProjectStatus(projectId, ProjectStatus.PARTIAL_SUCCESS,
                    "Chapter regeneration failed: " + e.getMessage());
        }
    }

    public void generate(long projectId) {
        ProjectEntity project = store.findProject(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        List<ChapterEntity> chapters = store.listChapters(projectId);

        store.updateProjectStatus(projectId, ProjectStatus.GENERATING, null);
        store.cacheProgress(projectId, 0, 0, chapters.size());

        Semaphore semaphore = new Semaphore(props.getMaxConcurrent());
        List<CompletableFuture<Script>> futures = new ArrayList<>();
        List<Character> knownChars = new ArrayList<>();
        knownChars.add(new Character(Character.NARRATOR_ID, "旁白", null, null, null,
                com.nailinai.noveltoscriptbackend.domain.script.Role.NPC, null, null));

        // 为保持人物表单调累积，我们顺序启动 future（实际并发由 LLM 调用决定），
        // 但每章内的 LLM 调用是异步的。
        for (ChapterEntity ch : chapters) {
            final List<Character> snapshot = List.copyOf(knownChars);
            final long chapterId = ch.getId();
            final int idx = ch.getIdx();
            final String title = ch.getTitle();
            final String content = ch.getContent();
            final int total = chapters.size();

            store.updateChapterStatus(chapterId, ChapterStatus.GENERATING, null, null, null);
            store.updateProjectProgress(projectId, computeProgress(idx, total, false), idx);
            store.cacheProgress(projectId, computeProgress(idx, total, false), idx, total);

            CompletableFuture<Script> f = CompletableFuture.supplyAsync(() -> {
                try {
                    semaphore.acquire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
                try {
                    Script s = generateOneChapter(title, idx, total, content, snapshot);
                    store.updateChapterStatus(chapterId, ChapterStatus.DONE,
                            yamlMapper.toYaml(s),
                            s.scenes() == null ? 0 : s.scenes().size(),
                            null);
                    if (s.characters() != null) {
                        synchronized (knownChars) {
                            Set<String> existing = knownChars.stream()
                                    .map(Character::id).collect(Collectors.toSet());
                            for (Character c : s.characters()) {
                                if (c.id() != null && !existing.contains(c.id())) {
                                    knownChars.add(c);
                                }
                            }
                        }
                    }
                    return s;
                } catch (Exception e) {
                    log.warn("Chapter {} generation failed: {}", idx, e.getMessage());
                    store.updateChapterStatus(chapterId, ChapterStatus.FAILED,
                            null, null, e.getMessage());
                    return null;
                } finally {
                    semaphore.release();
                }
            }, runnable -> Thread.ofPlatform().name("gen-ch-" + idx).start(runnable));

            futures.add(f);
        }

        // 等待所有完成
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

        // 收集成功的章节并合并
        List<Script> perChapter = futures.stream()
                .map(CompletableFuture::join)
                .filter(s -> s != null)
                .toList();

        long failed = futures.stream().filter(f -> f.join() == null).count();

        if (perChapter.isEmpty()) {
            store.updateProjectStatus(projectId, ProjectStatus.FAILED, "All chapters failed");
            return;
        }

        Script merged = merger.merge(
                project.getTitle(), project.getSourceNovel(), project.getGenre(), perChapter);

        String mergedYaml = yamlMapper.toYaml(merged);

        // 写人物表
        List<ProjectCharacterEntity> characterRows = merged.characters() == null ? List.of()
                : merged.characters().stream().map(c -> {
                    ProjectCharacterEntity row = new ProjectCharacterEntity();
                    row.setCharId(c.id());
                    row.setName(c.name());
                    row.setRole(c.role() == null ? null : c.role().yamlValue());
                    try {
                        row.setFullDataJson(json.writeValueAsString(c));
                    } catch (Exception e) {
                        row.setFullDataJson("{}");
                    }
                    return row;
                }).toList();
        store.replaceProjectCharacters(projectId, characterRows);

        store.saveProjectScript(projectId, mergedYaml, store.charactersToJson(characterRows));

        store.cacheProgress(projectId, 100, chapters.size(), chapters.size());

        if (failed > 0) {
            store.updateProjectStatus(projectId, ProjectStatus.PARTIAL_SUCCESS,
                    failed + " chapter(s) failed; partial result saved");
        } else {
            store.updateProjectStatus(projectId, ProjectStatus.COMPLETED, null);
        }

        log.info("Project {} generated: {} chapters ok, {} failed",
                projectId, perChapter.size(), failed);
    }

    /**
     * 仅重新生成指定章节，然后重新合并全部脚本。
     */
    public void regenerateSingleChapter(long projectId, long chapterId) {
        ProjectEntity project = store.findProject(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        List<ChapterEntity> allChapters = store.listChapters(projectId);

        // 找到目标章节
        ChapterEntity target = allChapters.stream()
                .filter(c -> c.getId().equals(chapterId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Chapter not found: " + chapterId));

        store.updateProjectStatus(projectId, ProjectStatus.GENERATING, null);
        store.updateChapterStatus(chapterId, ChapterStatus.GENERATING, null, null, null);
        store.cacheProgress(projectId, 0, target.getIdx(), allChapters.size());

        // 从已完成章节收集已知人物
        List<Character> knownChars = new ArrayList<>();
        knownChars.add(new Character(Character.NARRATOR_ID, "旁白", null, null, null,
                com.nailinai.noveltoscriptbackend.domain.script.Role.NPC, null, null));
        for (ChapterEntity ch : allChapters) {
            if (ch.getId().equals(chapterId)) continue;
            if (ch.getStatus() != null && ch.getStatus().equals(ChapterStatus.DONE.name())
                    && ch.getGeneratedYaml() != null) {
                try {
                    Script s = yamlMapper.fromYaml(ch.getGeneratedYaml());
                    if (s.characters() != null) {
                        Set<String> existing = knownChars.stream()
                                .map(Character::id).collect(Collectors.toSet());
                        for (Character c : s.characters()) {
                            if (c.id() != null && !existing.contains(c.id())) {
                                knownChars.add(c);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse existing chapter {} YAML: {}", ch.getIdx(), e.getMessage());
                }
            }
        }

        // 生成指定章节
        try {
            Script newScript = generateOneChapter(
                    target.getTitle(), target.getIdx(), allChapters.size(),
                    target.getContent(), knownChars);
            store.updateChapterStatus(chapterId, ChapterStatus.DONE,
                    yamlMapper.toYaml(newScript),
                    newScript.scenes() == null ? 0 : newScript.scenes().size(), null);
        } catch (Exception e) {
            log.warn("Single chapter {} regeneration failed: {}", target.getIdx(), e.getMessage());
            store.updateChapterStatus(chapterId, ChapterStatus.FAILED, null, null, e.getMessage());
            store.updateProjectStatus(projectId, ProjectStatus.PARTIAL_SUCCESS,
                    "Chapter " + target.getIdx() + " regeneration failed: " + e.getMessage());
            return;
        }

        // 重新合并全部章节
        remergeProject(projectId, project, allChapters);
    }

    /**
     * 重新合并所有已完成章节的 YAML，更新项目脚本和人物表。
     */
    private void remergeProject(long projectId, ProjectEntity project, List<ChapterEntity> allChapters) {
        // 重新加载最新章节状态
        List<ChapterEntity> freshChapters = store.listChapters(projectId);

        List<Script> perChapter = new ArrayList<>();
        for (ChapterEntity ch : freshChapters) {
            if (ChapterStatus.DONE.name().equals(ch.getStatus()) && ch.getGeneratedYaml() != null) {
                try {
                    perChapter.add(yamlMapper.fromYaml(ch.getGeneratedYaml()));
                } catch (Exception e) {
                    log.warn("Failed to parse chapter {} YAML during merge: {}", ch.getIdx(), e.getMessage());
                }
            }
        }

        long failed = freshChapters.stream()
                .filter(c -> ChapterStatus.FAILED.name().equals(c.getStatus()))
                .count();

        if (perChapter.isEmpty()) {
            store.updateProjectStatus(projectId, ProjectStatus.FAILED, "All chapters failed");
            return;
        }

        Script merged = merger.merge(
                project.getTitle(), project.getSourceNovel(), project.getGenre(), perChapter);
        String mergedYaml = yamlMapper.toYaml(merged);

        // 更新人物表
        List<ProjectCharacterEntity> characterRows = merged.characters() == null ? List.of()
                : merged.characters().stream().map(c -> {
                    ProjectCharacterEntity row = new ProjectCharacterEntity();
                    row.setCharId(c.id());
                    row.setName(c.name());
                    row.setRole(c.role() == null ? null : c.role().yamlValue());
                    try {
                        row.setFullDataJson(json.writeValueAsString(c));
                    } catch (Exception e) {
                        row.setFullDataJson("{}");
                    }
                    return row;
                }).toList();
        store.replaceProjectCharacters(projectId, characterRows);
        store.saveProjectScript(projectId, mergedYaml, store.charactersToJson(characterRows));

        if (failed > 0) {
            store.updateProjectStatus(projectId, ProjectStatus.PARTIAL_SUCCESS,
                    failed + " chapter(s) failed; partial result saved");
        } else {
            store.updateProjectStatus(projectId, ProjectStatus.COMPLETED, null);
        }
    }

    private Script generateOneChapter(String title, int idx, int total, String content,
                                      List<Character> known) {
        Map<String, String> prompts = promptBuilder.chapterPrompt(title, idx, total, content, known);

        // 第一次尝试
        String output = safeChat(prompts.get("system"), prompts.get("user"));
        ScriptValidator.ValidationResult vr = validator.validateYaml(output);
        if (vr.isOk()) {
            return yamlMapper.fromYaml(output);
        }
        log.warn("Chapter {} first attempt failed: {}", idx, vr.summary());
        // 第二次：附错误明细
        String retryUser = prompts.get("user")
                + "\n\n# 上次输出不合规，请修正\n# 错误：\n" + vr.summary();
        String output2 = safeChat(prompts.get("system"), retryUser);
        ScriptValidator.ValidationResult vr2 = validator.validateYaml(output2);
        if (vr2.isOk()) {
            return yamlMapper.fromYaml(output2);
        }
        throw new ScriptException("Chapter " + idx + " validation failed after retry: " + vr2.summary());
    }

    private String safeChat(String system, String user) {
        try {
            return llmClient.chat(system, user);
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("Unexpected LLM error: " + e.getMessage(), e);
        }
    }

    private int computeProgress(int currentIdx, int total, boolean finished) {
        if (finished || currentIdx >= total) return 100;
        return (int) Math.floor((currentIdx - 1) * 100.0 / total);
    }
}
