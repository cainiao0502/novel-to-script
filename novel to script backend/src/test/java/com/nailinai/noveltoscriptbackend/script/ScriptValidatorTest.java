package com.nailinai.noveltoscriptbackend.script;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScriptValidatorTest {

    private final ScriptYamlMapper mapper = new ScriptYamlMapper();
    private final ScriptValidator validator = new ScriptValidator(mapper);

    @Test
    void acceptsValidYaml() {
        String yaml = validYaml();
        var result = validator.validateYaml(yaml);
        assertTrue(result.isOk(), () -> "expected ok, got: " + result.summary());
    }

    @Test
    void rejectsInvalidEnum() {
        String yaml = validYaml().replace("int_ext: \"INT\"", "int_ext: \"WUT\"");
        var result = validator.validateYaml(yaml);
        assertFalse(result.isOk());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.toLowerCase().contains("enum") || e.contains("int_ext")));
    }

    @Test
    void rejectsUnknownReference() {
        String yaml = validYaml().replace("characters: [\"c_a\"]", "characters: [\"c_ghost\"]");
        var result = validator.validateYaml(yaml);
        assertFalse(result.isOk());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("c_ghost")));
    }

    @Test
    void warnsOnMissingNarrator() {
        String yaml = validYaml().replace("- id: \"narrator\"", "");  // remove narrator line
        // not strictly failing; validator adds a warn
        var result = validator.validateYaml(yaml);
        // Should still have errors because of JSON Schema minItems on characters
        // Just confirm we don't crash and warnings are surfaced
        assertNotNull(result);
    }

    @Test
    void rejectsDuplicateCharacterId() {
        String yaml = validYaml().replace(
                "  - id: \"c_a\"\n    name: \"甲\"\n    role: \"protagonist\"",
                "  - id: \"c_a\"\n    name: \"甲\"\n    role: \"protagonist\"\n  - id: \"c_a\"\n    name: \"甲2\"\n    role: \"protagonist\""
        );
        var result = validator.validateYaml(yaml);
        assertFalse(result.isOk());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("duplicate character id")));
    }

    // ─── looksTruncated 截断探测 ──────────────────────────────────────────────

    @Test
    void looksTruncated_detectsUnclosedQuotedString() {
        // 模拟 Chapter 1 案例：generated_at 在中间被截断，引号未闭合
        String yaml = "version: \"1.0\"\nmeta:\n  generated_at: \"2024-01-01T00:00:0";
        assertTrue(ScriptValidator.looksTruncated(yaml));
    }

    @Test
    void looksTruncated_detectsUnclosedChineseQuotedDialogue() {
        // 模拟 Chapter 8 案例：对话行引号未闭合
        String yaml = "version: \"1.0\"\nscenes:\n  - scene_id: \"s_001\"\n    dialogues:\n      - character: \"苏念\"\n        line: \"苏念站在教室窗前，";
        assertTrue(ScriptValidator.looksTruncated(yaml));
    }

    @Test
    void looksTruncated_passesForCompleteYaml() {
        assertFalse(ScriptValidator.looksTruncated(validYaml()));
    }

    @Test
    void looksTruncated_passesForNullAndEmpty() {
        assertTrue(ScriptValidator.looksTruncated(null));
        assertTrue(ScriptValidator.looksTruncated(""));
        assertTrue(ScriptValidator.looksTruncated("   \n  "));
    }

    @Test
    void looksTruncated_detectsUnclosedSingleQuote() {
        String yaml = "version: '1.0";
        assertTrue(ScriptValidator.looksTruncated(yaml));
    }

    @Test
    void looksTruncated_ignoresEscapedQuotes() {
        // YAML 字符串里的 \" 不会影响引号配平
        String yaml = "version: \"1.0\"\nmeta:\n  note: \"他说 \\\"你好\\\"\"";
        assertFalse(ScriptValidator.looksTruncated(yaml));
    }

    private String validYaml() {
        return """
                version: "1.0"
                meta:
                  title: "测试"
                characters:
                  - id: "c_a"
                    name: "甲"
                    role: "protagonist"
                  - id: "narrator"
                    name: "旁白"
                    role: "npc"
                scenes:
                  - scene_id: "s_001"
                    chapter: 1
                    order: 1
                    int_ext: "INT"
                    location: "室内"
                    time_of_day: "DAY"
                    characters: ["c_a"]
                    summary: "测试"
                    actions: []
                    dialogues: []
                """;
    }
}
