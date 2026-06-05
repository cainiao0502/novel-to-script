package com.nailinai.noveltoscriptbackend.script;

import com.nailinai.noveltoscriptbackend.domain.script.Character;
import com.nailinai.noveltoscriptbackend.domain.script.Script;
import com.nailinai.noveltoscriptbackend.domain.script.Scene;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把每章生成的 Script 片段合并成一个全剧 Script：
 *  - characters 按 id 去重，后生成的角色可补回早期章节
 *  - scenes 全剧重排 scene_id（s_001, s_002, ...），并按 chapter 升序、order 升序排序
 *  - notes 拼接
 *  - meta.title / source_novel / episode / genre 从 projectMeta 覆盖
 */
@Component
public class ScriptMerger {

    public Script merge(String projectTitle, String sourceNovel, String genre,
                        List<Script> perChapterScripts) {
        Map<String, Character> characterMap = new LinkedHashMap<>();
        // 默认必有 narrator
        characterMap.put(Character.NARRATOR_ID, new Character(
                Character.NARRATOR_ID, "旁白", null, null, null,
                com.nailinai.noveltoscriptbackend.domain.script.Role.NPC, null, null));

        for (Script s : perChapterScripts) {
            if (s.characters() == null) continue;
            for (Character c : s.characters()) {
                if (c.id() == null) continue;
                characterMap.putIfAbsent(c.id(), c);
            }
        }

        // 收集并排序 scenes
        List<Scene> allScenes = new ArrayList<>();
        for (Script s : perChapterScripts) {
            if (s.scenes() == null) continue;
            allScenes.addAll(s.scenes());
        }
        allScenes.sort((a, b) -> {
            int c1 = a.chapter() == null ? 0 : a.chapter();
            int c2 = b.chapter() == null ? 0 : b.chapter();
            if (c1 != c2) return Integer.compare(c1, c2);
            int o1 = a.order() == null ? 0 : a.order();
            int o2 = b.order() == null ? 0 : b.order();
            return Integer.compare(o1, o2);
        });

        // 重排 scene_id
        List<Scene> renumbered = new ArrayList<>(allScenes.size());
        for (int i = 0; i < allScenes.size(); i++) {
            Scene s = allScenes.get(i);
            String newId = String.format("s_%03d", i + 1);
            renumbered.add(new Scene(
                    newId, s.chapter(), s.order(), s.intExt(), s.location(),
                    s.timeOfDay(), s.characters(), s.summary(), s.actions(),
                    s.dialogues(), s.voiceover(), s.props(), s.sfx(),
                    s.musicCue(), s.cameraHint()
            ));
        }

        List<String> notes = new ArrayList<>();
        for (Script s : perChapterScripts) {
            if (s.notes() != null) notes.addAll(s.notes());
        }

        com.nailinai.noveltoscriptbackend.domain.script.Meta meta =
                new com.nailinai.noveltoscriptbackend.domain.script.Meta(
                        projectTitle, sourceNovel, null, genre, null,
                        renumbered.size(), java.time.Instant.now()
                );

        return new Script(Script.CURRENT_VERSION, meta,
                new ArrayList<>(characterMap.values()), renumbered, notes);
    }
}
