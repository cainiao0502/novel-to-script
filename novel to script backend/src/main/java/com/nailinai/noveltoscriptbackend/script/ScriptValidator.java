package com.nailinai.noveltoscriptbackend.script;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.nailinai.noveltoscriptbackend.domain.script.Character;
import com.nailinai.noveltoscriptbackend.domain.script.Dialogue;
import com.nailinai.noveltoscriptbackend.domain.script.Script;
import com.nailinai.noveltoscriptbackend.domain.script.Scene;
import com.nailinai.noveltoscriptbackend.domain.script.Voiceover;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 两阶段校验：
 *   Stage 1: JSON Schema（字段类型、枚举、格式、additionalProperties）
 *   Stage 2: 业务规则（ID 唯一性、跨字段引用完整性、narrator 必含）
 */
@Component
public class ScriptValidator {

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final JsonSchema schema;
    private final ScriptYamlMapper yamlMapper;
    private final ObjectMapper yamlObjectMapper;

    public ScriptValidator(ScriptYamlMapper yamlMapper) {
        this.yamlMapper = yamlMapper;
        this.yamlObjectMapper = yamlMapper.getYamlMapper();
        try (InputStream in = new ClassPathResource("script-v1.0.schema.json").getInputStream()) {
            JsonNode schemaNode = jsonMapper.readTree(in);
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
            this.schema = factory.getSchema(schemaNode);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load script schema", e);
        }
    }

    /** 直接校验 Script 对象。 */
    public ValidationResult validate(Script script) {
        ValidationResult result = new ValidationResult();
        try {
            String yaml = yamlMapper.toYaml(script);
            validateYaml(yaml, result);
        } catch (ScriptException e) {
            result.addError(e.getMessage());
        }
        validateBusinessRules(script, result);
        return result;
    }

    /** 校验原始 YAML 字符串。LLM 输出走这条路径。 */
    public ValidationResult validateYaml(String yaml) {
        ValidationResult result = new ValidationResult();
        validateYaml(yaml, result);
        if (result.isOk()) {
            try {
                Script script = yamlMapper.fromYaml(yaml);
                validateBusinessRules(script, result);
            } catch (ScriptException e) {
                result.addError(e.getMessage());
            }
        }
        return result;
    }

    private void validateYaml(String yaml, ValidationResult result) {
        try {
            // YAML → JSON tree（用 Jackson YAML ObjectMapper 读为 JsonNode）
            JsonNode node = yamlObjectMapper.readTree(yaml);
            Set<ValidationMessage> errors = schema.validate(node);
            for (ValidationMessage msg : errors) {
                result.addError(msg.getMessage());
            }
        } catch (JsonProcessingException e) {
            result.addError("YAML parse error: " + e.getOriginalMessage());
        } catch (IOException e) {
            result.addError("YAML read error: " + e.getMessage());
        }
    }

    private void validateBusinessRules(Script script, ValidationResult result) {
        if (script.characters() == null || script.characters().isEmpty()) {
            result.addError("Business: characters must not be empty");
            return;
        }
        if (script.scenes() == null || script.scenes().isEmpty()) {
            result.addError("Business: scenes must not be empty");
            return;
        }

        // 1. 人物 ID 唯一
        Set<String> charIds = new HashSet<>();
        for (Character c : script.characters()) {
            if (c.id() == null || c.id().isBlank()) {
                result.addError("Business: character missing id");
                continue;
            }
            if (!charIds.add(c.id())) {
                result.addError("Business: duplicate character id: " + c.id());
            }
        }

        // 2. 必含 narrator
        if (!charIds.contains(Character.NARRATOR_ID)) {
            result.addWarn("Business: missing narrator character, will be auto-injected");
        }

        // 3. 场景 ID 唯一
        Set<String> sceneIds = new HashSet<>();
        for (Scene s : script.scenes()) {
            if (s.sceneId() == null || s.sceneId().isBlank()) {
                result.addError("Business: scene missing scene_id");
                continue;
            }
            if (!sceneIds.add(s.sceneId())) {
                result.addError("Business: duplicate scene_id: " + s.sceneId());
            }
        }

        // 4. 跨字段引用
        for (Scene s : script.scenes()) {
            if (s.characters() != null) {
                for (String ref : s.characters()) {
                    if (!charIds.contains(ref)) {
                        result.addError("Business: scene " + s.sceneId()
                                + " references unknown character: " + ref);
                    }
                }
            }
            checkDialogueVoRefs(s.dialogues(), s.voiceover(), s.sceneId(), charIds, result);
        }
    }

