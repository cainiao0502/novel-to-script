package com.nailinai.noveltoscriptbackend.script;

import com.nailinai.noveltoscriptbackend.llm.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 对白局部改写服务。
 * 根据场景上下文 + 风格指令，让 LLM 重写一句对白。
 */
@Service
public class DialogueRewriteService {

    private static final Logger log = LoggerFactory.getLogger(DialogueRewriteService.class);

    private final LlmClient llmClient;

    public DialogueRewriteService(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    private static final String SYSTEM_PROMPT = """
            你是一名资深影视编剧，擅长打磨对白。
            根据场景上下文和用户指定的风格要求，重写目标对白。
            严格约束：
            1. 只返回改写后的台词文本，不要加引号、不要加人物名、不要加任何解释或前言
            2. 保持原对白的核心信息和意图，只改变表达风格
            3. 字数与原对白保持相近（±30%）
            4. 使用中文全角标点
            """;

    /**
     * 重写一句对白。
     *
     * @param currentLine 当前对白文本
     * @param style       风格：dramatic / humorous / concise / colloquial
     * @param context     场景上下文（summary + 前后对白）
     * @return 改写后的对白文本
     */
    public String rewrite(String currentLine, String style, String context) {
        String styleInstruction = switch (style) {
            case "dramatic" -> "更戏剧化：增强冲突感和张力，使用更有力的措辞，让台词更有舞台冲击力";
            case "humorous" -> "更幽默：加入诙谐、俏皮或反讽元素，让台词更轻松有趣";
            case "concise" -> "更简洁：精简冗余词语，用最少的字传达核心意思，短促有力";
            case "colloquial" -> "更口语化：像日常对话一样自然，使用口头表达、语气词，降低书面感";
            default -> "更自然流畅";
        };

        String userPrompt = """
                风格要求：%s
                
                场景上下文：
                %s
                
                待改写对白：
                %s
                
                请重写以上对白。
                """.formatted(styleInstruction, context, currentLine);

        log.info("Rewriting dialogue — style: {}, line length: {}", style, currentLine.length());
        return llmClient.chat(SYSTEM_PROMPT, userPrompt).trim();
    }
}
