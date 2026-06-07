package com.nailinai.noveltoscriptbackend.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nailinai.noveltoscriptbackend.config.LlmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiCompatibleClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleClient.class);

    private final WebClient webClient;
    private final LlmProperties props;

    public OpenAiCompatibleClient(WebClient llmWebClient, LlmProperties props) {
        this.webClient = llmWebClient;
        this.props = props;
        if (props.getApiKey() == null || props.getApiKey().isBlank()) {
            log.warn("LLM API key is empty. Set APP_LLM_API_KEY in .env before generation.");
        }
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        return chat(systemPrompt, userPrompt, props.getMaxTokens());
    }

    @Override
    public String chat(String systemPrompt, String userPrompt, int maxTokens) {
        return chatWithHistory(List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ), maxTokens);
    }

    @Override
    public String chatWithHistory(List<Map<String, String>> messages) {
        return chatWithHistory(messages, 4096);
    }

    @SuppressWarnings("unchecked")
    private String chatWithHistory(List<Map<String, String>> messages, int maxTokens) {
        if (props.getApiKey() == null || props.getApiKey().isBlank()) {
            throw new LlmException("LLM API key is not configured (APP_LLM_API_KEY)");
        }
        ChatRequest body = new ChatRequest(props.getModel(), messages, 0.4, maxTokens);
        try {
            Map<String, Object> response = webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                    .onErrorResume(e -> Mono.error(new LlmException("LLM call failed: " + e.getMessage(), e)))
                    .block();
            if (response == null) {
                throw new LlmException("LLM returned empty response");
            }
            return extractContent(response);
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("LLM call failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> response) {
        Object choicesObj = response.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
            throw new LlmException("LLM response missing choices");
        }
        Object first = choices.get(0);
        if (!(first instanceof Map<?, ?> firstMap)) {
            throw new LlmException("LLM choice is malformed");
        }
        Object message = firstMap.get("message");
        if (!(message instanceof Map<?, ?> msgMap)) {
            throw new LlmException("LLM message is malformed");
        }
        Object content = msgMap.get("content");
        if (content == null) {
            throw new LlmException("LLM content is empty");
        }
        return stripCodeFences(content.toString());
    }

    /**
     * 剥离 LLM 输出中可能出现的 Markdown 代码围栏（```yaml … ``` / ```json … ``` / ``` … ```）。
     * 部分模型即使 prompt 明确要求"不要包裹 ```"，仍会输出带围栏的内容，
     * 直接进入 YAML/JSON 解析会因首字符为反引号而失败。
     * 该方法按以下顺序处理（多策略兜底）：
     *   1. 整体被围栏包裹 → 抽取首对围栏之间的内容
     *   2. 任意一行以 ``` 开头（游离围栏）→ 整行删除；删除前先把首个 ``` 之后
     *      的一切截断（涵盖「``` + 多行散文」的情况）
     *   3. 末尾还残留 ```（同行带说明）→ 切到最末一个 ``` 之前
     *   4. 无任何围栏 → 原样返回
     */
    static String stripCodeFences(String raw) {
        if (raw == null) return "";
        String s = raw.strip();
        if (s.isEmpty()) return s;

        // 1) 整体被围栏包裹：抽取首对围栏之间的内容
        if (s.startsWith("```")) {
            int openFenceEnd = s.indexOf('\n');
            if (openFenceEnd > 0) {
                int closeFence = s.indexOf("```", openFenceEnd);
                if (closeFence > openFenceEnd) {
                    return s.substring(openFenceEnd + 1, closeFence).strip();
                }
                // 没有匹配的右围栏 → 退化为「从首围栏下一行到末尾」
                return s.substring(openFenceEnd + 1).strip();
            }
        }

        // 2a) 找到第一个游离的 ```，把从那里开始的所有内容截断（含同行/后续散文）
        int firstStray = s.indexOf("```");
        if (firstStray >= 0) {
            s = s.substring(0, firstStray).stripTrailing();
        }

        // 2b) 兜底：再清一遍（理论上 2a 已把所有 ``` 处理完），防御 NUL/拼接异常
        String[] lines = s.split("\n");
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String line : lines) {
            if (line.stripLeading().startsWith("```")) continue;
            if (!first) sb.append('\n');
            sb.append(line);
            first = false;
        }
        return sb.toString().strip();
    }

    @Override
    public String name() {
        return "openAiCompatible";
    }

    public record ChatRequest(
            String model,
            List<Map<String, String>> messages,
            @JsonProperty("temperature") Double temperature,
            @JsonProperty("max_tokens") Integer maxTokens
    ) {
        public ChatRequest(String model, List<Map<String, String>> messages) {
            this(model, messages, 0.4, 4096);
        }
    }
}
