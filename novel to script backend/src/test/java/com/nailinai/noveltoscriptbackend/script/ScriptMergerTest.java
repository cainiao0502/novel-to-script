package com.nailinai.noveltoscriptbackend.script;

import com.nailinai.noveltoscriptbackend.domain.script.Character;
import com.nailinai.noveltoscriptbackend.domain.script.Dialogue;
import com.nailinai.noveltoscriptbackend.domain.script.IntExt;
import com.nailinai.noveltoscriptbackend.domain.script.Meta;
import com.nailinai.noveltoscriptbackend.domain.script.Role;
import com.nailinai.noveltoscriptbackend.domain.script.Scene;
import com.nailinai.noveltoscriptbackend.domain.script.Script;
import com.nailinai.noveltoscriptbackend.domain.script.TimeOfDay;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScriptMergerTest {

    private final ScriptMerger merger = new ScriptMerger();

    @Test
    void renumbersScenesAcrossChapters() {
        Script ch1 = new Script("1.0",
                new Meta("测试", null, null, "短剧", null, null, Instant.now()),
                List.of(
                        new Character("c_a", "甲", null, "男", null, Role.PROTAGONIST, null, null),
                        new Character(Character.NARRATOR_ID, "旁白", null, null, null, Role.NPC, null, null)
                ),
                List.of(
                        scene("s_001", 1, 1),
                        scene("s_002", 1, 2)
                ),
                null);
        Script ch2 = new Script("1.0",
                new Meta("测试", null, null, "短剧", null, null, Instant.now()),
                List.of(
                        new Character("c_b", "乙", null, "女", null, Role.ANTAGONIST, null, null),
                        new Character(Character.NARRATOR_ID, "旁白", null, null, null, Role.NPC, null, null)
                ),
                List.of(
                        scene("s_001", 2, 1)
                ),
                null);

        Script merged = merger.merge("测试", null, "短剧", List.of(ch1, ch2));

        assertEquals(3, merged.scenes().size());
        assertEquals("s_001", merged.scenes().get(0).sceneId());
        assertEquals("s_002", merged.scenes().get(1).sceneId());
        assertEquals("s_003", merged.scenes().get(2).sceneId());
        // sort by chapter then order
        assertEquals(1, merged.scenes().get(0).chapter());
        assertEquals(1, merged.scenes().get(1).chapter());
        assertEquals(2, merged.scenes().get(2).chapter());
        // characters: narrator + c_a + c_b
        assertEquals(3, merged.characters().size());
        assertTrue(merged.characters().stream().anyMatch(c -> "c_a".equals(c.id())));
        assertTrue(merged.characters().stream().anyMatch(c -> "c_b".equals(c.id())));
    }

    @Test
    void alwaysIncludesNarrator() {
        Script ch1 = new Script("1.0",
                new Meta("t", null, null, null, null, null, Instant.now()),
                List.of(new Character("c_a", "甲", null, null, null, Role.PROTAGONIST, null, null)),
                List.of(scene("s_001", 1, 1)),
                null);
        Script merged = merger.merge("t", null, null, List.of(ch1));
        assertTrue(merged.characters().stream().anyMatch(Character::isNarrator));
    }

    private Scene scene(String id, int chapter, int order) {
        return new Scene(id, chapter, order, IntExt.INT, "室内", TimeOfDay.DAY,
                List.of("c_a"), "summary", List.of("a"), List.of(), List.of(),
                List.of(), List.of(), null, null);
    }
}