    private void checkDialogueVoRefs(List<Dialogue> dialogues, List<Voiceover> voiceover,
                                     String sceneId, Set<String> charIds, ValidationResult result) {
        if (dialogues != null) {
            for (Dialogue d : dialogues) {
                if (d.character() == null || !charIds.contains(d.character())) {
                    result.addError("Business: scene " + sceneId
                            + " dialogue references unknown character: " + d.character());
                }
            }
        }
        if (voiceover != null) {
            for (Voiceover v : voiceover) {
                if (v.character() == null || !charIds.contains(v.character())) {
                    result.addError("Business: scene " + sceneId
                            + " voiceover references unknown character: " + v.character());
                }
            }
        }
    }

    public static class ValidationResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        public void addError(String e) { errors.add(e); }
        public void addWarn(String w) { warnings.add(w); }

        public boolean isOk() { return errors.isEmpty(); }
        public List<String> getErrors() { return List.copyOf(errors); }
        public List<String> getWarnings() { return List.copyOf(warnings); }

        public String summary() {
            if (isOk() && warnings.isEmpty()) return "OK";
            StringBuilder sb = new StringBuilder();
            if (!errors.isEmpty()) {
                sb.append("errors: ").append(errors.size()).append('\n');
                sb.append(errors.stream().collect(Collectors.joining("\n  - ", "  - ", "")));
            }
            if (!warnings.isEmpty()) {
                if (sb.length() > 0) sb.append('\n');
                sb.append("warnings: ").append(warnings.size()).append('\n');
                sb.append(warnings.stream().collect(Collectors.joining("\n  - ", "  - ", "")));
            }
            return sb.toString();
        }
    }

    /**
     * 判断一段 YAML 文本是否疑似被 max_tokens 截断。
     * 判定规则（任一命中即视为截断）：
     *   1. 末尾双引号数量为奇数（未闭合的标量）
     *   2. 最后一行未以合法的 YAML 结束符结尾（值/键/列表项缺尾巴）
     */
    public static boolean looksTruncated(String yaml) {
        if (yaml == null || yaml.isBlank()) return true;
        String stripped = stripCommentLines(yaml);
        if (looksUnclosedQuotedString(stripped)) return true;
        return !endsCleanly(stripped);
    }

    private static String stripCommentLines(String yaml) {
        StringBuilder sb = new StringBuilder();
        for (String line : yaml.split("\n", -1)) {
            String t = line.trim();
            if (t.startsWith("#")) continue;
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private static boolean looksUnclosedQuotedString(String yaml) {
        boolean inSingle = false;
        boolean inDouble = false;
        boolean escape = false;
        for (int i = 0; i < yaml.length(); i++) {
            char c = yaml.charAt(i);
            if (escape) { escape = false; continue; }
            if (c == '\\' && inDouble) { escape = true; continue; }
            if (!inSingle && c == '"') { inDouble = !inDouble; }
            else if (!inDouble && c == '\'') { inSingle = !inSingle; }
        }
        return inDouble || inSingle;
    }

    private static boolean endsCleanly(String yaml) {
        String trimmed = yaml.stripTrailing();
        if (trimmed.isEmpty()) return true;
        char last = trimmed.charAt(trimmed.length() - 1);
        // 合法收尾：换行已被 strip；这里判断的是最后一个可见字符
        return last == '"' || last == '\'' || last == ']' || last == '}'
                || last == ':' || last == '-' || java.lang.Character.isLetterOrDigit(last)
                || isCjk(last);
    }

    private static boolean isCjk(char c) {
        java.lang.Character.UnicodeBlock b = java.lang.Character.UnicodeBlock.of(c);
        return b == java.lang.Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || b == java.lang.Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || b == java.lang.Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS;
    }
}
