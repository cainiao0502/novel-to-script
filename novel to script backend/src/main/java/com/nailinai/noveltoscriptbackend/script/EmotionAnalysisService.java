package com.nailinai.noveltoscriptbackend.script;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nailinai.noveltoscriptbackend.domain.script.Character;
import com.nailinai.noveltoscriptbackend.domain.script.Dialogue;
import com.nailinai.noveltoscriptbackend.domain.script.Role;
import com.nailinai.noveltoscriptbackend.domain.script.Scene;
import com.nailinai.noveltoscriptbackend.domain.script.Script;
import com.nailinai.noveltoscriptbackend.llm.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 全剧情绪曲线分析 — 按角色维度。
 * 每个角色生成一条情绪弧线：在每场戏中的情绪强度 1-10。
 * 结果以 JSON 缓存在 Redis，供前端 Chart.js 消费。
 */
@Service
public class EmotionAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(EmotionAnalysisService.class);
    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final String CACHE_VERSION = "v2";

    private static final int ROLE_PRIORITY_PROTAGONIST = 0;
    private static final int ROLE_PRIORITY_ANTAGONIST = 1;
    private static final int ROLE_PRIORITY_SUPPORTING = 2;
    private static final int ROLE_PRIORITY_NPC = 3;

    private final LlmClient llmClient;
    private final ScriptYamlMapper yamlMapper;
    private final RedisTemplate<String, Object> redis;
    private final ObjectMapper json;
    private final ObjectMapper lenientYamlMapper;

    public EmotionAnalysisService(LlmClient llmClient,
                                  ScriptYamlMapper yamlMapper,
                                  RedisTemplate<String, Object> redis,
                                  ObjectMapper json) {
        this.llmClient = llmClient;
        this.yamlMapper = yamlMapper;
        this.redis = redis;
        this.json = json;
        YAMLFactory yamlFactory = new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        this.lenientYamlMapper = new ObjectMapper(yamlFactory)
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    private static final String SYSTEM_PROMPT = """
            你是一名资深剧本情绪分析师。
            以下是全剧的人物表和场景序列。请为**每个角色**分析其在**每场戏**中的情绪状态。

            严格约束：
            1. 只输出合法 JSON 数组，不要包裹 ```json 等标记，不要任何前言后语
            2. 顶层是一个数组，每个元素代表一个角色，字段：
               char_id (string)、char_name (string)、role (string)、
               emotions (数组，一场戏一个)
            3. emotions 数组中每个元素字段：
               scene_id (string)、intensity (整数 1-10)、dominant_emotion (中文情绪词)、tags (字符串数组)
            4. intensity 含义：1=极其平静/舒缓/未出场时，5=中等张力，10=爆发/高潮
            5. 如果角色在某场戏中未出场，intensity 填 0，dominant_emotion 填 "未出场"
            6. dominant_emotion 选取最贴切的中文情绪词，如：悬疑、愤怒、温馨、悲伤、紧张、幽默、压抑、激昂、恐惧、期待、冷静、忧虑
            7. 请感知每个角色的独立情绪弧线：主角应有完整弧线，配角只需在有戏份的场景标注
            8. 输出顺序必须与输入的人物表顺序一致
            """;

    /**
     * 按角色分析全剧情绪。
     *
     * @param scriptYaml 完整的剧本 YAML
     * @return 角色情绪弧线列表（按 role 优先级排序）
     */
    public List<EmotionArc> analyze(String scriptYaml) {
        Script script = parseLenient(scriptYaml);
        return analyze(script);
    }

    private Script parseLenient(String yaml) {
        try {
            return lenientYamlMapper.readValue(yaml, Script.class);
        } catch (Exception e) {
            log.error("Even lenient YAML parsing failed: {}", e.getMessage());
            throw new ScriptException("Cannot parse script YAML for emotion analysis: " + e.getMessage(), e);
        }
    }

    public List<EmotionArc> analyze(Script script) {
        List<Character> characters = script.characters();
        List<Scene> scenes = script.scenes();
        if (characters == null || characters.isEmpty()) return List.of();
        if (scenes == null || scenes.isEmpty()) return List.of();

        // 排除 narrator（旁白不需要情绪曲线），除非剧本只有 narrator
        List<Character> analyzable = characters.stream()
                .filter(c -> !Character.NARRATOR_ID.equals(c.id()))
                .toList();
        if (analyzable.isEmpty()) {
            analyzable = characters;
        }

        String userPrompt = buildPrompt(analyzable, scenes);

        // 估计所需 token：每角色每场约 200 tokens
        int maxTokens = Math.max(8192, analyzable.size() * scenes.size() * 220);

        log.info("Analyzing per-character emotions: {} characters × {} scenes (maxTokens={})",
                analyzable.size(), scenes.size(), maxTokens);

        String output = llmClient.chat(SYSTEM_PROMPT, userPrompt, maxTokens);

        List<LlmEmotionArc> parsed;
        try {
            parsed = parseResponse(output);
        } catch (ScriptException firstError) {
            log.warn("First parse attempt failed: {}. Retrying with fix prompt...",
                    firstError.getMessage());
            // 附上截断的错误输出，让 LLM 修正 JSON
            String tail = output.length() > 300
                    ? "…" + output.substring(output.length() - 300)
                    : output;
            String retryUser = userPrompt
                    + "\n\n# ⚠ 上次输出的 JSON 有语法错误（截断/缺逗号/未闭合）。请修正后重新输出完整 JSON 数组。\n"
                    + "# 错误输出末尾：\n# " + tail.replace("\n", "\n# ");
            String output2 = llmClient.chat(SYSTEM_PROMPT, retryUser, maxTokens);
            parsed = parseResponse(output2);
        }

        parsed.sort(Comparator.comparingInt(this::rolePriority));
        return parsed.stream().map(EmotionAnalysisService::toWire).toList();
    }

    /**
     * 缓存结果并返回。
     */
    public List<EmotionArc> analyzeAndCache(long projectId, String scriptYaml) {
        List<EmotionArc> result = analyze(scriptYaml);
        String cacheKey = cacheKey(projectId);
        try {
            redis.opsForValue().set(cacheKey, json.writeValueAsString(result), CACHE_TTL);
        } catch (Exception e) {
            log.warn("Failed to cache emotion analysis for project {}: {}", projectId, e.getMessage());
        }
        return result;
    }

    /**
     * 从缓存获取，null 表示未分析。
     */
    public List<EmotionArc> getCached(long projectId) {
        String cacheKey = cacheKey(projectId);
        Object cached = redis.opsForValue().get(cacheKey);
        if (cached == null) return null;
        try {
            return json.readValue(cached.toString(), new TypeReference<List<EmotionArc>>() {});
        } catch (Exception e) {
            log.warn("Failed to deserialize cached emotion analysis: {}", e.getMessage());
            return null;
        }
    }

    // ── Chapter-level cache ──

    public List<EmotionArc> analyzeChapterAndCache(long chapterId, String yaml) {
        List<EmotionArc> result = analyze(yaml);
        String cacheKey = chapterCacheKey(chapterId);
        try {
            redis.opsForValue().set(cacheKey, json.writeValueAsString(result), CACHE_TTL);
        } catch (Exception e) {
            log.warn("Failed to cache chapter emotion analysis for chapter {}: {}", chapterId, e.getMessage());
        }
        return result;
    }

    public List<EmotionArc> getCachedChapter(long chapterId) {
        String cacheKey = chapterCacheKey(chapterId);
        Object cached = redis.opsForValue().get(cacheKey);
        if (cached == null) return null;
        try {
            return json.readValue(cached.toString(), new TypeReference<List<EmotionArc>>() {});
        } catch (Exception e) {
            log.warn("Failed to deserialize cached chapter emotion analysis: {}", e.getMessage());
            return null;
        }
    }

    // ── Prompt building ──

    private String buildPrompt(List<Character> characters, List<Scene> scenes) {
        StringBuilder sb = new StringBuilder();

        // 人物表
        sb.append("# 人物表\n");
        for (Character c : characters) {
            sb.append("- ").append(c.id()).append(": ").append(c.name());
            if (c.role() != null) sb.append(" (").append(roleLabel(c.role())).append(")");
            sb.append("\n");
        }

        // 场景序列
        sb.append("\n# 场景序列\n");
        for (int i = 0; i < scenes.size(); i++) {
            Scene s = scenes.get(i);
            sb.append("---\n");
            sb.append("scene_id: ").append(s.sceneId()).append("\n");
            sb.append("序号: 第").append(i + 1).append("场\n");
            sb.append("地点: ").append(s.location() != null ? s.location() : "未知").append("\n");
            sb.append("概要: ").append(s.summary() != null ? s.summary() : "").append("\n");

            // 出场人物
            if (s.characters() != null && !s.characters().isEmpty()) {
                sb.append("出场: ");
                sb.append(s.characters().stream()
                        .map(cid -> charName(characters, cid))
                        .collect(Collectors.joining("、")));
                sb.append("\n");
            }

            // 前 2 条动作
            if (s.actions() != null && !s.actions().isEmpty()) {
                sb.append("动作: ");
                sb.append(s.actions().stream().limit(2).collect(Collectors.joining("；")));
                sb.append("\n");
            }

            // 对白摘要（带说话人）
            if (s.dialogues() != null && !s.dialogues().isEmpty()) {
                sb.append("对白: ");
                sb.append(s.dialogues().stream()
                        .limit(6)
                        .map(d -> charName(characters, d.character()) + "：" + d.line()
                                + (d.emotion() != null ? "（" + d.emotion() + "）" : ""))
                        .collect(Collectors.joining(" | ")));
                sb.append("\n");
            }
        }

        sb.append("---\n");
        sb.append("请为以上 ").append(characters.size()).append(" 个角色 × ")
                .append(scenes.size()).append(" 场戏输出情绪分析 JSON 数组。");
        return sb.toString();
    }

    // ── Helpers ──

    private String charName(List<Character> characters, String charId) {
        if (charId == null) return "?";
        return characters.stream()
                .filter(c -> charId.equals(c.id()))
                .findFirst()
                .map(c -> c.name() != null ? c.name() : charId)
                .orElse(charId);
    }

    private String roleLabel(Role role) {
        return switch (role) {
            case PROTAGONIST -> "主角";
            case ANTAGONIST -> "反派";
            case SUPPORTING -> "配角";
            case NPC -> "NPC";
        };
    }

    private int rolePriority(LlmEmotionArc arc) {
        return switch (arc.role() == null ? "" : arc.role()) {
            case "protagonist" -> ROLE_PRIORITY_PROTAGONIST;
            case "antagonist" -> ROLE_PRIORITY_ANTAGONIST;
            case "supporting" -> ROLE_PRIORITY_SUPPORTING;
            default -> ROLE_PRIORITY_NPC;
        };
    }

    private static EmotionArc toWire(LlmEmotionArc src) {
        if (src == null) return null;
        List<EmotionPoint> pts = src.emotions() == null
                ? List.of()
                : src.emotions().stream()
                        .map(EmotionAnalysisService::toWire)
                        .toList();
        return new EmotionArc(src.charId(), src.charName(), src.role(), pts);
    }

    private static EmotionPoint toWire(LlmEmotionPoint src) {
        if (src == null) return null;
        return new EmotionPoint(
                src.sceneId(),
                src.intensity(),
                src.dominantEmotion(),
                src.tags() == null ? List.of() : src.tags()
        );
    }

    // ── JSON parsing ──

    private List<LlmEmotionArc> parseResponse(String output) {
        String trimmed = output.trim();
        // Strip possible markdown fences
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf("\n");
            int end = trimmed.lastIndexOf("```");
            if (start >= 0 && end > start) {
                trimmed = trimmed.substring(start, end).trim();
            }
        }
        try {
            return json.readValue(trimmed, new TypeReference<List<LlmEmotionArc>>() {});
        } catch (Exception e) {
            log.error("Failed to parse emotion analysis response (first 300 chars): {}",
                    trimmed.substring(0, Math.min(300, trimmed.length())));
            throw new ScriptException("Failed to parse emotion analysis: " + e.getMessage(), e);
        }
    }

    private String cacheKey(long projectId) {
        return "emotion:analysis:" + CACHE_VERSION + ":" + projectId;
    }

    private String chapterCacheKey(long chapterId) {
        return "emotion:chapter:analysis:" + CACHE_VERSION + ":" + chapterId;
    }

    // ── Data records ──

    /**
     * LLM 响应解析用：snake_case 字段映射到 Java camelCase。
     * 仅在 parseResponse 内部使用，不直接对外暴露。
     */
    public record LlmEmotionArc(
            @JsonProperty("char_id") String charId,
            @JsonProperty("char_name") String charName,
            @JsonProperty("role") String role,
            @JsonProperty("emotions") List<LlmEmotionPoint> emotions
    ) {}

    public record LlmEmotionPoint(
            @JsonProperty("scene_id") String sceneId,
            @JsonProperty("intensity") int intensity,
            @JsonProperty("dominant_emotion") String dominantEmotion,
            @JsonProperty("tags") List<String> tags
    ) {}

    /**
     * 对外 API 输出：camelCase 字段，与项目其他 DTO 保持一致。
     */
    public record EmotionArc(
            String charId,
            String charName,
            String role,
            List<EmotionPoint> emotions
    ) {}

    public record EmotionPoint(
            String sceneId,
            int intensity,
            String dominantEmotion,
            List<String> tags
    ) {}
}
