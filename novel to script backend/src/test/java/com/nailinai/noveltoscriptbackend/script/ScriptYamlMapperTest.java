package com.nailinai.noveltoscriptbackend.script;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScriptYamlMapperTest {

    private final ScriptYamlMapper mapper = new ScriptYamlMapper();

    @Test
    void roundTripsMinimalValidScript() {
        String yaml = """
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
                    summary: "测试场景"
                    actions: ["甲进入"]
                    dialogues:
                      - character: "c_a"
                        line: "你好"
                """;
        var script = assertDoesNotThrow(() -> mapper.fromYaml(yaml));
        assertEquals("1.0", script.version());
        assertEquals("测试", script.meta().title());
        assertEquals(2, script.characters().size());
        assertEquals(1, script.scenes().size());
        String again = mapper.toYaml(script);
        assertTrue(again.contains("characters:"));
        assertTrue(again.contains("scenes:"));
    }

    @Test
    void rejectsUnknownField() {
        String yaml = """
                version: "1.0"
                meta:
                  title: "测试"
                  extra_field: "illegal"
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
        assertThrows(ScriptException.class, () -> mapper.fromYaml(yaml));
    }

    @Test
    void emptyYamlThrows() {
        assertThrows(ScriptException.class, () -> mapper.fromYaml(""));
    }
}
