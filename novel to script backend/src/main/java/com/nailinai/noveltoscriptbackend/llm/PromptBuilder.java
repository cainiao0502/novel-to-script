package com.nailinai.noveltoscriptbackend.llm;

import com.nailinai.noveltoscriptbackend.domain.script.Character;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class PromptBuilder {

    private static final String YAML_TEMPLATE = """
            version: "1.0"
            meta:
              title: "剧本标题"
              source_novel: "原著出处"
              episode: 1
              genre: "短剧/古装"
              logline: "一句话梗概"
              total_scenes: 2
              generated_at: "2024-01-01T00:00:00Z"
            characters:
              - id: "narrator"
                name: "旁白"
                role: "npc"
              - id: "zhong_xiaodie"
                name: "钟小蝶"
                role: "protagonist"
                gender: "女"
                age_range: "25-30"
              - id: "lihao"
                name: "李浩"
                role: "supporting"
                gender: "男"
            scenes:
              - scene_id: "s_001"
                chapter: 1
                order: 1
                int_ext: "INT"
                location: "将军府大厅"
                time_of_day: "DAY"
                characters: ["narrator", "zhong_xiaodie", "lihao"]
                summary: "钟小蝶与李浩在将军府对峙"
                actions:
                  - "钟小蝶快步走进大厅，神色凝重"
                  - "李浩站起身，挡在她面前"
                dialogues:
                  - character: "zhong_xiaodie"
                    parenthetical: "（冷冷地）"
                    line: "让开。"
                    emotion: "愤怒"
                  - character: "lihao"
                    line: "你不能进去。"
                    emotion: "坚定"
              - scene_id: "s_002"
                chapter: 1
                order: 2
                int_ext: "INT"
                location: "将军府书房"
                time_of_day: "NIGHT"
                characters: ["narrator", "zhong_xiaodie"]
                summary: "钟小蝶独自在书房查找线索"
                actions:
                  - "她点燃蜡烛，翻开桌上的卷宗"
                dialogues: []
                voiceover:
                  - character: "narrator"
                    line: "夜深了，将军府的书房里只有烛火摇曳。"
            """;

    private static final String SYSTEM_PROMPT =
            "你是一名资深影视剧本改编。请阅读【小说章节正文】，\n" +
            "按以下 YAML 模板的**结构**输出该章节的剧本片段。\n" +
            "严格遵循模板中的字段名、层级、枚举值，不要自创字段。\n\n" +
            "模板如下（字段名不可变更，不可增减）：\n" +
            "```yaml\n" +
            YAML_TEMPLATE +
            "```\n\n" +
            "严格约束：\n" +
            "1. 只输出合法 YAML，不要包裹 ```yaml 等代码块标记，不要任何前言后语\n" +
            "2. 顶层字段必须只有：version / meta / characters / scenes\n" +
            "3. characters 数组必须包含 id=\"narrator\" 的人物（旁白）\n" +
            "4. scene.characters 和 dialogue.character 只能引用已声明的 Character.id，禁止使用 others/unknown 等占位符\n" +
            "5. 不要自创未在模板中出现的字段\n" +
            "6. actions / dialogues 数组可为空 []，但**必须存在**\n" +
            "7. parenthetical 若出现必须用全角括号（……），例如（微笑）、（低声说）\n" +
            "8. time_of_day 只能取 DAY/NIGHT/DAWN/DUSK/CONTINUOUS\n" +
            "9. int_ext 只能取 INT/EXT/INT-EXT\n" +
            "10. role 只能取 protagonist/antagonist/supporting/npc\n" +
            "11. scene_id 格式如 s_001, s_002 ... 全剧递增\n" +
            "12. 每章至少 1 场戏；多场景章节拆 2-5 场\n" +
            "13. 输出必须是**完整**的 YAML，确保所有引号闭合、scenes 数组以 ] 收尾，**绝不要中途截断**\n" +
            "14. 输出末尾不要追加任何说明文字、不要 ``` 围栏、不要 markdown 装饰\n" +
            "15. 所有 string 字段值必须用英文双引号 \"…\" 包裹；字段值内若含特殊字符（: , # $ @ \\ ` { } [ ] 等）必须用引号包好\n" +
            "16. 严禁在任何字段（尤其 line / actions / summary）中直接以 $ 开头或单独出现 $ 字符（金钱/变量用「金」/「奖励点」等中文替代，或整段用引号包裹）\n" +
            "17. meta.generated_at 用 ISO8601 字符串，例如 \"2024-01-01T00:00:00Z\"，**必须完整闭合**\n" +
            "18. 若章节正文中出现超过 4 场戏的素材，仍只输出 2-5 场代表性场景（避免 YAML 过长被截断）\n";

    /** 单章生成 prompt。 */
    public Map<String, String> chapterPrompt(String chapterTitle,
                                              int chapterIndex,
                                              int totalChapters,
                                              String chapterText,
                                              List<Character> knownCharacters) {
        String knownCharsYaml = knownCharacters.isEmpty()
                ? "（无前文人物）"
                : knownCharacters.stream()
                        .map(c -> "  - id: \"%s\"\n    name: \"%s\""
                                .formatted(c.id(), c.name() == null ? "" : c.name()))
                        .collect(Collectors.joining("\n"));

        String user = """
                # 章节信息
                标题：%s
                章节号：第 %d / %d 章

                # 当前已知人物（来自前文，请直接引用其 id；如需新人物请新增 id）
                characters:
                %s

                # 小说章节正文
                %s
                """.formatted(chapterTitle, chapterIndex, totalChapters, knownCharsYaml, chapterText);

        return Map.of("system", SYSTEM_PROMPT, "user", user);
    }
}
